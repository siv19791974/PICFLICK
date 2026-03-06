package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picflick.data.NotificationPreferences
import com.example.picflick.data.NotificationType
import com.example.picflick.data.UserProfile

/**
 * Notification Settings Screen - Granular control over push and in-app notifications
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onSavePreferences: (NotificationPreferences) -> Unit
) {
    var preferences by remember { mutableStateOf(userProfile.notificationPreferences) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSavePreferences(preferences) }
                    ) {
                        Text(
                            "Save",
                            color = Color(0xFFD7ECFF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Push Notifications Section
            PushNotificationsSection(
                preferences = preferences,
                onPreferencesChange = { preferences = it }
            )

            HorizontalDivider(
                color = Color(0xFF2C2C2E),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // In-App Notifications Section
            InAppNotificationsSection(
                preferences = preferences,
                onPreferencesChange = { preferences = it }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PushNotificationsSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = Color(0xFFD7ECFF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PUSH NOTIFICATIONS",
                color = Color(0xFFD7ECFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Master Toggle
        PreferenceToggle(
            title = "Enable Push Notifications",
            subtitle = "Receive notifications when app is closed",
            icon = Icons.Default.NotificationsActive,
            checked = preferences.pushNotificationsEnabled,
            onCheckedChange = {
                onPreferencesChange(preferences.copy(pushNotificationsEnabled = it))
            }
        )

        if (preferences.pushNotificationsEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            // Individual toggles
            Text(
                text = "Notification Types",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            PreferenceToggle(
                title = "New Photos",
                subtitle = "Friends post new photos",
                icon = Icons.Default.Photo,
                checked = preferences.pushNewPhotos,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushNewPhotos = it))
                }
            )

            PreferenceToggle(
                title = "Likes",
                subtitle = "Someone likes your photo",
                icon = Icons.Default.Favorite,
                checked = preferences.pushLikes,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushLikes = it))
                }
            )

            PreferenceToggle(
                title = "Reactions",
                subtitle = "Someone reacts to your photo",
                icon = Icons.Default.Mood,
                checked = preferences.pushReactions,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushReactions = it))
                }
            )

            PreferenceToggle(
                title = "Comments",
                subtitle = "Someone comments on your photo",
                icon = Icons.Default.Comment,
                checked = preferences.pushComments,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushComments = it))
                }
            )

            PreferenceToggle(
                title = "New Followers",
                subtitle = "Someone follows you",
                icon = Icons.Default.PersonAdd,
                checked = preferences.pushFollows,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushFollows = it))
                }
            )

            PreferenceToggle(
                title = "Messages",
                subtitle = "New chat messages",
                icon = Icons.Default.Message,
                checked = preferences.pushMessages,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushMessages = it))
                }
            )

            PreferenceToggle(
                title = "Mentions",
                subtitle = "Someone mentions you",
                icon = Icons.Default.AlternateEmail,
                checked = preferences.pushMentions,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushMentions = it))
                }
            )

            PreferenceToggle(
                title = "Streak Reminders",
                subtitle = "Daily upload reminders",
                icon = Icons.Default.LocalFireDepartment,
                checked = preferences.pushStreakReminders,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushStreakReminders = it))
                }
            )

            PreferenceToggle(
                title = "Achievements",
                subtitle = "Earn new achievements",
                icon = Icons.Default.EmojiEvents,
                checked = preferences.pushAchievements,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushAchievements = it))
                }
            )

            PreferenceToggle(
                title = "System Announcements",
                subtitle = "Important updates from PicFlick",
                icon = Icons.Default.Info,
                checked = preferences.pushSystemAnnouncements,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pushSystemAnnouncements = it))
                }
            )
        }
    }
}

@Composable
private fun InAppNotificationsSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = Color(0xFFD7ECFF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "IN-APP NOTIFICATIONS",
                color = Color(0xFFD7ECFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Master Toggle
        PreferenceToggle(
            title = "Enable In-App Notifications",
            subtitle = "Show notifications in the notification bell",
            icon = Icons.Default.NotificationImportant,
            checked = preferences.inAppNotificationsEnabled,
            onCheckedChange = {
                onPreferencesChange(preferences.copy(inAppNotificationsEnabled = it))
            }
        )

        if (preferences.inAppNotificationsEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            // Individual toggles
            Text(
                text = "Notification Types",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            PreferenceToggle(
                title = "New Photos",
                subtitle = "Friends post new photos",
                icon = Icons.Default.Photo,
                checked = preferences.inAppNewPhotos,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppNewPhotos = it))
                }
            )

            PreferenceToggle(
                title = "Likes",
                subtitle = "Someone likes your photo",
                icon = Icons.Default.Favorite,
                checked = preferences.inAppLikes,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppLikes = it))
                }
            )

            PreferenceToggle(
                title = "Reactions",
                subtitle = "Someone reacts to your photo",
                icon = Icons.Default.Mood,
                checked = preferences.inAppReactions,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppReactions = it))
                }
            )

            PreferenceToggle(
                title = "Comments",
                subtitle = "Someone comments on your photo",
                icon = Icons.Default.Comment,
                checked = preferences.inAppComments,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppComments = it))
                }
            )

            PreferenceToggle(
                title = "New Followers",
                subtitle = "Someone follows you",
                icon = Icons.Default.PersonAdd,
                checked = preferences.inAppFollows,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppFollows = it))
                }
            )

            PreferenceToggle(
                title = "Messages",
                subtitle = "New chat messages",
                icon = Icons.Default.Message,
                checked = preferences.inAppMessages,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppMessages = it))
                }
            )

            PreferenceToggle(
                title = "Mentions",
                subtitle = "Someone mentions you",
                icon = Icons.Default.AlternateEmail,
                checked = preferences.inAppMentions,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppMentions = it))
                }
            )

            PreferenceToggle(
                title = "Streak Reminders",
                subtitle = "Daily upload reminders",
                icon = Icons.Default.LocalFireDepartment,
                checked = preferences.inAppStreakReminders,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppStreakReminders = it))
                }
            )

            PreferenceToggle(
                title = "Achievements",
                subtitle = "Earn new achievements",
                icon = Icons.Default.EmojiEvents,
                checked = preferences.inAppAchievements,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppAchievements = it))
                }
            )

            PreferenceToggle(
                title = "System Announcements",
                subtitle = "Important updates from PicFlick",
                icon = Icons.Default.Info,
                checked = preferences.inAppSystemAnnouncements,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(inAppSystemAnnouncements = it))
                }
            )
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Color(0xFF2C2C2E),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) Color(0xFFD7ECFF) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            // Toggle
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD7ECFF),
                    checkedTrackColor = Color(0xFFD7ECFF).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }
    }
}
