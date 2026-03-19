package com.picflick.app.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.picflick.app.util.withCacheBust

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
    BackHandler(onBack = onBack)

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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
            Spacer(modifier = Modifier.height(8.dp))

            // Profile photo
            val avatarSize = if (isLandscape) 116.dp else 150.dp
            val sidePadding = if (isLandscape) 20.dp else 32.dp
            Box(
                modifier = Modifier
                    .size(avatarSize)
.clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .border(4.dp, tierRingColor, CircleShape)
                    .clickable { onProfilePhotoClick() },
                contentAlignment = Alignment.Center
            ) {
                if (userProfile.photoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = withCacheBust(userProfile.photoUrl, userProfile.uid),
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

            Spacer(modifier = Modifier.height(8.dp))

            // Bio
            if (userProfile.bio.isNotEmpty()) {
                Text(
                    text = userProfile.bio,
                    fontSize = 14.sp,
                    color = secondaryTextColor,
                    modifier = Modifier                        .padding(horizontal = sidePadding)
)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons above photos
            if (isFriend) {
                Button(
                    onClick = onMessageClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePadding)
) {
                    Text("Send Message")
                }

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        text = "${photos.size} photos",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                            items(
                                items = photos,
                                key = { it.id },
                                contentType = { "photo" }
                            ) { photo ->
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
            } else if (hasReceivedRequest) {
                // Accept friend request button
                Text(
                    text = "${userProfile.displayName} wants to be friends",
                    fontSize = 16.sp,
                    color = primaryTextColor,
                    modifier = Modifier                        .padding(horizontal = sidePadding)
)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAcceptRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePadding)
) {
                    Text("Accept Friend Request")
                }
            } else if (hasSentRequest) {
                // Request pending
                Text(
                    text = "Friend request sent",
                    fontSize = 16.sp,
                    color = secondaryTextColor,
                    modifier = Modifier                        .padding(horizontal = sidePadding)
)
            } else {
                // Add friend button
                Button(
                    onClick = onAddFriend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePadding)
) {
                    Text("Add Friend")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Block user button (same boxed style)
            OutlinedButton(
                onClick = onBlockUser,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Red.copy(alpha = 0.85f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
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
    val reactionCounts = flick.getReactionCounts()
    val topReaction = reactionCounts.maxByOrNull { it.value }
    val topReactionCount = topReaction?.value ?: 0
    val topReactionEmoji = topReaction?.key?.toEmoji() ?: "❤️"

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
                model = withCacheBust(flick.imageUrl, flick.timestamp),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Fixed mini info banner (same style as Home/Profile)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(color = Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = flick.userName,
                        color = Color.White,
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier.width(36.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (topReactionCount > 0) {
                            Text(
                                text = "$topReactionEmoji $topReactionCount",
                                fontSize = 10.sp,
                                color = Color.White
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
