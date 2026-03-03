package com.example.picflick.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.picflick.R
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.FullScreenLoading
import com.example.picflick.ui.components.TopBarWithBackButton
import com.example.picflick.viewmodel.FriendsViewModel

/**
 * Screen for finding and following new users
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindFriendsScreen(
    viewModel: FriendsViewModel,
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                title = "Find Friends",
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { query ->
                    viewModel.searchUsers(query, userProfile.uid)
                },
                label = { Text("Search users...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search results
            if (viewModel.isLoading) {
                FullScreenLoading()
            } else if (viewModel.searchResults.isEmpty() && viewModel.searchQuery.isNotBlank()) {
                EmptySearchResults()
            } else {
                LazyColumn {
                    items(viewModel.searchResults) { user ->
                        UserSearchResultItem(
                            user = user,
                            isFollowing = userProfile.following.contains(user.uid),
                            onFollowClick = {
                                if (userProfile.following.contains(user.uid)) {
                                    viewModel.unfollowUser(userProfile.uid, user.uid)
                                } else {
                                    viewModel.followUser(userProfile.uid, user, userProfile)
                                }
                            }
                        )
                    }
                }
            }

            // Show error if any
            viewModel.errorMessage?.let { error ->
                LaunchedEffect(error) {
                    // Could show snackbar here
                    viewModel.clearError()
                }
            }
        }
    }
}

@Composable
private fun UserSearchResultItem(
    user: UserProfile,
    isFollowing: Boolean,
    onFollowClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                error = painterResource(id = android.R.drawable.ic_menu_myplaces)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.Bold)
                Text(
                    "${user.followers.size} followers",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Button(onClick = onFollowClick) {
                Text(if (isFollowing) "Following" else "Follow")
            }
        }
    }
}

@Composable
private fun EmptySearchResults() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No users found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
