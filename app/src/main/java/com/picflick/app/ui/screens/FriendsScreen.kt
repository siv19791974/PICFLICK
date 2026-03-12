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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.R
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.ListItemShimmer
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.ui.theme.ThemeManager
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
    onProfilePhotoClick: (UserProfile) -> Unit = {}
) {

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
                
                IconButton(onClick = onFindFriendsClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Find Friends",
                        tint = Color.White
                    )
                }
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
                viewModel.isLoading && viewModel.followingUsers.isEmpty() -> {
                    // Show 5 shimmer items
                    Column {
                        repeat(5) {
                            ListItemShimmer()
                        }
                    }
                }
                viewModel.followingUsers.isEmpty() -> EmptyFriendsState()
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(viewModel.followingUsers) { friend ->
                            FriendListItem(
                                friend = friend,
                                onProfilePhotoClick = { onProfilePhotoClick(friend) },
                                onDeleteFriend = {
                                    viewModel.unfollowUser(userProfile.uid, friend.uid)
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

        // Find Friends button - NOW AT THE BOTTOM
        Button(
            onClick = onFindFriendsClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Find Friends")
        }
    }
}

@Composable
private fun FriendListItem(
    friend: UserProfile,
    onProfilePhotoClick: () -> Unit = {},
    onDeleteFriend: () -> Unit = {}
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF2A4A73) else Color(0xFFB8D4F0) // Mid blue - darker than background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo - clickable
            if (friend.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = friend.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable { onProfilePhotoClick() },
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFE0E0E0))
                        .clickable { onProfilePhotoClick() },
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

            // User info - takes available space
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkMode) Color.White else Color.Black
                )
                Text(
                    text = "${friend.followers.size} followers",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkMode) Color.Gray else Color.DarkGray
                )
            }
            
            // Delete Friend button - small, on the right
            OutlinedButton(
                onClick = onDeleteFriend,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.wrapContentWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF4444) // Red
                )
            ) {
                Text("Delete", fontSize = 12.sp)
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
                text = "No friends yet. Find some!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
