package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import com.picflick.app.data.NotificationPreferences
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.PullRefreshContainer
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground

/**
 * Simplified Notification Settings Screen
 * One toggle controls both push and in-app notifications
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun NotificationSettingsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onSavePreferences: (NotificationPreferences) -> Unit
) {
    var preferences by remember { mutableStateOf(userProfile.notificationPreferences) }
    var isRefreshing by remember { mutableStateOf(false) }
    val isDarkMode = ThemeManager.isDarkMode.value

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            preferences = userProfile.notificationPreferences
            isRefreshing = true
        }
    )

    fun updatePreferences(updated: NotificationPreferences) {
        preferences = updated
        onSavePreferences(updated)
    }

    LaunchedEffect(userProfile.notificationPreferences) {
        preferences = userProfile.notificationPreferences
    }

    LaunchedEffect(preferences) {
        onSavePreferences(preferences)
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            // Custom compact 48dp title bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Notification Settings",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // Spacer for balance (same width as back button)
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        containerColor = isDarkModeBackground(isDarkMode)
    ) { padding ->
        PullRefreshContainer(
            refreshing = isRefreshing,
            pullRefreshState = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // In-app notification controls
                SettingsSectionHeader(title = "In-app notifications", isDarkMode = isDarkMode)

            MasterToggle(
                title = "Enable In-App Notifications",
                subtitle = "Show activity inside the app",
                checked = preferences.notificationsEnabled,
                onCheckedChange = {
                    updatePreferences(preferences.copy(notificationsEnabled = it))
                },
                isDarkMode = isDarkMode
            )

            // Push selector controls
            SettingsSectionHeader(title = "Push notifications", isDarkMode = isDarkMode)
            MasterToggle(
                title = "Enable Push Notifications",
                subtitle = "Receive alerts on your device",
                checked = preferences.pushNotificationsEnabled,
                onCheckedChange = {
                    updatePreferences(preferences.copy(pushNotificationsEnabled = it))
                },
                isDarkMode = isDarkMode
            )

            if (preferences.notificationsEnabled || preferences.pushNotificationsEnabled) {
                HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)
                
                // Notification Type Toggles
                SettingsSectionHeader(title = "Notify me about (in-app)", isDarkMode = isDarkMode)

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

                HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

                // Push Type Toggles
                SettingsSectionHeader(title = "Notify me about (push)", isDarkMode = isDarkMode)

                NotificationToggle(
                    title = "Messages",
                    subtitle = "Push for new chat messages",
                    icon = Icons.Default.MailOutline,
                    checked = preferences.pushMessages,
                    onCheckedChange = { updatePreferences(preferences.copy(pushMessages = it)) }
                )

                NotificationToggle(
                    title = "Friend Requests",
                    subtitle = "Push for follow/friend requests",
                    icon = Icons.Default.PersonAdd,
                    checked = preferences.pushFollows,
                    onCheckedChange = { updatePreferences(preferences.copy(pushFollows = it)) }
                )

                NotificationToggle(
                    title = "Tags & Mentions",
                    subtitle = "Push when someone tags/mentions you",
                    icon = Icons.Default.AlternateEmail,
                    checked = preferences.pushMentions,
                    onCheckedChange = { updatePreferences(preferences.copy(pushMentions = it)) }
                )

                NotificationToggle(
                    title = "Reactions & Comments",
                    subtitle = "Push for likes/reactions/comments",
                    icon = Icons.Default.Favorite,
                    checked = preferences.pushReactions,
                    onCheckedChange = { updatePreferences(preferences.copy(pushReactions = it, pushLikes = it, pushComments = it)) }
                )

                NotificationToggle(
                    title = "System Announcements",
                    subtitle = "Important app updates",
                    icon = Icons.Default.Info,
                    checked = preferences.pushSystemAnnouncements,
                    onCheckedChange = { updatePreferences(preferences.copy(pushSystemAnnouncements = it)) }
                )

                HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

                // Quiet Hours
                SettingsSectionHeader(title = "Quiet hours", isDarkMode = isDarkMode)
                QuietHoursSection(
                    preferences = preferences,
                    onPreferencesChange = { preferences = it },
                    isDarkMode = isDarkMode
                )

                HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

                // Display Settings
                SettingsSectionHeader(title = "Display", isDarkMode = isDarkMode)
                DisplaySettingsSection(
                    preferences = preferences,
                    onPreferencesChange = { preferences = it },
                    isDarkMode = isDarkMode
                )

                HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

                // Email Notifications
                SettingsSectionHeader(title = "Email", isDarkMode = isDarkMode)
                EmailSection(
                    preferences = preferences,
                    onPreferencesChange = { preferences = it },
                    isDarkMode = isDarkMode
                )
            }

                Spacer(modifier = Modifier.height(80.dp))
            }

        }
    }
}

@Composable
private fun MasterToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDarkMode: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                tint = if (checked) Color(0xFF1565C0) else if (isDarkMode) Color.Gray else Color.DarkGray,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isDarkMode) Color.White else Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (isDarkMode) Color.Gray else Color.DarkGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF1565C0),
                    checkedTrackColor = Color(0xFF1565C0).copy(alpha = 0.5f)
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
    onCheckedChange: (Boolean) -> Unit,
    isDarkMode: Boolean = ThemeManager.isDarkMode.value
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        if (checked) {
                            if (isDarkMode) Color(0xFF1565C0).copy(alpha = 0.2f) else Color(0xFFE3F2FD)
                        } else {
                            if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE0E0E0)
                        },
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) Color(0xFF1565C0) else if (isDarkMode) Color.Gray else Color.DarkGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isDarkMode) Color.White else Color.Black,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = if (isDarkMode) Color.Gray else Color.DarkGray,
                    fontSize = 13.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF1565C0),
                    checkedTrackColor = Color(0xFF1565C0).copy(alpha = 0.5f),
                    uncheckedThumbColor = if (isDarkMode) Color.Gray else Color.Gray,
                    uncheckedTrackColor = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray
                )
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    isDarkMode: Boolean
) {
    Text(
        text = title,
        color = if (isDarkMode) Color.Gray else Color.DarkGray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun QuietHoursSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    isDarkMode: Boolean
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        tint = if (preferences.quietHoursEnabled) Color(0xFFFF6B6B) else if (isDarkMode) Color.Gray else Color.DarkGray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Do Not Disturb", color = if (isDarkMode) Color.White else Color.Black, fontWeight = FontWeight.Medium)
                        Text("Silence push notifications", color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 13.sp)
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
                            Text("From", color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 12.sp)
                            Text(
                                formatHour(preferences.quietHoursStart),
                                color = if (isDarkMode) Color.White else Color.Black,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (isDarkMode) Color.Gray else Color.DarkGray)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("To", color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 12.sp)
                            Text(
                                formatHour(preferences.quietHoursEnd),
                                color = if (isDarkMode) Color.White else Color.Black,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietPresetButton("Night (10PM-8AM)", preferences.quietHoursStart == 22, isDarkMode) {
                            onPreferencesChange(preferences.copy(quietHoursStart = 22, quietHoursEnd = 8))
                        }
                        QuietPresetButton("Work (9AM-5PM)", preferences.quietHoursStart == 9, isDarkMode) {
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
private fun QuietPresetButton(label: String, isSelected: Boolean, isDarkMode: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF2A4A73) else if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            color = if (isSelected) Color.White else if (isDarkMode) Color.White else Color.Black,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DisplaySettingsSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    isDarkMode: Boolean
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DisplayToggle(
                    "Show Previews",
                    "Display content in notifications",
                    Icons.Default.Visibility,
                    preferences.showNotificationPreviews,
                    isDarkMode
                ) {
                    onPreferencesChange(preferences.copy(showNotificationPreviews = it))
                }
                DisplayToggle(
                    "Sound",
                    "Play sound for notifications",
                    Icons.AutoMirrored.Filled.VolumeUp,
                    preferences.notificationSoundEnabled,
                    isDarkMode
                ) {
                    onPreferencesChange(preferences.copy(notificationSoundEnabled = it))
                }
                DisplayToggle(
                    "Vibration",
                    "Vibrate for notifications",
                    Icons.Default.Vibration,
                    preferences.notificationVibrationEnabled,
                    isDarkMode
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
    isDarkMode: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (checked) Color(0xFF1565C0) else if (isDarkMode) Color.Gray else Color.DarkGray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isDarkMode) Color.White else Color.Black, fontWeight = FontWeight.Medium)
            Text(subtitle, color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1565C0),
                checkedTrackColor = Color(0xFFD7ECFF).copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun EmailSection(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    isDarkMode: Boolean
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.clickable {
                        onPreferencesChange(preferences.copy(emailNotificationsEnabled = !preferences.emailNotificationsEnabled))
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Email, null, tint = if (preferences.emailNotificationsEnabled) Color(0xFF1565C0) else if (isDarkMode) Color.Gray else Color.DarkGray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Email Notifications", color = if (isDarkMode) Color.White else Color.Black, fontWeight = FontWeight.Medium)
                        Text("Important updates via email", color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 13.sp)
                    }
                    Switch(
                        checked = preferences.emailNotificationsEnabled,
                        onCheckedChange = {
                            onPreferencesChange(preferences.copy(emailNotificationsEnabled = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1565C0),
                            checkedTrackColor = Color(0xFF1565C0).copy(alpha = 0.5f)
                        )
                    )
                }

                if (preferences.emailNotificationsEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    EmailToggle("Security Alerts", "Login from new devices", preferences.emailForSecurityAlerts, isDarkMode) {
                        onPreferencesChange(preferences.copy(emailForSecurityAlerts = it))
                    }
                    EmailToggle("Account Changes", "Profile updates", preferences.emailForAccountChanges, isDarkMode) {
                        onPreferencesChange(preferences.copy(emailForAccountChanges = it))
                    }
                    EmailToggle("Weekly Digest", "Summary of your activity", preferences.emailWeeklyDigest, isDarkMode) {
                        onPreferencesChange(preferences.copy(emailWeeklyDigest = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailToggle(title: String, subtitle: String, checked: Boolean, isDarkMode: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isDarkMode) Color.White else Color.Black, fontWeight = FontWeight.Medium)
            Text(subtitle, color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1565C0),
                checkedTrackColor = Color(0xFF1565C0).copy(alpha = 0.5f)
            )
        )
    }
}
