package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.isDarkModeSurface
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.util.rememberLiveUserTierColor

/**
 * Privacy Settings Screen - Privacy by Default
 * - Friends not accepted can't do anything
 * - Blocked users can't do anything
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkMode = ThemeManager.isDarkMode.value
    val repository = remember { FlickRepository.getInstance() }
    var blockedUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var mutedUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var defaultPrivacy by remember { mutableStateOf(userProfile.defaultPrivacy) } // Load from userProfile
    var showUnblockDialog by remember { mutableStateOf<UserProfile?>(null) }
    var showUnmuteDialog by remember { mutableStateOf<UserProfile?>(null) }

    // Theme-aware colors
    val accentColor = Color(0xFF1565C0) // Blue for light mode
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBackground = isDarkModeSurface(isDarkMode)
    val dividerColor = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray

    // Load blocked + muted users
    LaunchedEffect(userProfile.blockedUsers, userProfile.mutedUsers) {
        val blockedIds = userProfile.blockedUsers
        val activeMutedIds = userProfile.mutedUsers
            .filterValues { it == Long.MAX_VALUE || it > System.currentTimeMillis() }
            .keys
            .toList()

        val idsToLoad = (blockedIds + activeMutedIds).distinct()

        if (idsToLoad.isEmpty()) {
            blockedUsers = emptyList()
            mutedUsers = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        val loadedProfiles = mutableListOf<UserProfile>()
        var completed = 0

        idsToLoad.forEach { uid ->
            repository.getUserProfile(uid) { result ->
                if (result is com.picflick.app.data.Result.Success) {
                    loadedProfiles.add(result.data)
                }
                completed++
                if (completed >= idsToLoad.size) {
                    blockedUsers = loadedProfiles.filter { blockedIds.contains(it.uid) }
                    mutedUsers = loadedProfiles.filter { activeMutedIds.contains(it.uid) }
                    isLoading = false
                }
            }
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
                        text = "Privacy",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        containerColor = isDarkModeBackground(isDarkMode)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy by Default Banner
            item {
                PrivacyBanner(isDarkMode = isDarkMode)
            }

            // Default Privacy Setting
            item {
                DefaultPrivacySetting(
                    currentPrivacy = defaultPrivacy,
                    onPrivacyChange = { newPrivacy ->
                        defaultPrivacy = newPrivacy
                        // Save to Firestore
                        repository.updateDefaultPrivacy(userProfile.uid, newPrivacy) { result ->
                            if (result is com.picflick.app.data.Result.Success) {
                                // Show success toast
                                android.widget.Toast.makeText(
                                    context,
                                    "Privacy setting saved",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    isDarkMode = isDarkMode
                )
            }

            // Privacy Policy Link
            item {
                PrivacyPolicyLink(
                    isDarkMode = isDarkMode,
                    onClick = onPrivacyPolicy
                )
            }

            // Muted Users Section
            item {
                MutedUsersHeader(count = mutedUsers.size, isDarkMode = isDarkMode)
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = accentColor
                        )
                    }
                }
            } else if (mutedUsers.isEmpty()) {
                item {
                    EmptyMutedUsers(isDarkMode = isDarkMode)
                }
            } else {
                items(
                    items = mutedUsers,
                    key = { it.uid },
                    contentType = { "muted_user" }
                ) { mutedUser ->
                    MutedUserItem(
                        user = mutedUser,
                        onUnmute = { showUnmuteDialog = mutedUser },
                        isDarkMode = isDarkMode
                    )
                }
            }

            // Blocked Users Section
            item {
                BlockedUsersHeader(count = blockedUsers.size, isDarkMode = isDarkMode)
            }

            if (!isLoading && blockedUsers.isEmpty()) {
                item {
                    EmptyBlockedUsers(isDarkMode = isDarkMode)
                }
            } else if (!isLoading) {
                items(
                    items = blockedUsers,
                    key = { it.uid },
                    contentType = { "blocked_user" }
                ) { blockedUser ->
                    BlockedUserItem(
                        user = blockedUser,
                        onUnblock = { showUnblockDialog = blockedUser },
                        isDarkMode = isDarkMode
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Unmute Confirmation Dialog
    showUnmuteDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showUnmuteDialog = null },
            title = { Text("Unmute ${user.displayName}?") },
            text = { Text("Their uploads will appear in your feed again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.unmuteUser(userProfile.uid, user.uid) { result ->
                            if (result is com.picflick.app.data.Result.Success) {
                                mutedUsers = mutedUsers.filter { it.uid != user.uid }
                            }
                        }
                        showUnmuteDialog = null
                    }
                ) {
                    Text("Unmute", color = Color(0xFFFFB347))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmuteDialog = null }) {
                    Text("Cancel", color = if (isDarkMode) Color.White else Color.Black)
                }
            },
            containerColor = cardBackground,
            titleContentColor = textColor,
            textContentColor = subtitleColor
        )
    }

    // Unblock Confirmation Dialog
    showUnblockDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showUnblockDialog = null },
            title = { Text("Unblock ${user.displayName}?") },
            text = { Text("They will be able to see your public content and interact with you again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.unblockUser(userProfile.uid, user.uid) { result ->
                            if (result is com.picflick.app.data.Result.Success) {
                                blockedUsers = blockedUsers.filter { it.uid != user.uid }
                            }
                        }
                        showUnblockDialog = null
                    }
                ) {
                    Text("Unblock", color = Color(0xFF1565C0))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockDialog = null }) {
                    Text("Cancel", color = if (isDarkMode) Color.White else Color.Black)
                }
            },
            containerColor = cardBackground,
            titleContentColor = textColor,
            textContentColor = subtitleColor
        )
    }
}

@Composable
private fun PrivacyBanner(isDarkMode: Boolean) {
    val accentColor = Color(0xFF1565C0)
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBackground = if (isDarkMode) Color(0xFF1C1C1E) else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) accentColor.copy(alpha = 0.1f) else accentColor.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Privacy by Default",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Only accepted friends can see and interact with your content. Blocked users can't do anything.",
                    color = subtitleColor,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DefaultPrivacySetting(
    currentPrivacy: String,
    onPrivacyChange: (String) -> Unit,
    isDarkMode: Boolean
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBackground = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val accentColor = Color(0xFF1565C0)
    val dividerColor = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "DEFAULT PRIVACY",
            color = subtitleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = cardBackground
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Friends Only Option
                PrivacyOption(
                    icon = Icons.Default.Group,
                    title = "Friends Only",
                    subtitle = "Only your accepted friends can see",
                    selected = currentPrivacy == "friends",
                    onClick = { onPrivacyChange("friends") },
                    isDarkMode = isDarkMode
                )

                HorizontalDivider(
                    color = dividerColor,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Public Option
                PrivacyOption(
                    icon = Icons.Default.Public,
                    title = "Public",
                    subtitle = "Anyone can see (but blocked users still can't)",
                    selected = currentPrivacy == "public",
                    onClick = { onPrivacyChange("public") },
                    isDarkMode = isDarkMode
                )
            }
        }

        Text(
            text = "This sets the default for new posts. You can change it per post.",
            color = subtitleColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
    }
}

@Composable
private fun PrivacyOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    isDarkMode: Boolean
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val accentColor = Color(0xFF1565C0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accentColor else subtitleColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 13.sp
            )
        }

        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PrivacyPolicyLink(
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val accentColor = Color(0xFF1565C0)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "LEGAL",
            color = subtitleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Policy,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Policy",
                        color = textColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Read how we protect your data",
                        color = subtitleColor,
                        fontSize = 13.sp
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Go",
                    tint = subtitleColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MutedUsersHeader(count: Int, isDarkMode: Boolean) {
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "MUTED USERS",
            color = subtitleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        if (count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge(
                containerColor = Color(0xFFFFB347),
                contentColor = Color.Black
            ) {
                Text(text = count.toString())
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "$count muted",
            color = subtitleColor,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyMutedUsers(isDarkMode: Boolean) {
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = subtitleColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No muted users",
                color = subtitleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MutedUserItem(
    user: UserProfile,
    onUnmute: () -> Unit,
    isDarkMode: Boolean
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBackground = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val liveUserPhoto = rememberLiveUserPhotoUrl(user.uid, user.photoUrl)
    val tierRingColor = rememberLiveUserTierColor(user.uid)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (liveUserPhoto.isNotEmpty()) {
                AsyncImage(
                    model = liveUserPhoto,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                        .background(subtitleColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = "Muted user",
                    color = subtitleColor,
                    fontSize = 13.sp
                )
            }

            OutlinedButton(
                onClick = onUnmute,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFB347))
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFFB347)
                )
            ) {
                Text("Unmute")
            }
        }
    }
}

@Composable
private fun BlockedUsersHeader(count: Int, isDarkMode: Boolean) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "BLOCKED USERS",
            color = subtitleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        if (count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge(
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Text(text = count.toString())
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "$count blocked",
            color = subtitleColor,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyBlockedUsers(isDarkMode: Boolean) {
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = subtitleColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No blocked users",
                color = subtitleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Block someone from their profile if needed",
                color = subtitleColor,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun BlockedUserItem(
    user: UserProfile,
    onUnblock: () -> Unit,
    isDarkMode: Boolean
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBackground = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val liveUserPhoto = rememberLiveUserPhotoUrl(user.uid, user.photoUrl)
    val tierRingColor = rememberLiveUserTierColor(user.uid)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Photo
            if (liveUserPhoto.isNotEmpty()) {
                AsyncImage(
                    model = liveUserPhoto,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                        .background(subtitleColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = "Blocked user",
                    color = subtitleColor,
                    fontSize = 13.sp
                )
            }

            // Unblock Button
            OutlinedButton(
                onClick = onUnblock,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1565C0))
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF1565C0)
                )
            ) {
                Text("Unblock")
            }
        }
    }
}
