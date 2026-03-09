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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.AsyncImage
import com.picflick.app.R
import com.picflick.app.data.ChatSession
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.PicFlickBackground
import com.picflick.app.viewmodel.ChatViewModel
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
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onChatClick: (ChatSession, String) -> Unit,
    onUserProfileClick: (String) -> Unit = {}
) {
    LaunchedEffect(userProfile.uid) {
        viewModel.loadChatSessions(userProfile.uid)
        viewModel.observeUnreadCount(userProfile.uid)
    }

    // Modern PullRefresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.loadChatSessions(userProfile.uid) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground)
    ) {
        // NO BANNER - banner is in MainActivity

        // Modern PullRefresh content
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                text = "Start chatting with friends!",
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
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
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
