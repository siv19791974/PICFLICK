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
    
    // ONLY send push for IMPORTANT notification types
    const IMPORTANT_TYPES = ['FRIEND_REQUEST', 'MESSAGE', 'FOLLOW_ACCEPTED', 'MENTION', 'COMMENT'];

    if (!IMPORTANT_TYPES.includes(notification.type)) {
      console.log('Skipping push - type not important enough:', notification.type,
                  '| Important types:', IMPORTANT_TYPES.join(', '));
      // Still create in-app notification, just no push
      return null;
    }

    console.log('Sending push for important type:', notification.type);
    
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
      // Determine which screen to open when user taps the notification
      const type = (notification.type || '').toUpperCase();
      const explicitTarget = (notification.targetScreen || '').toLowerCase();
      let targetScreen = explicitTarget || 'notifications'; // Default

      if (!explicitTarget) {
        if (type === 'MESSAGE') {
          targetScreen = 'chat'; // Opens message thread
        } else if (
          type === 'MENTION' ||
          type === 'COMMENT' ||
          type === 'LIKE' ||
          type === 'REACTION' ||
          type === 'PHOTO_ADDED'
        ) {
          targetScreen = 'photo'; // Opens picture
        } else if (
          type === 'FOLLOW_ACCEPTED' ||
          type === 'FOLLOW' ||
          type === 'PROFILE_PHOTO_UPDATED'
        ) {
          targetScreen = 'profile'; // Opens sender profile
        } else if (type === 'FRIEND_REQUEST') {
          targetScreen = 'notifications'; // Opens request actions
        }
      }

      console.log('Push will open screen:', targetScreen, 'for type:', notification.type);
      
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
          targetScreen: targetScreen,
          click_action: 'FLUTTER_NOTIFICATION_CLICK',
        },
        android: {
          // HIGH delivery priority helps wake device from doze for urgent friend requests/messages.
          priority: 'high',
          notification: {
            // Must match app-created channel ID (PicFlickMessagingService.CHANNEL_ID)
            channelId: 'picflick_important_v2',
            sound: 'default',
            clickAction: 'FLUTTER_NOTIFICATION_CLICK',
          },
        },
        apns: {
          headers: {
            'apns-priority': '10',
          },
          payload: {
            aps: {
              sound: 'default',
              badge: 1,
              category: 'IMPORTANT',
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
    
    // Use deterministic numeric timestamps (app model expects Long) and batch write both notifications.
    const baseTs = Date.now();

    // Create first onboarding notification (Find Friends)
    const welcomeFindFriendsNotif = {
      userId: context.params.userId,
      senderId: 'system',
      senderName: 'PicFlick',
      type: 'SYSTEM',
      title: 'Welcome to PicFlick! 📸',
      message: 'Start sharing photos and connecting with friends. Tap Find Friends to get started!',
      targetScreen: 'find_friends',
      timestamp: baseTs,
      isRead: false,
    };

    // Create second onboarding notification (Add first photo)
    const welcomeFirstPhotoNotif = {
      userId: context.params.userId,
      senderId: 'system',
      senderName: 'PicFlick',
      type: 'SYSTEM',
      title: 'Welcome to PicFlick! 📸',
      message: 'Add your 1st photo to PicFlick. Tap to get started!',
      targetScreen: 'upload',
      timestamp: baseTs + 1,
      isRead: false,
    };

    const db = admin.firestore();
    const notificationsRef = db.collection('notifications');
    const firstRef = notificationsRef.doc();
    const secondRef = notificationsRef.doc();

    const batch = db.batch();
    batch.set(firstRef, welcomeFindFriendsNotif);
    batch.set(secondRef, welcomeFirstPhotoNotif);
    await batch.commit();

    console.log('Welcome onboarding notifications created for user:', context.params.userId, {
      firstId: firstRef.id,
      secondId: secondRef.id,
    });

    return null;
  });

/**
 * Cloud Function: Send push notification to assigned support user on new feedback
 */
exports.sendFeedbackPushToDeveloper = functions.firestore
  .document('feedback/{feedbackId}')
  .onCreate(async (snap, context) => {
    const feedback = snap.data() || {};
    const supportUid = feedback.assignedToUid || 'LpSqE40IZGeAGMknTAEzysqp5l33';

    try {
      const supportUserDoc = await admin.firestore()
        .collection('users')
        .doc(supportUid)
        .get();

      if (!supportUserDoc.exists) {
        console.log('Support user not found:', supportUid);
        return null;
      }

      const supportData = supportUserDoc.data() || {};
      const supportToken = supportData.fcmToken;

      if (!supportToken) {
        console.log('Support user has no FCM token:', supportUid);
        return null;
      }

      const category = (feedback.category || 'GENERAL').toUpperCase();
      const senderName = feedback.userName || 'Unknown user';
      const title = `New Contact Us: ${category}`;
      const body = `${senderName}: ${(feedback.subject || 'No subject').toString().slice(0, 80)}`;

      const message = {
        token: supportToken,
        notification: {
          title,
          body,
        },
        data: {
          type: 'SUPPORT_FEEDBACK',
          feedbackId: context.params.feedbackId,
          senderId: feedback.userId || '',
          senderName,
          targetScreen: 'settings',
          click_action: 'FLUTTER_NOTIFICATION_CLICK',
        },
        android: {
          priority: 'high',
          notification: {
            channelId: 'picflick_important_v2',
            sound: 'default',
            clickAction: 'FLUTTER_NOTIFICATION_CLICK',
          },
        },
        apns: {
          headers: {
            'apns-priority': '10',
          },
          payload: {
            aps: {
              sound: 'default',
              badge: 1,
              category: 'IMPORTANT',
            },
          },
        },
      };

      const response = await admin.messaging().send(message);
      console.log('Support feedback push sent:', response);

      await snap.ref.update({
        pushSentToSupport: true,
        pushSentToSupportAt: admin.firestore.FieldValue.serverTimestamp(),
        assignedToUid: supportUid,
      });

      return response;
    } catch (error) {
      console.error('Error sending support feedback push:', error);
      await snap.ref.update({
        pushSentToSupport: false,
        pushSupportError: error.message || 'unknown',
        assignedToUid: supportUid,
      });
      return null;
    }
  });

// Export purchase/subscription validation callables from the dedicated module.
Object.assign(exports, require('./purchaseValidation'));
