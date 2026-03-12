package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.picflick.app.R
import com.picflick.app.data.ChatSession
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.viewmodel.ChatViewModel
import com.picflick.app.viewmodel.FriendsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * WhatsApp-style Chats List Screen
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatsScreen(
    userProfile: UserProfile,
    viewModel: ChatViewModel,
    friendsViewModel: FriendsViewModel,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onChatClick: (ChatSession, String) -> Unit,
    onStartNewChat: ((String, String, String) -> Unit)? = null,
    onUserProfileClick: (String) -> Unit = {}
) {
    LaunchedEffect(userProfile.uid) {
        viewModel.loadChatSessions(userProfile.uid)
        viewModel.observeUnreadCount(userProfile.uid)
        // Load friends for new chat dialog
        friendsViewModel.loadFollowingUsers(userProfile.following)
    }

    var showNewChatDialog by remember { mutableStateOf(false) }
    val isDarkMode = ThemeManager.isDarkMode.value

    // Modern PullRefresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.loadChatSessions(userProfile.uid) }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // NO BANNER - banner is in MainActivity

            // Error message display - compact snackbar style, only when NOT loading
            if (viewModel.errorMessage != null && !viewModel.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { 
                                viewModel.clearError()
                                viewModel.loadChatSessions(userProfile.uid) 
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Retry", 
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Modern PullRefresh content - uses weight to fill remaining space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                    viewModel.isLoading && viewModel.chatSessions.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    viewModel.chatSessions.isEmpty() -> {
                        // Empty state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.content_desc_person),
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No messages yet",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap + to start chatting with friends!",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                    else -> {
                        // Chat list - WhatsApp style
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = viewModel.chatSessions,
                                key = { it.id }
                            ) { session ->
                                val otherUserId = session.participants.find { it != userProfile.uid } ?: ""
                                val otherUserName = session.participantNames[otherUserId] ?: "Unknown"
                                val otherUserPhoto = session.participantPhotos[otherUserId] ?: ""

                                ChatListItem(
                                    session = session,
                                    otherUserId = otherUserId,
                                    otherUserName = otherUserName,
                                    otherUserPhoto = otherUserPhoto,
                                    currentUserId = userProfile.uid,
                                    onClick = { onChatClick(session, otherUserId) },
                                    onProfilePhotoClick = { onUserProfileClick(otherUserId) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 80.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }

                // Modern PullRefreshIndicator
                PullRefreshIndicator(
                    refreshing = viewModel.isLoading,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        // NEW CHAT FAB - Bottom right
        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color.Black,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Message",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // New Chat Dialog
    if (showNewChatDialog) {
        NewChatDialog(
            friends = friendsViewModel.followingUsers,
            isLoading = friendsViewModel.isLoading,
            isDarkMode = isDarkMode,
            onDismiss = { showNewChatDialog = false },
            onFriendSelected = { friendId, friendName, friendPhoto ->
                showNewChatDialog = false
                onStartNewChat?.invoke(friendId, friendName, friendPhoto)
            },
            onUserProfileClick = onUserProfileClick
        )
    }
}

@Composable
private fun ChatListItem(
    session: ChatSession,
    otherUserId: String,
    otherUserName: String,
    otherUserPhoto: String,
    currentUserId: String,
    onClick: () -> Unit,
    onProfilePhotoClick: () -> Unit = {}
) {
    val isLastMessageFromMe = session.lastSenderId == currentUserId
    val hasUnread = session.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo - clickable to view profile
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable { onProfilePhotoClick() }
        ) {
            if (otherUserPhoto.isNotEmpty()) {
                AsyncImage(
                    model = otherUserPhoto,
                    contentDescription = otherUserName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Online indicator (could be dynamic)
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF25D366)) // WhatsApp green
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Chat info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Name and time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = otherUserName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatChatTime(session.lastTimestamp),
                    fontSize = 12.sp,
                    color = if (hasUnread) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Last message preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show ticks if last message is from me
                if (isLastMessageFromMe) {
                    Text(
                        text = "✓✓",
                        fontSize = 10.sp,  // Smaller WhatsApp-style
                        color = if (session.unreadCount > 0) Color.Gray else Color(0xFF25D366),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                Text(
                    text = buildString {
                        if (isLastMessageFromMe) append("You: ")
                        append(session.lastMessage)
                    },
                    fontSize = 14.sp,
                    color = if (hasUnread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Unread badge
                if (hasUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366)), // WhatsApp green
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (session.unreadCount > 99) "99+" else session.unreadCount.toString(),
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""

    val date = Date(timestamp)
    val now = Date()
    val diff = now.time - date.time

    val oneDay = 24 * 60 * 60 * 1000

    return when {
        diff < oneDay -> {
            // Today - show time
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
        diff < oneDay * 7 -> {
            // This week - show day name
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        }
        else -> {
            // Older - show date
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
        }
    }
}

/**
 * Full-screen dialog to select a friend to start a new chat with
 * WhatsApp-style contact selector - edge to edge
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatDialog(
    friends: List<com.picflick.app.data.UserProfile>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onFriendSelected: (String, String, String) -> Unit,
    onUserProfileClick: (String) -> Unit
) {
    val backgroundColor = if (isDarkMode) Color.Black else Color(0xFFB8D4F0) // Light blue in light mode
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header - like WhatsApp's "Select contact"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(if (isDarkMode) Color.Black else Color(0xFFB8D4F0))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (isDarkMode) Color.White else Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Select contact",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color.White else Color.Black
                                )
                                Text(
                                    text = "${friends.size} contacts",
                                    fontSize = 14.sp,
                                    color = if (isDarkMode) Color.Gray else Color.DarkGray
                                )
                            }
                        }
                        
                        IconButton(onClick = { /* Search */ }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isDarkMode) Color.White else Color.Black
                            )
                        }
                    }
                }
                
                // New Group / New Contact buttons like WhatsApp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // New Group button
                    NewContactActionItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366)), // WhatsApp green
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        title = "New group",
                        isDarkMode = isDarkMode,
                        onClick = { /* TODO: New group */ }
                    )
                    
                    // New Contact button
                    NewContactActionItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366)), // WhatsApp green
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        title = "New contact",
                        isDarkMode = isDarkMode,
                        onClick = { /* TODO: New contact */ }
                    )
                }
                
                // Contacts list - edge to edge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = if (isDarkMode) Color.White else Color.Black)
                            }
                        }
                        friends.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No friends yet",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Follow some friends to start chatting!",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Section header
                                item {
                                    Text(
                                        text = "Contacts on PicFlick",
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color.Gray else Color.DarkGray,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                
                                items(friends) { friend ->
                                    FullScreenFriendItem(
                                        friend = friend,
                                        isDarkMode = isDarkMode,
                                        onClick = { onFriendSelected(friend.uid, friend.displayName, friend.photoUrl) },
                                        onProfilePhotoClick = { onUserProfileClick(friend.uid) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Action item for new group/contact - WhatsApp style
 */
@Composable
private fun NewContactActionItem(
    icon: @Composable () -> Unit,
    title: String,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDarkMode) Color.White else Color.Black
        )
    }
}

/**
 * Friend item for full-screen contact selector - WhatsApp style
 */
@Composable
private fun FullScreenFriendItem(
    friend: com.picflick.app.data.UserProfile,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onProfilePhotoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo - clickable
        Box(
            modifier = Modifier.clickable { onProfilePhotoClick() }
        ) {
            if (friend.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = friend.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = friend.displayName.firstOrNull()?.uppercase() ?: "?"
                    Text(
                        text = initial,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color.DarkGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Friend info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.displayName,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Show status or "Available" subtitle
            val subtitle = friend.bio.takeIf { it.isNotBlank() } ?: "Available"
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = if (isDarkMode) Color.Gray else Color.DarkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Individual friend item in the new chat dialog
 */
@Composable
private fun FriendListItem(
    friend: com.picflick.app.data.UserProfile,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onProfilePhotoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo
        Box(
            modifier = Modifier.clickable { onProfilePhotoClick() }
        ) {
            AsyncImage(
                model = friend.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Friend name
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "Tap to start chatting",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // Chat icon
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = "Start chat",
            modifier = Modifier.size(24.dp),
            tint = Color.Black
        )
    }
}
