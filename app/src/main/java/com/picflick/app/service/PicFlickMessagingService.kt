package com.picflick.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.picflick.app.MainActivity
import com.picflick.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service for push notifications
 */
class PicFlickMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "picflick_important"  // Must match Firebase Function
        const val CHANNEL_NAME = "PicFlick Important"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        
        // Save token to Firestore for the current user
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM", "Token saved to Firestore successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM", "Failed to save token: ${e.message}")
                }
        } else {
            Log.w("FCM", "No user logged in, cannot save token")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Get message data
        val title = remoteMessage.notification?.title ?: "PicFlick"
        val message = remoteMessage.notification?.body ?: "New notification"
        val data = remoteMessage.data

        // Show notification
        showNotification(title, message, data)
    }

    private fun showNotification(title: String, message: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun Map<String, String>.firstNonBlank(vararg keys: String): String? {
            return keys.firstNotNullOfOrNull { key -> this[key]?.takeIf { it.isNotBlank() } }
        }

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH  // Changed to HIGH for important messages
            ).apply {
                description = "Important notifications for messages, friend requests, and activity"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open app
        val flickId = data.firstNonBlank("flickId", "flick_id", "postId", "post_id") ?: ""
        val chatId = data.firstNonBlank("chatId", "chat_id", "conversationId") ?: ""
        val senderId = data.firstNonBlank("senderId", "sender_id", "fromUserId", "userId") ?: ""
        val senderName = data.firstNonBlank("senderName", "sender_name", "fromUserName", "userName") ?: ""
        val type = data.firstNonBlank("type", "notificationType") ?: ""

        val resolvedTargetScreen = data.firstNonBlank("targetScreen", "screen", "destination")
            ?: when {
                flickId.isNotBlank() -> "photo"
                chatId.isNotBlank() -> "chat"
                else -> "notifications"
            }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Add explicit data fields for notification routing
            putExtra("targetScreen", resolvedTargetScreen)
            putExtra("type", type)
            putExtra("flickId", flickId)
            putExtra("senderId", senderId)
            putExtra("senderName", senderName)
            putExtra("chatId", chatId)
            // Add any other data payload fields
            data.forEach { (key, value) ->
                if (!hasExtra(key)) { // Don't overwrite explicit fields
                    putExtra(key, value)
                }
            }
        }

        val intentIdentity = listOf(type, flickId, chatId, senderId, data["timestamp"] ?: "").joinToString("|")
        val requestCode = intentIdentity.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Show notification
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
