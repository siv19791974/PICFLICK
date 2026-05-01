package com.picflick.app.ui.screens

import com.picflick.app.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.res.painterResource
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
import kotlin.math.roundToInt
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
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
import com.picflick.app.ui.theme.PicFlickLightBackground
import com.picflick.app.util.withCacheBust
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import java.net.URL
import java.net.HttpURLConnection

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun FullScreenPhotoViewer(
    flick: Flick,
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onReaction: (Flick, ReactionType?) -> Unit = { _, _ -> },
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
    onEditPhotoClick: (Flick) -> Unit = {},
    friendProfiles: Map<String, UserProfile> = emptyMap(), // Map of userId -> UserProfile for looking up profile pics
    openCommentPanelInitially: Boolean = false,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val requestWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }
    val requestHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.roundToPx() }
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Orientation is controlled below, based on current displayed photo orientation.

    // Helper function to show custom toast with PicFlick logo
    fun showPicFlickToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            val inflater = LayoutInflater.from(context)
            val layout = inflater.inflate(R.layout.custom_toast, null, false)

            val toastMessage = layout.findViewById<TextView>(R.id.toast_message)
            toastMessage.text = message

            val toast = Toast(context)
            toast.duration = Toast.LENGTH_SHORT
            toast.view = layout
            toast.show()
        }
    }
    
    val repository = remember { FlickRepository.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val prefetchImageLoader = remember(context) { SingletonImageLoader.get(context) }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        if (activity != null) {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { }
    }
    
    // Scroll state for comments section
    val scrollState = rememberScrollState()
    
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var newCommentText by remember { mutableStateOf("") }
    var lastCommentSentAt by remember { mutableStateOf(0L) }
    var isLoadingComments by remember { mutableStateOf(true) }
    var commentsRefreshNonce by remember { mutableStateOf(0) }
    var isRefreshingComments by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditCaption by remember { mutableStateOf(false) }

    val commentsPullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshingComments,
        onRefresh = {
            isLoadingComments = true
            isRefreshingComments = true
            commentsRefreshNonce++
        }
    )

    LaunchedEffect(isLoadingComments, isRefreshingComments) {
        if (isRefreshingComments && !isLoadingComments) {
            isRefreshingComments = false
        }
    }

    // Report and block menu state
    var showMoreMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var showMuteUserDialog by remember { mutableStateOf(false) }
    var selectedMuteDurationMillis by remember { mutableStateOf<Long?>(null) }
    var selectedReportReason by remember { mutableStateOf("") }
    
    // UI visibility toggle
    var uiVisible by remember { mutableStateOf(true) }
    
    // 2D Pager state
    var currentPageIndex by remember { mutableIntStateOf(currentIndex) }
    
    val deletedFlickIds = remember { mutableStateListOf<String>() }
    var currentSwipeTraceStartedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentSwipeTracePhotoKey by remember { mutableStateOf("") }
    var lastLoggedSuccessPhotoKey by remember { mutableStateOf<String?>(null) }
    var lastPageIndexForPrefetch by remember { mutableIntStateOf(currentIndex) }
    val prefetchLastEnqueuedAt = remember { mutableStateMapOf<String, Long>() }
    val prefetchInFlightKeys = remember { mutableStateMapOf<String, Boolean>() }

    // Keep a stable viewer session list so background feed refreshes don't shrink pager while open.
    val sessionPhotos = remember { mutableStateListOf<Flick>() }
    LaunchedEffect(allPhotos) {
        if (allPhotos.isEmpty()) return@LaunchedEffect

        if (sessionPhotos.isEmpty()) {
            sessionPhotos.addAll(allPhotos)
        } else {
            val existingIndexById = sessionPhotos
                .mapIndexed { index, existing -> existing.id to index }
                .toMap()

            allPhotos.forEach { flickFromFeed ->
                val existingIndex = existingIndexById[flickFromFeed.id]
                if (existingIndex != null) {
                    // Keep session list in sync with live feed updates (reactions/comments/caption)
                    sessionPhotos[existingIndex] = flickFromFeed
                } else {
                    sessionPhotos.add(flickFromFeed)
                }
            }
        }

        sessionPhotos.sortWith(
            compareByDescending<Flick> { it.timestamp }
                .thenByDescending { it.id }
        )
    }

    // Filter out photos with empty image URLs and locally deleted items.
    val validPhotos = sessionPhotos.filter { it.imageUrl.isNotBlank() && !deletedFlickIds.contains(it.id) }
    
    // Current flick based on page index
    val currentFlick = if (validPhotos.isNotEmpty() && currentPageIndex in validPhotos.indices) {
        validPhotos[currentPageIndex]
    } else flick

    // Force portrait everywhere (including fullscreen), regardless of photo orientation.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
    // Calculate if user can delete/react based on CURRENT flick (updates when swiping)
val canDeleteCurrent = currentFlick.userId == currentUser.uid
    val canReactCurrent = !canDeleteCurrent
    
    // Description states - keyed to id + description so edits on same photo id refresh correctly
    var editCaptionText by remember(currentFlick.id, currentFlick.description) { mutableStateOf(currentFlick.description) }
    var currentDescription by remember(currentFlick.id, currentFlick.description) { mutableStateOf(currentFlick.description) }
    
    // Get user's current reaction
    val userReaction = currentFlick.getUserReaction(currentUser.uid)
    val reactionCounts = currentFlick.getReactionCounts()
    val totalReactions = currentFlick.getTotalReactions()
    
    // Show reaction picker state
    var showReactionPicker by remember { mutableStateOf(false) }
    var showReactionTallySheet by remember { mutableStateOf(false) }
    val reactionProfileCache = remember { mutableStateMapOf<String, UserProfile>() }
    val reactionUserMetaCache = remember { mutableStateMapOf<String, Pair<String, String>>() }
    
    // Show comment panel state
    var showCommentPanel by remember(openCommentPanelInitially) { mutableStateOf(openCommentPanelInitially) }
    var canonicalCommentFlickId by remember(currentFlick.id, currentFlick.imageUrl) {
        mutableStateOf(if (currentFlick.id.startsWith("chat_photo_")) null else currentFlick.id)
    }
    
    // Like animation state for double-tap
    var showLikeAnimation by remember { mutableStateOf(false) }
    var isUnlikeAnimation by remember { mutableStateOf(false) }
    val isLiked = userReaction != null
    
    // Fetch User B's profile photo if not available
    var fetchedUserPhotoUrl by remember(currentFlick.userId) { mutableStateOf<String?>(null) }

    LaunchedEffect(currentFlick.userId) {
        // Fetch latest user photo unless we already have a valid current profile photo for this user
        val friendProfile = friendProfiles[currentFlick.userId]
        val hasCurrentProfilePhoto = friendProfile?.photoUrl?.isNotBlank() == true
        val needsFetch = currentFlick.userId != currentUser.uid && !hasCurrentProfilePhoto

        if (needsFetch) {
            try {
                val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentFlick.userId)
                    .get()
                    .await()

                val photoUrl = userDoc.getString("photoUrl")
                if (!photoUrl.isNullOrBlank()) {
                    fetchedUserPhotoUrl = photoUrl
                }
            } catch (e: Exception) {
                // Silently fail - will use initials fallback
            }
        }
    }
    
    // Show share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    
    // Tagged friends display state - reset when flick changes
    var taggedFriendsProfiles by remember(currentFlick.id) { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingTaggedFriends by remember { mutableStateOf(false) }
    
    // Reset tagged friends profiles when flick changes
    LaunchedEffect(currentFlick.id) {
        taggedFriendsProfiles = emptyList()
        isLoadingTaggedFriends = false
    }
    
    // Tag friends dialog state
    var showTagFriendsDialog by remember { mutableStateOf(false) }
    var taggedFriends by remember(currentFlick.id) { mutableStateOf<List<String>>(currentFlick.taggedFriends) }
    var isLoadingTagFriends by remember { mutableStateOf(false) }
    
    // Close dialogs when flick changes (user swiped to new photo)
    LaunchedEffect(currentFlick.id) {
        showShareDialog = false
        showTagFriendsDialog = false
        showEditCaption = false
        showDeleteConfirmation = false
        showMoreMenu = false
    }

    // Load comments when flick changes - listen on current + fallback + resolved canonical IDs and merge
    DisposableEffect(currentFlick.id, currentFlick.imageUrl, currentFlick.userId, commentsRefreshNonce) {
        isLoadingComments = true

        val fallbackCommentThreadId = "chat_photo_${currentFlick.imageUrl.substringBefore("?").hashCode()}"
        var primaryComments: List<Comment> = emptyList()
        var secondaryComments: List<Comment> = emptyList()
        var tertiaryComments: List<Comment> = emptyList()
        var tertiaryListener: ListenerRegistration? = null

        fun publishMergedComments() {
            comments = (primaryComments + secondaryComments + tertiaryComments)
                .distinctBy { comment ->
                    if (comment.id.isNotBlank()) comment.id
                    else "${comment.userId}_${comment.text}_${comment.timestamp?.time ?: 0L}"
                }
                .sortedBy { it.timestamp?.time ?: 0L }
            isLoadingComments = false
        }

        fun attachCanonicalListenerIfNeeded(canonicalId: String) {
            if (canonicalId.isBlank() || canonicalId == currentFlick.id || canonicalId == fallbackCommentThreadId) return
            tertiaryListener?.remove()
            tertiaryListener = repository.getComments(canonicalId) { result ->
                when (result) {
                    is com.picflick.app.data.Result.Success -> {
                        tertiaryComments = result.data
                        publishMergedComments()
                    }
                    else -> Unit
                }
            }
        }

        val primaryListener = repository.getComments(currentFlick.id) { result ->
            when (result) {
                is com.picflick.app.data.Result.Success -> {
                    primaryComments = result.data
                    publishMergedComments()
                }
                else -> isLoadingComments = false
            }
        }

        val secondaryListener = if (fallbackCommentThreadId != currentFlick.id) {
            repository.getComments(fallbackCommentThreadId) { result ->
                when (result) {
                    is com.picflick.app.data.Result.Success -> {
                        secondaryComments = result.data
                        publishMergedComments()
                    }
                    else -> Unit
                }
            }
        } else null

        if (currentFlick.id.startsWith("chat_photo_")) {
            canonicalCommentFlickId = null
            repository.getFlickByImageUrl(currentFlick.imageUrl, currentFlick.userId) { result ->
                if (result is com.picflick.app.data.Result.Success) {
                    canonicalCommentFlickId = result.data.id
                    android.util.Log.d("CommentThread", "Resolved canonical flickId='${result.data.id}' for chat flick='${currentFlick.id}'")
                    attachCanonicalListenerIfNeeded(result.data.id)
                } else {
                    android.util.Log.w("CommentThread", "Failed canonical resolve for chat flick='${currentFlick.id}', imageUrl='${currentFlick.imageUrl.take(120)}'")
                }
            }
        } else {
            canonicalCommentFlickId = currentFlick.id
            attachCanonicalListenerIfNeeded(currentFlick.id)
        }

        onDispose {
            primaryListener.remove()
            secondaryListener?.remove()
            tertiaryListener?.remove()
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

            val activeFlick = validPhotos[currentPageIndex]
            currentSwipeTraceStartedAt = System.currentTimeMillis()
            currentSwipeTracePhotoKey = activeFlick.id.ifBlank { activeFlick.imageUrl }
            lastLoggedSuccessPhotoKey = null
            android.util.Log.d(
                "PhotoViewerPerf",
                "SWIPE_START index=$currentPageIndex flickId=${activeFlick.id} key=$currentSwipeTracePhotoKey"
            )
        }
    }

    // Preload nearby photos to reduce swipe latency on slower devices.
    // Direction-aware + de-duped to avoid flooding the request queue.
    LaunchedEffect(currentPageIndex, validPhotos) {
        if (validPhotos.isEmpty()) return@LaunchedEffect

        val direction = (currentPageIndex - lastPageIndexForPrefetch).coerceIn(-1, 1)
        lastPageIndexForPrefetch = currentPageIndex

        val neighborIndices = when {
            direction > 0 -> listOf(
                currentPageIndex + 1,
                currentPageIndex + 2,
                currentPageIndex + 3
            )
            direction < 0 -> listOf(
                currentPageIndex - 1,
                currentPageIndex - 2,
                currentPageIndex - 3
            )
            else -> listOf(
                currentPageIndex + 1,
                currentPageIndex + 2
            )
        }
            .filter { it in validPhotos.indices }
            .distinct()

        val now = System.currentTimeMillis()

        neighborIndices.forEach { index ->
            val neighbor = validPhotos[index]
            if (neighbor.imageUrl.isBlank()) return@forEach

            val distance = kotlin.math.abs(index - currentPageIndex)
            val enqueueTtlMs = when (distance) {
                1 -> 120L
                2 -> 350L
                else -> 700L
            }

            val prefetchKey = neighbor.id.ifBlank { neighbor.imageUrl }
            val lastEnqueuedAt = prefetchLastEnqueuedAt[prefetchKey] ?: 0L
            val isInFlight = prefetchInFlightKeys[prefetchKey] == true
            val inFlightStaleMs = 1_000L
            val isStaleInFlight = isInFlight && (now - lastEnqueuedAt > inFlightStaleMs)
            if (isStaleInFlight) {
                prefetchInFlightKeys[prefetchKey] = false
            }

            if ((prefetchInFlightKeys[prefetchKey] == true) || now - lastEnqueuedAt < enqueueTtlMs) return@forEach

            prefetchInFlightKeys[prefetchKey] = true
            prefetchLastEnqueuedAt[prefetchKey] = now

            android.util.Log.d("ThumbVerify", "Fullscreen prefetch: flick=${neighbor.id.take(8)} using ${if (neighbor.thumbnailUrl512.isNotBlank()) "THUMB_512" else "ORIGINAL"}")
            val request = ImageRequest.Builder(context)
                .data(withCacheBust(neighbor.thumbnailUrl512.ifBlank { neighbor.imageUrl }, neighbor.timestamp))
                .size(requestWidthPx, requestHeightPx)
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .listener(
                    onSuccess = { _, _ ->
                        prefetchInFlightKeys[prefetchKey] = false
                    },
                    onError = { _, _ ->
                        prefetchInFlightKeys[prefetchKey] = false
                    },
                    onCancel = { _ ->
                        prefetchInFlightKeys[prefetchKey] = false
                    }
                )
                .build()

            prefetchImageLoader.enqueue(request)
            android.util.Log.d(
                "PhotoViewerPerf",
                "PREFETCH_ENQUEUE from=$currentPageIndex to=$index flickId=${neighbor.id} dir=$direction dist=$distance"
            )
        }

        if (prefetchLastEnqueuedAt.size > 300) {
            val staleCutoff = now - 700L
            prefetchLastEnqueuedAt.entries.removeAll { it.value < staleCutoff }
            prefetchInFlightKeys.entries.removeAll { (key, inFlight) ->
                !inFlight && (prefetchLastEnqueuedAt[key] ?: 0L) < staleCutoff
            }
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
                        showDeleteConfirmation = false

                        // Optimistic UX: remove immediately in-viewer + parent feed, then delete in background.
                        val flickIdToDelete = currentFlick.id
                        deletedFlickIds.add(flickIdToDelete)
                        onDeleteClick()
                        onDismiss()

                        repository.deleteFlick(flickIdToDelete) { result ->
                            when (result) {
                                is com.picflick.app.data.Result.Success -> {
                                    showPicFlickToast("Photo Deleted")
                                }
                                is com.picflick.app.data.Result.Error -> {
                                    showPicFlickToast("Delete failed, refreshing...")
                                    onDeleteClick()
                                }
                                else -> {
                                    // Loading or other states - do nothing
                                }
                            }
                        }
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

    // MUTE USER DIALOG
    if (showMuteUserDialog) {
        val muteDurations = listOf(
            "1 hour" to TimeUnit.HOURS.toMillis(1),
            "1 day" to TimeUnit.DAYS.toMillis(1),
            "1 week" to TimeUnit.DAYS.toMillis(7),
            "1 month" to TimeUnit.DAYS.toMillis(30),
            "1 year" to TimeUnit.DAYS.toMillis(365),
            "Mute user" to Long.MAX_VALUE
        )

        AlertDialog(
            onDismissRequest = {
                showMuteUserDialog = false
                selectedMuteDurationMillis = null
            },
            title = { Text("Mute User", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hide ${currentFlick.userName}'s uploads for how long?",
                        color = Color.Gray
                    )
                    muteDurations.forEach { (label, durationMs) ->
                        FilterChip(
                            selected = selectedMuteDurationMillis == durationMs,
                            onClick = { selectedMuteDurationMillis = durationMs },
                            label = {
                                Text(
                                    label,
                                    color = if (selectedMuteDurationMillis == durationMs) Color.Black else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF58A6FF),
                                containerColor = Color(0xFF2C2C2E),
                                selectedLabelColor = Color.Black,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDuration = selectedMuteDurationMillis ?: return@TextButton
                        val muteUntil = if (selectedDuration == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + selectedDuration
                        repository.muteUser(
                            currentUserId = currentUser.uid,
                            targetUserId = currentFlick.userId,
                            muteUntilEpochMs = muteUntil
                        ) { muteResult ->
                            when (muteResult) {
                                is com.picflick.app.data.Result.Success -> {
                                    showPicFlickToast("User muted")
                                    onDismiss()
                                }
                                is com.picflick.app.data.Result.Error -> showPicFlickToast("Failed to mute user")
                                else -> Unit
                            }
                        }
                        showMuteUserDialog = false
                        selectedMuteDurationMillis = null
                    },
                    enabled = selectedMuteDurationMillis != null
                ) {
                    Text(
                        "Mute",
                        color = if (selectedMuteDurationMillis != null) Color(0xFFFFB347) else Color.Gray
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMuteUserDialog = false
                    selectedMuteDurationMillis = null
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
                var dragXOffset by remember { mutableFloatStateOf(0f) }
                var dragYOffset by remember { mutableFloatStateOf(0f) }
                var rawDragX by remember { mutableFloatStateOf(0f) }
                var rawDragY by remember { mutableFloatStateOf(0f) }
                var isDraggingVertically by remember { mutableStateOf(false) }
                var isDragging by remember { mutableStateOf(false) }

                // Reset drag when photo or orientation changes
                LaunchedEffect(currentPageIndex, configuration.screenWidthDp, configuration.screenHeightDp) {
                    dragXOffset = 0f
                    dragYOffset = 0f
                    rawDragX = 0f
                    rawDragY = 0f
                    isDraggingVertically = false
                    isDragging = false
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(validPhotos.size) {
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
                                    val shouldNavigateVertical = isDraggingVertically && kotlin.math.abs(rawDragY) > 80f
                                    val shouldNavigateHorizontal = !isDraggingVertically && kotlin.math.abs(rawDragX) > 80f
                                    val attemptedNext =
                                        (shouldNavigateVertical && rawDragY < 0) ||
                                            (shouldNavigateHorizontal && rawDragX < 0)

                                    var didNavigate = false
                                    when {
                                        // Vertical swipe - threshold 150f
                                        shouldNavigateVertical -> {
                                            if (rawDragY < 0 && currentPageIndex < validPhotos.size - 1) {
                                                currentPageIndex++ // UP = NEXT
                                                didNavigate = true
                                            } else if (rawDragY > 0 && currentPageIndex > 0) {
                                                currentPageIndex-- // DOWN = PREV
                                                didNavigate = true
                                            }
                                        }
                                        // Horizontal swipe - threshold 150f
                                        shouldNavigateHorizontal -> {
                                            if (rawDragX < 0 && currentPageIndex < validPhotos.size - 1) {
                                                currentPageIndex++ // LEFT = NEXT
                                                didNavigate = true
                                            } else if (rawDragX > 0 && currentPageIndex > 0) {
                                                currentPageIndex-- // RIGHT = PREV
                                                didNavigate = true
                                            }
                                        }
                                    }

                                    if (!didNavigate) {
                                        if (attemptedNext && currentPageIndex >= validPhotos.size - 1) {
                                            android.util.Log.d(
                                                "PhotoViewerPerf",
                                                "EDGE_NEXT_REQUEST index=$currentPageIndex size=${validPhotos.size}"
                                            )
                                            onNavigateToPhoto(currentPageIndex)
                                        }

                                        // Reset drag offsets immediately for snappier feel
                                        dragXOffset = 0f
                                        dragYOffset = 0f
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
                                        
                                        val blockedAtEdge = (atTop && goingDown) || (atBottom && goingUp)
                                        val effectiveY = if (blockedAtEdge) amount.y * 0.22f else amount.y
                                        rawDragY += effectiveY
                                        dragYOffset = rawDragY
                                    } else {
                                        val atLeft = currentPageIndex == 0
                                        val atRight = currentPageIndex == validPhotos.size - 1
                                        
                                        // Block: at left can't drag right (to prev), at right can't drag left (to next)
                                        val goingRight = amount.x > 0
                                        val goingLeft = amount.x < 0
                                        
                                        val blockedAtEdge = (atLeft && goingRight) || (atRight && goingLeft)
                                        val effectiveX = if (blockedAtEdge) amount.x * 0.22f else amount.x
                                        rawDragX += effectiveX
                                        dragXOffset = rawDragX
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
                        val finalX = baseX + dragXOffset
                        val finalY = baseY + dragYOffset
                        
                        // Calculate scale and fade for swipe effect - only when dragging
                        val dragProgress = kotlin.math.abs(dragXOffset) / screenWidthPx
                        val verticalProgress = kotlin.math.abs(dragYOffset) / screenHeightPx
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
                            val displayUrl = photo.thumbnailUrl512.ifBlank { photo.imageUrl }
                            android.util.Log.d("ThumbVerify", "Fullscreen viewer: flick=${photo.id.take(8)} using ${if (photo.thumbnailUrl512.isNotBlank()) "THUMB_512" else "ORIGINAL"}")
                            val photoModel = remember(displayUrl, photo.timestamp, requestWidthPx, requestHeightPx) {
                                ImageRequest.Builder(context)
                                    .data(withCacheBust(displayUrl, photo.timestamp))
                                    .size(requestWidthPx, requestHeightPx)
                                    .crossfade(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }

                            AsyncImage(
                                model = photoModel,
                                imageLoader = prefetchImageLoader,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onSuccess = { success ->
                                    if (isCurrent) {
                                        val key = photo.id.ifBlank { photo.imageUrl }
                                        if (lastLoggedSuccessPhotoKey != key) {
                                            val elapsedMs = System.currentTimeMillis() - currentSwipeTraceStartedAt
                                            lastLoggedSuccessPhotoKey = key
                                            android.util.Log.d(
                                                "PhotoViewerPerf",
                                                "SWIPE_RENDER_OK index=$currentPageIndex flickId=${photo.id} key=$key elapsedMs=$elapsedMs source=${success.result.dataSource}"
                                            )
                                        }
                                    }
                                },
                                onError = { error ->
                                    if (isCurrent) {
                                        val key = photo.id.ifBlank { photo.imageUrl }
                                        val elapsedMs = System.currentTimeMillis() - currentSwipeTraceStartedAt
                                        android.util.Log.w(
                                            "PhotoViewerPerf",
                                            "SWIPE_RENDER_ERR index=$currentPageIndex flickId=${photo.id} key=$key elapsedMs=$elapsedMs throwable=${error.result.throwable.message}"
                                        )
                                    }
                                }
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
                    
                    // Keep only the current photo rendered to avoid side-strip artifacts in landscape.
                }
                
                // UI OVERLAY - Shows on tap (back button + right menu)
                AnimatedVisibility(
                    visible = uiVisible && !showCommentPanel,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    if (isLandscape) {
                                        WindowInsetsSides.Horizontal
                                    } else {
                                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                    }
                                )
                            )
                    ) {

                        // BACK BUTTON
                        Box(
                            modifier = Modifier
                                .padding(start = if (isLandscape) 8.dp else 16.dp, top = if (isLandscape) 8.dp else 16.dp)
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
                                .padding(end = if (isLandscape) 4.dp else 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 20.dp)
                        ) {
if (canDeleteCurrent) {
                                // MY PHOTOS MODE - Icons: Tag, Comment, Chat/Message, Edit, Delete
                                
                                // 1. TAG FRIENDS BUTTON
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
                                
                                // 2. COMMENT BUTTON
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
                                
                                // 3. CHAT/MESSAGE BUTTON (Share to friends)
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
                                
                                // 4. EDIT PHOTO/CAPTION BUTTON - Navigates to Edit Photo screen
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { onEditPhotoClick(currentFlick) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                // 5. DELETE BUTTON
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
                                                        // Remove reaction from currently visible photo
                                                        onReaction(currentFlick, null)
                                                    } else {
                                                        showReactionPicker = !showReactionPicker // TOGGLE: click to open, click again to close
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
                                    
                                    // Dropdown Menu for Report / Mute / Block
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
                                        
                                        // MUTE USER Option
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Mute User",
                                                    color = Color(0xFFFFB347)
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.NotificationsOff,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFB347)
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                showMuteUserDialog = true
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
                                    .pullRefresh(commentsPullRefreshState)
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
                                    var activeReactionPickerCommentId by remember { mutableStateOf<String?>(null) }
                                    var hasAutoScrolledInitially by remember { mutableStateOf(false) }
                                    
                                    // Separate top-level comments and replies FIRST (needed for LaunchedEffect)
                                    val topLevelComments = comments.filter { it.parentCommentId == null }
                                    val repliesByParent = comments.filter { it.parentCommentId != null }
                                        .groupBy { it.parentCommentId }
                                    
                                    // Smart auto-scroll: first load always goes to bottom; later only if user is already near bottom.
                                    // Handles both new top-level comments and new replies.
                                    LaunchedEffect(comments.size) {
                                        if (topLevelComments.isNotEmpty()) {
                                            val latestComment = comments.maxByOrNull { it.timestamp?.time ?: 0L }
                                            val targetTopLevelId = latestComment?.parentCommentId ?: latestComment?.id
                                            val targetIndex = topLevelComments.indexOfFirst { it.id == targetTopLevelId }
                                                .takeIf { it >= 0 } ?: topLevelComments.lastIndex

                                            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                            val isNearBottom = lastVisibleIndex >= topLevelComments.lastIndex - 1

                                            if (!hasAutoScrolledInitially || isNearBottom) {
                                                listState.animateScrollToItem(targetIndex)
                                                hasAutoScrolledInitially = true
                                            }
                                        }
                                    }
                                    
                                    // Auto-scroll to comment when reaction picker opens
                                    LaunchedEffect(activeReactionPickerCommentId) {
                                        activeReactionPickerCommentId?.let { commentId ->
                                            val index = topLevelComments.indexOfFirst { it.id == commentId }
                                            if (index != -1) {
                                                listState.animateScrollToItem(index)
                                            }
                                        }
                                    }
                                    
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            items = topLevelComments,
                                            key = { it.id },
                                            contentType = { "comment" }
                                        ) { comment ->
                                            Column {
                                                // Main comment
                                                CompactCommentItem(
                                                    comment = comment,
                                                    currentUserId = currentUser.uid,
                                                    currentUserName = currentUser.displayName ?: "",
                                                    flickId = currentFlick.id,
                                                    repository = repository,
                                                    coroutineScope = coroutineScope,
                                                    onReplyClick = {
                                                        replyingToComment = comment
                                                        keyboardController?.show()
                                                    },
                                                    onDelete = {
                                                        comments = comments.filter { it.id != comment.id }
                                                    },
                                                    onReactionPickerVisibilityChanged = { isVisible ->
                                                        activeReactionPickerCommentId = if (isVisible) comment.id else null
                                                    }
                                                )
                                                
                                                // Replies (indented)
                                                val replies = repliesByParent[comment.id] ?: emptyList()
                                                if (replies.isNotEmpty()) {
                                                    Column(
                                                        modifier = Modifier
                                                            .padding(start = 48.dp, top = 4.dp)
                                                    ) {
                                                        replies.forEach { reply ->
                                                            CompactCommentItem(
                                                                comment = reply,
                                                                currentUserId = currentUser.uid,
                                                                currentUserName = currentUser.displayName ?: "",
                                                                flickId = currentFlick.id,
                                                                repository = repository,
                                                                coroutineScope = coroutineScope,
                                                                onReplyClick = {
                                                                    replyingToComment = reply
                                                                    keyboardController?.show()
                                                                },
                                                                onDelete = {
                                                                    comments = comments.filter { it.id != reply.id }
                                                                },
                                                                onReactionPickerVisibilityChanged = { isVisible ->
                                                                    activeReactionPickerCommentId = if (isVisible) reply.id else null
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                PullRefreshIndicator(
                                    refreshing = isRefreshingComments,
                                    state = commentsPullRefreshState,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    contentColor = Color.White,
                                    backgroundColor = Color(0xFF1A1A1A)
                                )
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

                                val isCanonicalThreadPending = currentFlick.id.startsWith("chat_photo_") && canonicalCommentFlickId.isNullOrBlank()

                                // Send button
                                FloatingActionButton(
                                    onClick = {
                                        if (isCanonicalThreadPending) {
                                            android.util.Log.w(
                                                "CommentThread",
                                                "Blocked send: canonical unresolved for chat flick='${currentFlick.id}', currentCanonical='${canonicalCommentFlickId ?: "null"}'"
                                            )
                                            showPicFlickToast("Syncing comments thread... try again")
                                            return@FloatingActionButton
                                        }

                                        val now = System.currentTimeMillis()
                                        val debounceMs = 2_000L // 2-second rate limit between comments
                                        if (newCommentText.isNotBlank() && (now - lastCommentSentAt) >= debounceMs) {
                                            val text = newCommentText.trim()
                                            lastCommentSentAt = now
                                            val parentComment = replyingToComment
                                            val replyParentCommentId = parentComment?.parentCommentId ?: parentComment?.id
                                            // The comment directly being replied to (for notification target)
                                            val notifyCommentId = parentComment?.id

                                            val targetFlickId = canonicalCommentFlickId ?: currentFlick.id

                                            // OPTIMISTIC UPDATE: Add to UI immediately
                                            val tempComment = Comment(
                                                id = "temp_${System.currentTimeMillis()}",
                                                flickId = targetFlickId,
                                                userId = currentUser.uid,
                                                userName = currentUser.displayName ?: "",
                                                userPhotoUrl = currentUser.photoUrl ?: "",
                                                text = text,
                                                parentCommentId = replyParentCommentId,
                                                timestamp = java.util.Date()
                                            )
                                            comments = comments + tempComment

                                            // Clear input immediately
                                            newCommentText = ""
                                            keyboardController?.hide()
                                            replyingToComment = null

                                            // Send to Firestore in background
                                            coroutineScope.launch {
                                                android.util.Log.d("CommentAdd", "Adding comment: text='$text', userId='${currentUser.uid}', flickId='$targetFlickId', replyParent=$replyParentCommentId, notify=$notifyCommentId")
                                                val result = if (parentComment != null && !replyParentCommentId.isNullOrBlank() && !notifyCommentId.isNullOrBlank()) {
                                                    repository.addReply(
                                                        flickId = targetFlickId,
                                                        parentCommentId = replyParentCommentId,
                                                        notifyCommentId = notifyCommentId,
                                                        userId = currentUser.uid,
                                                        userName = currentUser.displayName ?: "",
                                                        userPhotoUrl = currentUser.photoUrl ?: "",
                                                        text = text
                                                    )
                                                } else {
                                                    repository.addComment(
                                                        targetFlickId,
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
                                            // Prefer current profile photo sources, then fall back to old photo-stored URL
                                            friendProfiles[currentFlick.userId]?.photoUrl?.takeIf { it.isNotBlank() }
                                                ?: fetchedUserPhotoUrl?.takeIf { it.isNotBlank() }
                                                ?: currentFlick.userPhotoUrl.takeIf { it.isNotBlank() }
                                                ?: "" // Empty will trigger initials fallback
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
                                        
                                        // Name + Description/Time row
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onUserProfileClick(currentFlick.userId) }
                                        ) {
                                            // TOP ROW: Username + "is with" + tagged friends
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    // Username
                                                    Text(
                                                        text = currentFlick.userName,
                                                        color = Color.White,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )

                                                    // "is with" + tagged friends (if any)
                                                    if (taggedFriendsProfiles.isNotEmpty()) {
                                                        // "is with" text
                                                        Text(
                                                            text = "is with",
                                                            color = Color.White.copy(alpha = 0.7f),
                                                            fontSize = 14.sp
                                                        )

                                                        // Profile pics of tagged friends
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy((-4).dp), // Overlap slightly
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            taggedFriendsProfiles.take(3).forEach { friend ->
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

                                                            // Show "+X" if more than 3 tagged
                                                            if (taggedFriendsProfiles.size > 3) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(24.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "+${taggedFriendsProfiles.size - 3}",
                                                                        color = Color.White,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // RIGHT: Reactions moved up to align with username row
                                                ReactionCountersRow(
                                                    flick = currentFlick,
                                                    onReactionClick = {
                                                        showReactionTallySheet = true
                                                    }
                                                )
                                            }
                                            
                                            // BOTTOM ROW: Description • Timestamp (left) and Reactions (right)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // LEFT: Description and timestamp
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
                            onReaction(currentFlick, reaction)
                            showReactionPicker = false
                        },
                        currentReaction = userReaction
                    )
                }

                if (showReactionTallySheet) {
                    data class ReactionUserUi(
                        val userId: String,
                        val displayName: String,
                        val photoUrl: String,
                        val emoji: String
                    )

                    val reactionProfilesSnapshot = reactionProfileCache.toMap()
                    val reactionMetaSnapshot = reactionUserMetaCache.toMap()

                    val reactionUsers = remember(
                        currentFlick.reactions,
                        friendProfiles,
                        currentUser,
                        reactionProfilesSnapshot,
                        reactionMetaSnapshot
                    ) {
                        currentFlick.reactions.entries.map { (userId, reactionName) ->
                            val profile = when {
                                userId == currentUser.uid -> currentUser
                                friendProfiles[userId] != null -> friendProfiles.getValue(userId)
                                reactionProfilesSnapshot[userId] != null -> reactionProfilesSnapshot.getValue(userId)
                                else -> null
                            }
                            val cachedMeta = reactionMetaSnapshot[userId]
                            val displayName = profile?.displayName?.takeIf { it.isNotBlank() }
                                ?: cachedMeta?.first?.takeIf { it.isNotBlank() }
                                ?: if (userId == currentUser.uid) "You" else "User"
                            val photoUrl = profile?.photoUrl?.takeIf { it.isNotBlank() }
                                ?: cachedMeta?.second.orEmpty()
                            val emoji = runCatching { ReactionType.valueOf(reactionName).toEmoji() }.getOrDefault("❤️")
                            ReactionUserUi(
                                userId = userId,
                                displayName = displayName,
                                photoUrl = photoUrl,
                                emoji = emoji
                            )
                        }.sortedBy { it.displayName.lowercase() }
                    }

                    LaunchedEffect(showReactionTallySheet, currentFlick.id, currentFlick.reactions) {
                        if (!showReactionTallySheet) return@LaunchedEffect
                        currentFlick.reactions.keys
                            .filter { it != currentUser.uid }
                            .filterNot {
                                friendProfiles.containsKey(it) ||
                                    reactionProfileCache.containsKey(it) ||
                                    reactionUserMetaCache.containsKey(it)
                            }
                            .forEach { userId ->
                                repository.getUserProfile(userId) { result ->
                                    when (result) {
                                        is com.picflick.app.data.Result.Success -> {
                                            reactionProfileCache[userId] = result.data
                                            reactionUserMetaCache[userId] = result.data.displayName to result.data.photoUrl
                                        }
                                        is com.picflick.app.data.Result.Error -> {
                                            reactionUserMetaCache.putIfAbsent(userId, userId to "")
                                        }
                                        is com.picflick.app.data.Result.Loading -> Unit
                                    }
                                }
                            }
                    }

                    ModalBottomSheet(
                        onDismissRequest = { showReactionTallySheet = false },
                        containerColor = PicFlickLightBackground
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.72f)
                                .background(PicFlickLightBackground)
                        ) {
                            Text(
                                text = "Reactions",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            )

                            if (reactionUsers.isEmpty()) {
                                Text(
                                    text = "No reactions yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(
                                        items = reactionUsers,
                                        key = { it.userId }
                                    ) { userReaction ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clickable {
                                                        showReactionTallySheet = false
                                                        if (userReaction.userId != currentUser.uid) {
                                                            onUserProfileClick(userReaction.userId)
                                                        }
                                                    }
                                            ) {
                                                if (userReaction.photoUrl.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = userReaction.photoUrl,
                                                        contentDescription = userReaction.displayName,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape),
                                                        contentScale = ContentScale.Crop,
                                                        error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = userReaction.displayName.firstOrNull()?.uppercase() ?: "?",
                                                            fontSize = 20.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        showReactionTallySheet = false
                                                        if (userReaction.userId != currentUser.uid) {
                                                            onUserProfileClick(userReaction.userId)
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = userReaction.displayName,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }

                                            Text(
                                                text = userReaction.emoji,
                                                style = MaterialTheme.typography.titleMedium
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

// Comment item - Layout matching reference image (profile pic | name timestamp | comment | reply | reactions)
@Composable
private fun CompactCommentItem(
    comment: Comment,
    currentUserId: String,
    currentUserName: String,  // NEW: Need actual name for notifications
    flickId: String,
    repository: FlickRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onReplyClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReactionPickerVisibilityChanged: (Boolean) -> Unit = {},
    showReplies: Boolean = false,
    replies: List<Comment> = emptyList(),
    onLoadReplies: () -> Unit = {}
) {
    var showReactionPicker by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    // Track reaction locally for optimistic update
    var localReactions by remember { mutableStateOf(comment.reactions) }
    
    // Update local when comment changes
    LaunchedEffect(comment.reactions) {
        localReactions = comment.reactions
    }
    
    // Notify parent when picker visibility changes
    LaunchedEffect(showReactionPicker) {
        onReactionPickerVisibilityChanged(showReactionPicker)
    }
    
    AnimatedVisibility(
        visible = !isDeleting,
        exit = fadeOut(tween(180)) + shrinkVertically(animationSpec = tween(180))
    ) {
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
                    val userReaction = localReactions[currentUserId]
                    IconButton(
                        onClick = { 
                            if (userReaction != null) {
                                // Optimistically remove reaction from UI first
                                localReactions = localReactions - currentUserId
                                // Remove reaction if already reacted
                                coroutineScope.launch {
                                    repository.toggleCommentReaction(
                                        commentId = comment.id,
                                        userId = currentUserId,
                                        userName = currentUserName,
                                        userPhotoUrl = "",
                                        emoji = userReaction,
                                        onResult = {}
                                    )
                                }
                            } else {
                                showReactionPicker = !showReactionPicker
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
                    
                    // Reaction count badge - use local count for optimistic update
                    if (localReactions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 20.dp, end = 4.dp)
                                .background(Color(0xFFFF4081), CircleShape)
                                .size(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${localReactions.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.wrapContentSize()
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
                            
                            coroutineScope.launch {
                                isDeleting = true
                                delay(180)

                                // Remove from UI after exit animation
                                onDelete()

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
                        val emoji = reactionType.toEmoji()
                        // Optimistically add to UI immediately
                        localReactions = localReactions + (currentUserId to emoji)
                        showReactionPicker = false
                        
                        coroutineScope.launch {
                            repository.toggleCommentReaction(
                                commentId = comment.id,
                                userId = currentUserId,
                                userName = currentUserName,
                                userPhotoUrl = "",
                                emoji = emoji,
                                onResult = {}
                            )
                        }
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
                        currentUserId = currentUserId,
                        currentUserName = currentUserName
                    )
                }
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
    currentUserId: String,
    currentUserName: String = ""  // NEW: Need for notifications
) {
    var showReactionPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // Track reaction locally for optimistic update
    var localReactions by remember { mutableStateOf(reply.reactions) }
    
    // Update local when reply changes
    LaunchedEffect(reply.reactions) {
        localReactions = reply.reactions
    }
    
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
            val userReaction = localReactions[currentUserId]
            IconButton(
                onClick = { 
                    if (userReaction != null) {
                        // Optimistically remove reaction from UI first
                        localReactions = localReactions - currentUserId
                        coroutineScope.launch {
                            repository.toggleCommentReaction(
                                commentId = reply.id,
                                userId = currentUserId,
                                userName = currentUserName,
                                userPhotoUrl = "",
                                emoji = userReaction,
                                onResult = {}
                            )
                        }
                    } else {
                        showReactionPicker = !showReactionPicker
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
            
            // Reaction count badge - use local count for optimistic update
            if (localReactions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, end = 2.dp)
                        .background(Color(0xFFFF4081), CircleShape)
                        .size(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${localReactions.size}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.wrapContentSize()
                    )
                }
            }
        }
    }
    
    if (showReactionPicker) {
        ReactionPicker(
            currentReaction = null,
            onReactionSelected = { reactionType ->
                val emoji = reactionType.toEmoji()
                // Optimistically add to UI immediately
                localReactions = localReactions + (currentUserId to emoji)
                showReactionPicker = false
                
                coroutineScope.launch {
                    repository.toggleCommentReaction(
                        commentId = reply.id,
                        userId = currentUserId,
                        userName = currentUserName,
                        userPhotoUrl = "",
                        emoji = emoji,
                        onResult = {}
                    )
                }
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
        modifier = Modifier
            .wrapContentWidth(Alignment.End)
            .clickable { onReactionClick() }
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
                        model = withCacheBust(flick.imageUrl, flick.timestamp),
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
                                        containerColor = Color(0xFF2A4A73),
                                        contentColor = Color.White
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
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2A4A73)
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
                        model = withCacheBust(flick.imageUrl, flick.timestamp),
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
                                                contentColor = Color(0xFF2A4A73)
                                            ),
                                            modifier = Modifier.height(36.dp),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                                width = 1.dp,
                                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2A4A73))
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
