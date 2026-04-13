package com.picflick.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.FullScreenLoading
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.util.rememberLiveUserTierColor
import com.picflick.app.viewmodel.FriendsViewModel

/**
 * Comprehensive screen for finding friends, inviting contacts, and following users
 * Now with 3 tabs: Discover, Contacts, Invite
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FindFriendsScreen(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    onBack: () -> Unit, // Kept for API compatibility
    onNavigateToProfile: (String) -> Unit = {},
    onProfileRefresh: () -> Unit = {}, // Callback to refresh user profile after follow/unfollow
    priorityRequesterId: String? = null
) {
    val context = LocalContext.current
    val isDarkMode = ThemeManager.isDarkMode.value
    var selectedTab by remember { mutableIntStateOf(0) }
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
        // Top bar with tabs - 3 TABS ONLY: Discover, Contacts, Invite
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
                priorityRequesterId = priorityRequesterId,
                onFollowClick = { user, action ->
                    when (action) {
                        "unfollow" -> {
                            viewModel.unfollowUser(userProfile.uid, user.uid)
                            onProfileRefresh() // Refresh to update button state
                        }
                        "accept" -> {
                            viewModel.acceptFollowRequest(userProfile.uid, userProfile.displayName, user)
                            onProfileRefresh() // Refresh to update button state
                        }
                        "send_request" -> {
                            viewModel.sendFollowRequest(userProfile.uid, user, userProfile) { success ->
                                if (success) {
                                    android.widget.Toast.makeText(context, "Friend request sent to ${user.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                                    onProfileRefresh() // Refresh to update button state
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to send request. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                onUserClick = onNavigateToProfile
            )
            1 -> ContactsTab(
                viewModel = viewModel,
                userProfile = userProfile,
                priorityRequesterId = priorityRequesterId,
                hasPermission = hasContactPermission,
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                },
                onFollowClick = { user, action ->
                    when (action) {
                        "unfollow" -> {
                            viewModel.unfollowUser(userProfile.uid, user.uid)
                            onProfileRefresh() // Refresh to update button state
                        }
                        "accept" -> {
                            viewModel.acceptFollowRequest(userProfile.uid, userProfile.displayName, user)
                            onProfileRefresh() // Refresh to update button state
                        }
                        "send_request" -> {
                            viewModel.sendFollowRequest(userProfile.uid, user, userProfile) { success ->
                                if (success) {
                                    android.widget.Toast.makeText(context, "Friend request sent to ${user.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                                    onProfileRefresh() // Refresh to update button state
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to send request. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                onUserClick = onNavigateToProfile,
                onRefresh = { viewModel.syncContacts(context, userProfile.uid) }
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DiscoverTab(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    priorityRequesterId: String? = null,
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

    // Infinite scroll state
    val listState = rememberLazyListState()

    // Detect when scrolled to bottom for infinite scroll
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Load more when 5 items from end
            lastVisibleItem >= totalItems - 5 && totalItems > 0 && !viewModel.isLoading
        }
    }

    // Trigger load more
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && viewModel.canLoadMore) {
            viewModel.loadMoreUsers()
        }
    }

    // Reload all users when Discover tab is opened
    LaunchedEffect(Unit) {
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

            // All Users Section - Filter out already followed friends
            val filteredSuggestedUsers = viewModel.suggestedUsers.filter { user ->
                !userProfile.following.contains(user.uid) && user.uid != userProfile.uid
            }

            // Sort by mutual friends (high -> low), while keeping priority requester pinned first when applicable
            val orderedSuggestedUsers = remember(filteredSuggestedUsers, priorityRequesterId, userProfile.following) {
                val followingSet = userProfile.following.toSet()
                filteredSuggestedUsers
                    .sortedWith(
                        compareByDescending<UserProfile> { user ->
                            (user.followers intersect followingSet).size
                        }.thenBy { it.displayName.lowercase() }
                    )
                    .let { sortedByMutuals ->
                        if (priorityRequesterId.isNullOrBlank()) {
                            sortedByMutuals
                        } else {
                            sortedByMutuals.sortedByDescending { it.uid == priorityRequesterId }
                        }
                    }
            }
            
            if (viewModel.searchQuery.isBlank() && orderedSuggestedUsers.isNotEmpty()) {
                Text(
                    text = "People on PicFlick",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDarkMode) Color.White else Color.Black,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    state = listState
                ) {
                    items(
                        items = orderedSuggestedUsers,
                        key = { it.uid },
                        contentType = { "suggested_user" }
                    ) { user ->
                        UserResultItem(
                            user = user,
                            isFollowing = false, // Already filtered out followed users
                            hasSentRequest = userProfile.sentFollowRequests.contains(user.uid),
                            hasReceivedRequest = userProfile.pendingFollowRequests.contains(user.uid),
                            isProcessing = viewModel.processingUserIds.contains(user.uid),
                            onFollowClick = {
                                val action = when {
                                    userProfile.pendingFollowRequests.contains(user.uid) -> "accept"
                                    else -> "send_request"
                                }
                                onFollowClick(user, action)
                            },
                            onCancelRequest = {
                                viewModel.cancelFollowRequest(userProfile.uid, user.uid)
                            },
                            onDeclineRequest = {
                                viewModel.declineFollowRequest(userProfile.uid, user.uid)
                            },
                            onUserClick = { onUserClick(user.uid) },
                            showMutualFriends = true,
                            mutualCount = (user.followers intersect userProfile.following.toSet()).size
                        )
                    }
                    
                    // Loading footer for infinite scroll
                    if (viewModel.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else if (viewModel.searchQuery.isBlank() && orderedSuggestedUsers.isEmpty() && viewModel.suggestedUsers.isNotEmpty()) {
                // All suggested users are already friends
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
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You're following everyone!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invite more friends to join PicFlick",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                            text = "No users found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Be the first to invite friends to PicFlick!",
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
                        items(
                            items = viewModel.searchResults,
                            key = { it.uid },
                            contentType = { "search_user" }
                        ) { user ->
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
                                onCancelRequest = {
                                    viewModel.cancelFollowRequest(userProfile.uid, user.uid)
                                },
                                onDeclineRequest = {
                                    viewModel.declineFollowRequest(userProfile.uid, user.uid)
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
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = Color(0xFF2A2A2A), // Dark gray background
            contentColor = Color.White // White spinner
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ContactsTab(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    priorityRequesterId: String? = null,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onFollowClick: (UserProfile, String) -> Unit,
    onUserClick: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    var isContactsSynced by remember { mutableStateOf(false) }
    val midBlue = Color(0xFF2A4A73)
    
    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = onRefresh
    )

    // Infinite scroll state
    val listState = rememberLazyListState()

    // Detect when scrolled to bottom for infinite scroll
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - 5 && totalItems > 0 && !viewModel.isLoading
        }
    }

    // Trigger load more
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && viewModel.canLoadMore) {
            viewModel.loadMoreUsers()
        }
    }
    
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
    } else {
        // Show contacts with pull-to-refresh
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            if (viewModel.isLoading && viewModel.contactsOnApp.isEmpty()) {
                FullScreenLoading()
            } else {
                // Show contacts on the app - filter out already followed
                val filteredContacts = viewModel.contactsOnApp.filter { user ->
                    !userProfile.following.contains(user.uid) && user.uid != userProfile.uid
                }

                // Bring priority requester to top when arriving from friend-request notifications
                val orderedContacts = remember(filteredContacts, priorityRequesterId) {
                    if (priorityRequesterId.isNullOrBlank()) {
                        filteredContacts
                    } else {
                        filteredContacts.sortedByDescending { it.uid == priorityRequesterId }
                    }
                }
                
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contacts on PicFlick (${orderedContacts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                        Button(
                            onClick = {
                                onRefresh()
                                isContactsSynced = true
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = if (isContactsSynced) {
                                ButtonDefaults.buttonColors(
                                    containerColor = midBlue,
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = if (isDarkMode) Color.White else Color.Black
                                )
                            },
                            border = if (isContactsSynced) null else androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.2f)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (isContactsSynced) "Contacts synced" else "Sync contacts",
                                fontSize = 12.sp
                            )
                        }
                    }

                    // On the app section
                    if (orderedContacts.isNotEmpty()) {
                        LazyColumn(
                            state = listState
                        ) {
                            items(
                                items = orderedContacts,
                                key = { it.uid },
                                contentType = { "contact_user" }
                            ) { user ->
                                UserResultItem(
                                    user = user,
                                    isFollowing = false, // Already filtered
                                    hasSentRequest = userProfile.sentFollowRequests.contains(user.uid),
                                    hasReceivedRequest = userProfile.pendingFollowRequests.contains(user.uid),
                                    isProcessing = viewModel.processingUserIds.contains(user.uid),
                                    onFollowClick = {
                                        val action = when {
                                            userProfile.pendingFollowRequests.contains(user.uid) -> "accept"
                                            else -> "send_request"
                                        }
                                        onFollowClick(user, action)
                                    },
                                    onCancelRequest = {
                                        viewModel.cancelFollowRequest(userProfile.uid, user.uid)
                                    },
                                    onDeclineRequest = {
                                        viewModel.declineFollowRequest(userProfile.uid, user.uid)
                                    },
                                    onUserClick = { onUserClick(user.uid) },
                                    subtitle = "From your contacts"
                                )
                            }
                            
                            // Loading footer for infinite scroll
                            if (viewModel.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    } else if (viewModel.contactsOnApp.isNotEmpty() && orderedContacts.isEmpty()) {
                        // All contacts are already friends
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "You're following all your contacts!",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Invite more friends to join!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
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
            
            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = Color(0xFF2A2A2A), // Dark gray background
                contentColor = Color.White // White spinner
            )
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
https://play.google.com/store/apps/details?id=com.picflick.app

My username: ${userProfile.displayName}"""

    val logoShareUri = remember {
        runCatching {
            val logoFile = File(context.cacheDir, "picflick_logo_512.png")
            val bitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                com.picflick.app.R.drawable.ic_launcher_phone_box
            )
            FileOutputStream(logoFile).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logoFile
            )
        }.getOrNull()
    }

    fun shareGeneral() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (logoShareUri != null) "image/png" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, inviteMessage)
            logoShareUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Invite friends to PicFlick"))
    }

    fun shareViaWhatsApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (logoShareUri != null) "image/png" else "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, inviteMessage)
            logoShareUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
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
        Button(
            onClick = { shareGeneral() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A4A73),
                contentColor = Color.White
            )
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
    hasSentRequest: Boolean = false,
    hasReceivedRequest: Boolean = false,
    isProcessing: Boolean = false,
    onFollowClick: () -> Unit,
    onCancelRequest: () -> Unit = {},
    onDeclineRequest: () -> Unit = {},
    onUserClick: () -> Unit,
    showMutualFriends: Boolean = false,
    mutualCount: Int = 0,
    subtitle: String? = null
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val liveUserPhoto = rememberLiveUserPhotoUrl(user.uid, user.photoUrl)
    val tierRingColor = rememberLiveUserTierColor(user.uid)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo - clickable
            if (liveUserPhoto.isNotEmpty()) {
                AsyncImage(
                    model = liveUserPhoto,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                        .clickable { onUserClick() },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                        .background(if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFE0E0E0))
                        .clickable { onUserClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (isDarkMode) Color.Gray else Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User info - takes available space
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color.White else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (showMutualFriends && mutualCount > 0) {
                    Text(
                        text = "$mutualCount mutual followers",
                        fontSize = 14.sp,
                        color = if (isDarkMode) Color.Gray else Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = if (isDarkMode) Color.Gray else Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "${user.followers.size} followers",
                        fontSize = 14.sp,
                        color = if (isDarkMode) Color.Gray else Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action button - unchanged behavior
            val addColor = Color(0xFF2A4A73)
            val waitingColor = Color(0xFF2A4A73)
            val friendsColor = Color(0xFF2A4A73)

            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                hasSentRequest -> {
                    OutlinedButton(
                        onClick = onCancelRequest,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.wrapContentWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = waitingColor,
                            contentColor = Color.White,
                            disabledContainerColor = waitingColor,
                            disabledContentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, waitingColor)
                    ) {
                        Text("Waiting", fontSize = 12.sp, color = Color.White)
                    }
                }
                hasReceivedRequest -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onFollowClick,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.wrapContentWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A4A73),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Accept", fontSize = 12.sp)
                        }
                        Button(
                            onClick = onDeclineRequest,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.wrapContentWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC62828),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Decline", fontSize = 12.sp)
                        }
                    }
                }
                isFollowing -> {
                    OutlinedButton(
                        onClick = onFollowClick,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.wrapContentWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = friendsColor,
                            contentColor = Color.White,
                            disabledContainerColor = friendsColor,
                            disabledContentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, friendsColor)
                    ) {
                        Text("Friends", fontSize = 12.sp, color = Color.White)
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = onFollowClick,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.wrapContentWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = addColor,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = addColor
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, addColor)
                    ) {
                        Text("Add", fontSize = 12.sp, color = addColor)
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            color = if (isDarkMode) Color(0xFF222222) else Color(0x22000000),
            thickness = 0.5.dp
        )
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
