const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin
admin.initializeApp();

// Emergency runtime kill-switch (1st gen functions config).
// Set with: firebase functions:config:set safety.disable_triggers="true"
// Then deploy functions to apply.
const runtimeConfig = functions.config() || {};
const areSafetyTriggersDisabled = runtimeConfig?.safety?.disable_triggers === 'true';

function shouldSkipTrigger(triggerName) {
  if (!areSafetyTriggersDisabled) return false;
  console.warn(`[SAFETY] Trigger disabled by runtime config: ${triggerName}`);
  return true;
}

/**
 * Cloud Function: Send push notification when a new notification document is created
 * This function listens to the 'notifications' collection and sends FCM push
 */
exports.sendPushNotification = functions
  .runWith({
    serviceAccount: 'picflick-4793175b@appspot.gserviceaccount.com',
    memory: '256MB',
    timeoutSeconds: 60,
  })
  .firestore
  .document('notifications/{notificationId}')
  .onCreate(async (snap, context) => {
    if (shouldSkipTrigger('sendPushNotification')) return null;

    const notification = snap.data();
    
    console.log('New notification created:', context.params.notificationId);
    console.log('Notification data:', JSON.stringify(notification));
    
    // Don't send push for the sender's own actions
    if (notification.userId === notification.senderId) {
      console.log('Skipping - sender and recipient are the same');
      return null;
    }
    
    // ONLY send push for IMPORTANT notification types
    const IMPORTANT_TYPES = ['FRIEND_REQUEST', 'GROUP_INVITE', 'MESSAGE', 'FOLLOW_ACCEPTED', 'MENTION', 'COMMENT'];

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
        } else if (type === 'FRIEND_REQUEST' || type === 'GROUP_INVITE') {
          targetScreen = 'notifications'; // Opens actionable request cards
        }
      }

      console.log('Push will open screen:', targetScreen, 'for type:', notification.type);
      
      const isGroupMessage = type === 'MESSAGE' && (notification.groupName || '').trim().length > 0;
      const pushTitle = isGroupMessage
        ? `New message "${notification.groupName}"`
        : (notification.title || 'PicFlick');

      const message = {
        token: fcmToken,
        notification: {
          title: pushTitle,
          body: notification.message || 'You have a new notification',
        },
        data: {
          notificationId: context.params.notificationId,
          type: notification.type || 'GENERIC',
          senderId: notification.senderId || '',
          senderName: notification.senderName || '',
          groupName: notification.groupName || '',
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
exports.cleanupOldNotifications = functions
  .runWith({ memory: '256MB', timeoutSeconds: 120 })
  .pubsub
  .schedule('0 0 * * *') // Run at midnight every day
  .timeZone('UTC')
  .onRun(async (context) => {
    if (shouldSkipTrigger('cleanupOldNotifications')) return null;

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
exports.sendWelcomeNotification = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .firestore
  .document('users/{userId}')
  .onCreate(async (snap, context) => {
    if (shouldSkipTrigger('sendWelcomeNotification')) return null;

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
exports.sendFeedbackPushToDeveloper = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .firestore
  .document('feedback/{feedbackId}')
  .onCreate(async (snap, context) => {
    if (shouldSkipTrigger('sendFeedbackPushToDeveloper')) return null;

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

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function isStringArray(value) {
  return Array.isArray(value) && value.every((v) => typeof v === 'string');
}

function isValidCanonicalGroup(group) {
  return !!group &&
    isNonEmptyString(group.ownerId) &&
    isStringArray(group.memberIds) &&
    isStringArray(group.adminIds) &&
    group.memberIds.includes(group.ownerId) &&
    Number(group.schemaVersion) === 2;
}

function isValidChatSessionSchema(session) {
  if (!session || !isStringArray(session.participants)) return false;

  const unreadMap = session.unreadCount;
  if (!unreadMap || typeof unreadMap !== 'object' || Array.isArray(unreadMap)) return false;

  const hasLegacyUnreadKey = Object.keys(session).some((k) => k.startsWith('unreadCount_'));
  if (hasLegacyUnreadKey) return false;

  return session.participants.every((uid) => Number.isFinite(Number(unreadMap[uid] ?? 0)));
}

/**
 * Validation guard: flag malformed canonical group writes.
 * This is a temporary safety net until strict rules are fully rolled out.
 */
exports.validateCanonicalGroupWrite = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .firestore
  .document('groups/{groupId}')
  .onWrite(async (change, context) => {
    if (shouldSkipTrigger('validateCanonicalGroupWrite')) return null;
    if (!change.after.exists) return null;

    const before = change.before.exists ? (change.before.data() || {}) : null;
    const data = change.after.data() || {};

    // Valid schema: clear stale invalid flags exactly once (idempotent), then stop.
    if (isValidCanonicalGroup(data)) {
      if (data.invalidSchema === true) {
        await change.after.ref.set({
          invalidSchema: admin.firestore.FieldValue.delete(),
          invalidSchemaReason: admin.firestore.FieldValue.delete(),
          invalidSchemaDetectedAt: admin.firestore.FieldValue.delete(),
          validationVersion: admin.firestore.FieldValue.delete()
        }, { merge: true });
      }
      return null;
    }

    // Guard against recursive self-triggering writes:
    // if already flagged invalid, do not write back again.
    if (data.invalidSchema === true) {
      return null;
    }

    const now = Date.now();
    const reason = 'Missing/invalid canonical group fields';

    console.error('Invalid group schema detected', {
      groupId: context.params.groupId,
      ownerId: data.ownerId,
      schemaVersion: data.schemaVersion,
      hadInvalidFlagBefore: before?.invalidSchema === true
    });

    // Single merge write to source doc; will retrigger once, then short-circuit via guard above.
    await change.after.ref.set({
      invalidSchema: true,
      invalidSchemaReason: reason,
      invalidSchemaDetectedAt: now,
      validationVersion: 1
    }, { merge: true });

    // Deterministic error doc id to avoid unbounded add() growth.
    await admin.firestore().collection('schemaValidationErrors').doc(`groups_${context.params.groupId}`).set({
      entity: 'groups',
      entityId: context.params.groupId,
      detectedAt: now,
      reason,
      occurrences: admin.firestore.FieldValue.increment(1),
      lastSeenAt: now
    }, { merge: true });

    return null;
  });

/**
 * Validation guard: flag malformed chat session writes.
 * Enforces map-based unreadCount and blocks legacy unreadCount_<uid> keys.
 */
exports.validateChatSessionSchema = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .firestore
  .document('chatSessions/{chatId}')
  .onWrite(async (change, context) => {
    if (shouldSkipTrigger('validateChatSessionSchema')) return null;
    if (!change.after.exists) return null;

    const before = change.before.exists ? (change.before.data() || {}) : null;
    const data = change.after.data() || {};

    // Valid schema: clear stale invalid flags exactly once (idempotent), then stop.
    if (isValidChatSessionSchema(data)) {
      if (data.invalidSchema === true) {
        await change.after.ref.set({
          invalidSchema: admin.firestore.FieldValue.delete(),
          invalidSchemaReason: admin.firestore.FieldValue.delete(),
          invalidSchemaDetectedAt: admin.firestore.FieldValue.delete(),
          validationVersion: admin.firestore.FieldValue.delete()
        }, { merge: true });
      }
      return null;
    }

    // Guard against recursive self-triggering writes:
    // if already flagged invalid, do not write back again.
    if (data.invalidSchema === true) {
      return null;
    }

    const now = Date.now();
    const reason = 'Invalid unreadCount map or legacy unreadCount_<uid> keys present';

    console.error('Invalid chat session schema detected', {
      chatId: context.params.chatId,
      participantsCount: Array.isArray(data.participants) ? data.participants.length : 0,
      hadInvalidFlagBefore: before?.invalidSchema === true
    });

    // Single merge write to source doc; will retrigger once, then short-circuit via guard above.
    await change.after.ref.set({
      invalidSchema: true,
      invalidSchemaReason: reason,
      invalidSchemaDetectedAt: now,
      validationVersion: 1
    }, { merge: true });

    // Deterministic error doc id to avoid unbounded add() growth.
    await admin.firestore().collection('schemaValidationErrors').doc(`chatSessions_${context.params.chatId}`).set({
      entity: 'chatSessions',
      entityId: context.params.chatId,
      detectedAt: now,
      reason,
      occurrences: admin.firestore.FieldValue.increment(1),
      lastSeenAt: now
    }, { merge: true });

    return null;
  });

/**
 * Admin-only callable: migrate legacy group schemas to canonical shape.
 * Idempotent and safe to run multiple times.
 */
exports.migrateGroupsToCanonicalSchema = functions
  .runWith({ memory: '512MB', timeoutSeconds: 120 })
  .https
  .onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Authentication required');
  }

  const callerDoc = await admin.firestore().collection('users').doc(context.auth.uid).get();
  const caller = callerDoc.data() || {};
  const isAdmin = caller.isAdmin === true || caller.role === 'admin';
  if (!isAdmin) {
    throw new functions.https.HttpsError('permission-denied', 'Admin access required');
  }

  const dryRun = data?.dryRun === true;
  const limit = Math.max(1, Math.min(Number(data?.limit || 200), 500));

  const snapshot = await admin.firestore().collection('groups').limit(limit).get();
  let scanned = 0;
  let updated = 0;

  const batch = admin.firestore().batch();

  snapshot.docs.forEach((doc) => {
    scanned += 1;
    const g = doc.data() || {};

    const ownerId = String(g.ownerId || g.userId || '').trim();
    const memberIds = Array.isArray(g.memberIds)
      ? g.memberIds.filter(Boolean)
      : [...new Set([...(Array.isArray(g.friendIds) ? g.friendIds : []), ownerId].filter(Boolean))];
    const adminIds = Array.isArray(g.adminIds)
      ? g.adminIds.filter((id) => !!id && id !== ownerId && memberIds.includes(id))
      : [];

    const canonical = {
      id: g.id || doc.id,
      ownerId,
      name: g.name || '',
      memberIds,
      adminIds,
      icon: g.icon || '👥',
      color: g.color || '#4FC3F7',
      eventAt: g.eventAt || null,
      orderIndex: typeof g.orderIndex === 'number' ? g.orderIndex : Date.now(),
      createdAt: typeof g.createdAt === 'number' ? g.createdAt : Date.now(),
      updatedAt: Date.now(),
      schemaVersion: 2
    };

    const hasDiff = (
      g.ownerId !== canonical.ownerId ||
      JSON.stringify(g.memberIds || []) !== JSON.stringify(canonical.memberIds) ||
      JSON.stringify(g.adminIds || []) !== JSON.stringify(canonical.adminIds) ||
      g.schemaVersion !== 2 ||
      ('userId' in g) ||
      ('friendIds' in g)
    );

    if (hasDiff) {
      updated += 1;
      if (!dryRun) {
        batch.set(doc.ref, canonical, { merge: true });
        batch.update(doc.ref, {
          userId: admin.firestore.FieldValue.delete(),
          friendIds: admin.firestore.FieldValue.delete()
        });
      }
    }
  });

  if (!dryRun && updated > 0) {
    await batch.commit();
  }

  return { success: true, dryRun, scanned, updated };
});

/**
 * Admin-only callable: migrate chat unread counters to canonical map-only format.
 * Reads legacy unreadCount_<uid> values and stores them into unreadCount.{uid}.
 */
exports.migrateChatUnreadToMap = functions
  .runWith({ memory: '512MB', timeoutSeconds: 120 })
  .https
  .onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Authentication required');
  }

  const callerDoc = await admin.firestore().collection('users').doc(context.auth.uid).get();
  const caller = callerDoc.data() || {};
  const isAdmin = caller.isAdmin === true || caller.role === 'admin';
  if (!isAdmin) {
    throw new functions.https.HttpsError('permission-denied', 'Admin access required');
  }

  const dryRun = data?.dryRun === true;
  const limit = Math.max(1, Math.min(Number(data?.limit || 300), 500));

  const snapshot = await admin.firestore().collection('chatSessions').limit(limit).get();
  let scanned = 0;
  let updated = 0;

  const batch = admin.firestore().batch();

  snapshot.docs.forEach((doc) => {
    scanned += 1;
    const session = doc.data() || {};
    const participants = Array.isArray(session.participants) ? session.participants.filter(Boolean) : [];
    const unreadMap = { ...(session.unreadCount || {}) };

    let changed = false;

    participants.forEach((uid) => {
      const legacyKey = `unreadCount_${uid}`;
      const legacyVal = session[legacyKey];
      const parsedLegacy = Number.isFinite(Number(legacyVal)) ? Number(legacyVal) : null;
      const currentVal = Number.isFinite(Number(unreadMap[uid])) ? Number(unreadMap[uid]) : null;

      if (currentVal == null && parsedLegacy != null) {
        unreadMap[uid] = parsedLegacy;
        changed = true;
      } else if (currentVal == null) {
        unreadMap[uid] = 0;
        changed = true;
      }
    });

    if (changed || session.schemaVersion !== 2) {
      updated += 1;
      if (!dryRun) {
        const payload = {
          unreadCount: unreadMap,
          schemaVersion: 2,
          updatedAt: Date.now()
        };

        participants.forEach((uid) => {
          payload[`unreadCount_${uid}`] = admin.firestore.FieldValue.delete();
        });

        batch.update(doc.ref, payload);
      }
    }
  });

  if (!dryRun && updated > 0) {
    await batch.commit();
  }

  return { success: true, dryRun, scanned, updated };
});

const REACTIONS_SUBCOLLECTION_THRESHOLD = 500;

/**
 * Cloud Function: Auto-migrate inline reactions to subcollection when count exceeds threshold.
 * Keeps reactionsCount on the parent doc for fast reads; moves individual reactions to
 * flicks/{flickId}/reactions/{userId} documents.
 */
exports.migrateReactionsToSubcollection = functions
  .runWith({ memory: '512MB', timeoutSeconds: 120 })
  .firestore
  .document('flicks/{flickId}')
  .onUpdate(async (change, context) => {
    if (shouldSkipTrigger('migrateReactionsToSubcollection')) return null;

    const before = change.before.exists ? change.before.data() : {};
    const after = change.after.exists ? change.after.data() : {};

    // Only act when crossing the threshold and inline reactions still exist
    const reactionsCount = Number(after.reactionsCount || 0);
    const inlineReactions = after.reactions;
    const alreadyMigrated = after.reactionsMigrated === true;

    if (reactionsCount < REACTIONS_SUBCOLLECTION_THRESHOLD) return null;
    if (alreadyMigrated) return null;
    if (!inlineReactions || typeof inlineReactions !== 'object' || Array.isArray(inlineReactions)) return null;
    const entries = Object.entries(inlineReactions);
    if (entries.length === 0) return null;

    const flickId = context.params.flickId;
    const batch = admin.firestore().batch();
    let written = 0;

    entries.forEach(([userId, reactionType]) => {
      const ref = admin.firestore().collection('flicks').doc(flickId).collection('reactions').doc(userId);
      batch.set(ref, {
        userId,
        reactionType: String(reactionType),
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      written += 1;
    });

    // Mark migrated and remove inline reactions map
    batch.update(change.after.ref, {
      reactionsMigrated: true,
      reactions: admin.firestore.FieldValue.delete()
    });

    await batch.commit();
    console.log(`Migrated ${written} reactions to subcollection for flick=${flickId}`);
    return null;
  });

// Export purchase/subscription validation callables from the dedicated module.
Object.assign(exports, require('./purchaseValidation'));

// Export thumbnail backfill callable + HTTP trigger.
Object.assign(exports, require('./thumbnailBackfill'));

// deploy-bump 
