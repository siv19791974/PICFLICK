package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.R
import com.picflick.app.data.Flick
import com.picflick.app.data.UserProfile

/**
 * Screen for viewing another user's profile
 * Shows full profile if friends, limited profile if not friends
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UserProfileScreen(
    userProfile: UserProfile, // The user being viewed
    currentUser: UserProfile, // The logged-in user
    photos: List<Flick>,
    isFriend: Boolean,
    hasSentRequest: Boolean = false, // Current user sent friend request to this user
    hasReceivedRequest: Boolean = false, // This user sent friend request to current user
    isLoading: Boolean,
    onBack: () -> Unit,
    onPhotoClick: (Flick, Int) -> Unit = { _, _ -> },
    onProfilePhotoClick: () -> Unit = {},
    onAddFriend: () -> Unit = {},
    onAcceptRequest: () -> Unit = {}, // Accept friend request from this user
    onMessageClick: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    onUnfriend: () -> Unit = {}, // NEW: Delete friend/unfriend
    onRefresh: () -> Unit = {}
) {
    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = onRefresh
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with back button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = if (isFriend) "Friend Profile" else "Profile",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Photo
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .clickable { onProfilePhotoClick() },
                contentAlignment = Alignment.Center
            ) {
                if (userProfile.photoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = userProfile.photoUrl,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(id = android.R.drawable.ic_menu_myplaces),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.content_desc_person),
                        modifier = Modifier.size(80.dp),
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Name
            Text(
                text = userProfile.displayName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Bio (shown regardless of friendship status)
            if (userProfile.bio.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = userProfile.bio,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons based on friendship status - 4 STATES
            when {
                // STATE 1: Friends - Show stats, photos, and message button
                isFriend -> {
                    // Stats Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            value = photos.size.toString(),
                            label = "Photos"
                        )
                        StatItem(
                            value = userProfile.followers.size.toString(),
                            label = "Followers"
                        )
                        StatItem(
                            value = userProfile.following.size.toString(),
                            label = "Following"
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Photos Grid (only for friends)
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White)
                    } else if (photos.isEmpty()) {
                        Text(
                            text = "No photos yet",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    } else {
                        Text(
                            text = "Photos",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 600.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(photos) { photo ->
                                AsyncImage(
                                    model = photo.imageUrl,
                                    contentDescription = stringResource(R.string.content_desc_photo),
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable {
                                            onPhotoClick(photo, photos.indexOf(photo))
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message button for friends
                    Button(
                        onClick = onMessageClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Send Message")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Unfriend button for friends
                    OutlinedButton(
                        onClick = onUnfriend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Delete friend",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Friend")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Block User button for friends
                    OutlinedButton(
                        onClick = onBlockUser,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF4444)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Block user",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Block User")
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                // STATE 2: Received friend request - Show Accept button
                hasReceivedRequest -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.content_desc_person),
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Friend Request Received",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CAF50)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${userProfile.displayName} wants to be your friend",
                            fontSize = 14.sp,
                            color = Color.Gray.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Accept Request button (Green)
                        Button(
                            onClick = onAcceptRequest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accept Friend Request")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Block User button
                        OutlinedButton(
                            onClick = onBlockUser,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF4444)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Block user",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Block User")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                // STATE 3: Sent friend request - Show disabled Friend Requested button
                hasSentRequest -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.content_desc_person),
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Friend Requested",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Waiting for ${userProfile.displayName} to accept",
                            fontSize = 14.sp,
                            color = Color.Gray.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Disabled button showing "Friend Requested" state
                        OutlinedButton(
                            onClick = { /* Can't cancel yet */ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Gray
                            )
                        ) {
                            Text("Friend Requested")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Block User button
                        OutlinedButton(
                            onClick = onBlockUser,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF4444)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Block user",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Block User")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                // STATE 4: Not friends, no request - Show Add Friend button
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.content_desc_person),
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Not Friends Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add ${userProfile.displayName} as a friend to see their photos",
                            fontSize = 14.sp,
                            color = Color.Gray.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onAddFriend,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.content_desc_add_friend),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Friend")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Block User button for non-friends
                        OutlinedButton(
                            onClick = onBlockUser,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF4444)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Block user",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Block User")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            // End of friendship states when block
        }
        // End of Column (pull-refresh content)

        // PullRefreshIndicator
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    // End of Box (pull-refresh container)
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}
