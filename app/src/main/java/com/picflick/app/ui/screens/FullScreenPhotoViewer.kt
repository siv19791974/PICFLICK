package com.picflick.app.ui.screens

import com.picflick.app.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import com.picflick.app.data.Comment
import com.picflick.app.data.Flick
import com.picflick.app.data.ReactionType
import com.picflick.app.data.UserProfile
import com.picflick.app.data.toEmoji
import com.picflick.app.repository.FlickRepository
import com.picflick.app.ui.components.AnimatedReactionPicker
import com.picflick.app.ui.components.ReactionPicker
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.view.LayoutInflater
import android.widget.TextView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.URL
import java.net.HttpURLConnection

import com.google.firebase.auth.FirebaseAuth

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
    
    // Unlock orientation to allow landscape when viewing photos
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        
        onDispose {
            // Lock back to portrait when closing photo viewer
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
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

    // Report and block menu state
    var showMoreMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var selectedReportReason by remember { mutableStateOf("") }
    
    // UI visibility toggle
    var uiVisible by remember { mutableStateOf(true) }
    
    // 2D Pager state
    var currentPageIndex by remember { mutableIntStateOf(currentIndex) }
    
    // Filter out photos with empty image URLs - NO remember() so it updates when reactions change
    val validPhotos = allPhotos.filter { it.imageUrl.isNotBlank() }
    
    // Current flick based on page index
    val currentFlick = if (validPhotos.isNotEmpty() && currentPageIndex in validPhotos.indices) {
        validPhotos[currentPageIndex]
    } else flick
    
    // Calculate if user can delete/react based on CURRENT flick (updates when swiping)
    val canDeleteCurrent = currentFlick.userId == currentUser.uid
    val canReactCurrent = !canDeleteCurrent
    
    // Description states - keyed to currentFlick.id to reset when swiping
    var editCaptionText by remember(currentFlick.id) { mutableStateOf(currentFlick.description) }
    var currentDescription by remember(currentFlick.id) { mutableStateOf(currentFlick.description) }
    
    // Get user's current reaction
    val userReaction = currentFlick.getUserReaction(currentUser.uid)
    val reactionCounts = currentFlick.getReactionCounts()
    val totalReactions = currentFlick.getTotalReactions()
    
    // Show reaction picker state
    var showReactionPicker by remember { mutableStateOf(false) }
    
    // Show comment panel state
    var showCommentPanel by remember { mutableStateOf(false) }
    
    // Like animation state for double-tap
    var showLikeAnimation by remember { mutableStateOf(false) }
    var isUnlikeAnimation by remember { mutableStateOf(false) }
    val isLiked = userReaction != null
    
    // Show share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    
    // Tagged friends display state
    var taggedFriendsProfiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingTaggedFriends by remember { mutableStateOf(false) }
    
    // Tag friends dialog state
    var showTagFriendsDialog by remember { mutableStateOf(false) }
    var taggedFriends by remember { mutableStateOf<List<String>>(currentFlick.taggedFriends) }
    var isLoadingTagFriends by remember { mutableStateOf(false) }

    // Load comments when flick changes - use DisposableEffect to properly manage listener
    DisposableEffect(currentFlick.id) {
        isLoadingComments = true
        val listener = repository.getComments(currentFlick.id) { result ->
            when (result) {
                is com.picflick.app.data.Result.Success -> {
                    comments = result.data
                    isLoadingComments = false
                }
                else -> isLoadingComments = false
            }
        }
        onDispose {
            listener.remove()
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
                        is com.picflick.app.data.Result.Success -> {
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
        if (validPhotos.isNotEmpty()) {
            onNavigateToPhoto(currentPageIndex)
        }
    }
    
    // Edit caption dialog
    if (showEditCaption && canDeleteCurrent) {
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
                                is com.picflick.app.data.Result.Success -> {
                                    showPicFlickToast("Photo Deleted")
                                    onDeleteClick()
                                    onDismiss()
                                }
                                is com.picflick.app.data.Result.Error -> {
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

    // REPORT PHOTO DIALOG
    if (showReportDialog) {
        val reportReasons = listOf(
            "Sexual or inappropriate content",
            "Violence or dangerous content",
            "Harassment or bullying",
            "Spam or scam",
            "Hate speech",
            "Copyright violation",
            "Other"
        )

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Photo", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Why are you reporting this photo?",
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    reportReasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReportReason = reason }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReportReason == reason,
                                onClick = { selectedReportReason = reason },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFD7ECFF)
                                )
                            )
                            Text(
                                text = reason,
                                color = Color.White,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedReportReason.isNotEmpty()) {
                            coroutineScope.launch {
                                val result = repository.reportPhoto(
                                    flickId = currentFlick.id,
                                    reporterId = currentUser.uid,
                                    reason = selectedReportReason,
                                    details = "Reported from photo viewer"
                                )
                                when (result) {
                                    is com.picflick.app.data.Result.Success -> {
                                        showPicFlickToast("Report submitted. Thank you!")
                                    }
                                    is com.picflick.app.data.Result.Error -> {
                                        showPicFlickToast("Failed to submit report")
                                    }
                                    else -> {}
                                }
                            }
                            showReportDialog = false
                            selectedReportReason = ""
                        }
                    },
                    enabled = selectedReportReason.isNotEmpty()
                ) {
                    Text("Submit Report", color = if (selectedReportReason.isNotEmpty()) Color(0xFFFF6B6B) else Color.Gray)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showReportDialog = false
                    selectedReportReason = ""
                }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // BLOCK USER CONFIRMATION DIALOG
    if (showBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmation = false },
            title = { Text("Block User?", color = Color.White) },
            text = {
                Text(
                    "You are about to block ${currentFlick.userName}.\n\n" +
                    "They will no longer be able to:\n" +
                    "� See your photos\n" +
                    "� Message you\n" +
                    "� Find you in search\n\n" +
                    "You can unblock them anytime from Privacy Settings.",
                    color = Color.Gray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val result = repository.blockUser(
                                currentUserId = currentUser.uid,
                                targetUserId = currentFlick.userId
                            ) { blockResult ->
                                when (blockResult) {
                                    is com.picflick.app.data.Result.Success -> {
                                        showPicFlickToast("User blocked")
                                        onDismiss() // Close the photo viewer
                                    }
                                    is com.picflick.app.data.Result.Error -> {
                                        showPicFlickToast("Failed to block user")
                                    }
                                    else -> {}
                                }
                            }
                        }
                        showBlockConfirmation = false
                    }
                ) {
                    Text("Block", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmation = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
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
        // Configure window for keyboard handling
        val view = LocalView.current
        val context = LocalContext.current
        
        SideEffect {
            val window = (view.parent as? android.view.View)?.let {
                (it.context as? android.app.Activity)?.window
            } ?: (context as? android.app.Activity)?.window
            
            window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // SIMPLE 2D PAGER - Just direct positioning with Zoomable for pinch
                // NO SPRING - direct snap only
                val dragXAnim = remember { Animatable(0f) }
                val dragYAnim = remember { Animatable(0f) }
                var rawDragX by remember { mutableFloatStateOf(0f) }
                var rawDragY by remember { mutableFloatStateOf(0f) }
                var isDraggingVertically by remember { mutableStateOf(false) }
                var isDragging by remember { mutableStateOf(false) }
                
                var currentPageIndex by remember { mutableIntStateOf(currentIndex) }
                
                // Reset drag when photo changes
                LaunchedEffect(currentPageIndex) {
                    dragXAnim.snapTo(0f)
                    dragYAnim.snapTo(0f)
                    rawDragX = 0f
                    rawDragY = 0f
                    isDraggingVertically = false
                    isDragging = false
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // SWIPE detection only - pinch handled by Zoomable library
                            detectDragGestures(
                                onDragStart = {
                                    rawDragX = 0f
                                    rawDragY = 0f
                                    isDraggingVertically = false
                                    isDragging = true
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val shouldNavigateVertical = isDraggingVertically && kotlin.math.abs(rawDragY) > 150f
                                    val shouldNavigateHorizontal = !isDraggingVertically && kotlin.math.abs(rawDragX) > 150f
                                    
                                    when {
                                        // Vertical swipe - threshold 150f
                                        shouldNavigateVertical -> {
                                            if (rawDragY < 0 && currentPageIndex < validPhotos.size - 1) {
                                                currentPageIndex++ // UP = NEXT
                                            } else if (rawDragY > 0 && currentPageIndex > 0) {
                                                currentPageIndex-- // DOWN = PREV
                                            }
                                        }
                                        // Horizontal swipe - threshold 150f
                                        shouldNavigateHorizontal -> {
                                            if (rawDragX < 0 && currentPageIndex < validPhotos.size - 1) {
                                                currentPageIndex++ // LEFT = NEXT
                                            } else if (rawDragX > 0 && currentPageIndex > 0) {
                                                currentPageIndex-- // RIGHT = PREV
                                            }
                                        }
                                        else -> {
                                            // Simple snap back to center - no animation
                                            coroutineScope.launch {
                                                dragXAnim.snapTo(0f)
                                            }
                                            coroutineScope.launch {
                                                dragYAnim.snapTo(0f)
                                            }
                                        }
                                    }
                                    rawDragX = 0f
                                    rawDragY = 0f
                                },
                                onDrag = { change, amount ->
                                    val absY = kotlin.math.abs(amount.y)
                                    val absX = kotlin.math.abs(amount.x)
                                    
                                    // Detect direction on first real movement
                                    if (kotlin.math.abs(rawDragX) < 10f && kotlin.math.abs(rawDragY) < 10f) {
                                        isDraggingVertically = absY > absX
                                    }
                                    
                                    // LOCK TO ONE AXIS - no diagonal wobble!
                                    // Simple boundary block - no change to screen, just prevent past edges
                                    if (isDraggingVertically) {
                                        val atTop = currentPageIndex == 0
                                        val atBottom = currentPageIndex == validPhotos.size - 1
                                        
                                        // Block: at top can't drag down (to prev), at bottom can't drag up (to next)
                                        val goingDown = amount.y > 0
                                        val goingUp = amount.y < 0
                                        
                                        if (!(atTop && goingDown) && !(atBottom && goingUp)) {
                                            rawDragY += amount.y
                                            coroutineScope.launch {
                                                dragYAnim.snapTo(rawDragY)
                                            }
                                        }
                                    } else {
                                        val atLeft = currentPageIndex == 0
                                        val atRight = currentPageIndex == validPhotos.size - 1
                                        
                                        // Block: at left can't drag right (to prev), at right can't drag left (to next)
                                        val goingRight = amount.x > 0
                                        val goingLeft = amount.x < 0
                                        
                                        if (!(atLeft && goingRight) && !(atRight && goingLeft)) {
                                            rawDragX += amount.x
                                            coroutineScope.launch {
                                                dragXAnim.snapTo(rawDragX)
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                            )
                        }
                ) {
                    // PLUS SIGN LAYOUT - 5 positions: center + left + right + top + bottom
                    // Current at center, next at right AND bottom, prev at left AND top
                    
                    // Helper to render a photo at a specific position
                    @Composable
                    fun PhotoAtPosition(
                        photo: Flick,
                        isCurrent: Boolean,
                        baseX: Float,
                        baseY: Float
                    ) {
                        val finalX = baseX + dragXAnim.value
                        val finalY = baseY + dragYAnim.value
                        
                        // Calculate scale and fade for swipe effect - only when dragging
                        val dragProgress = kotlin.math.abs(dragXAnim.value) / screenWidthPx
                        val verticalProgress = kotlin.math.abs(dragYAnim.value) / screenHeightPx
                        val maxProgress = kotlin.math.max(dragProgress, verticalProgress)
                        val swipeScale = if (isDragging) 1f - (maxProgress * 0.15f).coerceIn(0f, 0.15f) else 1f
                        val swipeAlpha = if (isDragging) 1f - (maxProgress * 0.3f).coerceIn(0f, 0.3f) else 1f
                        
                        // Smooth fade and slide animation when comments panel opens
                        val commentPanelAlpha by animateFloatAsState(
                            targetValue = if (showCommentPanel && isCurrent) 0.8f else 1f,
                            animationSpec = tween(durationMillis = 300),
                            label = "commentPanelAlpha"
                        )
                        
                        // Move photo up when comments open (so it's visible above comments)
                        val commentPanelOffset by animateIntOffsetAsState(
                            targetValue = if (showCommentPanel && isCurrent) IntOffset(0, -500) else IntOffset(0, 0),
                            animationSpec = tween(durationMillis = 300),
                            label = "commentPanelOffset"
                        )
                        
                        // Track last tap time to detect double-tap manually
                        var lastTapTime by remember { mutableLongStateOf(0L) }
                        val doubleTapTimeout = 300L // ms
                        
                        // Zoom state for current photo only
                        val zoomState = if (isCurrent) rememberZoomState() else null
                        
                        // Modifier chain: zoomable LAST (outermost) gets pinch FIRST
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    translationX = finalX + commentPanelOffset.x,
                                    translationY = finalY + commentPanelOffset.y,
                                    scaleX = swipeScale,
                                    scaleY = swipeScale,
                                    alpha = swipeAlpha * commentPanelAlpha // Smooth fade when comments open
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { uiVisible = !uiVisible }
                                        // NO onDoubleTap - reactions only via sidebar!
                                    )
                                }
                                .then(
                                    // ZOOMABLE LAST = OUTERMOST = gets events FIRST
                                    // Double-tap zoom disabled - pass empty lambda
                                    if (isCurrent && zoomState != null) {
                                        Modifier.zoomable(
                                            zoomState = zoomState,
                                            onDoubleTap = { _ -> } // Disable double-tap zoom - do nothing
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photo.imageUrl)
                                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit // Always fit, no jump when comments open
                            )
                            
                            // Like animation overlay for current photo
                            if (isCurrent && showLikeAnimation) {
                                LikeAnimation(
                                    isUnlike = isUnlikeAnimation,
                                    onAnimationComplete = { showLikeAnimation = false }
                                )
                            }
                        }
                }
                    
                    // 1. CURRENT at CENTER
                    if (currentPageIndex < validPhotos.size) {
                        PhotoAtPosition(
                            photo = validPhotos[currentPageIndex],
                            isCurrent = true,
                            baseX = 0f,
                            baseY = 0f
                        )
                    }
                    
                    // 2. NEXT at RIGHT (for horizontal swipe)
                    if (currentPageIndex + 1 < validPhotos.size) {
                        PhotoAtPosition(
                            photo = validPhotos[currentPageIndex + 1],
                            isCurrent = false,
                            baseX = screenWidthPx,
                            baseY = 0f
                        )
                    }
                    
                    // 3. PREV at LEFT (for horizontal swipe)
                    if (currentPageIndex - 1 >= 0) {
                        PhotoAtPosition(
                            photo = validPhotos[currentPageIndex - 1],
                            isCurrent = false,
                            baseX = -screenWidthPx,
                            baseY = 0f
                        )
                    }
                    
                    // 4. NEXT at BOTTOM (for vertical swipe) - SAME photo as RIGHT
                    if (currentPageIndex + 1 < validPhotos.size) {
                        PhotoAtPosition(
                            photo = validPhotos[currentPageIndex + 1],
                            isCurrent = false,
                            baseX = 0f,
                            baseY = screenHeightPx
                        )
                    }

                    // 5. PREV at TOP (for vertical swipe) - SAME photo as LEFT
                    if (currentPageIndex - 1 >= 0) {
                        PhotoAtPosition(
                            photo = validPhotos[currentPageIndex - 1],
                            isCurrent = false,
                            baseX = 0f,
                            baseY = -screenHeightPx
                        )
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
                            if (canDeleteCurrent) {
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
                                
                                // COMMENT BUTTON - Available to everyone including photo owner
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
                                            imageVector = Icons.AutoMirrored.Filled.Chat,
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
                                
                                // REACTION BUTTON - Shows filled heart if liked, toggles on click
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
                                            .clickable { 
                                                // Only allow reacting to OTHER people's photos
                                                if (!canDeleteCurrent) {
                                                    // Toggle reaction: if already reacted, remove it; else show picker
                                                    if (userReaction != null) {
                                                        // Remove reaction by passing null
                                                        onReaction(null)
                                                    } else {
                                                        showReactionPicker = true
                                                    }
                                                }
                                            },
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
                                            imageVector = Icons.AutoMirrored.Filled.Chat,
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
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save to device",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                // MORE MENU BUTTON - Report & Block
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                            .clickable { showMoreMenu = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More options",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    
                                    // Dropdown Menu for Report & Block
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false },
                                        modifier = Modifier.background(Color(0xFF1C1C1E))
                                    ) {
                                        // REPORT PHOTO Option
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    "Report Photo",
                                                    color = Color(0xFFFF6B6B)
                                                ) 
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Flag,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF6B6B)
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                showReportDialog = true
                                            }
                                        )
                                        
                                        // BLOCK USER Option
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    "Block User",
                                                    color = Color.White
                                                ) 
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Block,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                showBlockConfirmation = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // COMMENTS MODAL BOTTOM SHEET - Outside Dialog, handles keyboard automatically!
                if (showCommentPanel) {
                    val coroutineScope = rememberCoroutineScope()
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    var replyingToComment by remember { mutableStateOf<Comment?>(null) }
                    
                    ModalBottomSheet(
                        onDismissRequest = { showCommentPanel = false },
                        sheetState = sheetState,
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        dragHandle = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
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
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp) // Bottom padding for nav bar
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Comments (${comments.size})",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { 
                                        coroutineScope.launch { sheetState.hide() }
                                        showCommentPanel = false 
                                    },
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
                            
                            // Comments list
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 225.dp) // 25% shorter (was 300)
                            ) {
                                if (isLoadingComments) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else if (comments.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No comments yet. Be the first!",
                                            color = Color.Gray,
                                            modifier = Modifier.padding(vertical = 16.dp)
                                        )
                                    }
                                } else {
                                    val listState = rememberLazyListState()
                                    
                                    // Auto-scroll to latest comment when list changes
                                    LaunchedEffect(comments.size) {
                                        if (comments.isNotEmpty()) {
                                            listState.animateScrollToItem(comments.size - 1)
                                        }
                                    }
                                    
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(comments, key = { it.id }) { comment ->
                                            CompactCommentItem(
                                                comment = comment,
                                                currentUserId = currentUser.uid,
                                                flickId = currentFlick.id,
                                                repository = repository,
                                                onReplyClick = {
                                                    replyingToComment = comment
                                                    keyboardController?.show()
                                                },
                                                onDelete = {
                                                    // Remove from UI immediately
                                                    comments = comments.filter { it.id != comment.id }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Reply indicator
                            if (replyingToComment != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Replying to ${replyingToComment?.userName}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    TextButton(
                                        onClick = { replyingToComment = null },
                                        contentPadding = PaddingValues(horizontal = 0.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            
                            // Input bar - keyboard handled automatically by ModalBottomSheet!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val focusRequester = remember { FocusRequester() }
                                TextField(
                                    value = newCommentText,
                                    onValueChange = { newCommentText = it },
                                    placeholder = { 
                                        Text(
                                            if (replyingToComment != null) "Reply to ${replyingToComment?.userName}..." else "Add a comment...",
                                            color = Color.Gray
                                        ) 
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp, max = 120.dp)
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { 
                                            if (it.isFocused) keyboardController?.show()
                                        },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF1A1A1A),
                                        unfocusedContainerColor = Color(0xFF1A1A1A),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    maxLines = 5
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Send button
                                FloatingActionButton(
                                    onClick = {
                                        if (newCommentText.isNotBlank()) {
                                            val text = newCommentText.trim()
                                            val parentComment = replyingToComment
                                            
                                            // OPTIMISTIC UPDATE: Add to UI immediately
                                            val tempComment = Comment(
                                                id = "temp_${System.currentTimeMillis()}",
                                                flickId = currentFlick.id,
                                                userId = currentUser.uid,
                                                userName = currentUser.displayName ?: "",
                                                userPhotoUrl = currentUser.photoUrl ?: "",
                                                text = text,
                                                parentCommentId = parentComment?.id,
                                                timestamp = java.util.Date()
                                            )
                                            comments = comments + tempComment
                                            
                                            // Clear input immediately
                                            newCommentText = ""
                                            keyboardController?.hide()
                                            replyingToComment = null
                                            
                                            // Send to Firestore in background
                                            coroutineScope.launch {
                                                android.util.Log.d("CommentAdd", "Adding comment: text='$text', userId='${currentUser.uid}', flickId='${currentFlick.id}',")
                                                val result = if (parentComment != null) {
                                                    repository.addReply(
                                                        flickId = currentFlick.id,
                                                        parentCommentId = parentComment.id,
                                                        userId = currentUser.uid,
                                                        userName = currentUser.displayName ?: "",
                                                        userPhotoUrl = currentUser.photoUrl ?: "",
                                                        text = text
                                                    )
                                                } else {
                                                    repository.addComment(
                                                        currentFlick.id,
                                                        currentUser.uid,
                                                        currentUser.displayName ?: "",
                                                        currentUser.photoUrl ?: "",
                                                        text
                                                    )
                                                }
                                                
                                                when (result) {
                                                    is com.picflick.app.data.Result.Success -> {
                                                        android.util.Log.d("CommentAdd", "Comment added successfully to Firestore")
                                                        // Firestore listener will replace temp with real comment
                                                    }
                                                    is com.picflick.app.data.Result.Error -> {
                                                        android.util.Log.e("CommentAdd", "Failed to add comment: ${result.exception?.message}")
                                                        // Remove temp comment on failure
                                                        comments = comments.filter { it.id != tempComment.id }
                                                    }
                                                    else -> { /* Loading - do nothing */ }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color.White
                                    )
                                }
                            }
                            
                            // Extra bottom padding for system nav bar (handled by ModalBottomSheet imePadding!)
                            Spacer(modifier = Modifier.height(8.dp))
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
                                    is com.picflick.app.data.Result.Success -> {
                                        showPicFlickToast("Tags updated!")
                                        showTagFriendsDialog = false
                                    }
                                    is com.picflick.app.data.Result.Error -> {
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
                // Completely hidden when comment panel opens using AnimatedVisibility
                AnimatedVisibility(
                    visible = !showCommentPanel,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Box(
                        modifier = Modifier
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
                                                    modifier = if (canDeleteCurrent) {
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
                                    
                                    // RIGHT: Reactions with counts + Tagged friends below
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ReactionCountersRow(
                                            flick = currentFlick,
                                            canReact = !canDeleteCurrent, // Can't react to own photos
                                            onReactionClick = { 
                                                // Only open picker for other people's photos
                                                if (!canDeleteCurrent) {
                                                    showReactionPicker = true 
                                                }
                                            }
                                        )
                                        
                                        // TAGGED FRIENDS (if any) - Profile pics only, shown below reactions on right
                                        if (taggedFriendsProfiles.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Profile pics of tagged friends - side by side with small gap
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    taggedFriendsProfiles.take(5).forEach { friend ->
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
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
                                                                .size(24.dp)
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
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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

// Comment item - Layout matching reference image (profile pic | name timestamp | comment | reply | reactions)
@Composable
private fun CompactCommentItem(
    comment: Comment,
    currentUserId: String,
    flickId: String,
    repository: FlickRepository,
    onReplyClick: () -> Unit = {},
    onDelete: () -> Unit = {},  // NEW: Callback to remove from UI immediately
    showReplies: Boolean = false,
    replies: List<Comment> = emptyList(),
    onLoadReplies: () -> Unit = {}
) {
    var showReactionPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Main comment row - Profile pic + content
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Profile pic on left
            AsyncImage(
                model = comment.userPhotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Main content column (takes most space)
            Column(modifier = Modifier.weight(1f)) {
                // Name + Timestamp on same line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.userName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = " · ${comment.timestamp?.let { formatTimestamp(it.time) } ?: ""}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                
                // Comment text directly under name (left aligned, wraps naturally)
                Text(
                    text = comment.text,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                
                // Reply button directly under comment
                TextButton(
                    onClick = onReplyClick,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .height(24.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Text(
                        text = "Reply",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // Reaction and Delete buttons on far right
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.End
            ) {
                // Reaction button
                Box(contentAlignment = Alignment.TopEnd) {
                    val userReaction = comment.reactions[currentUserId]
                    IconButton(
                        onClick = { 
                            if (userReaction != null) {
                                // Remove reaction if already reacted
                                coroutineScope.launch {
                                    repository.toggleCommentReaction(
                                        commentId = comment.id,
                                        userId = currentUserId,
                                        userName = "", // Not needed for removal
                                        userPhotoUrl = "",
                                        emoji = userReaction,
                                        onResult = {}
                                    )
                                }
                            } else {
                                showReactionPicker = true
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (userReaction != null) {
                            Text(
                                text = userReaction,
                                fontSize = 18.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = "React",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Reaction count badge
                    if (comment.getTotalReactions() > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 20.dp, end = 4.dp)
                                .background(Color(0xFFFF4081), CircleShape)
                                .size(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${comment.getTotalReactions()}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Delete button - only shown to comment owner
                if (comment.userId == currentUserId) {
                    IconButton(
                        onClick = {
                            // DEBUG: Check Firebase Auth state
                            val auth = FirebaseAuth.getInstance()
                            android.util.Log.d("CommentDebug", "=== DELETE COMMENT DEBUG ===")
                            android.util.Log.d("CommentDebug", "Firebase Auth currentUser: ${auth.currentUser?.uid ?: "NULL"}")
                            android.util.Log.d("CommentDebug", "Passed currentUserId: $currentUserId")
                            android.util.Log.d("CommentDebug", "Comment.userId: ${comment.userId}")
                            android.util.Log.d("CommentDebug", "UIDs match: ${auth.currentUser?.uid == comment.userId}")
                            android.util.Log.d("CommentDebug", "Is Firebase Auth null: ${auth.currentUser == null}")
                            
                            android.util.Log.d("CommentDelete", "Delete clicked for comment ID: '${comment.id}', flickId: '${flickId}'")
                            
                            // Remove from UI immediately (optimistic update)
                            onDelete()
                            
                            coroutineScope.launch {
                                try {
                                    val result = repository.deleteComment(comment.id, flickId)
                                    if (result is com.picflick.app.data.Result.Error) {
                                        android.util.Log.e("CommentDelete", "Failed to delete: ${result.exception?.message}")
                                    } else {
                                        android.util.Log.d("CommentDelete", "Comment deleted successfully: ${comment.id}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("CommentDelete", "Exception: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete comment",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        // Reaction picker popup - anchored above the reaction button
        if (showReactionPicker) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 80.dp, bottom = 8.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                ReactionPicker(
                    currentReaction = null,
                    onReactionSelected = { reactionType ->
                        coroutineScope.launch {
                            repository.toggleCommentReaction(
                                commentId = comment.id,
                                userId = currentUserId,
                                userName = currentUserId,
                                userPhotoUrl = "",
                                emoji = reactionType.toEmoji(),
                                onResult = {}
                            )
                        }
                        showReactionPicker = false
                    },
                    onDismiss = { showReactionPicker = false }
                )
            }
        }
        
        // Replies section - indented ~1cm (40dp) in straight line
        if (replies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, top = 8.dp)
            ) {
                replies.forEach { reply ->
                    CompactReplyItem(
                        reply = reply,
                        flickId = flickId,
                        repository = repository,
                        currentUserId = currentUserId
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactReplyItem(
    reply: Comment,
    flickId: String,
    repository: FlickRepository,
    currentUserId: String
) {
    var showReactionPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Profile pic
        AsyncImage(
            model = reply.userPhotoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Reply content
        Column(modifier = Modifier.weight(1f)) {
            // Name + Timestamp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.userName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = " · ${reply.timestamp?.let { formatTimestamp(it.time) } ?: ""}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            
            // Reply text
            Text(
                text = reply.text,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        // Reaction button
        Box(contentAlignment = Alignment.TopEnd) {
            val userReaction = reply.reactions[currentUserId]
            IconButton(
                onClick = { 
                    if (userReaction != null) {
                        coroutineScope.launch {
                            repository.toggleCommentReaction(
                                commentId = reply.id,
                                userId = currentUserId,
                                userName = "",
                                userPhotoUrl = "",
                                emoji = userReaction,
                                onResult = {}
                            )
                        }
                    } else {
                        showReactionPicker = true
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                if (userReaction != null) {
                    Text(text = userReaction, fontSize = 16.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "React",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            if (reply.getTotalReactions() > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, end = 2.dp)
                        .background(Color(0xFFFF4081), CircleShape)
                        .size(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${reply.getTotalReactions()}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    if (showReactionPicker) {
        ReactionPicker(
            currentReaction = null,
            onReactionSelected = { reactionType ->
                coroutineScope.launch {
                    repository.toggleCommentReaction(
                        commentId = reply.id,
                        userId = currentUserId,
                        userName = currentUserId,
                        userPhotoUrl = "",
                        emoji = reactionType.toEmoji(),
                        onResult = {}
                    )
                }
                showReactionPicker = false
            },
            onDismiss = { showReactionPicker = false },
            modifier = Modifier.padding(start = 180.dp)
        )
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
        // Show nothing when no reactions - removed placeholder text
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
 * Heart like animation that shows when double-tapping a photo
 * Shows ❤️ for like, 🤍 for unlike
 */
@Composable
private fun LikeAnimation(
    isUnlike: Boolean = false,
    onAnimationComplete: () -> Unit
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1.5f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "like_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 1f,
        animationSpec = tween(durationMillis = 400, delayMillis = 200),
        finishedListener = { onAnimationComplete() },
        label = "like_alpha"
    )
    
    LaunchedEffect(Unit) {
        animationStarted = true
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isUnlike) "🤍" else "❤️",
            fontSize = 80.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
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
                        is com.picflick.app.data.Result.Success -> {
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
                                        onDismiss()
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
                        is com.picflick.app.data.Result.Success -> {
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
                                        onDismiss()
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
// REFRESH
