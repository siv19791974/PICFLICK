const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin
admin.initializeApp();

/**
 * Cloud Function: Send push notification when a new notification document is created
 * This function listens to the 'notifications' collection and sends FCM push
 */
exports.sendPushNotification = functions.firestore
  .document('notifications/{notificationId}')
  .onCreate(async (snap, context) => {
    const notification = snap.data();
    
    console.log('New notification created:', context.params.notificationId);
    console.log('Notification data:', JSON.stringify(notification));
    
    // Don't send push for the sender's own actions
    if (notification.userId === notification.senderId) {
      console.log('Skipping - sender and recipient are the same');
      return null;
    }
    
    // Get recipient's FCM token
    try {
      const userDoc = await admin.firestore()
        .collection('users')
        .doc(notification.userId)
        .get();
      
      if (!userDoc.exists) {
        console.log('Recipient user not found:', notification.userId);
        return null;
      }
      
      const userData = userDoc.data();
      const fcmToken = userData.fcmToken;
      
      if (!fcmToken) {
        console.log('No FCM token for user:', notification.userId);
        return null;
      }
      
      console.log('Found FCM token for user:', notification.userId);
      
      // Build notification message
      const message = {
        token: fcmToken,
        notification: {
          title: notification.title || 'PicFlick',
          body: notification.message || 'You have a new notification',
        },
        data: {
          notificationId: context.params.notificationId,
          type: notification.type || 'GENERIC',
          senderId: notification.senderId || '',
          senderName: notification.senderName || '',
          flickId: notification.flickId || '',
          click_action: 'FLUTTER_NOTIFICATION_CLICK',
        },
        android: {
          notification: {
            channelId: 'picflick_notifications',
            priority: 'high',
            sound: 'default',
          },
        },
        apns: {
          payload: {
            aps: {
              sound: 'default',
              badge: 1,
            },
          },
        },
      };
      
      // Send the push notification
      const response = await admin.messaging().send(message);
      console.log('Push notification sent successfully:', response);
      
      // Update the notification document with sent status
      await snap.ref.update({
        pushSent: true,
        pushSentAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      
      return response;
      
    } catch (error) {
      console.error('Error sending push notification:', error);
      
      // Update notification with error
      await snap.ref.update({
        pushSent: false,
        pushError: error.message,
      });
      
      return null;
    }
  });

/**
 * Cloud Function: Clean up old notifications (run daily)
 * Deletes notifications older than 30 days
 */
exports.cleanupOldNotifications = functions.pubsub
  .schedule('0 0 * * *') // Run at midnight every day
  .timeZone('UTC')
  .onRun(async (context) => {
    const thirtyDaysAgo = admin.firestore.Timestamp.fromDate(
      new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
    );
    
    const oldNotifications = await admin.firestore()
      .collection('notifications')
      .where('timestamp', '<', thirtyDaysAgo)
      .limit(500)
      .get();
    
    console.log(`Found ${oldNotifications.size} old notifications to delete`);
    
    const batch = admin.firestore().batch();
    oldNotifications.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });
    
    await batch.commit();
    console.log(`Deleted ${oldNotifications.size} old notifications`);
    
    return null;
  });

/**
 * Cloud Function: Send welcome notification when new user joins
 */
exports.sendWelcomeNotification = functions.firestore
  .document('users/{userId}')
  .onCreate(async (snap, context) => {
    const user = snap.data();
    
    // Don't send if it's a new signup from existing user
    if (user.createdAt && (Date.now() - user.createdAt.toMillis()) > 60000) {
      return null;
    }
    
    // Create welcome notification
    const welcomeNotif = {
      userId: context.params.userId,
      senderId: 'system',
      senderName: 'PicFlick',
      type: 'SYSTEM',
      title: 'Welcome to PicFlick! 📸',
      message: 'Start sharing photos and connecting with friends. Tap Find Friends to get started!',
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      isRead: false,
    };
    
    await admin.firestore().collection('notifications').add(welcomeNotif);
    console.log('Welcome notification created for user:', context.params.userId);
    
    return null;
  });