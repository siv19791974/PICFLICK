const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Send push notification when new notification document is created
exports.sendPushNotification = functions.firestore
  .document('notifications/{notificationId}')
  .onCreate(async (snap, context) => {
    const notification = snap.data();
    
    // Don't send if it's a system notification
    if (notification.senderId === 'system') {
      console.log('Skipping system notification');
      return null;
    }
    
    // Get the recipient's FCM token and notification preferences
    const userDoc = await admin.firestore()
      .collection('users')
      .doc(notification.userId)
      .get();
    
    const userData = userDoc.data();
    const fcmToken = userData?.fcmToken;
    
    if (!fcmToken) {
      console.log('No FCM token for user:', notification.userId);
      return null;
    }

    // Check user notification preferences
    const notificationPrefs = userData?.notificationPreferences || {};
    
    // Check if this notification type is enabled
    let shouldSend = true;
    switch (notification.type) {
      case 'PHOTO_ADDED':
        shouldSend = notificationPrefs.newPhotos !== false;
        break;
      case 'REACTION':
      case 'LIKE':
        shouldSend = notificationPrefs.reactions !== false;
        break;
      case 'COMMENT':
        shouldSend = notificationPrefs.comments !== false;
        break;
      case 'FOLLOW':
        shouldSend = notificationPrefs.newFollowers !== false;
        break;
      case 'MENTION':
        shouldSend = notificationPrefs.mentions !== false;
        break;
      case 'MESSAGE':
        shouldSend = notificationPrefs.messages !== false;
        break;
    }
    
    // Check quiet hours
    if (shouldSend && notificationPrefs?.quietHoursEnabled) {
      const now = new Date();
      const currentHour = now.getHours();
      const startHour = notificationPrefs.quietHoursStart || 22;
      const endHour = notificationPrefs.quietHoursEnd || 8;
      
      if (startHour > endHour) {
        // Overnight quiet hours (e.g., 22:00 - 08:00)
        if (currentHour >= startHour || currentHour < endHour) {
          shouldSend = false;
        }
      } else {
        // Daytime quiet hours (e.g., 08:00 - 17:00)
        if (currentHour >= startHour && currentHour < endHour) {
          shouldSend = false;
        }
      }
    }
    
    if (!shouldSend) {
      console.log('Notification suppressed due to user preferences:', notification.type);
      return null;
    }

    // Build the message
    const message = {
      token: fcmToken,
      notification: {
        title: notification.title,
        body: notification.message,
      },
      data: {
        type: notification.type,
        senderId: notification.senderId || '',
        senderName: notification.senderName || '',
        flickId: notification.flickId || '',
        chatId: notification.chatId || '',
        click_action: 'FLUTTER_NOTIFICATION_CLICK',
      },
      android: {
        priority: 'high',
        notification: {
          channelId: 'picflick_notifications',
          priority: 'high',
          defaultSound: true,
          defaultVibrateTimings: true,
          icon: 'ic_launcher_foreground',
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

    // Send the message
    try {
      await admin.messaging().send(message);
      console.log('Push notification sent successfully to:', notification.userId);
    } catch (error) {
      console.error('Error sending push notification:', error);
      // If token is invalid, remove it from user document
      if (error.code === 'messaging/invalid-registration-token' ||
          error.code === 'messaging/registration-token-not-registered') {
        await admin.firestore()
          .collection('users')
          .doc(notification.userId)
          .update({ fcmToken: admin.firestore.FieldValue.delete() });
        console.log('Invalid FCM token removed for user:', notification.userId);
      }
    }
    
    return null;
  });

// Clean up old notifications (runs daily)
exports.cleanupOldNotifications = functions.pubsub
  .schedule('0 0 * * *') // Run at midnight every day
  .onRun(async (context) => {
    const thirtyDaysAgo = Date.now() - (30 * 24 * 60 * 60 * 1000);
    
    const oldNotifications = await admin.firestore()
      .collection('notifications')
      .where('timestamp', '<', thirtyDaysAgo)
      .get();
    
    const batch = admin.firestore().batch();
    let count = 0;
    
    oldNotifications.docs.forEach((doc) => {
      batch.delete(doc.ref);
      count++;
    });
    
    await batch.commit();
    console.log(`Deleted ${count} old notifications`);
    return null;
  });

// Send streak reminders to users who haven't posted today (runs at 8 PM)
exports.sendStreakReminders = functions.pubsub
  .schedule('0 20 * * *') // Run at 8:00 PM every day
  .timeZone('America/New_York') // Adjust to your timezone
  .onRun(async (context) => {
    // Calculate start of today
    const now = new Date();
    const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    
    // Get all users with a streak > 0
    const usersSnapshot = await admin.firestore()
      .collection('users')
      .where('streak', '>', 0)
      .get();
    
    let remindersSent = 0;
    
    for (const userDoc of usersSnapshot.docs) {
      const userData = userDoc.data();
      const userId = userDoc.id;
      const userName = userData.displayName || 'there';
      const currentStreak = userData.streak || 0;
      const notificationPrefs = userData.notificationPreferences || {};
      
      // Skip if streak reminders are disabled
      if (notificationPrefs.streakReminders === false) {
        continue;
      }
      
      // Check if user posted today
      const todayPhotos = await admin.firestore()
        .collection('flicks')
        .where('userId', '==', userId)
        .where('timestamp', '>=', startOfDay)
        .limit(1)
        .get();
      
      // If no photos today, send streak reminder
      if (todayPhotos.empty) {
        const motivationalMessages = [
          `Don't break your ${currentStreak}-day streak! Share a photo today 📸`,
          `Your ${currentStreak}-day streak is at risk! Post now to keep it alive 🔥`,
          `Keep the flame burning! ${currentStreak} days and counting 🔥📸`,
          `One photo away from day ${currentStreak + 1}! Don't stop now 💪`
        ];
        
        const randomMessage = motivationalMessages[Math.floor(Math.random() * motivationalMessages.length)];
        
        const notification = {
          id: admin.firestore().collection('notifications').doc().id,
          userId: userId,
          senderId: 'system',
          senderName: 'PicFlick',
          senderPhotoUrl: '',
          type: 'STREAK_REMINDER',
          title: '🔥 Streak Alert!',
          message: randomMessage,
          isRead: false,
          timestamp: Date.now()
        };
        
        await admin.firestore().collection('notifications').add(notification);
        remindersSent++;
        console.log(`Streak reminder sent to ${userName} (${userId}) - ${currentStreak} day streak`);
      }
    }
    
    console.log(`Sent ${remindersSent} streak reminders`);
    return null;
  });

// Purchase validation functions
const purchaseValidation = require('./purchaseValidation');

exports.validatePurchase = purchaseValidation.validatePurchase;
exports.handlePurchaseCancelled = purchaseValidation.handlePurchaseCancelled;
exports.checkExpiredSubscriptions = purchaseValidation.checkExpiredSubscriptions;
exports.handleRealtimeNotification = purchaseValidation.handleRealtimeNotification;
