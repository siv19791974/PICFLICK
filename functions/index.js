const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin
admin.initializeApp();

// Emergency runtime kill-switch loaded from Firestore appConfig/functions.
// Set appConfig/functions.disableTriggers = true to globally disable triggers.
// Also checks appConfig/featureFlags.panicMode (set from Developer menu).
// Caches for 60s to avoid excessive Firestore reads.
let cachedDisableTriggers = false;
let cacheExpiryMs = 0;
const CONFIG_CACHE_TTL_MS = 60000;

async function isTriggersDisabled() {
  const now = Date.now();
  if (now < cacheExpiryMs) return cachedDisableTriggers;
  try {
    const functionsDoc = await admin.firestore().doc('appConfig/functions').get();
    const functionsDisabled = functionsDoc.exists && functionsDoc.data().disableTriggers === true;

    const flagsDoc = await admin.firestore().doc('appConfig/featureFlags').get();
    const panicMode = flagsDoc.exists && flagsDoc.data().panicMode === true;

    cachedDisableTriggers = functionsDisabled || panicMode;
    if (panicMode) {
      console.warn('[SAFETY] Panic mode detected in appConfig/featureFlags — all triggers disabled');
    }
  } catch (e) {
    console.warn('[SAFETY] Failed to load runtime config from Firestore:', e.message);
    cachedDisableTriggers = false;
  }
  cacheExpiryMs = now + CONFIG_CACHE_TTL_MS;
  return cachedDisableTriggers;
}

async function shouldSkipTrigger(triggerName) {
  if (!await isTriggersDisabled()) return false;
  console.warn(`[SAFETY] Trigger disabled by Firestore runtime config: ${triggerName}`);
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
    if (await shouldSkipTrigger('sendPushNotification')) return null;

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
    if (await shouldSkipTrigger('cleanupOldNotifications')) return null;

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
    if (await shouldSkipTrigger('sendWelcomeNotification')) return null;

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
    if (await shouldSkipTrigger('sendFeedbackPushToDeveloper')) return null;

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
    if (await shouldSkipTrigger('validateCanonicalGroupWrite')) return null;
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
    if (await shouldSkipTrigger('validateChatSessionSchema')) return null;
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
    if (await shouldSkipTrigger('migrateReactionsToSubcollection')) return null;

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

// Debug helper: check if a thumbnail file exists in Storage.
Object.assign(exports, require('./checkThumbnail'));

// Legacy 256px thumbnail cleanup (one-off).
Object.assign(exports, require('./cleanup256'));

/**
 * Cloud Function: Send push notification to developers when a new photo report is submitted
 */
exports.sendReportPushToDeveloper = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .firestore
  .document('reports/{reportId}')
  .onCreate(async (snap, context) => {
    if (await shouldSkipTrigger('sendReportPushToDeveloper')) return null;

    const report = snap.data() || {};
    const devUids = ['LpSqE40IZGeAGMknTAEzysqp5l33', 'cuj8dU3zNMN9TELEU2qmPR6Np5A2'];

    try {
      // Get reporter name
      let reporterName = 'A user';
      if (report.reporterId) {
        const reporterDoc = await admin.firestore().collection('users').doc(report.reporterId).get();
        if (reporterDoc.exists) {
          reporterName = reporterDoc.data().displayName || 'A user';
        }
      }

      const reason = report.reason || 'Unknown reason';
      const title = `Photo Reported: ${reason}`;
      const body = `${reporterName} reported a photo.`;

      // Send to all devs who have FCM tokens
      const sendPromises = devUids.map(async (uid) => {
        const devDoc = await admin.firestore().collection('users').doc(uid).get();
        if (!devDoc.exists) return null;
        const devData = devDoc.data() || {};
        const token = devData.fcmToken;
        if (!token) return null;

        const message = {
          token: token,
          notification: { title, body },
          data: {
            type: 'PHOTO_REPORT',
            reportId: context.params.reportId,
            flickId: report.flickId || '',
            reporterId: report.reporterId || '',
            reason,
            targetScreen: 'developer',
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
            headers: { 'apns-priority': '10' },
            payload: { aps: { sound: 'default', badge: 1, category: 'IMPORTANT' } },
          },
        };
        return admin.messaging().send(message);
      });

      const results = await Promise.all(sendPromises);
      const sentCount = results.filter(r => r !== null).length;
      console.log(`Report push sent to ${sentCount} developer(s)`);

      await snap.ref.update({
        pushSentToDevs: true,
        pushSentToDevsAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return results;
    } catch (error) {
      console.error('Error sending report push to devs:', error);
      await snap.ref.update({
        pushSentToDevs: false,
        pushDevError: error.message || 'unknown',
      });
      return null;
    }
  });

/**
 * Cloud Function: Auto-hide photos when they receive 3+ unique reports
 */
exports.autoHideReportedPhoto = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .firestore
  .document('reports/{reportId}')
  .onCreate(async (snap, context) => {
    if (await shouldSkipTrigger('autoHideReportedPhoto')) return null;

    const report = snap.data() || {};
    const flickId = report.flickId;
    if (!flickId) return null;

    try {
      // Count unique reporters for this flick
      const reportsSnapshot = await admin.firestore()
        .collection('reports')
        .where('flickId', '==', flickId)
        .get();

      const uniqueReporters = new Set();
      reportsSnapshot.docs.forEach((doc) => {
        const r = doc.data();
        if (r.reporterId) uniqueReporters.add(r.reporterId);
      });

      const reporterCount = uniqueReporters.size;
      console.log(`Flick ${flickId} has ${reporterCount} unique reporter(s)`);

      if (reporterCount >= 3) {
        // Auto-hide the flick
        await admin.firestore()
          .collection('flicks')
          .doc(flickId)
          .update({
            autoHiddenByReports: true,
            autoHiddenAt: admin.firestore.FieldValue.serverTimestamp(),
            autoHiddenReporterCount: reporterCount,
          });
        console.log(`Auto-hidden flick ${flickId} after ${reporterCount} unique reports`);
      }

      return null;
    } catch (error) {
      console.error('Error auto-hiding reported photo:', error);
      return null;
    }
  });

/**
 * Cloud Function: Monthly Mythic Draw — 1st of every month at 00:00 UTC
 *
 * FEATURES:
 * - Progressive thresholds: Month 1=10, 2=30, 3=50, 4=70, 5+=100
 * - Tiered prizes: 1st (3mo PRO + gold crown), 2nd (1mo PRO + silver crown), 3rd (2wk PRO + bronze crown)
 * - Streak-weighted tickets: 1 ticket per 10 streak days
 * - Winner repeat protection: 3-month cooldown
 * - Upload boost (+30% daily limit) for all entrants
 * - Contender badge tracking for all entrants
 * - All users notified of ALL winners
 * - Past winners / Hall of Fame tracking
 * - Leaderboard snapshot stored in draw doc
 */
async function executeMythicDraw(db, isManual = false, forcedMonthKey = null) {
  const now = Date.now();
  const date = forcedMonthKey ? new Date(forcedMonthKey + '-01T00:00:00Z') : new Date();
  const monthKey = forcedMonthKey || `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
  const drawDocRef = db.collection('system').doc(`mythic_draw_${monthKey}`);

  // ─── IDEMPOTENCY ───
  const existingDraw = await drawDocRef.get();
  if (existingDraw.exists && !isManual) {
    console.log(`Mythic draw ${monthKey} already completed.`);
    return { alreadyRun: true, monthKey, winners: existingDraw.data().winners || [] };
  }

  // ─── MONTH NUMBER & PROGRESSIVE THRESHOLD ───
  // Count how many previous mythic draws exist
  const allSystemDocs = await db.collection('system').listDocuments();
  const previousDraws = allSystemDocs.filter((d) => d.id.startsWith('mythic_draw_') && d.id !== `mythic_draw_${monthKey}`);
  const monthNumber = previousDraws.length + 1;
  const progressiveThresholds = { 1: 10, 2: 30, 3: 50, 4: 70 };
  const streakThreshold = monthNumber >= 5 ? 100 : (progressiveThresholds[monthNumber] || 100);
  console.log(`Mythic draw ${monthKey} (month #${monthNumber}): threshold = ${streakThreshold} days`);

  // Persist threshold so the app can display correct progress all month
  await db.collection('appConfig').doc('mythicDraw').set({
    currentMonthNumber: monthNumber,
    currentThreshold: streakThreshold,
    lastUpdated: now,
  }, { merge: true });

  // ─── QUERY ALL USERS (we need everyone for notifications & leaderboard) ───
  const allUsersSnapshot = await db.collection('users').get();
  const allUsers = allUsersSnapshot.docs
    .map((doc) => ({ uid: doc.id, ...doc.data() }))
    .filter((u) => u.uid && u.uid.length > 0);

  // ─── ELIGIBLE USERS (streak >= threshold) ───
  const eligibleUsers = allUsers.filter((u) => {
    const streak = u.streak?.current || 0;
    return streak >= streakThreshold;
  });

  console.log(`Mythic draw ${monthKey}: ${eligibleUsers.length}/${allUsers.length} eligible (threshold=${streakThreshold})`);

  // ─── NO ELIGIBLE USERS ───
  if (eligibleUsers.length === 0) {
    await drawDocRef.set({
      monthKey,
      monthNumber,
      streakThreshold,
      winners: [],
      eligibleUserCount: 0,
      allEntrantIds: [],
      createdAt: now,
      notificationSent: true,
      noWinner: true,
      leaderboard: buildLeaderboard(allUsers, 10),
    });
    // Notify all users that no one was eligible this month
    await notifyAllUsersNoWinner(db, allUsers, monthKey, monthNumber, streakThreshold, now);
    console.log(`Mythic draw ${monthKey}: no eligible users. Draw marked complete.`);
    return { noWinner: true, monthKey, monthNumber, streakThreshold };
  }

  // ─── WINNER REPEAT PROTECTION ───
  // Build set of users who won in the last 3 months
  const recentWinnerIds = new Set();
  const recentMonths = getRecentMonthKeys(monthKey, 3);
  for (const mk of recentMonths) {
    const pastDraw = await db.collection('system').doc(`mythic_draw_${mk}`).get();
    if (pastDraw.exists) {
      const pw = pastDraw.data().winners || [];
      pw.forEach((w) => { if (w.userId) recentWinnerIds.add(w.userId); });
    }
  }
  console.log(`Repeat protection: ${recentWinnerIds.size} recent winners excluded`);

  // ─── BUILD WEIGHTED TICKET POOL ───
  // 1 ticket per 10 streak days (minimum 1 ticket if eligible)
  const ticketPool = [];
  eligibleUsers.forEach((u) => {
    const streak = u.streak?.current || 0;
    const tickets = Math.max(1, Math.floor(streak / 10));
    const isProtected = recentWinnerIds.has(u.uid);
    if (!isProtected) {
      for (let i = 0; i < tickets; i++) {
        ticketPool.push(u);
      }
    }
  });

  console.log(`Ticket pool: ${ticketPool.length} tickets from ${eligibleUsers.length - recentWinnerIds.size} eligible non-recent-winners`);

  // ─── PICK 3 UNIQUE WINNERS ───
  const prizes = [
    { place: 1, label: '1st', proMonths: 3, crown: 'gold', crownEmoji: '👑', messagePrefix: '🎉 GRAND PRIZE WINNER' },
    { place: 2, label: '2nd', proMonths: 1, crown: 'silver', crownEmoji: '🥈', messagePrefix: '🎉 RUNNER-UP WINNER' },
    { place: 3, label: '3rd', proMonths: 0.5, crown: 'bronze', crownEmoji: '🥉', messagePrefix: '🎉 THIRD PLACE WINNER' },
  ];

  const winners = [];
  const pickedIds = new Set();
  const usedPool = [...ticketPool];

  for (const prize of prizes) {
    if (usedPool.length === 0) break;

    // Simple random pick — NO ALGORITHM
    const idx = Math.floor(Math.random() * usedPool.length);
    const winner = usedPool[idx];
    if (pickedIds.has(winner.uid)) {
      // Remove all instances of this already-picked user and retry
      for (let i = usedPool.length - 1; i >= 0; i--) {
        if (usedPool[i].uid === winner.uid) usedPool.splice(i, 1);
      }
      continue; // Try next prize with cleaned pool
    }

    pickedIds.add(winner.uid);
    winners.push({
      place: prize.place,
      userId: winner.uid,
      userName: winner.displayName || 'A PicFlick creator',
      streak: winner.streak?.current || 0,
      crown: prize.crown,
      crownEmoji: prize.crownEmoji,
      proMonths: prize.proMonths,
      prizeLabel: prize.label,
      messagePrefix: prize.messagePrefix,
    });

    // Remove all instances of this winner from pool
    for (let i = usedPool.length - 1; i >= 0; i--) {
      if (usedPool[i].uid === winner.uid) usedPool.splice(i, 1);
    }
  }

  console.log(`Mythic draw ${monthKey}: ${winners.length} winner(s) picked`, winners.map((w) => `#${w.place} ${w.userName} (${w.streak}d)`).join(', '));

  // ─── AWARD PRIZES, CROWNS, BANNERS TO WINNERS ───
  const winnerBannerText = `Mythic Draw Winner — ${monthKey}`;
  for (const w of winners) {
    const userRef = db.collection('users').doc(w.userId);
    const userDoc = await userRef.get();
    const currentTier = userDoc.data()?.subscriptionTier || 'FREE';
    const existingExpiry = userDoc.data()?.subscriptionExpiryDate || now;
    const existingHistory = userDoc.get('mythicDrawHistory') || [];

    // Tier-scaled prize: PRO → ULTRA, ULTRA → extend, below PRO → PRO
    let prizeTier = 'PRO';
    let prizeDurationMs = w.proMonths * 30 * 24 * 60 * 60 * 1000;
    let prizeLabelText = w.proMonths >= 1 ? `${w.proMonths} month PRO` : '2 week PRO';

    if (currentTier === 'PRO') {
      prizeTier = 'ULTRA';
      prizeLabelText = w.proMonths >= 1 ? `${w.proMonths} month ULTRA` : '2 week ULTRA';
    } else if (currentTier === 'ULTRA') {
      prizeTier = 'ULTRA';
      // ULTRA winners always get 3 months extension regardless of place
      prizeDurationMs = 3 * 30 * 24 * 60 * 60 * 1000;
      prizeLabelText = '3 month ULTRA extension';
    }

    const expiryDate = (currentTier === 'ULTRA')
      ? (existingExpiry + prizeDurationMs)
      : (now + prizeDurationMs);

    const winnerUpdate = {
      subscriptionTier: prizeTier,
      subscriptionActive: true,
      subscriptionExpiryDate: expiryDate,
      mythicDrawWonAt: now,
      mythicDrawWonMonth: monthKey,
      mythicCrown: w.crown,
      mythicCrownExpiry: now + (30 * 24 * 60 * 60 * 1000), // Crown lasts 1 month
      mythicWinnerBanner: winnerBannerText,
      mythicWinnerBannerExpiry: now + (30 * 24 * 60 * 60 * 1000), // Banner lasts 1 month
      mythicLastWonMonthKey: monthKey,
      mythicDrawHistory: admin.firestore.FieldValue.arrayUnion({
        monthKey,
        monthNumber,
        place: w.place,
        prizeLabel: w.prizeLabel,
        streak: w.streak,
        wonAt: now,
      }),
    };

    // ULTRA winners get the permanent Mythic Champion badge
    if (currentTier === 'ULTRA') {
      winnerUpdate.mythicChampion = true;
      winnerUpdate.mythicChampionMonth = monthKey;
    }

    await userRef.update(winnerUpdate);

    // Send personal FCM push to winner
    try {
      const winnerFcmToken = userDoc.get('fcmToken');
      if (winnerFcmToken) {
        await admin.messaging().send({
          token: winnerFcmToken,
          notification: {
            title: `${w.messagePrefix}!`,
            body: `You placed ${w.prizeLabel} in the Mythic Monthly Draw with a ${w.streak}-day streak! ${prizeLabelText} awarded.`,
          },
          data: {
            type: 'SYSTEM',
            targetScreen: 'achievements',
            click_action: 'FLUTTER_NOTIFICATION_CLICK',
          },
          android: { priority: 'high', notification: { sound: 'default', clickAction: 'FLUTTER_NOTIFICATION_CLICK' } },
          apns: { headers: { 'apns-priority': '10' }, payload: { aps: { sound: 'default', badge: 1 } } },
        });
        console.log(`FCM push sent to ${w.prizeLabel} winner ${w.userId} (${currentTier} → ${prizeTier})`);
      }
    } catch (pushErr) {
      console.error(`Error sending FCM push to ${w.prizeLabel} winner:`, pushErr.message);
    }
  }

  // ─── UPLOAD BOOST + CONTENDER BADGE FOR ALL ENTRANTS (including winners) ───
  const tierBaseLimits = { FREE: 10, STANDARD: 25, PLUS: 50, PRO: 100, ULTRA: 9999 };
  for (const entrant of eligibleUsers) {
    const entrantRef = db.collection('users').doc(entrant.uid);
    const userDoc = await entrantRef.get();
    const currentTier = userDoc.data()?.subscriptionTier || 'FREE';
    const base = tierBaseLimits[currentTier] || 10;
    const boost = (currentTier === 'ULTRA') ? 0 : Math.ceil(base * 0.3);

    await entrantRef.update({
      mythicContenderCount: admin.firestore.FieldValue.increment(1),
      mythicUploadBoostAmount: boost,
      mythicUploadBoostExpiry: now + (30 * 24 * 60 * 60 * 1000), // 30 days
    });
  }
  console.log(`Upload boost (+30%) and contender badge awarded to ${eligibleUsers.length} entrants`);

  // ─── NOTIFY ALL USERS (including winners get a separate announcement too) ───
  const winnerNamesList = winners.map((w) => `${w.crownEmoji} ${w.userName} (${w.prizeLabel}, ${w.streak}d)`).join(', ');
  const winnerSummary = winners.length > 0
    ? `Winners: ${winnerNamesList}`
    : 'No winners this month.';

  // Send batch notifications to ALL users (winners get this too, in addition to their personal push)
  await batchNotifyAllUsers(db, allUsers, {
    title: '🏆 Mythic Monthly Draw Results',
    message: `The ${monthKey} Mythic Draw is complete! ${winnerSummary}. Keep uploading daily to enter next month's draw!`,
    targetScreen: 'achievements',
    monthKey,
    monthNumber,
    streakThreshold,
    eligibleUserCount: eligibleUsers.length,
    isMythicDrawAnnouncement: true,
    winners: winners.map((w) => ({
      place: w.place,
      userId: w.userId,
      userName: w.userName,
      streak: w.streak,
      crown: w.crown,
    })),
    timestamp: now,
  });

  // ─── HALL OF FAME ───
  const hallOfFameRef = db.collection('system').doc('mythic_hall_of_fame');
  const hallSnap = await hallOfFameRef.get();
  const hallData = hallSnap.exists ? hallSnap.data() : { winners: [] };
  const updatedHall = [...(hallData.winners || [])];
  for (const w of winners) {
    updatedHall.push({
      monthKey,
      monthNumber,
      place: w.place,
      userId: w.userId,
      userName: w.userName,
      streak: w.streak,
      crown: w.crown,
      wonAt: now,
    });
  }
  await hallOfFameRef.set({ winners: updatedHall, lastUpdated: now }, { merge: true });

  // ─── LEADERBOARD (top 10 streaks) ───
  const leaderboard = buildLeaderboard(allUsers, 10);

  // ─── TIER BADGES — Track consecutive months entered ───
  const allMonthKeys = await db.collection('system')
    .listDocuments()
    .then((docs) => docs.filter((d) => d.id.startsWith('mythic_draw_') && d.id !== `mythic_draw_${monthKey}`).map((d) => d.id.replace('mythic_draw_', '')).sort());
  for (const entrant of eligibleUsers) {
    const entrantRef = db.collection('users').doc(entrant.uid);
    // Count how many of the past draws this user was in
    let consecutiveMonths = 1; // This month counts
    for (let i = allMonthKeys.length - 1; i >= 0; i--) {
      const pastKey = allMonthKeys[i];
      const pastDraw = await db.collection('system').doc(`mythic_draw_${pastKey}`).get();
      const pastEntrants = pastDraw.exists ? (pastDraw.data().allEntrantIds || []) : [];
      if (pastEntrants.includes(entrant.uid)) {
        consecutiveMonths++;
      } else {
        break;
      }
    }
    const tier = consecutiveMonths >= 12 ? 'diamond' : consecutiveMonths >= 6 ? 'gold' : consecutiveMonths >= 3 ? 'silver' : 'bronze';
    await entrantRef.update({
      mythicConsecutiveMonths: consecutiveMonths,
      mythicTier: tier,
      mythicTierUpdatedAt: now,
    });
  }
  console.log(`Tier badges awarded to ${eligibleUsers.length} entrants`);

  // ─── FEATURED WINNER — Store 1st place winner for Home screen banner ───
  const firstPlaceWinner = winners.find((w) => w.place === 1);
  let featuredWinner = null;
  if (firstPlaceWinner) {
    const winnerDoc = allUsers.find((u) => u.uid === firstPlaceWinner.userId);
    featuredWinner = {
      userId: firstPlaceWinner.userId,
      userName: firstPlaceWinner.userName,
      streak: firstPlaceWinner.streak,
      photoUrl: winnerDoc?.photoUrl || '',
      crown: firstPlaceWinner.crown,
      monthKey,
      monthNumber,
      featuredAt: now,
      expiresAt: now + (7 * 24 * 60 * 60 * 1000), // Featured for 7 days
    };
  }

  // ─── GLOBAL STATS ───
  const stats = {
    totalEntrants: eligibleUsers.length,
    totalUsers: allUsers.length,
    yourOddsDenom: ticketPool.length,
    ticketPoolSize: ticketPool.length,
    averageStreak: eligibleUsers.length > 0
      ? Math.round(eligibleUsers.reduce((sum, u) => sum + (u.streak?.current || 0), 0) / eligibleUsers.length)
      : 0,
    highestStreak: eligibleUsers.length > 0
      ? Math.max(...eligibleUsers.map((u) => u.streak?.current || 0))
      : 0,
    drawStatus: 'complete',
    drawCompletedAt: now,
  };

  const entrantIds = eligibleUsers.map((u) => u.uid);

  // ─── SAVE DRAW RESULT ───
  await drawDocRef.set({
    monthKey,
    monthNumber,
    streakThreshold,
    winners: winners.map((w) => ({
      place: w.place,
      userId: w.userId,
      userName: w.userName,
      streak: w.streak,
      crown: w.crown,
      proMonths: w.proMonths,
    })),
    allEntrantIds: entrantIds,
    eligibleUserCount: eligibleUsers.length,
    totalUserCount: allUsers.length,
    createdAt: now,
    notificationSent: true,
    leaderboard,
    ticketPoolSize: ticketPool.length,
    recentWinnersExcluded: Array.from(recentWinnerIds),
    featuredWinner,
    stats,
    drawAnimation: {
      isLive: false,
      startedAt: null,
      completedAt: now,
      winnerRevealed: winners.length > 0,
    },
  });

  // ─── DEV FEEDBACK ───
  const devFeedbackRef = db.collection('feedback').doc();
  await devFeedbackRef.set({
    userId: 'system',
    userName: 'Mythic Draw Bot',
    userEmail: 'system@picflick.app',
    subject: `Mythic Draw — ${monthKey} (Month #${monthNumber})`,
    message: `Mythic Draw completed for ${monthKey}.\n\nMonth #: ${monthNumber}\nThreshold: ${streakThreshold} days\nEligible: ${eligibleUsers.length}/${allUsers.length}\nTicket pool: ${ticketPool.length}\nWinners:\n${winners.map((w) => `  #${w.place}: ${w.userName} (${w.userId}) — ${w.streak}d streak — ${w.proMonths}mo PRO + ${w.crown} crown`).join('\n')}\nRecent winners excluded: ${Array.from(recentWinnerIds).length}`,
    category: 'GENERAL',
    timestamp: now,
    status: 'NEW',
    assignedToUid: 'LpSqE40IZGeAGMknTAEzysqp5l33',
    appVersion: 'cloud-function',
    deviceInfo: `month=${monthKey}|monthNumber=${monthNumber}|threshold=${streakThreshold}|eligible=${eligibleUsers.length}|winners=${winners.length}`,
  });

  console.log(`Mythic draw ${monthKey} completed. Month #${monthNumber}, ${winners.length} winner(s), ${eligibleUsers.length} eligible.`);
  return { monthKey, monthNumber, winners, eligibleCount: eligibleUsers.length, streakThreshold };
}

/**
 * Build a streak leaderboard from all users
 */
function buildLeaderboard(allUsers, limit = 10) {
  return allUsers
    .map((u) => ({
      userId: u.uid,
      userName: u.displayName || 'Unknown',
      streak: u.streak?.current || 0,
      photoUrl: u.photoUrl || '',
      tier: u.subscriptionTier || 'FREE',
    }))
    .sort((a, b) => b.streak - a.streak)
    .slice(0, limit);
}

/**
 * Get the last N month keys before the given monthKey
 */
function getRecentMonthKeys(monthKey, count) {
  const result = [];
  const [yearStr, monthStr] = monthKey.split('-');
  let year = parseInt(yearStr, 10);
  let month = parseInt(monthStr, 10);
  for (let i = 0; i < count; i++) {
    month--;
    if (month <= 0) { month = 12; year--; }
    result.push(`${year}-${String(month).padStart(2, '0')}`);
  }
  return result;
}

/**
 * Send notifications to all users in batches (500 per batch — Firestore limit)
 */
async function batchNotifyAllUsers(db, allUsers, notificationData) {
  const BATCH_SIZE = 500;
  const batches = [];
  for (let i = 0; i < allUsers.length; i += BATCH_SIZE) {
    const batch = db.batch();
    const chunk = allUsers.slice(i, i + BATCH_SIZE);
    let count = 0;
    chunk.forEach((userDoc) => {
      const uid = userDoc.uid;
      if (!uid) return;
      const notifRef = db.collection('notifications').doc();
      batch.set(notifRef, {
        userId: uid,
        senderId: 'system',
        senderName: 'PicFlick',
        senderPhotoUrl: '',
        type: 'SYSTEM',
        ...notificationData,
      });
      count++;
    });
    batches.push(batch.commit().then(() => count));
  }
  const results = await Promise.all(batches);
  const totalSent = results.reduce((a, b) => a + b, 0);
  console.log(`Mythic draw announcement sent to ${totalSent} user(s) in ${batches.length} batch(es)`);
}

/**
 * Notify all users when there are no eligible entrants
 */
async function notifyAllUsersNoWinner(db, allUsers, monthKey, monthNumber, streakThreshold, now) {
  const nextThreshold = monthNumber >= 4 ? 100 : ({ 1: 30, 2: 50, 3: 70 }[monthNumber] || 100);
  await batchNotifyAllUsers(db, allUsers, {
    title: '🏆 Mythic Monthly Draw — No Eligible Entrants',
    message: `The ${monthKey} Mythic Draw had no eligible entrants (threshold: ${streakThreshold} days). Next month's threshold is ${nextThreshold} days — keep uploading daily to enter!`,
    targetScreen: 'achievements',
    monthKey,
    monthNumber,
    streakThreshold,
    eligibleUserCount: 0,
    isMythicDrawAnnouncement: true,
    noWinner: true,
    timestamp: now,
  });
}

/**
 * Cloud Function: Scheduled Monthly Mythic Draw — 1st of every month at 00:00 UTC
 */
exports.runMonthlyMythicDraw = functions
  .runWith({ memory: '256MB', timeoutSeconds: 120 })
  .pubsub
  .schedule('0 0 1 * *')
  .timeZone('UTC')
  .onRun(async (context) => {
    if (await shouldSkipTrigger('runMonthlyMythicDraw')) return null;
    const db = admin.firestore();
    return executeMythicDraw(db, false);
  });

/**
 * Cloud Function: Manual Mythic Draw trigger (Callable — dev only)
 * Android app calls this via FirebaseFunctions.getInstance().getHttpsCallable("runMythicDrawManual")
 */
exports.runMythicDrawManual = functions
  .runWith({ memory: '256MB', timeoutSeconds: 120 })
  .https
  .onCall(async (data, context) => {
    // Auth check: must be authenticated and a dev UID
    const callerUid = context.auth?.uid;
    if (!callerUid) {
      throw new functions.https.HttpsError('unauthenticated', 'You must be signed in to run a manual draw.');
    }
    const devUids = ['LpSqE40IZGeAGMknTAEzysqp5l33', 'cuj8dU3zNMN9TELEU2qmPR6Np5A2'];
    if (!devUids.includes(callerUid)) {
      throw new functions.https.HttpsError('permission-denied', 'Only developers can trigger manual draws.');
    }

    const db = admin.firestore();
    try {
      const result = await executeMythicDraw(db, true);
      return {
        success: true,
        ...result,
        note: result.alreadyRun ? 'This month already had a scheduled draw. Manual draw was skipped.' : undefined,
      };
    } catch (error) {
      console.error('Manual Mythic Draw error:', error);
      throw new functions.https.HttpsError('internal', error.message);
    }
  });

/**
 * Cloud Function: Mythic Monday Weekly Push — Every Monday at 9:00 UTC
 * Sends a personalized streak reminder to all users with active streaks
 */
exports.mythicMondayPush = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .pubsub
  .schedule('0 12 * * 1')
  .timeZone('UTC')
  .onRun(async (context) => {
    if (await shouldSkipTrigger('mythicMondayPush')) return null;
    const db = admin.firestore();

    const date = new Date();
    const monthKey = `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;

    // Get current threshold from appConfig (persisted by executeMythicDraw)
    const appConfigDoc = await db.collection('appConfig').doc('mythicDraw').get();
    const appConfigData = appConfigDoc.exists ? appConfigDoc.data() : null;
    const streakThreshold = appConfigData?.currentThreshold || 10; // Month 1 default = 10

    // Get next draw date (1st of next month)
    const nextMonth = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 1));
    const daysUntilDraw = Math.ceil((nextMonth.getTime() - date.getTime()) / (24 * 60 * 60 * 1000));

    const usersSnap = await db.collection('users').get();
    let pushCount = 0;
    let failCount = 0;

    for (const userDoc of usersSnap.docs) {
      const userData = userDoc.data();
      const uid = userDoc.id;
      const fcmToken = userData.fcmToken;
      if (!fcmToken) continue;

      const streak = userData.streak?.current || 0;
      const displayName = userData.displayName || 'PicFlick creator';
      const isEligible = streak >= streakThreshold;
      const daysToGo = Math.max(0, streakThreshold - streak);

      let title, body;
      if (isEligible) {
        title = '👑 Mythic Monday — You are IN!';
        body = `Hi ${displayName}, your ${streak}-day streak qualifies you for the ${monthKey} Mythic Draw. ${daysUntilDraw} days until the draw! Keep the flame alive!`;
      } else if (streak > 0) {
        title = '🔥 Mythic Monday — Keep Going!';
        body = `Hi ${displayName}, your streak is ${streak} days. ${daysToGo} more days to enter the ${monthKey} Mythic Draw. Don't break it!`;
      } else {
        title = '📸 Mythic Monday — Start Your Streak!';
        body = `Hi ${displayName}, upload a photo today to start your streak. ${streakThreshold} days gets you into the Mythic Draw!`;
      }

      try {
        await admin.messaging().send({
          token: fcmToken,
          notification: { title, body },
          data: {
            type: 'SYSTEM',
            targetScreen: 'achievements',
            click_action: 'FLUTTER_NOTIFICATION_CLICK',
          },
          android: { priority: 'high', notification: { sound: 'default', clickAction: 'FLUTTER_NOTIFICATION_CLICK' } },
          apns: { headers: { 'apns-priority': '10' }, payload: { aps: { sound: 'default', badge: 1 } } },
        });
        pushCount++;
      } catch (err) {
        failCount++;
        console.warn(`Mythic Monday push failed for ${uid}:`, err.message);
      }
    }

    console.log(`Mythic Monday push complete: ${pushCount} sent, ${failCount} failed`);
    return { sent: pushCount, failed: failCount };
  });

/**
 * Cloud Function: Trigger Live Draw Animation (Callable — dev only)
 * Sets drawAnimation.isLive = true so the app can show the spinning wheel
 */
exports.triggerMythicDrawAnimation = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .https
  .onCall(async (data, context) => {
    const callerUid = context.auth?.uid;
    if (!callerUid) {
      throw new functions.https.HttpsError('unauthenticated', 'You must be signed in.');
    }
    const devUids = ['LpSqE40IZGeAGMknTAEzysqp5l33', 'cuj8dU3zNMN9TELEU2qmPR6Np5A2'];
    if (!devUids.includes(callerUid)) {
      throw new functions.https.HttpsError('permission-denied', 'Only developers can trigger the animation.');
    }

    const db = admin.firestore();
    const date = new Date();
    const monthKey = `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
    const drawDocRef = db.collection('system').doc(`mythic_draw_${monthKey}`);

    await drawDocRef.update({
      'drawAnimation.isLive': true,
      'drawAnimation.startedAt': Date.now(),
      'drawAnimation.winnerRevealed': false,
    });

    // Send push to all users: "Live draw starting!"
    const usersSnap = await db.collection('users').get();
    let pushCount = 0;
    for (const userDoc of usersSnap.docs) {
      const fcmToken = userDoc.data().fcmToken;
      if (!fcmToken) continue;
      try {
        await admin.messaging().send({
          token: fcmToken,
          notification: {
            title: '🎡 Mythic Draw is LIVE!',
            body: 'The monthly Mythic Draw is spinning now! Open PicFlick to watch.',
          },
          data: {
            type: 'SYSTEM',
            targetScreen: 'achievements',
            mythicDrawLive: 'true',
            click_action: 'FLUTTER_NOTIFICATION_CLICK',
          },
          android: { priority: 'high', notification: { sound: 'default', clickAction: 'FLUTTER_NOTIFICATION_CLICK' } },
          apns: { headers: { 'apns-priority': '10' }, payload: { aps: { sound: 'default', badge: 1 } } },
        });
        pushCount++;
      } catch (_err) { /* ignore */ }
    }

    return { success: true, monthKey, pushCount };
  });

/**
 * DEPRECATED: testMythicDraw has been removed for production safety.
 * The real draw runs automatically via executeMythicDraw on the 1st of every month.
 * To test, use the Firebase Functions shell locally with the emulator suite.
 */
exports.testMythicDraw = functions.https.onCall(async (data, context) => {
  throw new functions.https.HttpsError('failed-precondition', 'Test draw is disabled in production.');
});

/**
 * Cloud Function: Check Broken Streaks — Daily at 23:00 UTC
 * Finds users who uploaded yesterday but NOT today (streak broken).
 * Offers one free streak recovery per month.
 */
exports.checkBrokenStreaks = functions.pubsub
  .schedule('0 23 * * *')
  .timeZone('UTC')
  .onRun(async (context) => {
    if (await shouldSkipTrigger('checkBrokenStreaks')) return null;
    const db = admin.firestore();
    const now = new Date();
    const today = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
    const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000);
    const currentMonthKey = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, '0')}`;

    // Yesterday window: [yesterday midnight, today midnight)
    const yesterdayStart = admin.firestore.Timestamp.fromDate(yesterday);
    const yesterdayEnd = admin.firestore.Timestamp.fromDate(today);

    // Find users whose last upload was yesterday (not today, not earlier today)
    // ⚠️ REQUIRES FIRESTORE COMPOSITE INDEX:
    // Collection: users | Fields: streak.lastUpload (Ascending), streak.current (Ascending)
    // Create at: Firebase Console → Firestore Database → Indexes → Composite
    const brokenSnap = await db.collection('users')
      .where('streak.lastUpload', '>=', yesterdayStart)
      .where('streak.lastUpload', '<', yesterdayEnd)
      .where('streak.current', '>', 0)
      .get();

    console.log(`checkBrokenStreaks: ${brokenSnap.size} users with broken streaks found`);

    let recoveryOffered = 0;
    let pushSent = 0;

    for (const userDoc of brokenSnap.docs) {
      const data = userDoc.data();
      const uid = userDoc.id;
      const streakData = data.streak || {};
      const brokenStreakValue = streakData.current || 0;

      // Respect monthly limit
      const usedMonth = data.streakRecoveryUsedMonth || '';
      const alreadyAvailable = data.streakRecoveryAvailable === true;

      if (alreadyAvailable) continue;
      if (usedMonth === currentMonthKey) continue;

      // Offer recovery
      await db.collection('users').doc(uid).update({
        streakRecoveryAvailable: true,
        streakRecoveryValue: brokenStreakValue,
      });
      recoveryOffered++;

      // Send push notification
      const fcmToken = data.fcmToken;
      if (fcmToken) {
        try {
          await admin.messaging().send({
            token: fcmToken,
            notification: {
              title: '🔥 Streak Broken!',
              body: `Your ${brokenStreakValue}-day streak broke yesterday. Recover it free (once this month).`,
            },
            data: {
              type: 'SYSTEM',
              targetScreen: 'achievements',
              streakRecovery: 'true',
              click_action: 'FLUTTER_NOTIFICATION_CLICK',
            },
            android: { priority: 'high', notification: { sound: 'default', clickAction: 'FLUTTER_NOTIFICATION_CLICK' } },
            apns: { headers: { 'apns-priority': '10' }, payload: { aps: { sound: 'default', badge: 1 } } },
          });
          pushSent++;
        } catch (_err) { /* ignore token failures */ }
      }
    }

    console.log(`checkBrokenStreaks: offered=${recoveryOffered}, pushes=${pushSent}`);
    return { offered: recoveryOffered, pushes: pushSent };
  });

// deploy-bump 
