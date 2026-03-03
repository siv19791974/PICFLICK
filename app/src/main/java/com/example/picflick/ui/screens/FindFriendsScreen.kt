package com.example.picflick.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.example.picflick.R
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.FullScreenLoading
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.viewmodel.FriendsViewModel
import kotlinx.coroutines.launch

/**
 * Comprehensive screen for finding friends, inviting contacts, and following users
 * Similar to Instagram/Facebook friend discovery with WhatsApp integration
 */
@Composable
fun FindFriendsScreen(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var hasContactPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactPermission = isGranted
        if (isGranted) {
            viewModel.syncContacts(context)
        }
    }

    // Load contacts on first permission grant
    LaunchedEffect(hasContactPermission) {
        if (hasContactPermission) {
            viewModel.syncContacts(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground)
    ) {
        // Top bar with tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Black,
            contentColor = Color.White
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Discover") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Contacts") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Invite") }
            )
        }

        // Content based on tab
        when (selectedTab) {
            0 -> DiscoverTab(
                viewModel = viewModel,
                userProfile = userProfile,
                onFollowClick = { user, isFollowing ->
                    if (isFollowing) {
                        viewModel.unfollowUser(userProfile.uid, user.uid)
                    } else {
                        viewModel.followUser(userProfile.uid, user, userProfile)
                    }
                },
                onUserClick = onNavigateToProfile
            )
            1 -> ContactsTab(
                viewModel = viewModel,
                userProfile = userProfile,
                hasPermission = hasContactPermission,
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                },
                onFollowClick = { user, isFollowing ->
                    if (isFollowing) {
                        viewModel.unfollowUser(userProfile.uid, user.uid)
                    } else {
                        viewModel.followUser(userProfile.uid, user, userProfile)
                    }
                },
                onUserClick = onNavigateToProfile
            )
            2 -> InviteTab(
                userProfile = userProfile,
                hasContactPermission = hasContactPermission,
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }
            )
        }
    }
}

@Composable
private fun DiscoverTab(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    onFollowClick: (UserProfile, Boolean) -> Unit,
    onUserClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { query ->
                viewModel.searchUsers(query, userProfile.uid)
            },
            label = { Text("Search users...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        // Suggested Friends Section
        if (viewModel.searchQuery.isBlank() && viewModel.suggestedUsers.isNotEmpty()) {
            Text(
                text = "Suggested for you",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn {
                items(viewModel.suggestedUsers) { user ->
                    UserResultItem(
                        user = user,
                        isFollowing = userProfile.following.contains(user.uid),
                        onFollowClick = { onFollowClick(user, userProfile.following.contains(user.uid)) },
                        onUserClick = { onUserClick(user.uid) },
                        showMutualFriends = true,
                        mutualCount = (user.followers intersect userProfile.following.toSet()).size
                    )
                }
            }
        } else if (viewModel.searchQuery.isNotBlank()) {
            // Search results
            if (viewModel.isLoading) {
                FullScreenLoading()
            } else if (viewModel.searchResults.isEmpty()) {
                EmptySearchState()
            } else {
                LazyColumn {
                    items(viewModel.searchResults) { user ->
                        UserResultItem(
                            user = user,
                            isFollowing = userProfile.following.contains(user.uid),
                            onFollowClick = { onFollowClick(user, userProfile.following.contains(user.uid)) },
                            onUserClick = { onUserClick(user.uid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsTab(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onFollowClick: (UserProfile, Boolean) -> Unit,
    onUserClick: (String) -> Unit
) {
    if (!hasPermission) {
        // Permission request UI
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "See who's on PicFlick",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allow access to your contacts to find friends already using the app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Allow Access")
                }
            }
        }
    } else if (viewModel.isLoading) {
        FullScreenLoading()
    } else {
        // Show contacts on the app
        Column(modifier = Modifier.fillMaxSize()) {
            // On the app section
            if (viewModel.contactsOnApp.isNotEmpty()) {
                Text(
                    text = "Contacts on PicFlick (${viewModel.contactsOnApp.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn {
                    items(viewModel.contactsOnApp) { user ->
                        UserResultItem(
                            user = user,
                            isFollowing = userProfile.following.contains(user.uid),
                            onFollowClick = { onFollowClick(user, userProfile.following.contains(user.uid)) },
                            onUserClick = { onUserClick(user.uid) },
                            subtitle = "From your contacts"
                        )
                    }
                }
            } else {
                // No contacts on app
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No contacts on PicFlick yet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invite them to join!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteTab(
    userProfile: UserProfile,
    hasContactPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    val inviteMessage = """Hey! 👋

Check out PicFlick - a cool photo sharing app I've been using!

Join me and share your best shots: 
https://play.google.com/store/apps/details?id=com.example.picflick

My username: ${userProfile.displayName}"""

    fun shareGeneral() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, inviteMessage)
        }
        context.startActivity(Intent.createChooser(intent, "Invite friends to PicFlick"))
    }

    fun shareViaWhatsApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, inviteMessage)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // WhatsApp not installed, fallback to general share
            shareGeneral()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // App icon or logo
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Invite Friends",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Share PicFlick with your friends and see their photos",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // WhatsApp Button (Green like WhatsApp)
        Button(
            onClick = { shareViaWhatsApp() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF25D366) // WhatsApp green
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Share via WhatsApp",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // General Share Button
        OutlinedButton(
            onClick = { shareGeneral() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Share via...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Copy link option
        TextButton(
            onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("PicFlick Invite", inviteMessage)
                clipboard.setPrimaryClip(clip)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy invite text")
        }
    }
}

@Composable
private fun UserResultItem(
    user: UserProfile,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onUserClick: () -> Unit,
    showMutualFriends: Boolean = false,
    mutualCount: Int = 0,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onUserClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo
            if (user.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (showMutualFriends && mutualCount > 0) {
                    Text(
                        text = "$mutualCount mutual followers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "${user.followers.size} followers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Follow button
            if (isFollowing) {
                OutlinedButton(
                    onClick = onFollowClick,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Following")
                }
            } else {
                Button(
                    onClick = onFollowClick,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Follow")
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No users found",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try a different search term",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}