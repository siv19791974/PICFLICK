package com.picflick.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.picflick.app.data.Flick
import com.picflick.app.data.ReactionType
import com.picflick.app.data.UserProfile
import com.picflick.app.data.toEmoji
import com.picflick.app.ui.components.AnimatedReactionPicker
import com.picflick.app.ui.components.ErrorMessage
import com.picflick.app.ui.components.PhotoGridShimmer
import com.picflick.app.ui.theme.PicFlickBackground
import com.picflick.app.viewmodel.HomeViewModel
import java.io.File
import kotlinx.coroutines.delay

/**
 * Home screen with photo grid and bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    userProfile: UserProfile,
    viewModel: HomeViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    onUserProfileClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showUploadDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFlick by remember { mutableStateOf<Flick?>(null) }
    var selectedFlickIndex by remember { mutableIntStateOf(0) }
    var privacySetting by remember { mutableStateOf("friends") } // "friends" or "public"
    
    // Reaction picker state
    var showReactionPicker by remember { mutableStateOf(false) }
    var flickForReaction by remember { mutableStateOf<Flick?>(null) }
    
    // Flying reaction animation state
    var flyingReaction by remember { mutableStateOf<Pair<ReactionType, Int>?>(null) }

    // Load data
    LaunchedEffect(userProfile.uid) {
        viewModel.loadFlicks(userProfile.uid)
    }

    LaunchedEffect(userProfile.uid) {
        viewModel.checkDailyUploads(userProfile.uid)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            showUploadDialog = false
            viewModel.uploadFlick(
                userId = userProfile.uid,
                userDisplayName = userProfile.displayName,
                userPhotoUrl = userProfile.photoUrl,
                imageUri = tempCameraUri!!,
                context = context,
                privacy = privacySetting,
                onComplete = { success ->
                    if (success) {
                        viewModel.checkDailyUploads(userProfile.uid)
                    }
                }
            )
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            showUploadDialog = false
            viewModel.uploadFlick(
                userId = userProfile.uid,
                userDisplayName = userProfile.displayName,
                userPhotoUrl = userProfile.photoUrl,
                imageUri = it,
                context = context,
                privacy = privacySetting,
                onComplete = { success ->
                    if (success) {
                        viewModel.checkDailyUploads(userProfile.uid)
                    }
                }
            )
        }
    }

    // Column WITHOUT verticalScroll (because LazyVerticalGrid has its own scroll)
    // NO BANNER HERE - banner is now in MainActivity's Scaffold topBar!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modern PullRefresh content
        val pullRefreshState = rememberPullRefreshState(
            refreshing = viewModel.isLoading,
            onRefresh = { viewModel.loadFlicks(userProfile.uid) }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            when {
                viewModel.isLoading && viewModel.flicks.isEmpty() -> PhotoGridShimmer()
                viewModel.errorMessage != null -> ErrorMessage(
                    message = viewModel.errorMessage ?: "Unknown error",
                    onRetry = { viewModel.loadFlicks(userProfile.uid) }
                )
                viewModel.flicks.isEmpty() -> EmptyState()
                else -> FlickGrid(
                    flicks = viewModel.flicks,
                    userProfile = userProfile,
                    onLikeClick = { flick -> viewModel.toggleLike(flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl) },
                    onPhotoClick = { flick -> 
                        selectedFlick = flick
                        selectedFlickIndex = viewModel.flicks.indexOf(flick)
                    },
                    onLongPress = { flick ->
                        flickForReaction = flick
                        showReactionPicker = true
                    }
                )
            }

            // Modern PullRefreshIndicator
            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )

            // Upload mini FABs overlay
            if (showUploadDialog) {
                UploadOverlay(
                    onDismiss = { showUploadDialog = false },
                    onCameraClick = {
                        val photoFile = File(
                            context.cacheDir,
                            "photo_${System.currentTimeMillis()}.jpg"
                        )
                        tempCameraUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraLauncher.launch(tempCameraUri!!)
                        showUploadDialog = false
                    },
                    onGalleryClick = {
                        galleryLauncher.launch("image/*")
                        showUploadDialog = false
                    },
                    privacySetting = privacySetting,
                    onPrivacyChange = { privacySetting = it }
                )
            }
        }
    }

    // Full-screen photo viewer with comments
    selectedFlick?.let { flick ->
        FullScreenPhotoViewer(
            flick = flick,
            currentUser = userProfile,
            onDismiss = { selectedFlick = null },
            onReaction = { reactionType ->
                // Handle reaction via ViewModel
                viewModel.toggleReaction(flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, reactionType)
                // Update local copy for UI
                selectedFlick = if (reactionType != null) {
                    val newReactions = flick.reactions.toMutableMap().apply {
                        put(userProfile.uid, reactionType.name)
                    }
                    flick.copy(reactions = newReactions)
                } else {
                    val newReactions = flick.reactions.toMutableMap().apply {
                        remove(userProfile.uid)
                    }
                    flick.copy(reactions = newReactions)
                }
            },
            onShareClick = {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Check out this photo on PicFlick: ${flick.imageUrl}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
            },
            canDelete = flick.userId == userProfile.uid,
            onDeleteClick = {
                // Refresh the list after deletion
                viewModel.loadFlicks(userProfile.uid)
            },
            allPhotos = viewModel.flicks,
            currentIndex = selectedFlickIndex,
            onNavigateToPhoto = { index ->
                selectedFlickIndex = index
                selectedFlick = viewModel.flicks.getOrNull(index)
            },
            onUserProfileClick = { userId ->
                onUserProfileClick(userId)
            }
        )
    }

    // Animated Reaction Picker Dialog for long press
    if (showReactionPicker && flickForReaction != null) {
        AnimatedReactionPicker(
            onDismiss = {
                showReactionPicker = false
                flickForReaction = null
            },
            onReactionSelected = { reactionType ->
                // Trigger flying animation
                val index = viewModel.flicks.indexOfFirst { it.id == flickForReaction?.id }
                if (index != -1) {
                    flyingReaction = reactionType to index
                }
                // Handle reaction via ViewModel
                viewModel.toggleReaction(
                    flickForReaction!!,
                    userProfile.uid,
                    userProfile.displayName,
                    userProfile.photoUrl,
                    reactionType
                )
                showReactionPicker = false
                flickForReaction = null
            },
            currentReaction = flickForReaction?.getUserReaction(userProfile.uid)
        )
    }

    // Flying reaction animation overlay
    flyingReaction?.let { (reaction, targetIndex) ->
        FlyingReactionAnimation(
            reaction = reaction,
            targetIndex = targetIndex,
            onAnimationEnd = { flyingReaction = null }
        )
    }
}

@Composable
private fun UploadOverlay(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    privacySetting: String = "friends",
    onPrivacyChange: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Upload Photo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Camera option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onCameraClick() }
                ) {
                    FloatingActionButton(
                        onClick = onCameraClick,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        text = "Camera",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                
                // Gallery option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onGalleryClick() }
                ) {
                    FloatingActionButton(
                        onClick = onGalleryClick,
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Gallery",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                    Text(
                        text = "Gallery",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Privacy Toggle
            Text(
                text = "Who can see this?",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Friends only option
                FilterChip(
                    selected = privacySetting == "friends",
                    onClick = { onPrivacyChange("friends") },
                    label = { Text("Friends Only") },
                    leadingIcon = if (privacySetting == "friends") {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
                
                // Public option
                FilterChip(
                    selected = privacySetting == "public",
                    onClick = { onPrivacyChange("public") },
                    label = { Text("Public") },
                    leadingIcon = if (privacySetting == "public") {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (privacySetting == "friends") 
                    "Only your friends will see this photo" 
                else 
                    "Everyone can see this photo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FlickGrid(
    flicks: List<Flick>,
    userProfile: UserProfile,
    onLikeClick: (Flick) -> Unit,
    onPhotoClick: (Flick) -> Unit,
    onLongPress: (Flick) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // Calculate height for 4 rows with tiny gap at bottom for light blue BG
        val rowHeight = this.maxHeight / 4.1f
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 1.dp,
                end = 1.dp,
                top = 4.dp,  // Slight top gap to match bottom
                bottom = 0.dp
            ),
            userScrollEnabled = true // Enable scrolling for pull-to-refresh
        ) {
            // Show all items (scrollable)
            items(flicks, key = { it.id }) { flick ->
                FlickCard(
                    flick = flick,
                    userId = userProfile.uid,
                    onLikeClick = { onLikeClick(flick) },
                    onPhotoClick = { onPhotoClick(flick) },
                    onLongPress = { onLongPress(flick) },
                    rowHeight = rowHeight
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlickCard(
    flick: Flick,
    userId: String,
    onLikeClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onLongPress: () -> Unit,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    val userReaction = flick.getUserReaction(userId)
    
    Card(
        modifier = Modifier
            .padding(1.dp) // Smaller padding
            .height(rowHeight) // Fixed height for exact 4 rows
            .combinedClickable(
                onClick = { onPhotoClick() },
                onLongClick = { onLongPress() }
            ),
        shape = RectangleShape, // NO rounded corners
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // No elevation
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // Transparent background
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Photo
            AsyncImage(
                model = flick.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(), // Fill the exact height
                contentScale = ContentScale.Crop
            )
            
            // Tiny reaction overlay (top right) - shows if user has reacted
            userReaction?.let { reaction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = reaction.toEmoji(),
                        fontSize = 10.sp
                    )
                }
            }
            
            // Username overlay at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        color = Color.Black.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = flick.userName,
                    color = Color.White,
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No photos yet. Upload one!",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Flying reaction animation - animates emoji from center to target photo
 */
@Composable
private fun FlyingReactionAnimation(
    reaction: ReactionType,
    targetIndex: Int,
    onAnimationEnd: () -> Unit
) {
    val emoji = reaction.toEmoji()
    
    // Calculate target position (3-column grid)
    val column = targetIndex % 3
    val row = targetIndex / 3
    
    // Screen-relative positions (estimates for 3-column grid)
    val targetX = when (column) {
        0 -> -0.25f
        1 -> 0f
        2 -> 0.25f
        else -> 0f
    }
    val targetY = when (row) {
        0 -> -0.15f
        1 -> 0.05f
        2 -> 0.25f
        3 -> 0.45f
        else -> 0f
    }
    
    var animationStarted by remember { mutableStateOf(false) }
    
    val offsetX by animateFloatAsState(
        targetValue = if (animationStarted) targetX else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "fly_x"
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (animationStarted) targetY else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        finishedListener = { onAnimationEnd() },
        label = "fly_y"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 0.5f else 2f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "fly_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "fly_alpha"
    )
    
    LaunchedEffect(Unit) {
        delay(50)
        animationStarted = true
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 48.sp,
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX * size.width
                    translationY = offsetY * size.height
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        )
    }
}
