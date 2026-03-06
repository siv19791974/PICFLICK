const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { google } = require('googleapis');

// Initialize Firebase Admin
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Validate Google Play purchase receipt
 * This function verifies purchases with Google Play Developer API
 * and updates user's subscription in Firestore
 */
exports.validatePurchase = functions.https.onCall(async (data, context) => {
  // Check authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const { purchaseToken, productId, packageName = 'com.example.picflick' } = data;
  const userId = context.auth.uid;

  if (!purchaseToken || !productId) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing purchase token or product ID');
  }

  try {
    // Get service account credentials from Firebase config
    const serviceAccount = functions.config().googleplay.service_account;
    
    if (!serviceAccount) {
      console.error('Google Play service account not configured');
      // For development: skip validation but log warning
      // In production, this should fail
      return await handleValidationBypass(userId, purchaseToken, productId);
    }

    // Create JWT client for Google Play API
    const jwtClient = new google.auth.JWT(
      serviceAccount.client_email,
      null,
      serviceAccount.private_key,
      ['https://www.googleapis.com/auth/androidpublisher']
    );

    await jwtClient.authorize();

    // Call Google Play Developer API
    const androidpublisher = google.androidpublisher({
      version: 'v3',
      auth: jwtClient
    });

    const response = await androidpublisher.purchases.subscriptions.get({
      packageName: packageName,
      subscriptionId: productId,
      token: purchaseToken
    });

    const purchaseData = response.data;

    // Check if purchase is valid
    if (purchaseData.paymentState !== 1) { // 1 = payment received
      throw new functions.https.HttpsError('failed-precondition', 
        `Invalid purchase state: ${purchaseData.paymentState}`);
    }

    // Determine subscription tier from product ID
    const tier = getTierFromProductId(productId);
    if (!tier) {
      throw new functions.https.HttpsError('invalid-argument', 'Unknown product ID');
    }

    // Check if this is a yearly subscription
    const isYearly = productId.includes('yearly');

    // Calculate expiry date
    const expiryMillis = parseInt(purchaseData.expiryTimeMillis);
    const expiryDate = new Date(expiryMillis);

    // Update user's subscription in Firestore
    await updateUserSubscription(userId, {
      tier: tier,
      isYearly: isYearly,
      purchaseToken: purchaseToken,
      productId: productId,
      expiryDate: expiryDate,
      orderId: purchaseData.orderId,
      autoRenewing: purchaseData.autoRenewing || false,
      validatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Log successful validation
    console.log(`Purchase validated for user ${userId}: ${productId}, expires ${expiryDate}`);

    return {
      success: true,
      tier: tier,
      isYearly: isYearly,
      expiryDate: expiryDate.toISOString(),
      autoRenewing: purchaseData.autoRenewing || false
    };

  } catch (error) {
    console.error('Purchase validation failed:', error);
    
    if (error.code === 404) {
      throw new functions.https.HttpsError('not-found', 'Purchase not found in Google Play');
    }
    
    throw new functions.https.HttpsError('internal', `Validation failed: ${error.message}`);
  }
});

/**
 * Handle purchase cancellation
 */
exports.handlePurchaseCancelled = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const { productId } = data;
  const userId = context.auth.uid;

  try {
    // Downgrade user to free tier
    await db.collection('users').doc(userId).update({
      subscriptionTier: 'FREE',
      subscriptionExpiryDate: null,
      purchaseToken: null,
      productId: null,
      cancelledAt: admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`User ${userId} subscription cancelled for ${productId}`);
    
    return { success: true };
  } catch (error) {
    console.error('Failed to handle cancellation:', error);
    throw new functions.https.HttpsError('internal', 'Failed to process cancellation');
  }
});

/**
 * Scheduled function to check expired subscriptions
 * Runs daily at midnight
 */
exports.checkExpiredSubscriptions = functions.pubsub
  .schedule('0 0 * * *') // Every day at midnight
  .timeZone('UTC')
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();
    
    try {
      // Find users with expired subscriptions
      const expiredUsers = await db.collection('users')
        .where('subscriptionExpiryDate', '<', now)
        .where('subscriptionTier', '!=', 'FREE')
        .get();

      const batch = db.batch();
      let expiredCount = 0;

      expiredUsers.forEach(doc => {
        const userData = doc.data();
        
        // Don't downgrade if auto-renewing (Google Play handles renewal)
        if (!userData.autoRenewing) {
          batch.update(doc.ref, {
            subscriptionTier: 'FREE',
            subscriptionDowngradedAt: admin.firestore.FieldValue.serverTimestamp()
          });
          expiredCount++;
          
          // Send notification to user
          sendExpiryNotification(doc.id, userData);
        }
      });

      await batch.commit();
      console.log(`Processed ${expiredCount} expired subscriptions`);
      
      return null;
    } catch (error) {
      console.error('Failed to check expired subscriptions:', error);
      return null;
    }
  });

/**
 * Real-time subscription monitoring via Pub/Sub
 * Google Play can send real-time notifications
 */
exports.handleRealtimeNotification = functions.https.onRequest(async (req, res) => {
  try {
    const notification = req.body;
    
    // Verify notification signature (implement proper security)
    // For now, just log it
    console.log('Received realtime notification:', notification);
    
    // Handle different notification types
    switch (notification.notificationType) {
      case 'SUBSCRIPTION_RECOVERED':
        // Payment was recovered, reactivate subscription
        await reactivateSubscription(notification);
        break;
        
      case 'SUBSCRIPTION_RENEWED':
        // Subscription renewed, update expiry
        await updateSubscriptionExpiry(notification);
        break;
        
      case 'SUBSCRIPTION_CANCELED':
        // User cancelled, mark for downgrade at expiry
        await markCancellation(notification);
        break;
        
      case 'SUBSCRIPTION_EXPIRED':
        // Subscription expired, downgrade to free
        await downgradeToFree(notification);
        break;
        
      default:
        console.log('Unhandled notification type:', notification.notificationType);
    }
    
    res.status(200).send('OK');
  } catch (error) {
    console.error('Failed to handle realtime notification:', error);
    res.status(500).send('Error');
  }
});

/**
 * Helper: Get tier from product ID
 */
function getTierFromProductId(productId) {
  if (productId.includes('standard')) return 'STANDARD';
  if (productId.includes('plus')) return 'PLUS';
  if (productId.includes('pro')) return 'PRO';
  if (productId.includes('ultra')) return 'ULTRA';
  return null;
}

/**
 * Helper: Update user subscription in Firestore
 */
async function updateUserSubscription(userId, subscriptionData) {
  const userRef = db.collection('users').doc(userId);
  
  await userRef.update({
    subscriptionTier: subscriptionData.tier,
    isYearly: subscriptionData.isYearly,
    purchaseToken: subscriptionData.purchaseToken,
    productId: subscriptionData.productId,
    subscriptionExpiryDate: admin.firestore.Timestamp.fromDate(subscriptionData.expiryDate),
    orderId: subscriptionData.orderId,
    autoRenewing: subscriptionData.autoRenewing,
    subscriptionValidatedAt: subscriptionData.validatedAt,
    subscriptionActive: true
  });
}

/**
 * Helper: Send expiry notification to user
 */
async function sendExpiryNotification(userId, userData) {
  try {
    // Create in-app notification
    await db.collection('notifications').add({
      userId: userId,
      type: 'SUBSCRIPTION_EXPIRED',
      title: 'Subscription Expired',
      message: 'Your PicFlick subscription has expired. Upgrade to continue enjoying premium features!',
      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Send push notification if FCM token exists
    if (userData.fcmToken) {
      await admin.messaging().send({
        token: userData.fcmToken,
        notification: {
          title: 'Subscription Expired',
          body: 'Your subscription has expired. Tap to upgrade and keep your premium features.'
        },
        data: {
          type: 'SUBSCRIPTION_EXPIRED',
          screen: 'SUBSCRIPTION'
        }
      });
    }
  } catch (error) {
    console.error('Failed to send expiry notification:', error);
  }
}

/**
 * Development helper: Bypass validation (DANGEROUS - remove in production!)
 */
async function handleValidationBypass(userId, purchaseToken, productId) {
  console.warn(`VALIDATION BYPASSED for user ${userId}. This should only happen in development!`);
  
  const tier = getTierFromProductId(productId);
  const isYearly = productId.includes('yearly');
  
  // Set expiry to 1 year from now for development
  const expiryDate = new Date();
  expiryDate.setFullYear(expiryDate.getFullYear() + 1);
  
  await updateUserSubscription(userId, {
    tier: tier,
    isYearly: isYearly,
    purchaseToken: purchaseToken,
    productId: productId,
    expiryDate: expiryDate,
    orderId: 'DEV_BYPASS',
    autoRenewing: true,
    validatedAt: admin.firestore.FieldValue.serverTimestamp()
  });
  
  return {
    success: true,
    tier: tier,
    isYearly: isYearly,
    expiryDate: expiryDate.toISOString(),
    autoRenewing: true,
    warning: 'Development mode - validation bypassed'
  };
}

/**
 * Helper functions for realtime notifications
 */
async function reactivateSubscription(notification) {
  // Implementation for subscription recovery
  console.log('Reactivating subscription:', notification);
}

async function updateSubscriptionExpiry(notification) {
  // Update expiry date based on renewal
  console.log('Updating subscription expiry:', notification);
}

async function markCancellation(notification) {
  // Mark subscription for future downgrade
  console.log('Marking subscription for cancellation:', notification);
}

async function downgradeToFree(notification) {
  // Downgrade user to free tier
  console.log('Downgrading to free:', notification);
}
