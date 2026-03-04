package com.example.picflick.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.picflick.data.Comment
import com.example.picflick.data.Flick
import com.example.picflick.data.ReactionType
import com.example.picflick.data.UserProfile
import com.example.picflick.data.toEmoji
import com.example.picflick.repository.FlickRepository
import com.example.picflick.ui.components.ReactionPicker
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPhotoViewer(
    flick: Flick,
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onReaction: (ReactionType?) -> Unit = {},
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    canDelete: Boolean = false,
    onCaptionUpdated: (String) -> Unit = {},
    allPhotos: List<Flick> = emptyList(),
    currentIndex: Int = 0,
    onNavigateToPhoto: (Int) -> Unit = {}
) {
    val repository = remember { FlickRepository.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var newCommentText by remember { mutableStateOf("") }
    var isLoadingComments by remember { mutableStateOf(true) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditCaption by remember { mutableStateOf(false) }
    var editCaptionText by remember { mutableStateOf(flick.description) }
    var currentDescription by remember { mutableStateOf(flick.description) }
    
    // UI visibility toggle
    var uiVisible by remember { mutableStateOf(true) }
    
    // Scale for zoom
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Pager for horizontal swiping
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { allPhotos.size.coerceAtLeast(1) }
    )
    
    // Current flick based on pager
    val currentFlick = if (allPhotos.isNotEmpty() && pagerState.currentPage in allPhotos.indices) {
        allPhotos[pagerState.currentPage]
    } else flick
    
    // Get user's current reaction
    val userReaction = currentFlick.getUserReaction(currentUser.uid)
    val reactionCounts = currentFlick.getReactionCounts()
    val totalReactions = currentFlick.getTotalReactions()
    
    // Show reaction picker state
    var showReactionPicker by remember { mutableStateOf(false) }
    
    // Load comments when flick changes
    LaunchedEffect(currentFlick.id) {
        isLoadingComments = true
        repository.getComments(currentFlick.id) { result ->
            when (result) {
                is com.example.picflick.data.Result.Success -> {
                    comments = result.data
                    isLoadingComments = false
                }
                else -> isLoadingComments = false
            }
        }
    }
    
    // Handle page changes
    LaunchedEffect(pagerState.currentPage) {
        if (allPhotos.isNotEmpty()) {
            onNavigateToPhoto(pagerState.currentPage)
        }
    }
    
    // Reset zoom when photo changes
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }
    
    // Edit caption dialog
    if (showEditCaption && canDelete) {
        AlertDialog(
            onDismissRequest = { showEditCaption = false },
            title = { Text("Edit Caption") },
            text = {
                OutlinedTextField(
                    value = editCaptionText,
                    onValueChange = { editCaptionText = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.updateFlickDescription(currentFlick.id, editCaptionText)
                            currentDescription = editCaptionText
                            onCaptionUpdated(editCaptionText)
                            showEditCaption = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCaption = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to delete this photo? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // OUTER GESTURE HANDLER - Smart pinch vs swipe detection
                // Uses detectTransformGestures but allows swipe to pass through when not zooming
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures(
                                onGesture = { _, pan, zoom, _ ->
                                    // Only process if this is a real pinch (zoom != 1) or already zoomed
                                    if (zoom != 1f || scale > 1f) {
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offset += pan
                                    }
                                    // If zoom == 1 and scale == 1, gesture passes through to HorizontalPager
                                }
                            )
                        }
                ) {
                    // HORIZONTAL PAGER with dissolve transitions
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp,
                        userScrollEnabled = scale <= 1.01f  // Disable swipe when zoomed
                    ) { page ->
                        val pageFlick = if (allPhotos.isNotEmpty() && page in allPhotos.indices) {
                            allPhotos[page]
                        } else flick
                        
                        // Calculate page offset for dissolve effect (-1 to 1)
                        val pageOffset = (
                            (pagerState.currentPage - page) + 
                            pagerState.currentPageOffsetFraction
                        ).coerceIn(-1f, 1f)
                        
                        // Calculate alpha for dissolve (1.0 = fully visible, 0.0 = invisible)
                        val alpha = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                        
                        // Calculate scale for subtle zoom effect during transition
                        val scaleEffect = 1f - (pageOffset.absoluteValue * 0.1f).coerceIn(0f, 0.1f)
                        
                        // PHOTO BOX with tap detection and dissolve animation
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        if (pagerState.currentPage == page) uiVisible = !uiVisible
                                    },
                                    onDoubleClick = {
                                        if (pagerState.currentPage == page) {
                                            // Double tap to zoom
                                            scale = if (scale > 1.5f) 1f else 2.5f
                                            if (scale == 1f) offset = Offset.Zero
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = pageFlick.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // Apply dissolve alpha
                                        this.alpha = alpha
                                        // Apply subtle scale during transition
                                        scaleX = scaleEffect
                                        scaleY = scaleEffect
                                        // Apply zoom if this is the current page
                                        if (pagerState.currentPage == page && scale > 1f) {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        }
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                
                // REMOVED: Transparent overlay that was blocking swipes
                // clickable() on the Box above handles taps without blocking HorizontalPager
                
                // UI OVERLAY - Animated visibility
                AnimatedVisibility(
                    visible = uiVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        
                        // TOP BAR - Semi-transparent black
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Close button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // Photo counter
                            if (allPhotos.isNotEmpty()) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${allPhotos.size}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // Actions
                            Row {
                                if (canDelete) {
                                    IconButton(
                                        onClick = { showEditCaption = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = onShareClick,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                if (canDelete) {
                                    IconButton(
                                        onClick = { showDeleteConfirmation = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // BOTTOM INFO PANEL
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(16.dp)
                        ) {
                            // Caption
                            if (currentDescription.isNotEmpty()) {
                                Text(
                                    text = currentDescription,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            
                            // Action buttons with REACTIONS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // REACTION BUTTON with long-press picker
                                Box(contentAlignment = Alignment.Center) {
                                    // Main reaction button - tap to quick-like, long-press for picker
                                    IconButton(
                                        onClick = { /* handled by pointerInput */ },
                                        modifier = Modifier.pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    onReaction(
                                                        if (userReaction == ReactionType.LIKE) null 
                                                        else ReactionType.LIKE
                                                    )
                                                },
                                                onLongPress = {
                                                    showReactionPicker = true
                                                }
                                            )
                                        }
                                    ) {
                                        if (userReaction != null) {
                                            // Show user's current reaction emoji
                                            Text(
                                                text = userReaction.toEmoji(),
                                                fontSize = 24.sp
                                            )
                                        } else {
                                            // Default like outline
                                            Icon(
                                                imageVector = Icons.Default.FavoriteBorder,
                                                contentDescription = "React",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Show reaction counts
                                if (totalReactions > 0) {
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp, end = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Top 3 reactions (convert to list first)
                                        reactionCounts.toList().take(3).forEach { (type, count) ->
                                            Text(
                                                text = "${type.toEmoji()} $count ",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                
                                // Comments
                                Icon(
                                    imageVector = Icons.Outlined.MailOutline,
                                    contentDescription = "Comments",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "${currentFlick.commentCount}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp, end = 16.dp)
                                )
                            }
                            
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            
                            // Comments section
                            Text(
                                text = "Comments",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Comment list
                            if (isLoadingComments) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else if (comments.isEmpty()) {
                                Text(
                                    text = "No comments yet. Be the first!",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                comments.take(3).forEach { comment ->
                                    CompactCommentItem(comment = comment)
                                }
                            }
                            
                            // Add comment
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .background(
                                        Color.DarkGray.copy(alpha = 0.5f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = currentUser.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                TextField(
                                    value = newCommentText,
                                    onValueChange = { newCommentText = it },
                                    placeholder = { Text("Add a comment...", color = Color.Gray, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                
                                IconButton(
                                    onClick = {
                                        if (newCommentText.isNotBlank()) {
                                            coroutineScope.launch {
                                                val comment = Comment(
                                                    flickId = currentFlick.id,
                                                    userId = currentUser.uid,
                                                    userName = currentUser.displayName,
                                                    userPhotoUrl = currentUser.photoUrl,
                                                    text = newCommentText.trim()
                                                )
                                                repository.addComment(comment, comment.userName, comment.userPhotoUrl)
                                                comments = comments + comment
                                                newCommentText = ""
                                            }
                                        }
                                    },
                                    enabled = newCommentText.isNotBlank(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (newCommentText.isNotBlank()) 
                                            MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // SWIPE UP TO DISMISS hint (when UI hidden)
                AnimatedVisibility(
                    visible = !uiVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 60.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Tap to show controls",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
                
                // REACTION PICKER POPUP - positioned at bottom
                AnimatedVisibility(
                    visible = showReactionPicker,
                    modifier = Modifier.align(Alignment.BottomStart),
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                    exit = fadeOut() + scaleOut()
                ) {
                    ReactionPicker(
                        currentReaction = userReaction,
                        onReactionSelected = { reaction ->
                            onReaction(reaction)
                            showReactionPicker = false
                        },
                        onDismiss = { showReactionPicker = false },
                        modifier = Modifier.padding(bottom = 160.dp, start = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCommentItem(comment: Comment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = comment.userPhotoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Row {
            Text(
                text = comment.userName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = comment.text,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
        }
    }
}