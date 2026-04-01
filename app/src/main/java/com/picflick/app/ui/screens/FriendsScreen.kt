package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.ListItemShimmer
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.util.rememberLiveUserTierColor
import com.picflick.app.viewmodel.FriendsViewModel

/**
 * Screen showing list of friends the user is following
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FriendsScreen(
    userProfile: UserProfile,
    viewModel: FriendsViewModel,
    onBack: () -> Unit,
    onFindFriendsClick: () -> Unit = {},
    onProfilePhotoClick: (UserProfile) -> Unit = {},
    titleOverride: String? = null,
    showFindFriendsBar: Boolean = true,
    isOwnFriendsList: Boolean = true,
    currentUserId: String = userProfile.uid,
    currentUserFollowingIds: Set<String> = userProfile.following.toSet(),
    currentUserSentRequestIds: Set<String> = userProfile.sentFollowRequests.toSet(),
    displayedUsersOverride: List<UserProfile>? = null,
    onAddFriendClick: (UserProfile) -> Unit = {},
    onCancelFriendRequestClick: (UserProfile) -> Unit = {}
) {
    val context = LocalContext.current
    var pendingDeleteFriendId by remember { mutableStateOf<String?>(null) }
    val optimisticallyRemovedFriendIds = remember { mutableStateListOf<String>() }

    // Load following users
    LaunchedEffect(userProfile.following) {
        viewModel.loadFollowingUsers(userProfile.following)
    }

    // Modern PullRefresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.loadFollowingUsers(userProfile.following) }
    )

    val isDarkMode = ThemeManager.isDarkMode.value
    val sourceUsers = displayedUsersOverride ?: viewModel.followingUsers
    val visibleFollowingUsers by remember(sourceUsers, optimisticallyRemovedFriendIds) {
        derivedStateOf {
            sourceUsers
                .filterNot { it.uid in optimisticallyRemovedFriendIds }
                .filterNot { it.uid == currentUserId }
                .distinctBy { it.uid }
        }
    }

    Column(
modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode))
            .pointerInput(pendingDeleteFriendId) {
                detectTapGestures(onTap = {
                    if (pendingDeleteFriendId != null) {
                        pendingDeleteFriendId = null
                    }
                })
            }
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
                Text(
                    text = titleOverride ?: "My Friends (${visibleFollowingUsers.size})",
modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Modern PullRefresh content - takes all available space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                viewModel.isLoading && sourceUsers.isEmpty() -> {
                    // Show 5 shimmer items
                    Column {
                        repeat(5) {
                            ListItemShimmer()
                        }
                    }
                }
                visibleFollowingUsers.isEmpty() -> EmptyFriendsState()
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = visibleFollowingUsers,
                            key = { it.uid },
                            contentType = { "friend" }
                        ) { friend ->
                            val isAlreadyFriend = currentUserFollowingIds.contains(friend.uid)
                            val hasSentRequest = currentUserSentRequestIds.contains(friend.uid)
                            FriendListItem(
                                friend = friend,
                                onProfilePhotoClick = { onProfilePhotoClick(friend) },
                                isPendingDelete = pendingDeleteFriendId == friend.uid,
                                isRemoving = viewModel.processingUserIds.contains(friend.uid),
                                isOwnFriendsList = isOwnFriendsList,
                                isAlreadyFriend = isAlreadyFriend,
                                hasSentRequest = hasSentRequest,
                                currentUserId = currentUserId,
                                onDeleteFriend = {
                                    if (pendingDeleteFriendId == friend.uid) {
                                        val friendId = friend.uid
                                        optimisticallyRemovedFriendIds.add(friendId)
                                        pendingDeleteFriendId = null
                                        viewModel.unfollowUser(currentUserId, friendId) { success ->
                                            if (success) {
                                                Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
                                            } else {
                                                optimisticallyRemovedFriendIds.remove(friendId)
                                                Toast.makeText(context, "Couldn’t remove. Please try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        pendingDeleteFriendId = friend.uid
                                    }
                                },
                                onAddFriend = {
                                    if (!isAlreadyFriend && !hasSentRequest && friend.uid != currentUserId) {
                                        onAddFriendClick(friend)
                                    }
                                },
                                onCancelRequest = {
                                    if (hasSentRequest && !isAlreadyFriend && friend.uid != currentUserId) {
                                        onCancelFriendRequestClick(friend)
                                    }
                                }
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

        if (showFindFriendsBar) {
            // Find Friends button - NOW AT THE BOTTOM
            Button(
                onClick = onFindFriendsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A4A73),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find Friends", fontSize = 12.sp, color = Color.White)
            }
        }

    }
}

@Composable
private fun FriendListItem(
    friend: UserProfile,
    onProfilePhotoClick: () -> Unit = {},
    isPendingDelete: Boolean = false,
    isRemoving: Boolean = false,
    isOwnFriendsList: Boolean = true,
    isAlreadyFriend: Boolean = false,
    hasSentRequest: Boolean = false,
    currentUserId: String,
    onDeleteFriend: () -> Unit = {},
    onAddFriend: () -> Unit = {},
    onCancelRequest: () -> Unit = {}
) {
    val liveFriendPhoto = rememberLiveUserPhotoUrl(friend.uid, friend.photoUrl)
    val tierRingColor = rememberLiveUserTierColor(friend.uid)

    // Use Row like ChatListItem - no card
Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo - 56dp like ChatListItem
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable { onProfilePhotoClick() }
        ) {
            if (liveFriendPhoto.isNotEmpty()) {
                AsyncImage(
                    model = liveFriendPhoto,
                    contentDescription = friend.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
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
        }

        Spacer(modifier = Modifier.width(16.dp))

        // User info - same structure as ChatListItem
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Name - 16sp Bold, maxLines=1
            Text(
                text = friend.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Followers count - 14sp like message preview
            Text(
                text = "${friend.followers.size} followers",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        if (isOwnFriendsList) {
            // Delete friend flow: normal -> solid red "CONFIRM DELETE" -> delete
            val deleteRed = Color(0xFFD32F2F)
            OutlinedButton(
                onClick = onDeleteFriend,
                enabled = !isRemoving,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.wrapContentWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isPendingDelete) deleteRed else Color.Transparent,
                    contentColor = if (isPendingDelete) Color.White else deleteRed,
                    disabledContainerColor = if (isPendingDelete) deleteRed else Color.Transparent,
                    disabledContentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, deleteRed)
            ) {
                when {
                    isRemoving -> Text("Removing...", fontSize = 12.sp, color = Color.White)
                    isPendingDelete -> Text("Confirm delete", fontSize = 12.sp, color = Color.White)
                    else -> Text("Delete friend", fontSize = 12.sp, color = deleteRed)
                }
            }
        } else {
            val isWaiting = hasSentRequest
            val buttonColor = Color(0xFF2A4A73)

            OutlinedButton(
                onClick = {
                    when {
                        isWaiting -> onCancelRequest()
                        else -> onAddFriend()
                    }
                },
                enabled = !isAlreadyFriend && !isRemoving,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.wrapContentWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isAlreadyFriend || isWaiting) buttonColor else Color.Transparent,
                    contentColor = buttonColor,
                    disabledContainerColor = if (isAlreadyFriend || isWaiting) buttonColor else Color.Transparent,
                    disabledContentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, buttonColor)
            ) {
                when {
                    isAlreadyFriend -> Text("Friends", fontSize = 12.sp, color = Color.White)
                    isWaiting -> Text("Waiting", fontSize = 12.sp, color = Color.White)
                    else -> Text("Add", fontSize = 12.sp)
                }
            }
        }
}
}

@Composable
private fun EmptyFriendsState() {
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
                text = "No friends yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap Find Friends to connect with people.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
