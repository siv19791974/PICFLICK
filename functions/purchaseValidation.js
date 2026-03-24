const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');
let googleApi = null;

const VALIDATE_PURCHASE_RATE_LIMIT_WINDOW_MS = 60 * 1000;
const VALIDATE_PURCHASE_RATE_LIMIT_MAX = 10;

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

  const { purchaseToken, productId, packageName = 'com.picflick.app' } = data;
  const userId = context.auth.uid;

  if (!purchaseToken || !productId) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing purchase token or product ID');
  }

  await enforceValidatePurchaseRateLimit(userId);

  try {
    if (!googleApi) {
      googleApi = require('googleapis').google;
    }

    // Get service account credentials from Firebase config
    let serviceAccount = functions.config().googleplay.service_account;

    if (typeof serviceAccount === 'string') {
      if (serviceAccount.trim() === '-' || serviceAccount.trim().length === 0) {
        serviceAccount = null;
      } else {
        try {
          serviceAccount = JSON.parse(serviceAccount);
        } catch (parseError) {
          console.error('Failed to parse googleplay.service_account JSON:', parseError.message);
          serviceAccount = null;
        }
      }
    }

    if (!serviceAccount || !serviceAccount.client_email || !serviceAccount.private_key) {
      console.error('Google Play service account not configured correctly');
      throw new functions.https.HttpsError(
        'failed-precondition',
        'Google Play validation is not configured on the server'
      );
    }

    const privateKey = String(serviceAccount.private_key)
      .replace(/^"([\s\S]*)"$/, '$1')
      .replace(/\\r/g, '')
      .replace(/\\\\n/g, '\n')
      .replace(/\\n/g, '\n')
      .trim();

    if (!privateKey.includes('BEGIN PRIVATE KEY')) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'Google Play private key is malformed in server config'
      );
    }

    console.log('Google Play key format check', {
      hasBegin: privateKey.includes('BEGIN PRIVATE KEY'),
      hasEnd: privateKey.includes('END PRIVATE KEY'),
      newlineCount: (privateKey.match(/\n/g) || []).length
    });

    // Create JWT client for Google Play API
    const jwtClient = new googleApi.auth.JWT(
      serviceAccount.client_email,
      null,
      privateKey,
      ['https://www.googleapis.com/auth/androidpublisher']
    );

    await jwtClient.authorize();

    // Call Google Play Developer API
    const androidpublisher = googleApi.androidpublisher({
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

      let expiredCount = 0;

      const downgradePromises = [];
      expiredUsers.forEach(doc => {
        const userData = doc.data();

        // Don't downgrade if auto-renewing (Google Play handles renewal)
        if (!userData.autoRenewing) {
          downgradePromises.push(downgradeToFree({ userId: doc.id }));
          expiredCount++;

          // Send notification to user
          downgradePromises.push(sendExpiryNotification(doc.id, userData));
        }
      });

      await Promise.all(downgradePromises);
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
    if (req.method !== 'POST') {
      return res.status(405).send('Method not allowed');
    }

    if (!verifyRealtimeNotificationRequest(req)) {
      console.error('Realtime notification signature verification failed');
      return res.status(401).send('Unauthorized');
    }

    const notification = parseRealtimeNotification(req.body);
    if (!notification) {
      return res.status(400).send('Invalid payload');
    }

    console.log('Received realtime notification:', JSON.stringify(notification));

    // Handle different notification types
    switch (notification.notificationType) {
      case 1: // SUBSCRIPTION_RECOVERED
        await reactivateSubscription(notification);
        break;
      case 2: // SUBSCRIPTION_RENEWED
        await updateSubscriptionExpiry(notification);
        break;
      case 3: // SUBSCRIPTION_CANCELED
        await markCancellation(notification);
        break;
      case 13: // SUBSCRIPTION_EXPIRED
        await downgradeToFree(notification);
        break;
      default:
        console.log('Unhandled notification type:', notification.notificationType);
    }

    return res.status(200).send('OK');
  } catch (error) {
    console.error('Failed to handle realtime notification:', error);
    return res.status(500).send('Error');
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
 * Helper functions for realtime notifications
 */
function verifyRealtimeNotificationRequest(req) {
  const sharedSecret = functions.config().googleplay?.rtdn_secret;
  if (!sharedSecret) {
    console.error('Missing googleplay.rtdn_secret for RTDN verification');
    return false;
  }

  const provided = req.get('x-picflick-rtdn-signature') || '';
  if (!provided) return false;

  const payload = typeof req.body === 'string' ? req.body : JSON.stringify(req.body || {});
  const expected = crypto
    .createHmac('sha256', sharedSecret)
    .update(payload)
    .digest('hex');

  if (provided.length !== expected.length) return false;
  return crypto.timingSafeEqual(Buffer.from(provided), Buffer.from(expected));
}

function parseRealtimeNotification(body) {
  const messageData = body?.message?.data;
  if (!messageData) return null;

  const decoded = Buffer.from(messageData, 'base64').toString('utf8');
  const parsed = JSON.parse(decoded);
  const sub = parsed?.subscriptionNotification;
  if (!sub) return null;

  return {
    notificationType: sub.notificationType,
    purchaseToken: sub.purchaseToken,
    productId: sub.subscriptionId,
    packageName: parsed.packageName
  };
}

async function resolveUserIdForNotification(notification) {
  if (notification.userId) return notification.userId;

  const token = notification.purchaseToken;
  if (!token) return null;

  const snapshot = await db.collection('users')
    .where('purchaseToken', '==', token)
    .limit(1)
    .get();

  if (snapshot.empty) return null;
  return snapshot.docs[0].id;
}

async function reactivateSubscription(notification) {
  const userId = await resolveUserIdForNotification(notification);
  if (!userId) return;

  await db.collection('users').doc(userId).update({
    subscriptionActive: true,
    autoRenewing: true,
    subscriptionRecoveredAt: admin.firestore.FieldValue.serverTimestamp()
  });
}

async function updateSubscriptionExpiry(notification) {
  const userId = await resolveUserIdForNotification(notification);
  if (!userId) return;

  // Expiry refresh should still be handled by validatePurchase; here we mark renewal receipt.
  await db.collection('users').doc(userId).update({
    autoRenewing: true,
    subscriptionRenewedAt: admin.firestore.FieldValue.serverTimestamp(),
    productId: notification.productId || admin.firestore.FieldValue.delete(),
    purchaseToken: notification.purchaseToken || admin.firestore.FieldValue.delete()
  });
}

async function markCancellation(notification) {
  const userId = await resolveUserIdForNotification(notification);
  if (!userId) return;

  await db.collection('users').doc(userId).update({
    autoRenewing: false,
    cancellationMarkedAt: admin.firestore.FieldValue.serverTimestamp()
  });
}

async function downgradeToFree(notification) {
  const userId = await resolveUserIdForNotification(notification);
  if (!userId) return;

  await db.collection('users').doc(userId).update({
    subscriptionTier: 'FREE',
    subscriptionActive: false,
    subscriptionExpiryDate: null,
    purchaseToken: null,
    productId: null,
    autoRenewing: false,
    subscriptionDowngradedAt: admin.firestore.FieldValue.serverTimestamp()
  });
}

async function enforceValidatePurchaseRateLimit(userId) {
  const now = Date.now();
  const ref = db.collection('billingRateLimits').doc(userId);

  await db.runTransaction(async tx => {
    const doc = await tx.get(ref);
    const current = doc.exists ? doc.data() : { windowStartMs: now, attempts: 0 };

    const elapsed = now - (current.windowStartMs || now);
    const withinWindow = elapsed < VALIDATE_PURCHASE_RATE_LIMIT_WINDOW_MS;
    const attempts = withinWindow ? (current.attempts || 0) : 0;

    if (attempts >= VALIDATE_PURCHASE_RATE_LIMIT_MAX) {
      throw new functions.https.HttpsError(
        'resource-exhausted',
        'Too many purchase validations. Please try again shortly.'
      );
    }

    tx.set(ref, {
      windowStartMs: withinWindow ? current.windowStartMs : now,
      attempts: attempts + 1,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  });
}
