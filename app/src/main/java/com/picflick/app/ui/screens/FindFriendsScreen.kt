package com.picflick.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.FullScreenLoading
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.viewmodel.FriendsViewModel

/**
 * Comprehensive screen for finding friends, inviting contacts, and following users
 * Similar to Instagram/Facebook friend discovery with WhatsApp integration
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FindFriendsScreen(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkMode = ThemeManager.isDarkMode.value
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
            viewModel.syncContacts(context, userProfile.uid)
        }
    }

    // Load contacts on first permission grant
    LaunchedEffect(hasContactPermission) {
        if (hasContactPermission) {
            viewModel.syncContacts(context, userProfile.uid)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode))
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
                text = { Text("Friends") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Discover") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Contacts") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Invite") }
            )
        }

    // Load friends when tab changes to Friends
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.loadFollowingUsers(userProfile.following)
        }
    }

    // Content based on tab
        when (selectedTab) {
            0 -> FriendsTab(
                viewModel = viewModel,
                userProfile = userProfile,
                onUnfollow = { user ->
                    viewModel.unfollowUser(userProfile.uid, user.uid)
                    android.widget.Toast.makeText(context, "Unfollowed ${user.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                },
                onUserClick = onNavigateToProfile
            )
            1 -> DiscoverTab(
                viewModel = viewModel,
                userProfile = userProfile,
                onFollowClick = { user, action ->
                    when (action) {
                        "unfollow" -> viewModel.unfollowUser(userProfile.uid, user.uid)
                        "accept" -> viewModel.acceptFollowRequest(userProfile.uid, user)
                        "send_request" -> {
                            viewModel.sendFollowRequest(userProfile.uid, user, userProfile)
                            android.widget.Toast.makeText(context, "Friend request sent to ${user.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onUserClick = onNavigateToProfile
            )
            2 -> ContactsTab(
                viewModel = viewModel,
                userProfile = userProfile,
                hasPermission = hasContactPermission,
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                },
                onFollowClick = { user, action ->
                    when (action) {
                        "unfollow" -> viewModel.unfollowUser(userProfile.uid, user.uid)
                        "accept" -> viewModel.acceptFollowRequest(userProfile.uid, user)
                        "send_request" -> {
                            viewModel.sendFollowRequest(userProfile.uid, user, userProfile)
                            android.widget.Toast.makeText(context, "Friend request sent to ${user.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onUserClick = onNavigateToProfile
            )
            3 -> InviteTab(
                userProfile = userProfile,
                hasContactPermission = hasContactPermission,
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DiscoverTab(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    onFollowClick: (UserProfile, String) -> Unit,
    onUserClick: (String) -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    
    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { 
            if (viewModel.searchQuery.isBlank()) {
                viewModel.loadAllUsers(userProfile.uid)
            } else {
                viewModel.searchUsers(viewModel.searchQuery, userProfile.uid)
            }
        }
    )

    // Reload all users when screen opens
    LaunchedEffect(userProfile.uid) {
        viewModel.loadAllUsers(userProfile.uid)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
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

            // All Users Section
            if (viewModel.searchQuery.isBlank() && viewModel.suggestedUsers.isNotEmpty()) {
                Text(
                    text = "People on PicFlick",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDarkMode) Color.White else Color.Black,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn {
                    items(viewModel.suggestedUsers) { user ->
                        UserResultItem(
                            user = user,
                            isFollowing = userProfile.following.contains(user.uid),
                            hasSentRequest = userProfile.sentFollowRequests.contains(user.uid),
                            hasReceivedRequest = userProfile.pendingFollowRequests.contains(user.uid),
                            isProcessing = viewModel.processingUserIds.contains(user.uid),
                            onFollowClick = {
                                val action = when {
                                    userProfile.following.contains(user.uid) -> "unfollow"
                                    userProfile.pendingFollowRequests.contains(user.uid) -> "accept"
                                    else -> "send_request"
                                }
                                onFollowClick(user, action)
                            },
                            onUserClick = { onUserClick(user.uid) },
                            showMutualFriends = true,
                            mutualCount = (user.followers intersect userProfile.following.toSet()).size
                        )
                    }
                }
            } else if (viewModel.searchQuery.isBlank() && viewModel.suggestedUsers.isEmpty()) {
                // Empty state when no suggestions available
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No suggestions yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Search for friends by name or invite your contacts!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                                hasSentRequest = userProfile.sentFollowRequests.contains(user.uid),
                                hasReceivedRequest = userProfile.pendingFollowRequests.contains(user.uid),
                                isProcessing = viewModel.processingUserIds.contains(user.uid),
                                onFollowClick = {
                                val action = when {
                                    userProfile.following.contains(user.uid) -> "unfollow"
                                    userProfile.pendingFollowRequests.contains(user.uid) -> "accept"
                                    else -> "send_request"
                                }
                                onFollowClick(user, action)
                            },
                                onUserClick = { onUserClick(user.uid) }
                            )
                        }
                    }
                }
            }
        }

        // PullRefreshIndicator
        PullRefreshIndicator(
            refreshing = viewModel.isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun ContactsTab(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onFollowClick: (UserProfile, String) -> Unit,
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
                            hasSentRequest = userProfile.sentFollowRequests.contains(user.uid),
                            hasReceivedRequest = userProfile.pendingFollowRequests.contains(user.uid),
                            isProcessing = viewModel.processingUserIds.contains(user.uid),
                            onFollowClick = {
                                val action = when {
                                    userProfile.following.contains(user.uid) -> "unfollow"
                                    userProfile.pendingFollowRequests.contains(user.uid) -> "accept"
                                    else -> "send_request"
                                }
                                onFollowClick(user, action)
                            },
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FriendsTab(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    onUnfollow: (UserProfile) -> Unit,
    onUserClick: (String) -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val followingUsers = viewModel.followingUsers
    val isLoading = viewModel.isLoading
    
    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { viewModel.loadFollowingUsers(userProfile.following) }
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (followingUsers.isEmpty() && !isLoading) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No friends yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Follow people in the Discover tab to see them here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(followingUsers) { user ->
                    FriendItem(
                        user = user,
                        onUnfollow = { onUnfollow(user) },
                        onUserClick = { onUserClick(user.uid) }
                    )
                }
            }
        }
        
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun FriendItem(
    user: UserProfile,
    onUnfollow: () -> Unit,
    onUserClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ThemeManager.isDarkMode.value) Color(0xFF1C1C1E) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2C2E))
            ) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // User info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (user.bio.isNotEmpty()) {
                    Text(
                        text = user.bio,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
            
            // Unfollow button
            OutlinedButton(
                onClick = onUnfollow,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Following")
            }
        }
    }
}

@Composable
private fun UserResultItem(
    user: UserProfile,
    isFollowing: Boolean,
    hasSentRequest: Boolean = false,
    hasReceivedRequest: Boolean = false,
    isProcessing: Boolean = false,
    onFollowClick: () -> Unit,
    onUserClick: () -> Unit,
    showMutualFriends: Boolean = false,
    mutualCount: Int = 0,
    subtitle: String? = null
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Top row: Profile photo + user info - clickable to view profile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUserClick() },
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
                            .background(if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (isDarkMode) Color.Gray else Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // User info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color.White else Color.Black
                    )

                    if (showMutualFriends && mutualCount > 0) {
                        Text(
                            text = "$mutualCount mutual followers",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color.Gray else Color.DarkGray
                        )
                    } else if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color.Gray else Color.DarkGray
                        )
                    } else {
                        Text(
                            text = "${user.followers.size} followers",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkMode) Color.Gray else Color.DarkGray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Follow button - NOW AT BOTTOM (full width)
            when {
                isProcessing -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                hasSentRequest -> {
                    OutlinedButton(
                        onClick = { /* Cancel functionality can be added */ },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Text("Requested")
                    }
                }
                hasReceivedRequest -> {
                    Button(
                        onClick = onFollowClick,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Accept")
                    }
                }
                isFollowing -> {
                    OutlinedButton(
                        onClick = onFollowClick,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Following")
                    }
                }
                else -> {
                    Button(
                        onClick = onFollowClick,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Follow")
                    }
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
