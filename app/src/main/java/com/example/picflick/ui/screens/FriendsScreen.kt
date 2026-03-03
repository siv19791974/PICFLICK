package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.components.TopBarWithBackButton
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground

/**
 * Screen showing list of friends the user is following
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                title = "Friends",
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PicFlickBackground)
                .padding(padding)
        ) {
            // Logo banner at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(top = 36.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                LogoImage()
            }
            
            if (userProfile.following.isEmpty()) {
                EmptyFriendsState()
            } else {
                FriendsList(
                    followingCount = userProfile.following.size
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

@Composable
private fun FriendsList(followingCount: Int) {
    Text(
        text = "Following $followingCount users",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.titleMedium
    )
    // TODO: Load and display actual user details for following list
}
