package com.picflick.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil3.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.data.Notification
import com.picflick.app.data.NotificationType
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.viewmodel.NotificationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.tasks.await

/**
 * Notifications screen - displays all notifications in a list
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun NotificationsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    onPhotoClick: (flickId: String, imageUrl: String?, userId: String) -> Unit = { _, _, _ -> },
    onChatClick: (chatId: String, otherUserId: String, otherUserName: String, otherUserPhoto: String) -> Unit = { _, _, _, _ -> },
    onFindFriendsClick: (String?) -> Unit = {},
    onAddFirstPhotoClick: () -> Unit = {},
    viewModel: NotificationViewModel = viewModel()
) {
    val notifications = viewModel.notifications
    val unreadCount = viewModel.unreadCount
    val isLoading = viewModel.isLoading
    val isDarkMode = ThemeManager.isDarkMode.value
    var showHeaderMenu by remember { mutableStateOf(false) }
    val selectedNotificationIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val oneDayMs = 24L * 60L * 60L * 1000L
    val newNotifications = notifications.filter { now - it.timestamp < oneDayMs }
    val yesterdayNotifications = notifications.filter { now - it.timestamp >= oneDayMs }

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
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                if (isSelectionMode) {
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (selectedNotificationIds.isNotEmpty()) {
                                    viewModel.deleteNotifications(selectedNotificationIds.toSet())
                                    selectedNotificationIds.clear()
                                    isSelectionMode = false
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = selectedNotificationIds.size.toString(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedNotificationIds.clear()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel selection",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Notifications",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Box {
                        IconButton(onClick = { showHeaderMenu = true }, modifier = Modifier.size(48.dp)) {
                            Text(
                                text = "⋮",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mark all read") },
                                onClick = {
                                    viewModel.markAllAsRead(userProfile.uid)
                                    showHeaderMenu = false
                                },
                                enabled = unreadCount > 0
                            )
                            DropdownMenuItem(
                                text = { Text("Delete all") },
                                onClick = {
                                    viewModel.deleteAllNotifications(userProfile.uid)
                                    showHeaderMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // PullRefresh content (edge-to-edge like FriendsScreen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {

                        if (newNotifications.isNotEmpty()) {
                            item { SectionLabel("NEW", isDarkMode) }
                            items(newNotifications, key = { it.id }) { notification ->
                                SwipeableNotificationItem(
                                    notification = notification,
                                    isDarkMode = isDarkMode,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = notification.id in selectedNotificationIds,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (notification.id in selectedNotificationIds) {
                                                selectedNotificationIds.remove(notification.id)
                                                if (selectedNotificationIds.isEmpty()) isSelectionMode = false
                                            } else {
                                                selectedNotificationIds.add(notification.id)
                                            }
                                        } else {
                                            if (!notification.isRead) viewModel.markAsRead(notification.id)
                                            when (notification.type) {
                                                NotificationType.LIKE,
                                                NotificationType.REACTION,
                                                NotificationType.COMMENT,
                                                NotificationType.MENTION,
                                                NotificationType.PHOTO_ADDED -> {
                                                    val fallbackId = notification.flickId ?: notification.id
                                                    if (!notification.flickImageUrl.isNullOrBlank()) {
                                                        onPhotoClick(fallbackId, notification.flickImageUrl, notification.senderId)
                                                    }
                                                }
                                                NotificationType.FOLLOW,
                                                NotificationType.PROFILE_PHOTO_UPDATED -> onUserProfileClick(notification.senderId)
                                                NotificationType.MESSAGE -> onChatClick("", notification.senderId, notification.senderName, notification.senderPhotoUrl)
                                                NotificationType.SYSTEM -> {
                                                    val target = notification.targetScreen?.lowercase().orEmpty()
                                                    val hint = "${notification.title} ${notification.message}".lowercase()
                                                    when {
                                                        target == "upload" ||
                                                            hint.contains("add your 1st photo") ||
                                                            hint.contains("add your first photo") ||
                                                            hint.contains("upload your first photo") -> onAddFirstPhotoClick()
                                                        target == "find_friends" ||
                                                            hint.contains("find friends") ||
                                                            hint.contains("tap to find") ||
                                                            hint.contains("welcome") ||
                                                            hint.contains("onboarding") -> onFindFriendsClick(notification.senderId)
                                                    }
                                                }
                                                NotificationType.FRIEND_REQUEST -> onFindFriendsClick(notification.senderId)
                                                else -> Unit
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) isSelectionMode = true
                                        if (notification.id !in selectedNotificationIds) selectedNotificationIds.add(notification.id)
                                    },
                                    onDelete = { viewModel.deleteNotification(notification) },
                                    onUserProfileClick = onUserProfileClick,
                                    onPhotoClick = {
                                        val fallbackId = notification.flickId ?: notification.id
                                        if (!notification.flickImageUrl.isNullOrBlank()) {
                                            onPhotoClick(fallbackId, notification.flickImageUrl, notification.senderId)
                                        }
                                    },
                                    onAcceptFriendRequest = { senderId ->
                                        viewModel.acceptFollowRequest(userProfile.uid, senderId, notification.id)
                                    },
                                    onDeclineFriendRequest = { senderId ->
                                        viewModel.declineFollowRequest(userProfile.uid, senderId, notification.id)
                                    }
                                )
                            }
                        }

                        if (yesterdayNotifications.isNotEmpty()) {
                            item { SectionLabel("YESTERDAY", isDarkMode) }
                            items(yesterdayNotifications, key = { it.id }) { notification ->
                                SwipeableNotificationItem(
                                    notification = notification,
                                    isDarkMode = isDarkMode,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = notification.id in selectedNotificationIds,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (notification.id in selectedNotificationIds) {
                                                selectedNotificationIds.remove(notification.id)
                                                if (selectedNotificationIds.isEmpty()) isSelectionMode = false
                                            } else {
                                                selectedNotificationIds.add(notification.id)
                                            }
                                        } else {
                                            if (!notification.isRead) viewModel.markAsRead(notification.id)
                                            when (notification.type) {
                                                NotificationType.LIKE,
                                                NotificationType.REACTION,
                                                NotificationType.COMMENT,
                                                NotificationType.MENTION,
                                                NotificationType.PHOTO_ADDED -> {
                                                    val fallbackId = notification.flickId ?: notification.id
                                                    if (!notification.flickImageUrl.isNullOrBlank()) {
                                                        onPhotoClick(fallbackId, notification.flickImageUrl, notification.senderId)
                                                    }
                                                }
                                                NotificationType.FOLLOW,
                                                NotificationType.PROFILE_PHOTO_UPDATED -> onUserProfileClick(notification.senderId)
                                                NotificationType.MESSAGE -> onChatClick("", notification.senderId, notification.senderName, notification.senderPhotoUrl)
                                                NotificationType.SYSTEM -> {
                                                    val target = notification.targetScreen?.lowercase().orEmpty()
                                                    val hint = "${notification.title} ${notification.message}".lowercase()
                                                    when {
                                                        target == "upload" ||
                                                            hint.contains("add your 1st photo") ||
                                                            hint.contains("add your first photo") ||
                                                            hint.contains("upload your first photo") -> onAddFirstPhotoClick()
                                                        target == "find_friends" ||
                                                            hint.contains("find friends") ||
                                                            hint.contains("tap to find") ||
                                                            hint.contains("welcome") ||
                                                            hint.contains("onboarding") -> onFindFriendsClick(notification.senderId)
                                                    }
                                                }
                                                NotificationType.FRIEND_REQUEST -> onFindFriendsClick(notification.senderId)
                                                else -> Unit
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) isSelectionMode = true
                                        if (notification.id !in selectedNotificationIds) selectedNotificationIds.add(notification.id)
                                    },
                                    onDelete = { viewModel.deleteNotification(notification) },
                                    onUserProfileClick = onUserProfileClick,
                                    onPhotoClick = {
                                        val fallbackId = notification.flickId ?: notification.id
                                        if (!notification.flickImageUrl.isNullOrBlank()) {
                                            onPhotoClick(fallbackId, notification.flickImageUrl, notification.senderId)
                                        }
                                    },
                                    onAcceptFriendRequest = { senderId ->
                                        viewModel.acceptFollowRequest(userProfile.uid, senderId, notification.id)
                                    },
                                    onDeclineFriendRequest = { senderId ->
                                        viewModel.declineFollowRequest(userProfile.uid, senderId, notification.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Modern PullRefreshIndicator (overlay, does not consume layout height)
            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationItem(
    notification: Notification,
    isDarkMode: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onUserProfileClick: (String) -> Unit = {},
    onPhotoClick: () -> Unit = {},
    onAcceptFriendRequest: (String) -> Unit = {},
    onDeclineFriendRequest: (String) -> Unit = {}
) {
    var senderName by remember(notification.id, notification.senderName) {
        mutableStateOf(notification.senderName.ifBlank { "Someone" })
    }
    val senderPhotoUrl = rememberLiveUserPhotoUrl(
        userId = notification.senderId,
        fallbackPhotoUrl = notification.senderPhotoUrl
    )

    LaunchedEffect(notification.id, notification.senderId, notification.chatId) {
        if (notification.senderId.isBlank()) return@LaunchedEffect
        if (senderName != "Someone") return@LaunchedEffect

        try {
            // For message notifications, first try chat session participant data
            if (notification.type == NotificationType.MESSAGE && !notification.chatId.isNullOrBlank()) {
                val chatDoc = FirebaseFirestore.getInstance()
                    .collection("chatSessions")
                    .document(notification.chatId)
                    .get()
                    .await()

                val participantNames = chatDoc.get("participantNames") as? Map<*, *>

                val chatName = participantNames?.get(notification.senderId) as? String

                if (!chatName.isNullOrBlank()) senderName = chatName
                // Photo URL is resolved live via users listener
            }

            // Fallback to users collection
            if (senderName == "Someone" || senderPhotoUrl.isBlank()) {
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(notification.senderId)
                    .get()
                    .await()

                val fetchedName = userDoc.getString("displayName")
                    .orEmpty()
                    .ifBlank { userDoc.getString("username").orEmpty() }
                    .ifBlank { userDoc.getString("name").orEmpty() }
                    .ifBlank { senderName }

                senderName = fetchedName
            }
        } catch (_: Exception) {
            // Keep existing fallback values
        }
    }

    val displayMessage = when (notification.type) {
        NotificationType.MESSAGE -> {
            val body = notification.message.ifBlank { "New message" }
            "New message: $body"
        }
        NotificationType.PHOTO_ADDED -> "New flick uploaded"
        NotificationType.PROFILE_PHOTO_UPDATED -> "$senderName updated their profile photo"
        NotificationType.MENTION -> "You're tagged in a photo"
        NotificationType.FRIEND_REQUEST -> "$senderName has requested to be your friend"
        else -> notification.message.ifBlank { notification.title.ifBlank { "Notification" } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Color.White.copy(alpha = if (isDarkMode) 0.18f else 0.35f)
                else if (isDarkMode) isDarkModeBackground(true)
                else isDarkModeBackground(false)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .then(if (isSelectionMode) Modifier else Modifier.clickable { onUserProfileClick(notification.senderId) })
        ) {
            if (senderPhotoUrl.isNotBlank()) {
                AsyncImage(
                    model = senderPhotoUrl,
                    contentDescription = senderName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color.Gray else Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF7A665C) else Color(0xFFB39B8F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = senderName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

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

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = senderName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color.White else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• ${formatTimestamp(notification.timestamp)}",
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displayMessage,
                fontSize = 14.sp,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Medium,
                color = if (isDarkMode) Color.White.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isSelectionMode) {
            when {
                notification.type == NotificationType.FRIEND_REQUEST -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onAcceptFriendRequest(notification.senderId) },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Accept", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onDeclineFriendRequest(notification.senderId) },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Deny", fontSize = 12.sp)
                        }
                    }
                }
                notification.type == NotificationType.FOLLOW || notification.type == NotificationType.PROFILE_PHOTO_UPDATED -> {
                    Button(
                        onClick = { onUserProfileClick(notification.senderId) },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Profile", fontSize = 13.sp)
                    }
                }
                !notification.flickImageUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = notification.flickImageUrl,
                        contentDescription = "Related photo",
                        modifier = Modifier
                            .size(width = 64.dp, height = 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPhotoClick() },
                        contentScale = ContentScale.Crop
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SwipeableNotificationItem(
    notification: Notification,
    isDarkMode: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    onPhotoClick: () -> Unit = {},
    onAcceptFriendRequest: (String) -> Unit = {},
    onDeclineFriendRequest: (String) -> Unit = {}
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
        enableDismissFromStartToEnd = !isSelectionMode,   // Disable swipe while selecting
        enableDismissFromEndToStart = false,  // Disable LEFT swipe
        backgroundContent = {
            val color = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                Color(0xFFFF4444) // Red when swiping right
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
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
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick,
                onUserProfileClick = onUserProfileClick,
                onPhotoClick = onPhotoClick,
                onAcceptFriendRequest = onAcceptFriendRequest,
                onDeclineFriendRequest = onDeclineFriendRequest
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
    NotificationType.PROFILE_PHOTO_UPDATED -> Icons.Default.Person
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
    NotificationType.PROFILE_PHOTO_UPDATED -> Color(0xFF3F51B5) // Indigo
    NotificationType.MENTION -> Color(0xFFFF5722) // Deep Orange
    NotificationType.STREAK_REMINDER -> Color(0xFFFFD700) // Gold
    NotificationType.ACHIEVEMENT -> Color(0xFFFFD700) // Gold
    NotificationType.SYSTEM -> Color(0xFF607D8B) // Blue Grey
}

@Composable
private fun SectionLabel(title: String, isDarkMode: Boolean) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (isDarkMode) Color.Gray else Color.DarkGray
    )
}

internal fun formatTimestamp(timestamp: Long): String {
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
