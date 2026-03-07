package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import coil3.compose.AsyncImage
import com.picflick.app.data.UserProfile
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.data.getColor
import com.picflick.app.data.getDarkColor
import com.picflick.app.data.getDisplayName
import com.picflick.app.data.getLightColor
import com.picflick.app.data.getStorageLimitBytes
import com.picflick.app.data.getStorageLimitGB

/**
 * Settings screen with user preferences and account options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit = {},
    onManageStorage: () -> Unit = {},
    onSubscriptionStatus: () -> Unit = {},
    onPrivacySettings: () -> Unit = {},
    onNotificationsSettings: () -> Unit = {},
    onHelpSupport: () -> Unit = {},
    onAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf("24 MB") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
            // Profile Section with Storage Meter and Tier Ring
            ProfileHeaderWithStorage(
                userProfile = userProfile,
                onManageStorage = onManageStorage,
                onSubscriptionStatus = onSubscriptionStatus
            )

            HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

            // Account Section
            SettingsSection(title = "ACCOUNT") {
                SettingsItem(
                    icon = Icons.Default.Edit,
                    title = "Edit Profile",
                    subtitle = "Change name, bio, photo",
                    onClick = onEditProfile
                )
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = "Manage Storage",
                    subtitle = getStorageSubtitle(userProfile),
                    onClick = onManageStorage,
                    iconBackgroundColor = Color(0xFFE3F2FD),
                    iconColor = Color(0xFF1565C0)
                )
                SettingsItem(
                    icon = Icons.Default.AccountCircle,
                    title = "Subscription",
                    subtitle = getSubscriptionSubtitle(userProfile),
                    onClick = onSubscriptionStatus,
                    iconBackgroundColor = userProfile.subscriptionTier.getColor().copy(alpha = 0.2f),
                    iconColor = userProfile.subscriptionTier.getColor()
                )
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy",
                    subtitle = "Photo visibility, blocked users",
                    onClick = onPrivacySettings
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Push, email preferences",
                    onClick = onNotificationsSettings
                )
            }

            HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

            // Preferences Section
            SettingsSection(title = "PREFERENCES") {
                SettingsItem(
                    icon = Icons.Default.Menu,
                    title = "Appearance",
                    subtitle = if (isDarkMode) "Dark mode (active)" else "Light mode",
                    onClick = { showAppearanceDialog = true },
                    showArrow = false
                )
                SettingsItem(
                    icon = Icons.Default.Edit,
                    title = "Storage & Data",
                    subtitle = "Clear cache: $cacheSize",
                    onClick = { showClearCacheDialog = true }
                )
            }

            HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

            // Support Section
            SettingsSection(title = "SUPPORT") {
                SettingsItem(
                    icon = Icons.Default.Menu,
                    title = "Help Center",
                    subtitle = "FAQ, contact support",
                    onClick = onHelpSupport
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version 1.0.0",
                    onClick = onAbout
                )
            }

            HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

            // Danger Zone
            SettingsSection(title = "DANGER ZONE") {
                SettingsItem(
                    icon = Icons.Default.Close,
                    title = "Sign Out",
                    titleColor = Color.Red,
                    onClick = { showSignOutDialog = true },
                    showArrow = false
                )
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Delete Account",
                    titleColor = Color.Red,
                    subtitle = "Permanently remove your data",
                    onClick = { showDeleteAccountDialog = true },
                    showArrow = false
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out?") },
            text = { Text("You'll need to sign in again to access your account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Sign Out", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account?") },
            text = { 
                Text("This will permanently delete all your photos, friends, and data. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        // TODO: Implement account deletion
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // Clear Cache Confirmation Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?") },
            text = { Text("This will free up $cacheSize of storage space. Your photos and data are safe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        // Clear app cache
                        val cacheDir = context.cacheDir
                        val deleted = cacheDir.deleteRecursively()
                        if (deleted) {
                            cacheSize = "0 MB"
                        }
                    }
                ) {
                    Text("Clear", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // Appearance Settings Dialog
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                isDarkMode = false
                                showAppearanceDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "☀️ Light Mode",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                        if (!isDarkMode) {
                            Text("✓", color = Color(0xFF00D09C))
                        }
                    }
                    
                    HorizontalDivider(color = Color(0xFF2C2C2E))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                isDarkMode = true
                                showAppearanceDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌙 Dark Mode",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                        if (isDarkMode) {
                            Text("✓", color = Color(0xFF00D09C))
                        }
                    }
                    
                    Text(
                        text = "Note: Full dark mode support coming in next update",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Close")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
private fun ProfileHeaderWithStorage(
    userProfile: UserProfile,
    onManageStorage: () -> Unit,
    onSubscriptionStatus: () -> Unit
) {
    val tier = userProfile.subscriptionTier
    val tierColor = tier.getColor()
    val storageUsed = userProfile.storageUsedBytes
    val storageLimit = tier.getStorageLimitBytes()
    val storagePercent = if (storageLimit > 0) {
        (storageUsed * 100 / storageLimit).toInt()
    } else 0
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Top row: Profile Photo with Tier Ring + Name/Storage
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Photo with Tier Color Ring
            Box(
                modifier = Modifier
                    .size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer ring with tier color
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    tierColor,
                                    tier.getDarkColor(),
                                    tier.getLightColor(),
                                    tierColor
                                )
                            )
                        )
                        .padding(3.dp) // Ring thickness
                ) {
                    // Inner profile photo
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userProfile.photoUrl.isNotEmpty()) {
                            AsyncImage(
                                model = userProfile.photoUrl,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = userProfile.displayName.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Founder badge overlay
                if (userProfile.isFounder) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6200EA))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "★",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Name, Email, and Tier Badge
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userProfile.displayName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = userProfile.email,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Tier Badge Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tier Color Dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Tier Name
                    Text(
                        text = tier.getDisplayName().uppercase(),
                        color = tierColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (userProfile.isFounder) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• FOUNDER",
                            color = Color(0xFF6200EA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Storage Progress Bar
        if (tier != SubscriptionTier.FREE || storageUsed > 0) {
            StorageProgressBar(
                usedBytes = storageUsed,
                totalBytes = storageLimit,
                percent = storagePercent,
                tierColor = tierColor,
                onClick = onManageStorage
            )
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Manage Storage Button
            QuickActionButton(
                text = "Manage Storage",
                onClick = onManageStorage,
                color = Color(0xFF1565C0)
            )
            
            // Subscription Button
            QuickActionButton(
                text = "Subscription",
                onClick = onSubscriptionStatus,
                color = tierColor
            )
        }
    }
}

@Composable
private fun StorageProgressBar(
    usedBytes: Long,
    totalBytes: Long,
    percent: Int,
    tierColor: Color,
    onClick: () -> Unit
) {
    val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
    val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Storage Text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Storage Used",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Text(
                text = String.format("%.1f GB / %.0f GB", usedGB, totalGB),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Progress Bar Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2C2C2E))
        ) {
            // Progress Fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                tierColor.copy(alpha = 0.7f),
                                tierColor
                            )
                        )
                    )
            )
        }
        
        // Percentage Text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$percent% full",
                color = if (percent > 90) Color.Red else Color.Gray,
                fontSize = 12.sp
            )
            if (percent > 80) {
                Text(
                    text = "Upgrade for more",
                    color = Color(0xFF00D09C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    text: String,
    onClick: () -> Unit,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    titleColor: Color = Color.White,
    iconBackgroundColor: Color = Color(0xFF2C2C2E),
    iconColor: Color = if (titleColor == Color.Red) Color.Red else Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with custom background
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    titleColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (titleColor == Color.Red) Color.Red else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        // Arrow
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Get storage subtitle text for the Manage Storage settings item
 */
private fun getStorageSubtitle(userProfile: UserProfile): String {
    val usedGB = userProfile.calculateStorageUsedGB()
    val totalGB = userProfile.getTier().getStorageLimitGB()
    return "$usedGB GB / $totalGB GB used"
}

/**
 * Get subscription subtitle text for the Subscription settings item
 */
private fun getSubscriptionSubtitle(userProfile: UserProfile): String {
    val tier = userProfile.getTier()
    return if (userProfile.isFounder) {
        "${tier.getDisplayName()} - Founder (Free)"
    } else {
        tier.getDisplayName()
    }
}
