package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.picflick.app.R
import com.picflick.app.data.Notification
import com.picflick.app.data.NotificationType
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.viewmodel.NotificationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Notifications screen - displays all notifications in a list
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun NotificationsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    onPhotoClick: (flickId: String, imageUrl: String?, userId: String) -> Unit = { _, _, _ -> },
    onChatClick: (chatId: String, otherUserId: String, otherUserName: String, otherUserPhoto: String) -> Unit = { _, _, _, _ -> },
    viewModel: NotificationViewModel = viewModel()
) {
    val notifications = viewModel.notifications
    val unreadCount = viewModel.unreadCount
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage
    val isDarkMode = ThemeManager.isDarkMode.value

    // Load notifications
    LaunchedEffect(userProfile.uid) {
        viewModel.loadNotifications(userProfile.uid)
    }

    // Modern PullRefresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { viewModel.loadNotifications(userProfile.uid) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode))
    ) {
        // Header - PicFlick black banner with logo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(PicFlickBannerBackground)
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
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // PicFlick Logo
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "PicFlick",
                        modifier = Modifier.height(32.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Mark all read button
                if (unreadCount > 0) {
                    TextButton(onClick = { viewModel.markAllAsRead(userProfile.uid) }) {
                        Text("Mark all read", color = Color.White)
                    }
                }
            }
        }

        // Modern PullRefresh content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            when {
                isLoading && notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = if (isDarkMode) Color.White else Color.Black)
                    }
                }

                notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = if (isDarkMode) Color.Gray else Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No notifications yet",
                                fontSize = 18.sp,
                                color = if (isDarkMode) Color.Gray else Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "When someone likes, comments, or follows you,\nit will appear here",
                                fontSize = 14.sp,
                                color = if (isDarkMode) Color.DarkGray else Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            SwipeableNotificationItem(
                                notification = notification,
                                isDarkMode = isDarkMode,
                                onClick = {
                                    if (!notification.isRead) {
                                        viewModel.markAsRead(notification.id)
                                    }
                                    // Navigate based on notification type
                                    when (notification.type) {
                                        NotificationType.LIKE,
                                        NotificationType.REACTION,
                                        NotificationType.COMMENT,
                                        NotificationType.MENTION,
                                        NotificationType.PHOTO_ADDED -> {
                                            // Navigate to photo
                                            notification.flickId?.let { flickId ->
                                                onPhotoClick(flickId, notification.flickImageUrl, notification.senderId)
                                            }
                                        }
                                        NotificationType.FOLLOW -> {
                                            // Navigate to follower's profile
                                            onUserProfileClick(notification.senderId)
                                        }
                                        NotificationType.FRIEND_REQUEST -> {
                                            // Don't navigate, buttons handle the action
                                        }
                                        NotificationType.MESSAGE -> {
                                            // Navigate to chat - need to get chat session
                                            // For now, just show the chat list
                                            onChatClick("", notification.senderId, notification.senderName, notification.senderPhotoUrl)
                                        }
                                        else -> {
                                            // Default: just mark as read
                                        }
                                    }
                                },
                                onDelete = {
                                    viewModel.deleteNotification(notification.id)
                                },
                                onUserProfileClick = { senderId: String ->
                                    onUserProfileClick(senderId)
                                },
                                onAcceptFriendRequest = { senderId: String ->
                                    // Accept the friend request and delete notification
                                    viewModel.acceptFollowRequest(userProfile.uid, senderId, notification.id)
                                },
                                onDeclineFriendRequest = { senderId: String ->
                                    // Decline the friend request and delete notification
                                    viewModel.declineFollowRequest(userProfile.uid, senderId, notification.id)
                                },
                                onAcceptTag = { flickId: String, notificationId: String ->
                                    // Accept being tagged in photo
                                    viewModel.acceptTag(flickId, notificationId)
                                },
                                onDeclineTag = { flickId: String, notificationId: String ->
                                    // Decline being tagged in photo
                                    viewModel.declineTag(flickId, notificationId)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Modern PullRefreshIndicator
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun NotificationItem(
    notification: Notification,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    onAcceptFriendRequest: (String) -> Unit = {},
    onDeclineFriendRequest: (String) -> Unit = {},
    onAcceptTag: (String, String) -> Unit = { _, _ -> }, // flickId, notificationId
    onDeclineTag: (String, String) -> Unit = { _, _ -> } // flickId, notificationId
) {
    // Use Row like ChatListItem - no card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sender avatar with notification icon overlay - 56dp like ChatListItem
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable { onUserProfileClick(notification.senderId) }
        ) {
            AsyncImage(
                model = notification.senderPhotoUrl,
                contentDescription = notification.senderName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (isDarkMode) Color.Gray else Color.LightGray),
                contentScale = ContentScale.Crop
            )

            // Notification type icon (small overlay)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
                    .background(
                        color = getNotificationColor(notification.type),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getNotificationIcon(notification.type),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (isDarkMode) Color.White else Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Notification content - same structure as ChatListItem
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Title and time row (like name/time in ChatListItem)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notification.title,
                    fontSize = 16.sp,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = formatTimestamp(notification.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message row (like last message in ChatListItem)
            Text(
                text = notification.message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Action area - Accept/Decline buttons for FRIEND_REQUEST only
        if (notification.type == NotificationType.FRIEND_REQUEST && !notification.isRead) {
            // Accept/Decline buttons stacked vertically
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = {
                        onAcceptFriendRequest(notification.senderId)
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.wrapContentWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50) // Green
                    )
                ) {
                    Text("Accept", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        onDeclineFriendRequest(notification.senderId)
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.wrapContentWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF4444) // Red
                    )
                ) {
                    Text("Decline", fontSize = 12.sp)
                }
            }
        } else {
            // Unread indicator only (delete via swipe)
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SwipeableNotificationItem(
    notification: Notification,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    onAcceptFriendRequest: (String) -> Unit = {},
    onDeclineFriendRequest: (String) -> Unit = {},
    onAcceptTag: (String, String) -> Unit = { _, _ -> },
    onDeclineTag: (String, String) -> Unit = { _, _ -> }
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {  // Swipe RIGHT to delete
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,   // Enable RIGHT swipe
        enableDismissFromEndToStart = false,  // Disable LEFT swipe
        backgroundContent = {
            val color = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                Color(0xFFFF4444) // Red when swiping right
            } else {
                Color(0x80FF4444) // Semi-transparent red
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart  // Icon on LEFT for right swipe
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        content = {
            NotificationItem(
                notification = notification,
                isDarkMode = isDarkMode,
                onClick = onClick,
                onDelete = onDelete,
                onUserProfileClick = onUserProfileClick,
                onAcceptFriendRequest = onAcceptFriendRequest,
                onDeclineFriendRequest = onDeclineFriendRequest,
                onAcceptTag = onAcceptTag,
                onDeclineTag = onDeclineTag
            )
        }
    )
}

@Composable
private fun getNotificationIcon(type: NotificationType) = when (type) {
    NotificationType.LIKE -> Icons.Default.Favorite
    NotificationType.REACTION -> Icons.Default.Favorite // Will show emoji instead
    NotificationType.COMMENT -> Icons.Default.Email
    NotificationType.FOLLOW -> Icons.Default.Person
    NotificationType.FRIEND_REQUEST -> Icons.Default.Person
    NotificationType.MESSAGE -> Icons.Default.Email
    NotificationType.PHOTO_ADDED -> Icons.Default.Info
    NotificationType.MENTION -> Icons.Default.Email
    NotificationType.STREAK_REMINDER -> Icons.Default.Notifications
    NotificationType.ACHIEVEMENT -> Icons.Default.Notifications
    NotificationType.SYSTEM -> Icons.Default.Info
}

@Composable
private fun getNotificationColor(type: NotificationType) = when (type) {
    NotificationType.LIKE -> Color(0xFFE91E63) // Pink
    NotificationType.REACTION -> Color(0xFFE91E63) // Pink (same as LIKE)
    NotificationType.COMMENT -> Color(0xFF4FC3F7) // Light Blue
    NotificationType.FOLLOW -> Color(0xFF4CAF50) // Green
    NotificationType.FRIEND_REQUEST -> Color(0xFF9C27B0) // Purple
    NotificationType.MESSAGE -> Color(0xFFFF9800) // Orange
    NotificationType.PHOTO_ADDED -> Color(0xFF00BCD4) // Cyan
    NotificationType.MENTION -> Color(0xFFFF5722) // Deep Orange
    NotificationType.STREAK_REMINDER -> Color(0xFFFFD700) // Gold
    NotificationType.ACHIEVEMENT -> Color(0xFFFFD700) // Gold
    NotificationType.SYSTEM -> Color(0xFF607D8B) // Blue Grey
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val diff = now.time - date.time

    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
