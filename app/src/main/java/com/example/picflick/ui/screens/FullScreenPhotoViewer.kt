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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.view.LayoutInflater
import android.widget.TextView
import com.example.picflick.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.URL
import java.net.HttpURLConnection

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
    onNavigateToPhoto: (Int) -> Unit = {},
    onUserProfileClick: (String) -> Unit = {},
    onShareToFriend: (String, String) -> Unit = { _, _ -> },
    onNavigateToFindFriends: () -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    
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
    
    // Simple 2D pager state
    var currentPageIndex by remember { mutableIntStateOf(currentIndex) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var isDraggingVertically by remember { mutableStateOf(false) }
    
    // Zoom state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }
    
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
    
    // Show share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    
    // Tagged friends display state
    var taggedFriendsProfiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingTaggedFriends by remember { mutableStateOf(false) }
    
    // Tag friends dialog state
    var showTagFriendsDialog by remember { mutableStateOf(false) }
    var taggedFriends by remember { mutableStateOf<List<String>>(currentFlick.taggedFriends) }
    var isLoadingTagFriends by remember { mutableStateOf(false) }
    
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
    
    // Load tagged friends profiles when flick changes
    LaunchedEffect(currentFlick.id) {
        if (currentFlick.taggedFriends.isNotEmpty()) {
            isLoadingTaggedFriends = true
            val loadedProfiles = mutableListOf<UserProfile>()
            var loadedCount = 0
            
            currentFlick.taggedFriends.forEach { userId ->
                repository.getUserProfile(userId) { result ->
                    when (result) {
                        is com.example.picflick.data.Result.Success -> {
                            loadedProfiles.add(result.data)
                        }
                        else -> { /* Skip failed loads */ }
                    }
                    loadedCount++
                    if (loadedCount >= currentFlick.taggedFriends.size) {
                        taggedFriendsProfiles = loadedProfiles.sortedBy { it.displayName }
                        isLoadingTaggedFriends = false
                    }
                }
            }
        } else {
            taggedFriendsProfiles = emptyList()
            isLoadingTaggedFriends = false
        }
    }
    
    // Handle page changes
    LaunchedEffect(currentPageIndex) {
        if (allPhotos.isNotEmpty()) {
            onNavigateToPhoto(currentPageIndex)
        }
    }
    
    // Reset drag when photo changes
    LaunchedEffect(currentPageIndex) {
        dragX = 0f
        dragY = 0f
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
                
                // SIMPLE 2D PAGER - No pinch, no fades, just direct positioning
                var dragX by remember { mutableFloatStateOf(0f) }
                var dragY by remember { mutableFloatStateOf(0f) }
                var isDraggingVertically by remember { mutableStateOf(false) }
                var currentPageIndex by remember { mutableIntStateOf(currentIndex) }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // UNIFIED GESTURE HANDLER - Pinch + Swipe coexist!
                            awaitPointerEventScope {
                                var isPinching = false
                                var isDragging = false
                                var dragStartX = 0f
                                var dragStartY = 0f
                                
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    
                                    when {
                                        // PINCH: Multiple fingers down
                                        changes.size > 1 -> {
                                            isPinching = true
                                            isDragging = false
                                            
                                            // Calculate zoom from pointer distance
                                            if (changes.size == 2) {
                                                val p1 = changes[0].position
                                                val p2 = changes[1].position
                                                val distance = kotlin.math.hypot(p2.x - p1.x, p2.y - p1.y)
                                                
                                                // Update zoom scale (simplified)
                                                if (distance > 0) {
                                                    zoomScale = (zoomScale * 1.01f).coerceIn(1f, 5f)
                                                }
                                            }
                                            changes.forEach { it.consume() }
                                        }
                                        
                                        // DRAG: Single finger, not zoomed or panning when zoomed
                                        changes.size == 1 -> {
                                            val change = changes[0]
                                            
                                            if (!isPinching) {
                                                when {
                                                    // Starting drag
                                                    !isDragging && change.pressed -> {
                                                        isDragging = true
                                                        dragStartX = change.position.x
                                                        dragStartY = change.position.y
                                                    }
                                                    
                                                    // Ending drag
                                                    isDragging && !change.pressed -> {
                                                        isDragging = false
                                                        
                                                        // If not zoomed, navigate
                                                        if (zoomScale <= 1.01f) {
                                                            when {
                                                                // Vertical swipe
                                                                isDraggingVertically && kotlin.math.abs(dragY) > 100f -> {
                                                                    if (dragY < 0 && currentPageIndex < allPhotos.size - 1) {
                                                                        currentPageIndex++
                                                                    } else if (dragY > 0 && currentPageIndex > 0) {
                                                                        currentPageIndex--
                                                                    }
                                                                }
                                                                // Horizontal swipe
                                                                !isDraggingVertically && kotlin.math.abs(dragX) > 100f -> {
                                                                    if (dragX < 0 && currentPageIndex < allPhotos.size - 1) {
                                                                        currentPageIndex++
                                                                    } else if (dragX > 0 && currentPageIndex > 0) {
                                                                        currentPageIndex--
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        dragX = 0f
                                                        dragY = 0f
                                                        isDraggingVertically = false
                                                    }
                                                    
                                                    // During drag
                                                    isDragging && change.pressed -> {
                                                        val dx = change.position.x - dragStartX
                                                        val dy = change.position.y - dragStartY
                                                        
                                                        // Detect direction on first real movement
                                                        if (kotlin.math.abs(dx) > 10f || kotlin.math.abs(dy) > 10f) {
                                                            if (kotlin.math.abs(dragX) < 10f && kotlin.math.abs(dragY) < 10f) {
                                                                isDraggingVertically = kotlin.math.abs(dy) > kotlin.math.abs(dx)
                                                            }
                                                        }
                                                        
                                                        if (zoomScale > 1.01f) {
                                                            // When zoomed, pan the image
                                                            zoomOffsetX += change.position.x - dragStartX
                                                            zoomOffsetY += change.position.y - dragStartY
                                                            dragStartX = change.position.x
                                                            dragStartY = change.position.y
                                                        } else {
                                                            // When not zoomed, drag for navigation
                                                            if (isDraggingVertically) {
                                                                dragY = dy
                                                            } else {
                                                                dragX = dx
                                                            }
                                                        }
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Reset pinch when fingers lift
                                        changes.isEmpty() || changes.none { it.pressed } -> {
                                            isPinching = false
                                            isDragging = false
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // Show all pages positioned by index
                    allPhotos.forEachIndexed { index, photo ->
                        val isCurrent = index == currentPageIndex
                        val isNext = index == currentPageIndex + 1
                        val isPrev = index == currentPageIndex - 1
                        
                        // Only render current, next, and prev
                        if (isCurrent || isNext || isPrev) {
                            // Base position (back to instant, no animation)
                            val baseX = (index - currentPageIndex) * screenWidthPx
                            
                            // Apply drag offsets
                            val finalX = baseX + if (isCurrent) dragX else 0f
                            val finalY = if (isCurrent) dragY else if (isNext && dragY < 0) screenHeightPx + dragY else if (isPrev && dragY > 0) -screenHeightPx + dragY else screenHeightPx * 2f
                            
                            // Calculate scale shrink and fade based on drag amount
                            val dragProgress = kotlin.math.abs(dragX) / screenWidthPx
                            val verticalProgress = kotlin.math.abs(dragY) / screenHeightPx
                            val maxProgress = kotlin.math.max(dragProgress, verticalProgress)
                            val swipeScale = 1f - (maxProgress * 0.25f).coerceIn(0f, 0.25f)
                            val swipeAlpha = 1f - (maxProgress * 0.5f).coerceIn(0f, 0.5f)  // Fade to 50%
                            
                            // Apply zoom only to current photo
                            val finalScale = if (isCurrent) swipeScale * zoomScale else swipeScale
                            val finalAlpha = if (isCurrent && zoomScale <= 1.01f) swipeAlpha else 1f  // Only fade when not zoomed
                            val finalOffsetX = if (isCurrent) finalX + zoomOffsetX else finalX
                            val finalOffsetY = if (isCurrent) finalY + zoomOffsetY else finalY
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset { IntOffset(finalOffsetX.toInt(), finalOffsetY.toInt()) }
                                    .graphicsLayer {
                                        scaleX = finalScale
                                        scaleY = finalScale
                                        alpha = finalAlpha
                                    }
                                    .combinedClickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { if (isCurrent && zoomScale <= 1.01f) uiVisible = !uiVisible }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = photo.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
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
                                .size(44.dp)
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
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        // RIGHT SIDE ACTION BAR - With subtle backgrounds
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            if (canDelete) {
                                // MY PHOTOS MODE - 4 icons: Tag Friends, Share, Edit, Delete
                                
                                // TAG FRIENDS BUTTON
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { 
                                            showTagFriendsDialog = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Tag Friends",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                // SHARE BUTTON - In-app share to friends
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { showShareDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Share to friends",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                // EDIT PHOTO/CAPTION BUTTON
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { showEditCaption = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                // DELETE BUTTON
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { showDeleteConfirmation = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                // OTHER USER'S PHOTOS MODE - Original icons
                                
                                // REACTION BUTTON - Shows filled heart if liked
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                            .clickable { showReactionPicker = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (userReaction != null) {
                                            Text(
                                                text = userReaction.toEmoji(),
                                                fontSize = 26.sp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
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
                                
                                // COMMENT BUTTON - Chat bubble icon
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                            .clickable { showCommentPanel = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
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
                                
                                // SHARE BUTTON - In-app share to friends
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { showShareDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Share to friends",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                // SAVE/DOWNLOAD BUTTON - Actually downloads the image
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable {
                                            // Launch download in coroutine
                                            coroutineScope.launch {
                                                downloadImageToGallery(context, currentFlick.imageUrl, currentFlick.id)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Save to device",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
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
                    val keyboardController = LocalSoftwareKeyboardController.current
                    
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Backdrop
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { 
                                    showCommentPanel = false
                                    keyboardController?.hide()
                                }
                        )
                        
                        // Panel content - fixed max height so it doesn't overflow
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 500.dp) // Max height constraint
                                .imePadding() // Handle keyboard insets here
                                .background(
                                    Color.Black.copy(alpha = 0.98f),
                                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            // Handle bar
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
                            
                            // Header
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
                            
                            // Comments list - takes available space but doesn't push input off screen
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f) // Takes remaining space
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
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
                            }
                            
                            // Comment input bar - fixed at bottom, not scrollable
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 8.dp) // Bottom padding
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
                                                    currentUser.photoUrl,
                                                    newCommentText.trim()
                                                )
                                                comments = comments + comment
                                                newCommentText = ""
                                                keyboardController?.hide() // Hide keyboard after sending
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
                
                // SHARE DIALOG - Share to friends
                AnimatedVisibility(
                    visible = showShareDialog,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ShareFriendsDialog(
                        flick = currentFlick,
                        currentUser = currentUser,
                        onDismiss = { showShareDialog = false },
                        onShareToFriend = onShareToFriend,
                        onNavigateToFindFriends = onNavigateToFindFriends,
                        showPicFlickToast = { message -> showPicFlickToast(message) }
                    )
                }
                
                // TAG FRIENDS DIALOG - Tag existing friends or go to Find Friends
                AnimatedVisibility(
                    visible = showTagFriendsDialog,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TagFriendsDialog(
                        flick = currentFlick,
                        currentUser = currentUser,
                        taggedFriends = taggedFriends,
                        onDismiss = { showTagFriendsDialog = false },
                        onTagFriendsChanged = { newTaggedFriends ->
                            taggedFriends = newTaggedFriends
                        },
                        onSaveTags = { finalTaggedFriends ->
                            coroutineScope.launch {
                                isLoadingTagFriends = true
                                val result = repository.updateTaggedFriends(
                                    flickId = currentFlick.id,
                                    taggedFriends = finalTaggedFriends,
                                    photoOwnerId = currentUser.uid,
                                    photoOwnerName = currentUser.displayName,
                                    photoOwnerPhotoUrl = currentUser.photoUrl
                                )
                                isLoadingTagFriends = false
                                when (result) {
                                    is com.example.picflick.data.Result.Success -> {
                                        showPicFlickToast("Tags updated!")
                                        showTagFriendsDialog = false
                                    }
                                    is com.example.picflick.data.Result.Error -> {
                                        showPicFlickToast("Failed to update tags")
                                    }
                                    else -> {}
                                }
                            }
                        },
                        onNavigateToFindFriends = {
                            showTagFriendsDialog = false
                            onNavigateToFindFriends()
                        },
                        repository = repository,
                        showPicFlickToast = { message -> showPicFlickToast(message) }
                    )
                }
                
                // BOTTOM USER INFO - With gradient transparent box
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 64.dp)
                ) {
                    // Gradient transparent background box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.2f),
                                        Color.Black.copy(alpha = 0.4f)
                                    ),
                                    startY = 0f,
                                    endY = 100f
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        // Profile pic + name/description + reactions row
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // MAIN ROW: Profile + Name + Reactions
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // LEFT: Profile pic + name/description
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Profile picture - USE CURRENT USER'S PHOTO
                                    val profilePhotoUrl = if (currentFlick.userId == currentUser.uid) {
                                        currentUser.photoUrl // Use current/up-to-date photo for own photos
                                    } else {
                                        currentFlick.userPhotoUrl // Use stored photo for others
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.Gray.copy(alpha = 0.4f))
                                            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                            .clickable { onUserProfileClick(currentFlick.userId) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (profilePhotoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = profilePhotoUrl,
                                                contentDescription = "View ${currentFlick.userName}'s profile",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            // Fallback - show initial
                                            Text(
                                                text = currentFlick.userName.take(1).uppercase(),
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    // Name + Description + Time in one column
                                    Column(
                                        modifier = Modifier.clickable { onUserProfileClick(currentFlick.userId) }
                                    ) {
                                        // Username
                                        Text(
                                            text = currentFlick.userName,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        // Description and timestamp in ONE LINE
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Description - clickable to edit if owner
                                            val descriptionText = currentDescription.ifEmpty { "Add a caption..." }
                                            val descriptionColor = if (currentDescription.isEmpty()) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.85f)
                                            
                                            Text(
                                                text = descriptionText,
                                                color = descriptionColor,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                modifier = if (canDelete) {
                                                    Modifier.clickable { showEditCaption = true }
                                                } else Modifier
                                            )
                                            
                                            // Dot separator
                                            Box(
                                                modifier = Modifier
                                                    .size(3.dp)
                                                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
                                            )
                                            
                                            // Timestamp
                                            Text(
                                                text = formatTimestamp(currentFlick.timestamp),
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                
                                // RIGHT: Reactions with counts
                                ReactionCountersRow(
                                    flick = currentFlick,
                                    canReact = !canDelete, // Can't react to own photos
                                    onReactionClick = { showReactionPicker = true }
                                )
                            }
                            
                            // TAGGED FRIENDS ROW (if any)
                            if (taggedFriendsProfiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Label
                                    Text(
                                        text = "with",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                    
                                    // Profile pics of tagged friends
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy((-8).dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        taggedFriendsProfiles.take(5).forEach { friend ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                                    .clickable { onUserProfileClick(friend.uid) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = friend.photoUrl,
                                                    contentDescription = friend.displayName,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                        
                                        // Show "+X more" if more than 5 tagged
                                        if (taggedFriendsProfiles.size > 5) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.6f))
                                                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "+${taggedFriendsProfiles.size - 5}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Names (first 2 names)
                                    val namesText = if (taggedFriendsProfiles.size <= 2) {
                                        taggedFriendsProfiles.joinToString(" and ") { it.displayName }
                                    } else {
                                        "${taggedFriendsProfiles[0].displayName} and ${taggedFriendsProfiles.size - 1} others"
                                    }
                                    
                                    Text(
                                        text = namesText,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 4.dp)
                                    )
                                }
                            }
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

/**
 * Reaction counters row - displays all 5 reaction types with counts
 */
@Composable
private fun ReactionCountersRow(
    flick: Flick,
    canReact: Boolean,
    onReactionClick: () -> Unit
) {
    val reactionCounts = flick.getReactionCounts()
    val totalReactions = flick.getTotalReactions()
    
    if (totalReactions == 0) {
        // Show placeholder only if user CAN react (not their own photo)
        if (canReact) {
            Text(
                text = "Be first to react!",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.clickable { onReactionClick() }
            )
        }
        return
    }
    
    // Display reactions in a row - only show reactions with count > 0
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onReactionClick() }
    ) {
        ReactionType.entries.forEach { reactionType ->
            val count = reactionCounts[reactionType] ?: 0
            if (count > 0) {
                ReactionCounterItem(
                    emoji = reactionType.toEmoji(),
                    count = count
                )
            }
        }
    }
}

/**
 * Individual reaction counter item - emoji with count
 */
@Composable
private fun ReactionCounterItem(
    emoji: String,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = emoji,
            fontSize = 14.sp
        )
        Text(
            text = count.toString(),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Share Friends Dialog - Share photo with friends
 * Full screen style matching Tag Friends design
 */
@Composable
private fun ShareFriendsDialog(
    flick: Flick,
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onShareToFriend: (String, String) -> Unit,
    onNavigateToFindFriends: () -> Unit,
    showPicFlickToast: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { FlickRepository.getInstance() }
    var friendsList by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingFriends by remember { mutableStateOf(true) }
    
    // Load friends when dialog opens
    LaunchedEffect(Unit) {
        if (currentUser.following.isNotEmpty()) {
            val loadedFriends = mutableListOf<UserProfile>()
            var loadedCount = 0
            
            currentUser.following.forEach { userId ->
                repository.getUserProfile(userId) { result ->
                    when (result) {
                        is com.example.picflick.data.Result.Success -> {
                            loadedFriends.add(result.data)
                        }
                        else -> { /* Skip failed loads */ }
                    }
                    loadedCount++
                    if (loadedCount >= currentUser.following.size) {
                        friendsList = loadedFriends.sortedBy { it.displayName }
                        isLoadingFriends = false
                    }
                }
            }
        } else {
            friendsList = emptyList()
            isLoadingFriends = false
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            shape = RectangleShape,
            color = Color(0xFF1A1A1A)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share with Friends",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preview of photo being shared - TALLER with SQUARE corners and WHITE BORDER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(3.dp, Color.White.copy(alpha = 0.9f), RectangleShape)
                        .padding(3.dp)
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = flick.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Currently sharing count
                Text(
                    text = "Select a friend to share with:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Friends list or empty state
                when {
                    isLoadingFriends -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF87CEEB)
                            )
                        }
                    }
                    friendsList.isEmpty() -> {
                        // No friends - show Go to Find Friends button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No friends yet",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { 
                                        onNavigateToFindFriends()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF87CEEB)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Find Friends")
                                }
                            }
                        }
                    }
                    else -> {
                        // Show friends list with Share button on right
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            friendsList.forEach { friend ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // LEFT: Profile pic + name
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Friend photo with white border like profile pic
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color.Gray.copy(alpha = 0.4f))
                                                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = friend.photoUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        
                                        // Friend name
                                        Text(
                                            text = friend.displayName,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    // RIGHT: Share button
                                    Button(
                                        onClick = {
                                            onShareToFriend(flick.id, friend.uid)
                                            showPicFlickToast("Shared with ${friend.displayName}")
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF87CEEB)
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(
                                            "Share",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                if (friend != friendsList.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        thickness = 1.dp,
                                        color = Color.White.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tag Friends Dialog - Tag existing friends or navigate to Find Friends
 * Full screen style with no rounded corners
 */
@Composable
private fun TagFriendsDialog(
    flick: Flick,
    currentUser: UserProfile,
    taggedFriends: List<String>,
    onDismiss: () -> Unit,
    onTagFriendsChanged: (List<String>) -> Unit,
    onSaveTags: (List<String>) -> Unit,
    onNavigateToFindFriends: () -> Unit,
    repository: FlickRepository,
    showPicFlickToast: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var friendsList by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingFriends by remember { mutableStateOf(true) }
    var selectedFriends by remember { mutableStateOf(taggedFriends.toMutableSet()) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Load friends when dialog opens
    LaunchedEffect(Unit) {
        if (currentUser.following.isNotEmpty()) {
            val loadedFriends = mutableListOf<UserProfile>()
            var loadedCount = 0
            
            currentUser.following.forEach { userId ->
                repository.getUserProfile(userId) { result ->
                    when (result) {
                        is com.example.picflick.data.Result.Success -> {
                            loadedFriends.add(result.data)
                        }
                        else -> { /* Skip failed loads */ }
                    }
                    loadedCount++
                    if (loadedCount >= currentUser.following.size) {
                        friendsList = loadedFriends.sortedBy { it.displayName }
                        isLoadingFriends = false
                    }
                }
            }
        } else {
            friendsList = emptyList()
            isLoadingFriends = false
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            shape = RectangleShape,
            color = Color(0xFF1A1A1A)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tag Friends",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preview of photo being tagged - TALLER with SQUARE corners and WHITE BORDER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(3.dp, Color.White.copy(alpha = 0.9f), RectangleShape)
                        .padding(3.dp)
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = flick.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Currently tagged count
                if (selectedFriends.isNotEmpty()) {
                    Text(
                        text = "${selectedFriends.size} friend${if (selectedFriends.size > 1) "s" else ""} tagged",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Friends list or empty state
                when {
                    isLoadingFriends -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    friendsList.isEmpty() -> {
                        // No friends - show Go to Find Friends button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No friends yet",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { 
                                        onNavigateToFindFriends()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Find Friends")
                                }
                            }
                        }
                    }
                    else -> {
                        // Show friends list with TAG button on right
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            friendsList.forEach { friend ->
                                val isSelected = selectedFriends.contains(friend.uid)
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // LEFT: Profile pic + name
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Friend photo with white border like profile pic
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color.Gray.copy(alpha = 0.4f))
                                                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = friend.photoUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        
                                        // Friend name
                                        Text(
                                            text = friend.displayName,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    // RIGHT: Tag/Untag button
                                    if (isSelected) {
                                        // Already tagged - show "Tagged" button (green/primary)
                                        Button(
                                            onClick = {
                                                selectedFriends = selectedFriends.toMutableSet().apply { remove(friend.uid) }
                                                onTagFriendsChanged(selectedFriends.toList())
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text(
                                                "Tagged",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        // Not tagged - show "Tag" outlined button
                                        OutlinedButton(
                                            onClick = {
                                                selectedFriends = selectedFriends.toMutableSet().apply { add(friend.uid) }
                                                onTagFriendsChanged(selectedFriends.toList())
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.height(36.dp),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                                width = 1.dp,
                                                brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.5f))
                                            )
                                        ) {
                                            Text(
                                                "Tag",
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                                
                                if (friend != friendsList.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        thickness = 1.dp,
                                        color = Color.White.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons (only show Save if there are friends)
                if (friendsList.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                isSaving = true
                                onSaveTags(selectedFriends.toList())
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save Tags")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Download image from URL and save to gallery
 */
private suspend fun downloadImageToGallery(context: Context, imageUrl: String, flickId: String) {
    withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            
            val inputStream = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    saveBitmapToGallery(context, bitmap, "PicFlick_$flickId")
                    Toast.makeText(context, "Saved to gallery!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to download image", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Save bitmap to device gallery
 */
private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    
    uri?.let { imageUri ->
        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(imageUri, contentValues, null, null)
            }
        }
    }
}
