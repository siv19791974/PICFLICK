package com.app.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.picflick.data.NotificationPreferences
import com.app.picflick.data.UserProfile

/**
 * Simplified Notification Settings Screen
 * One toggle controls both push and in-app notifications
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Switch
            MasterToggle(
                title = "Enable Notifications",
                subtitle = "Receive notifications for activity",
                checked = preferences.notificationsEnabled,
                onCheckedChange = {
                    preferences = preferences.copy(notificationsEnabled = it)
                }
            )

            if (preferences.notificationsEnabled) {
                // Notification Type Toggles
                Text(
                    text = "NOTIFY ME ABOUT",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                NotificationToggle(
                    title = "Likes",
                    subtitle = "Someone likes my photo",
                    icon = Icons.Default.Favorite,
                    checked = preferences.likes,
                    onCheckedChange = { preferences = preferences.copy(likes = it) }
                )

                NotificationToggle(
                    title = "Reactions",
                    subtitle = "Someone reacts to my photo",
                    icon = Icons.Default.Mood,
                    checked = preferences.reactions,
                    onCheckedChange = { preferences = preferences.copy(reactions = it) }
                )

                NotificationToggle(
                    title = "Comments",
                    subtitle = "Someone comments on my photo",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    checked = preferences.comments,
                    onCheckedChange = { preferences = preferences.copy(comments = it) }
                )

                NotificationToggle(
                    title = "New Followers",
                    subtitle = "Someone follows me",
                    icon = Icons.Default.PersonAdd,
                    checked = preferences.follows,
                    onCheckedChange = { preferences = preferences.copy(follows = it) }
                )

                NotificationToggle(
                    title = "Messages",
                    subtitle = "New chat messages",
                    icon = Icons.Default.MailOutline,
                    checked = preferences.messages,
                    onCheckedChange = { preferences = preferences.copy(messages = it) }
                )

                NotificationToggle(
                    title = "New Photos",
                    subtitle = "Friends post new photos",
                    icon = Icons.Default.Photo,
                    checked = preferences.newPhotos,
                    onCheckedChange = { preferences = preferences.copy(newPhotos = it) }
                )

                NotificationToggle(
                    title = "Mentions",
                    subtitle = "Someone mentions me",
                    icon = Icons.Default.AlternateEmail,
                    checked = preferences.mentions,
                    onCheckedChange = { preferences = preferences.copy(mentions = it) }
                )

                NotificationToggle(
                    title = "Streak Reminders",
                    subtitle = "Daily upload reminders",
                    icon = Icons.Default.LocalFireDepartment,
                    checked = preferences.streakReminders,
                    onCheckedChange = { preferences = preferences.copy(streakReminders = it) }
                )

                NotificationToggle(
                    title = "Achievements",
                    subtitle = "Earn new achievements",
                    icon = Icons.Default.EmojiEvents,
                    checked = preferences.achievements,
                    onCheckedChange = { preferences = preferences.copy(achievements = it) }
                )

                NotificationToggle(
                    title = "System Announcements",
                    subtitle = "Important updates from PicFlick",
                    icon = Icons.Default.Info,
                    checked = preferences.systemAnnouncements,
                    onCheckedChange = { preferences = preferences.copy(systemAnnouncements = it) }
                )

                HorizontalDivider(
                    color = Color(0xFF2C2C2E),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Quiet Hours
                QuietHoursSection(
                    preferences = preferences,
                    onPreferencesChange = { preferences = it }
                )

                HorizontalDivider(
                    color = Color(0xFF2C2C2E),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Display Settings
                DisplaySettingsSection(
                    preferences = preferences,
                    onPreferencesChange = { preferences = it }
                )

                HorizontalDivider(
                    color = Color(0xFF2C2C2E),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Email Notifications
                EmailSection(
                    preferences = preferences,
                    onPreferencesChange = { preferences = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MasterToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) Color(0xFFD7ECFF).copy(alpha = 0.1f) else Color(0xFF1C1C1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = if (checked) Color(0xFFD7ECFF) else Color.Gray,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD7ECFF),
                    checkedTrackColor = Color(0xFFD7ECFF).copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun NotificationToggle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (checked) Color(0xFFD7ECFF).copy(alpha = 0.2f) else Color(0xFF2C2C2E),
                        RoundedCornerShape(10.dp)
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

@Composable
private fun QuietHoursSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit
) {
    Column {
        Text(
            text = "QUIET HOURS",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.clickable {
                        onPreferencesChange(preferences.copy(quietHoursEnabled = !preferences.quietHoursEnabled))
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DoNotDisturb,
                        null,
                        tint = if (preferences.quietHoursEnabled) Color(0xFFFF6B6B) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Do Not Disturb", color = Color.White, fontWeight = FontWeight.Medium)
                        Text("Silence push notifications", color = Color.Gray, fontSize = 13.sp)
                    }
                    Switch(
                        checked = preferences.quietHoursEnabled,
                        onCheckedChange = {
                            onPreferencesChange(preferences.copy(quietHoursEnabled = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFF6B6B),
                            checkedTrackColor = Color(0xFFFF6B6B).copy(alpha = 0.5f)
                        )
                    )
                }

                if (preferences.quietHoursEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("From", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                formatHour(preferences.quietHoursStart),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Gray)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("To", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                formatHour(preferences.quietHoursEnd),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietPresetButton("Night (10PM-8AM)", preferences.quietHoursStart == 22) {
                            onPreferencesChange(preferences.copy(quietHoursStart = 22, quietHoursEnd = 8))
                        }
                        QuietPresetButton("Work (9AM-5PM)", preferences.quietHoursStart == 9) {
                            onPreferencesChange(preferences.copy(quietHoursStart = 9, quietHoursEnd = 17))
                        }
                    }
                }
            }
        }
    }
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

@Composable
private fun QuietPresetButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFFD7ECFF) else Color(0xFF2C2C2E)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DisplaySettingsSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit
) {
    Column {
        Text(
            text = "DISPLAY & SOUND",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DisplayToggle(
                    "Show Previews",
                    "Display content in notifications",
                    Icons.Default.Visibility,
                    preferences.showNotificationPreviews
                ) {
                    onPreferencesChange(preferences.copy(showNotificationPreviews = it))
                }
                DisplayToggle(
                    "Sound",
                    "Play sound for notifications",
                    Icons.AutoMirrored.Filled.VolumeUp,
                    preferences.notificationSoundEnabled
                ) {
                    onPreferencesChange(preferences.copy(notificationSoundEnabled = it))
                }
                DisplayToggle(
                    "Vibration",
                    "Vibrate for notifications",
                    Icons.Default.Vibration,
                    preferences.notificationVibrationEnabled
                ) {
                    onPreferencesChange(preferences.copy(notificationVibrationEnabled = it))
                }
            }
        }
    }
}

@Composable
private fun DisplayToggle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (checked) Color(0xFFD7ECFF) else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFD7ECFF),
                checkedTrackColor = Color(0xFFD7ECFF).copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun EmailSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit
) {
    Column {
        Text(
            text = "EMAIL NOTIFICATIONS",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.clickable {
                        onPreferencesChange(preferences.copy(emailNotificationsEnabled = !preferences.emailNotificationsEnabled))
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Email, null, tint = if (preferences.emailNotificationsEnabled) Color(0xFFD7ECFF) else Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Email Notifications", color = Color.White, fontWeight = FontWeight.Medium)
                        Text("Important updates via email", color = Color.Gray, fontSize = 13.sp)
                    }
                    Switch(
                        checked = preferences.emailNotificationsEnabled,
                        onCheckedChange = {
                            onPreferencesChange(preferences.copy(emailNotificationsEnabled = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFD7ECFF),
                            checkedTrackColor = Color(0xFFD7ECFF).copy(alpha = 0.5f)
                        )
                    )
                }

                if (preferences.emailNotificationsEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    EmailToggle("Security Alerts", "Login from new devices", preferences.emailForSecurityAlerts) {
                        onPreferencesChange(preferences.copy(emailForSecurityAlerts = it))
                    }
                    EmailToggle("Account Changes", "Profile updates", preferences.emailForAccountChanges) {
                        onPreferencesChange(preferences.copy(emailForAccountChanges = it))
                    }
                    EmailToggle("Weekly Digest", "Summary of your activity", preferences.emailWeeklyDigest) {
                        onPreferencesChange(preferences.copy(emailWeeklyDigest = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFD7ECFF),
                checkedTrackColor = Color(0xFFD7ECFF).copy(alpha = 0.5f)
            )
        )
    }
}
