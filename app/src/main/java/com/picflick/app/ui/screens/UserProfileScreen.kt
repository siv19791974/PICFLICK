package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.RectangleShape
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
import com.picflick.app.data.ReactionType
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.AnimatedReactionPicker
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.data.getColor

/**
 * Screen for viewing another user's profile
 * Shows full profile if friends, limited profile if not friends
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
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
    onRefresh: () -> Unit = {},
    onReaction: (Flick, ReactionType?) -> Unit = { _, _ -> } // NEW: Reaction callback
) {
    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = onRefresh
    )

    // Reaction picker state for long-press on photos
    var showReactionPicker by remember { mutableStateOf(false) }
    var flickForReaction by remember { mutableStateOf<Flick?>(null) }
    
    // Dark mode state
    val isDarkMode = com.picflick.app.ui.theme.ThemeManager.isDarkMode.value
    val primaryTextColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color.Gray else Color.Black.copy(alpha = 0.7f)
    val tierRingColor = userProfile.subscriptionTier.getColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(isDarkModeBackground(isDarkMode))
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor
                    )
                }
            }

            // Profile photo
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .border(4.dp, tierRingColor, CircleShape)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Display name
            Text(
                text = userProfile.displayName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )

            // Username handle (use displayName as handle)
            Text(
                text = "@${userProfile.displayName.lowercase().replace(" ", "_")}",
                fontSize = 16.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(photos.size.toString(), "Photos", primaryTextColor, secondaryTextColor)
                StatItem(userProfile.followers.size.toString(), "Followers", primaryTextColor, secondaryTextColor)
                StatItem(userProfile.following.size.toString(), "Following", primaryTextColor, secondaryTextColor)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bio
            if (userProfile.bio.isNotEmpty()) {
                Text(
                    text = userProfile.bio,
                    fontSize = 14.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Photos Grid (only for friends)
            if (isFriend) {
                if (photos.isEmpty()) {
                    Text(
                        text = "No photos yet",
                        color = secondaryTextColor,
                        fontSize = 16.sp
                    )
                } else {
                    Text(
                        text = "Photos",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Match ProfileScreen standard sizing exactly
                    val rowCount = ((photos.size + 2) / 3).coerceAtLeast(4)
                    val homeLikeRowHeight = 148.dp
                    val gridHeight = (rowCount * homeLikeRowHeight.value).dp + 8.dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 1.dp,
                                end = 1.dp,
                                top = 4.dp,
                                bottom = 0.dp
                            ),
                            userScrollEnabled = false
                        ) {
                            items(photos) { photo ->
                                ProfilePhotoCard(
                                    flick = photo,
                                    rowHeight = homeLikeRowHeight,
                                    onPhotoClick = {
                                        onPhotoClick(photo, photos.indexOf(photo))
                                    },
                                    onLongPress = {
                                        // Only allow reacting to OTHER people's photos
                                        if (photo.userId != currentUser.uid) {
                                            flickForReaction = photo
                                            showReactionPicker = true
                                        }
                                    }
                                )
                            }
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
                ) {
                    Text("Send Message")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Unfriend button
                OutlinedButton(
                    onClick = onUnfriend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Gray
                    )
                ) {
                    Text("Remove Friend")
                }
            } else if (hasReceivedRequest) {
                // Accept friend request button
                Text(
                    text = "${userProfile.displayName} wants to be friends",
                    fontSize = 16.sp,
                    color = primaryTextColor,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAcceptRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text("Accept Friend Request")
                }
            } else if (hasSentRequest) {
                // Request pending
                Text(
                    text = "Friend request sent",
                    fontSize = 16.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            } else {
                // Add friend button
                Button(
                    onClick = onAddFriend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text("Add Friend")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Block user button
            TextButton(
                onClick = onBlockUser,
                modifier = Modifier.padding(horizontal = 32.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Red.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Block User")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
        // End of Column (pull-refresh content)

        // PullRefreshIndicator
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Reaction Picker Dialog for long-press on photos
        if (showReactionPicker && flickForReaction != null) {
            AnimatedReactionPicker(
                onDismiss = {
                    showReactionPicker = false
                    flickForReaction = null
                },
                onReactionSelected = { reactionType ->
                    // Handle reaction
                    flickForReaction?.let { flick ->
                        onReaction(flick, reactionType)
                    }
                    showReactionPicker = false
                    flickForReaction = null
                }
            )
        }
    }
    // End of Box (pull-refresh container)
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    valueColor: Color,
    labelColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = labelColor
        )
    }
}

/**
 * Photo card for profile grids - matches Home Feed FlickCard with reactions
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfilePhotoCard(
    flick: Flick,
    rowHeight: androidx.compose.ui.unit.Dp,
    onPhotoClick: () -> Unit,
    onLongPress: () -> Unit
) {
    // Get reaction counts and sort by count (descending) to show top 5
    val reactionCounts = flick.getReactionCounts()
    val topReactions = reactionCounts.entries.sortedByDescending { it.value }.take(5)
    val totalReactions = flick.getTotalReactions()

    Card(
        modifier = Modifier
            .padding(1.dp)
            .height(rowHeight)
            .combinedClickable(
                onClick = { onPhotoClick() },
                onLongClick = { onLongPress() }
            ),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Photo
            AsyncImage(
                model = flick.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Info overlay at bottom (reactions) - BLACK BAR
            if (topReactions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Show up to 5 reactions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            topReactions.forEach { (reactionType, count) ->
                                Text(
                                    text = "${reactionType.toEmoji()} $count",
                                    fontSize = 9.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Right: Total count if more than 5
                        if (totalReactions > 5) {
                            Text(
                                text = "+$totalReactions",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convert ReactionType to emoji string
 */
private fun ReactionType.toEmoji(): String = when (this) {
    ReactionType.LIKE -> "❤️"
    ReactionType.LOVE -> "😍"
    ReactionType.LAUGH -> "😂"
    ReactionType.WOW -> "😮"
    ReactionType.FIRE -> "🔥"
}
