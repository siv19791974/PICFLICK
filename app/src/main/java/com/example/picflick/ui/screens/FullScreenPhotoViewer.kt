package com.example.picflick.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.example.picflick.ui.components.AnimatedReactionPicker
import android.content.Intent
import android.widget.Toast
import android.view.LayoutInflater
import android.widget.TextView
import com.example.picflick.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    
    // Helper function to show custom toast with PicFlick logo
    fun showPicFlickToast(message: String) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.custom_toast, null, false)
        
        val toastMessage = layout.findViewById<TextView>(R.id.toast_message)
        
        toastMessage.text = message
        
        val toast = Toast(context)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.show()
    }
    
    val repository = remember { FlickRepository.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    
    // Scroll state for comments section
    val scrollState = rememberScrollState()
    
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
    
    // Show comment panel state
    var showCommentPanel by remember { mutableStateOf(false) }
    
    // Heart animation state for double tap
    var showHeartAnimation by remember { mutableStateOf(false) }
    var heartAnimationKey by remember { mutableIntStateOf(0) }
    
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
                        repository.deleteFlick(currentFlick.id) { result ->
                            when (result) {
                                is com.example.picflick.data.Result.Success -> {
                                    showPicFlickToast("Photo Deleted")
                                    onDeleteClick()
                                    onDismiss()
                                }
                                is com.example.picflick.data.Result.Error -> {
                                    showPicFlickToast("Failed to delete photo")
                                }
                                else -> {
                                    // Loading or other states - do nothing
                                }
                            }
                        }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures(
                                onGesture = { _, pan, zoom, _ ->
                                    if (zoom != 1f || scale > 1f) {
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offset += pan
                                    }
                                }
                            )
                        }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp,
                        userScrollEnabled = scale <= 1.01f
                    ) { page ->
                        val pageFlick = if (allPhotos.isNotEmpty() && page in allPhotos.indices) {
                            allPhotos[page]
                        } else flick
                        
                        val pageOffset = (
                            (pagerState.currentPage - page) + 
                            pagerState.currentPageOffsetFraction
                        ).coerceIn(-1f, 1f)
                        
                        val alpha = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                        val scaleEffect = 1f - (pageOffset.absoluteValue * 0.1f).coerceIn(0f, 0.1f)
                        
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
                                            // Trigger heart animation
                                            heartAnimationKey++
                                            showHeartAnimation = true
                                            // Like/unlike
                                            onReaction(
                                                if (userReaction == ReactionType.LIKE) null 
                                                else ReactionType.LIKE
                                            )
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
                                        this.alpha = alpha
                                        scaleX = scaleEffect
                                        scaleY = scaleEffect
                                        if (pagerState.currentPage == page && scale > 1f) {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        }
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                
                // UI OVERLAY - Shows on tap (back button + right menu)
                AnimatedVisibility(
                    visible = uiVisible && !showCommentPanel,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        
                        // BACK BUTTON
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp, top = 16.dp)
                                .size(40.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.4f),
                                    CircleShape
                                )
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // PROFILE PICTURE - Top right, clickable
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = 16.dp)
                        ) {
                            AsyncImage(
                                model = currentFlick.userPhotoUrl,
                                contentDescription = "View ${currentFlick.userName}'s profile",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                    .clickable {
                                        onDismiss()
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        // RIGHT SIDE ACTION BAR - No background box
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // REACTION BUTTON
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = { showReactionPicker = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    if (userReaction != null) {
                                        Text(
                                            text = userReaction.toEmoji(),
                                            fontSize = 26.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.FavoriteBorder,
                                            contentDescription = "React",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                if (totalReactions > 0) {
                                    Text(
                                        text = "$totalReactions",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            // COMMENT BUTTON
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = { showCommentPanel = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MailOutline,
                                        contentDescription = "Comments",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                if (currentFlick.commentCount > 0) {
                                    Text(
                                        text = "${currentFlick.commentCount}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            // SHARE BUTTON
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Check out this photo: ${currentFlick.imageUrl}")
                                        setPackage("com.whatsapp")
                                    }
                                    try {
                                        context.startActivity(shareIntent)
                                    } catch (_: Exception) {
                                        val genericIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Check out this photo: ${currentFlick.imageUrl}")
                                        }
                                        context.startActivity(Intent.createChooser(genericIntent, "Share via"))
                                    }
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            
                            // DELETE BUTTON
                            if (canDelete) {
                                IconButton(
                                    onClick = { showDeleteConfirmation = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // SLIDE-UP COMMENT PANEL
                AnimatedVisibility(
                    visible = showCommentPanel,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { showCommentPanel = false }
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.7f)
                                .background(
                                    Color.Black.copy(alpha = 0.95f),
                                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.3f),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Comments (${currentFlick.commentCount})",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showCommentPanel = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White
                                    )
                                }
                            }
                            
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(scrollState)
                            ) {
                                if (isLoadingComments) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else if (comments.isEmpty()) {
                                    Text(
                                        text = "No comments yet. Be the first!",
                                        color = Color.Gray,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    comments.forEach { comment ->
                                        CompactCommentItem(comment = comment)
                                    }
                                }
                            }
                            
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
                                                repository.addComment(
                                                    currentFlick.id, 
                                                    currentUser.uid, 
                                                    currentUser.displayName, 
                                                    newCommentText.trim()
                                                )
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
                
                // BOTTOM LEFT USER INFO - No background box, editable description for owner
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)
                ) {
                    // Username
                    Text(
                        text = currentFlick.userName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Description - clickable to edit if owner
                    val descriptionText = currentDescription.ifEmpty { "Add a caption..." }
                    val descriptionColor = if (currentDescription.isEmpty()) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.9f)
                    
                    Text(
                        text = descriptionText,
                        color = descriptionColor,
                        fontSize = 14.sp,
                        maxLines = 2,
                        modifier = if (canDelete) {
                            Modifier.clickable { showEditCaption = true }
                        } else Modifier
                    )
                    
                    // Timestamp and reactions row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = formatTimestamp(currentFlick.timestamp),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                        
                        if (totalReactions > 0) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                            
                            Text(
                                text = reactionCounts.maxByOrNull { it.value }?.key?.toEmoji() ?: "❤️",
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$totalReactions",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // DOUBLE TAP HEART ANIMATION
                if (showHeartAnimation) {
                    DoubleTapHeartAnimation(
                        key = heartAnimationKey,
                        onAnimationEnd = { showHeartAnimation = false }
                    )
                }
                
                // REACTION PICKER POPUP
                if (showReactionPicker) {
                    AnimatedReactionPicker(
                        onDismiss = { showReactionPicker = false },
                        onReactionSelected = { reaction ->
                            onReaction(reaction)
                            showReactionPicker = false
                        },
                        currentReaction = userReaction
                    )
                }
            }
        }
    }
}

/**
 * Large animated heart for double tap like (Instagram style)
 */
@Composable
private fun DoubleTapHeartAnimation(
    key: Int,
    onAnimationEnd: () -> Unit
) {
    // Reset animation when key changes
    var animationStarted by remember(key) { mutableStateOf(false) }
    
    // Scale animation: starts small, pops large, then fades
    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1.5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heart_scale"
    )
    
    // Alpha animation for fade out
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 1f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = 600,
            easing = FastOutSlowInEasing
        ),
        finishedListener = { onAnimationEnd() },
        label = "heart_alpha"
    )
    
    LaunchedEffect(key) {
        delay(50)
        animationStarted = true
    }
    
    // Full screen centered heart
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "❤️",
            fontSize = 120.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.coerceAtLeast(0f)
                scaleY = scale.coerceAtLeast(0f)
                this.alpha = if (animationStarted) alpha else 1f
            }
        )
    }
}

// Format timestamp
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        weeks < 4 -> "${weeks}w"
        else -> {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            format.format(date)
        }
    }
}

// Comment item
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