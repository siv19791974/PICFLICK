package com.example.picflick.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.picflick.R
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.ListItemShimmer
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.viewmodel.FriendsViewModel

/**
 * Screen showing list of friends the user is following
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FriendsScreen(
    userProfile: UserProfile,
    viewModel: FriendsViewModel,
    onBack: () -> Unit,
    onFindFriendsClick: () -> Unit = {}
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground)
    ) {
        // NO BANNER - banner is now in MainActivity's Scaffold topBar!

        // Find Friends button
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

        // Modern PullRefresh content
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    LazyColumn {
                        items(viewModel.followingUsers) { friend ->
                            FriendListItem(friend = friend)
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
private fun FriendListItem(friend: UserProfile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo
            if (friend.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = friend.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${friend.followers.size} followers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
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