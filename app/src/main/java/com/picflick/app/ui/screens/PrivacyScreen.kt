package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.picflick.data.UserProfile
import com.example.picflick.repository.FlickRepository

/**
 * Privacy Settings Screen - Privacy by Default
 * - Friends not accepted can't do anything
 * - Blocked users can't do anything
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    val repository = remember { FlickRepository.getInstance() }
    var blockedUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var defaultPrivacy by remember { mutableStateOf("friends") } // Default to friends-only
    var showUnblockDialog by remember { mutableStateOf<UserProfile?>(null) }

    // Load blocked users
    LaunchedEffect(userProfile.blockedUsers) {
        if (userProfile.blockedUsers.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        val loadedProfiles = mutableListOf<UserProfile>()
        var completed = 0

        userProfile.blockedUsers.forEach { blockedId ->
            repository.getUserProfile(blockedId) { result ->
                if (result is com.example.picflick.data.Result.Success) {
                    loadedProfiles.add(result.data)
                }
                completed++
                if (completed >= userProfile.blockedUsers.size) {
                    blockedUsers = loadedProfiles
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy by Default Banner
            item {
                PrivacyBanner()
            }

            // Default Privacy Setting
            item {
                DefaultPrivacySetting(
                    currentPrivacy = defaultPrivacy,
                    onPrivacyChange = { defaultPrivacy = it }
                )
            }

            // Who Can Find You
            item {
                WhoCanFindYouSection()
            }

            // Blocked Users Section
            item {
                BlockedUsersHeader(count = blockedUsers.size)
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
                            color = Color(0xFFD7ECFF)
                        )
                    }
                }
            } else if (blockedUsers.isEmpty()) {
                item {
                    EmptyBlockedUsers()
                }
            } else {
                items(blockedUsers) { blockedUser ->
                    BlockedUserItem(
                        user = blockedUser,
                        onUnblock = { showUnblockDialog = blockedUser }
                    )
                }
            }
        }
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
                            if (result is com.example.picflick.data.Result.Success) {
                                blockedUsers = blockedUsers.filter { it.uid != user.uid }
                            }
                        }
                        showUnblockDialog = null
                    }
                ) {
                    Text("Unblock", color = Color(0xFFD7ECFF))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockDialog = null }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
private fun PrivacyBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFD7ECFF).copy(alpha = 0.1f)
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
                tint = Color(0xFFD7ECFF),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Privacy by Default",
                    color = Color(0xFFD7ECFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Only accepted friends can see and interact with your content. Blocked users can't do anything.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DefaultPrivacySetting(
    currentPrivacy: String,
    onPrivacyChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "DEFAULT PRIVACY",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C1E)
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
                    onClick = { onPrivacyChange("friends") }
                )

                HorizontalDivider(
                    color = Color(0xFF2C2C2E),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Public Option
                PrivacyOption(
                    icon = Icons.Default.Public,
                    title = "Public",
                    subtitle = "Anyone can see (but blocked users still can't)",
                    selected = currentPrivacy == "public",
                    onClick = { onPrivacyChange("public") }
                )
            }
        }

        Text(
            text = "This sets the default for new posts. You can change it per post.",
            color = Color.Gray,
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
    onClick: () -> Unit
) {
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
            tint = if (selected) Color(0xFFD7ECFF) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )

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

        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFFD7ECFF),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun WhoCanFindYouSection() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "WHO CAN FIND YOU",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C1E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Friend Discovery",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "People can find you by username only",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedUsersHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "BLOCKED USERS",
            color = Color.Gray,
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
    }
}

@Composable
private fun EmptyBlockedUsers() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1E)
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
                tint = Color(0xFFD7ECFF),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No Blocked Users",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Blocked users can't see your content or interact with you",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun BlockedUserItem(
    user: UserProfile,
    onUnblock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2C2E)),
                contentAlignment = Alignment.Center
            ) {
                if (user.photoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name & Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = "Blocked - Can't see or interact",
                    color = Color.Red,
                    fontSize = 13.sp
                )
            }

            // Unblock Button
            TextButton(
                onClick = onUnblock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFD7ECFF)
                )
            ) {
                Text("Unblock")
            }
        }
    }
}
