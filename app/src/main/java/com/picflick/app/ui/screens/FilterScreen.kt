package com.picflick.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.picflick.app.R
import com.picflick.app.data.PhotoFilter
import com.picflick.app.data.UserProfile
import com.picflick.app.data.getDailyUploadLimit
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.PicFlickLightBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Screen for applying filters to selected photo with friend tagging and upload countdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    photoUri: Uri,
    currentUser: UserProfile,
    friends: List<UserProfile>,
    dailyUploadCount: Int,
    onBack: () -> Unit,
    onUpload: (Uri, PhotoFilter, List<String>, String) -> Unit,
    onNavigateToFindFriends: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(PhotoFilter.ORIGINAL) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val isDarkMode = ThemeManager.isDarkMode.value
    
    // Get daily upload limit from subscription tier
    val maxDailyUploads = currentUser.subscriptionTier.getDailyUploadLimit()
    
    // Friend tagging state
    var taggedFriends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var showFriendPicker by remember { mutableStateOf(false) }
    
    // Description/caption state
    var description by remember { mutableStateOf("") }

    // Crop state (zoom/pan image under a fixed crop frame)
    var isCropMode by remember { mutableStateOf(false) }
    var cropApplied by remember { mutableStateOf(false) }
    var cropScale by remember { mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropFrameRect by remember { mutableStateOf(Rect(0.1f, 0.1f, 0.9f, 0.9f)) }

    // Upload loading state
    var isUploading by remember { mutableStateOf(false) }
    
    // Optimistic daily upload counter - updates instantly on click
    var optimisticDailyCount by remember { mutableIntStateOf(dailyUploadCount) }
    
    // Sync optimistic count when prop changes (e.g., after successful upload)
    LaunchedEffect(dailyUploadCount) {
        optimisticDailyCount = dailyUploadCount
    }
    
    // Calculate remaining uploads using optimistic count
    val remainingUploads = maxDailyUploads - optimisticDailyCount
    val canUpload = remainingUploads > 0
    LaunchedEffect(photoUri) {
        scope.launch(Dispatchers.IO) {
            try {
                val loadedBitmap = loadBitmapFromUri(context, photoUri)
                withContext(Dispatchers.Main) {
                    bitmap = loadedBitmap
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    val filters = listOf(
        PhotoFilter.ORIGINAL,
        PhotoFilter.BLACK_AND_WHITE,
        PhotoFilter.SEPIA,
        PhotoFilter.NEGATIVE,
        PhotoFilter.HIGH_CONTRAST,
        PhotoFilter.WARM,
        PhotoFilter.COOL,
        PhotoFilter.VINTAGE,
        PhotoFilter.POLAROID,
        PhotoFilter.RETRO,
        PhotoFilter.LOMO,
        PhotoFilter._1977,
        PhotoFilter.NOIR,
        PhotoFilter.FADE,
        PhotoFilter.VIVID,
        PhotoFilter.COLOR_INVERT
    )
    
    // Animation state for countdown - now triggered immediately on upload click
    var showCountdownAnimation by remember { mutableStateOf(false) }

    // Upload function with loading state
    fun triggerUpload() {
        if (canUpload && !isUploading && bitmap != null) {
            // OPTIMISTIC UPDATE: Increment counter immediately on click
            optimisticDailyCount += 1
            // Trigger countdown animation immediately when clicking tick
            showCountdownAnimation = true
            isUploading = true
            scope.launch {
                try {
                    val filteredUri = applyFilterAndSave(
                        context = context,
                        bitmap = bitmap!!,
                        filter = selectedFilter,
                        cropEnabled = cropApplied || isCropMode,
                        cropFrameNormalized = cropFrameRect,
                        cropScale = cropScale,
                        cropOffsetPx = cropOffset,
                        previewSize = previewSize
                    )
                    onUpload(filteredUri, selectedFilter, taggedFriends.map { it.uid }, description.trim())
                    // Show success toast
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, context.getString(R.string.upload_complete), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // On failure, revert the optimistic counter
                    optimisticDailyCount -= 1
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, context.getString(R.string.upload_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isUploading = false
                    // Keep animation visible for a moment then hide
                    delay(1000)
                    showCountdownAnimation = false
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            // Custom compact 48dp title bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close/Camera icon
                    IconButton(
                        onClick = onNavigateToCamera,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.filter_back_camera),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Centered countdown box with upload complete animation
                    val isLimitReached = remainingUploads <= 0
                    
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Animated background color when upload just completed
                        val backgroundColor by androidx.compose.animation.animateColorAsState(
                            targetValue = when {
                                showCountdownAnimation -> Color(0xFF00C853) // Green success color
                                isLimitReached -> Color.Red
                                isDarkMode -> Color(0xFF2C2C2E)
                                else -> PicFlickLightBackground
                            },
                            animationSpec = androidx.compose.animation.core.tween(300),
                            label = "backgroundColor"
                        )
                        
                        val textColor by androidx.compose.animation.animateColorAsState(
                            targetValue = when {
                                showCountdownAnimation -> Color.White
                                isLimitReached -> Color.White
                                isDarkMode -> Color.White
                                else -> Color(0xFF1565C0)
                            },
                            animationSpec = androidx.compose.animation.core.tween(300),
                            label = "textColor"
                        )
                        
                        Box(
                            modifier = Modifier
                                .background(
                                    backgroundColor,
                                    RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = if (showCountdownAnimation) 0.dp else if (isLimitReached) 0.dp else 2.dp,
                                    color = if (showCountdownAnimation) Color.Transparent else if (isLimitReached) Color.Transparent else if (isDarkMode) Color.Gray else Color(0xFF1565C0),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            // Animated content for the number
                            androidx.compose.animation.AnimatedContent(
                                targetState = showCountdownAnimation,
                                transitionSpec = {
                                    // Slide up animation
                                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> -height } + fadeOut())
                                },
                                label = "countdownAnimation"
                            ) { isAnimating ->
                                Text(
                                    text = if (isLimitReached) {
                                        "SEE YOU TOMORROW!"
                                    } else {
                                        if (isAnimating) {
                                            "✓ $remainingUploads PHOTOS LEFT"
                                        } else {
                                            "$remainingUploads PHOTOS LEFT"
                                        }
                                    },
                                    color = textColor,
                                  fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Upload tick button
                    val canUpload = (maxDailyUploads - dailyUploadCount) > 0
                    
                    IconButton(
                        onClick = { triggerUpload() },
                        enabled = !isLoading && bitmap != null && canUpload && !isUploading,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (canUpload && !isLoading && bitmap != null) {
                                        if (isDarkMode) Color(0xFF4CAF50) else Color(0xFF1565C0)
                                    } else Color.Gray,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Upload",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = isDarkModeBackground(isDarkMode)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(isDarkModeBackground(isDarkMode))
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = if (isDarkMode) Color.White else Color(0xFF1565C0)
                )
            } else {
                bitmap?.let { bmp ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // Uploading Overlay (when uploading)
                        if (isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDarkMode) Color.Black.copy(alpha = 0.7f) else PicFlickLightBackground.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Uploading...",
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Main Filter Preview - Filters are the hero now!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.2f)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Show currently selected filter as large preview - FULL QUALITY
                            val previewBitmap = remember(bmp, selectedFilter) {
                                // Pass 0 to get full resolution (no downscaling)
                                applyFilterToBitmap(bmp, selectedFilter, thumbnailSize = 0)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(3.dp, if (isDarkMode) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                    .padding(3.dp)
                                    .onSizeChanged { previewSize = it }
                                    .pointerInput(isCropMode, previewSize, previewBitmap.width, previewBitmap.height, cropFrameRect) {
                                        if (!isCropMode) return@pointerInput
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (cropScale * zoom).coerceIn(1f, 5f)
                                            val proposed = cropOffset + (pan * 1.15f)
                                            cropScale = newScale
                                            cropOffset = clampCropOffsetToFrame(
                                                previewSize = previewSize,
                                                imageSize = IntSize(previewBitmap.width, previewBitmap.height),
                                                frameNormalized = cropFrameRect,
                                                scale = cropScale,
                                                offset = proposed
                                            )
                                        }
                                    }
                            ) {
                                Image(
                                    painter = BitmapPainter(previewBitmap.asImageBitmap()),
                                    contentDescription = selectedFilter.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (isCropMode) {
                                                scaleX = cropScale
                                                scaleY = cropScale
                                                translationX = cropOffset.x
                                                translationY = cropOffset.y
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )

                            }
                        }

                        // Bottom Panel with BIG filter thumbnails
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f) // Fill remaining space
                                .background(if (isDarkMode) Color(0xFF1C1C1E) else PicFlickLightBackground)
                                .padding(vertical = 8.dp) // Reduced padding
                                .windowInsetsPadding(WindowInsets.navigationBars) // Handle nav bar insets
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Filter Icons (simplified - no text)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.padding(bottom = 8.dp) // Reduced from 16.dp
                            ) {
                                items(filters) { filter ->
                                    FilterIcon(
                                        filter = filter,
                                        isSelected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        bitmap = bmp,
                                        isDarkMode = isDarkMode
                                    )
                                }
                            }
                            
                            // Tagged Friends Section
                            if (taggedFriends.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    items(taggedFriends) { friend ->
                                        TaggedFriendChip(
                                            friend = friend,
                                            onRemove = {
                                                taggedFriends = taggedFriends - friend
                                            },
                                            isDarkMode = isDarkMode
                                        )
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        if (friends.isEmpty()) {
                                            onNavigateToFindFriends()
                                        } else {
                                            showFriendPicker = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (friends.isEmpty()) "Find Friends" else "Tag Friends (${taggedFriends.size})",
                                        color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        if (!isCropMode) {
                                            isCropMode = true
                                            if (!cropApplied) {
                                                cropScale = 1f
                                                cropOffset = Offset.Zero
                                                cropFrameRect = Rect(0.1f, 0.1f, 0.9f, 0.9f)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isCropMode) "Cropping..." else if (cropApplied) "Edit Crop" else "Crop Photo",
                                        color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            // Description/Caption Input Field - Wrapped in surface to cover background
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isDarkMode) Color(0xFF1C1C1E) else PicFlickLightBackground
                            ) {
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    placeholder = { Text(stringResource(R.string.filter_caption_placeholder), color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = if (isDarkMode) Color.White else Color.Black,
                                        unfocusedTextColor = if (isDarkMode) Color.White else Color.Black,
                                        focusedBorderColor = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0),
                                        unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                                        focusedContainerColor = if (isDarkMode) Color(0xFF2C2C2E) else PicFlickLightBackground,
                                        unfocusedContainerColor = if (isDarkMode) Color(0xFF2C2C2E) else PicFlickLightBackground
                                    ),
                                    maxLines = 2,
                                    singleLine = false
                                )
                            }
                            
                            // Upload limit warning
                            if (!canUpload) {
                                Text(
                                    text = "Daily upload limit reached. Try again tomorrow!",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    if (isCropMode) {
                        val cropBitmap = remember(bmp, selectedFilter) {
                            applyFilterToBitmap(bmp, selectedFilter, thumbnailSize = 0)
                        }
                        FullScreenCropDialog(
                            previewBitmap = cropBitmap,
                            cropScale = cropScale,
                            cropOffset = cropOffset,
                            cropFrameRect = cropFrameRect,
                            onCropScaleChange = { cropScale = it },
                            onCropOffsetChange = { cropOffset = it },
                            onCropFrameChange = { newFrame ->
                                cropFrameRect = newFrame
                                cropOffset = clampCropOffsetToFrame(
                                    previewSize = previewSize,
                                    imageSize = IntSize(cropBitmap.width, cropBitmap.height),
                                    frameNormalized = cropFrameRect,
                                    scale = cropScale,
                                    offset = cropOffset
                                )
                            },
                            onPreviewSizeChange = { previewSize = it },
                            onDone = {
                                cropApplied = true
                                isCropMode = false
                            },
                            isDarkMode = isDarkMode
                        )
                    }
                }
            }
        }
    }
    
    // Friend Picker Dialog
    if (showFriendPicker) {
        FriendPickerDialog(
            friends = friends,
            alreadyTagged = taggedFriends.map { it.uid },
            onDismiss = { showFriendPicker = false },
            onFriendSelected = { friend ->
                if (friend !in taggedFriends) {
                    taggedFriends = taggedFriends + friend
                }
            },
            onNavigateToFindFriends = onNavigateToFindFriends
        )
    }
}

@Composable
private fun FilterIcon(
    filter: PhotoFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    bitmap: Bitmap? = null,
    isDarkMode: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)  // MUCH Bigger for better quality
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 4.dp else 2.dp,
                    color = if (isSelected) {
                        if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                    } else {
                        if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .background(if (isDarkMode) Color(0xFF2C2C2E) else PicFlickLightBackground),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                // Show actual filtered thumbnail at ULTRA HIGH RESOLUTION (768px for crisp 96dp display)
                val thumbnailBitmap = remember(bitmap, filter) {
                    applyFilterToBitmap(bitmap, filter, thumbnailSize = 768)  // Ultra high quality for 96dp display
                }
                Image(
                    painter = BitmapPainter(thumbnailBitmap.asImageBitmap()),
                    contentDescription = filter.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback to emoji
                Text(
                    text = filter.icon,
                    fontSize = 28.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Filter name
        Text(
            text = filter.displayName,
            color = if (isSelected) {
                if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
            } else {
                if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
            },
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TaggedFriendChip(
    friend: UserProfile,
    onRemove: () -> Unit,
    isDarkMode: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = friend.displayName.take(15),
                color = if (isDarkMode) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = if (isDarkMode) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendPickerDialog(
    friends: List<UserProfile>,
    alreadyTagged: List<String>,
    onDismiss: () -> Unit,
    onFriendSelected: (UserProfile) -> Unit,
    onNavigateToFindFriends: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Tag Friends",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (friends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
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
                            color = Color.Gray,
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
                            Text(stringResource(R.string.filter_find_friends))
                        }
                    }
                }
            } else {
                friends.filter { it.uid !in alreadyTagged }.forEach { friend ->
                    FriendPickerItem(
                        friend = friend,
                        onClick = {
                            onFriendSelected(friend)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendPickerItem(
    friend: UserProfile,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo with white border
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.4f))
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (friend.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = friend.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = friend.displayName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = friend.displayName,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FilteredImage(
    bitmap: Bitmap,
    filter: PhotoFilter,
    modifier: Modifier = Modifier
) {
    val filteredBitmap = remember(bitmap, filter) {
        applyFilterToBitmap(bitmap, filter)
    }

    Image(
        painter = BitmapPainter(filteredBitmap.asImageBitmap()),
        contentDescription = "Filtered photo",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun FullScreenCropDialog(
    previewBitmap: Bitmap,
    cropScale: Float,
    cropOffset: Offset,
    cropFrameRect: Rect,
    onCropScaleChange: (Float) -> Unit,
    onCropOffsetChange: (Offset) -> Unit,
    onCropFrameChange: (Rect) -> Unit,
    onPreviewSizeChange: (IntSize) -> Unit,
    onDone: () -> Unit,
    isDarkMode: Boolean
) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color.Black else Color(0xFF111111))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 16.dp)
                    .onSizeChanged { onPreviewSizeChange(it) }
                    .pointerInput(previewBitmap.width, previewBitmap.height, cropFrameRect, cropScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (cropScale * zoom).coerceIn(1f, 6f)
                            val proposed = cropOffset + (pan * 1.1f)
                            onCropScaleChange(newScale)
                            onCropOffsetChange(
                                clampCropOffsetToFrame(
                                    previewSize = IntSize(size.width, size.height),
                                    imageSize = IntSize(previewBitmap.width, previewBitmap.height),
                                    frameNormalized = cropFrameRect,
                                    scale = newScale,
                                    offset = proposed
                                )
                            )
                        }
                    }
            ) {
                Image(
                    painter = BitmapPainter(previewBitmap.asImageBitmap()),
                    contentDescription = "Crop preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = cropScale
                            scaleY = cropScale
                            translationX = cropOffset.x
                            translationY = cropOffset.y
                        },
                    contentScale = ContentScale.Crop
                )

                CropOverlay(
                    frameRect = cropFrameRect,
                    overlayColor = Color.Black.copy(alpha = 0.52f),
                    borderColor = Color.White,
                    onFrameRectChange = onCropFrameChange
                )
            }

            Button(
                onClick = onDone,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Text("Done Crop")
            }
        }
    }
}

@Composable
private fun CropOverlay(
    frameRect: Rect,
    overlayColor: Color,
    borderColor: Color,
    onFrameRectChange: (Rect) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(frameRect) {
                val minSize = 0.18f
                detectDragGestures { change, dragAmount ->
                    val dx = dragAmount.x / size.width
                    val dy = dragAmount.y / size.height
                    val edgeThreshold = 0.05f

                    var left = frameRect.left
                    var top = frameRect.top
                    var right = frameRect.right
                    var bottom = frameRect.bottom

                    val touchX = change.position.x / size.width
                    val touchY = change.position.y / size.height
                    val nearLeft = kotlin.math.abs(touchX - left) < edgeThreshold
                    val nearRight = kotlin.math.abs(touchX - right) < edgeThreshold
                    val nearTop = kotlin.math.abs(touchY - top) < edgeThreshold
                    val nearBottom = kotlin.math.abs(touchY - bottom) < edgeThreshold

                    if (!(nearLeft || nearRight || nearTop || nearBottom)) {
                        return@detectDragGestures
                    }
                    change.consume()

                    if (nearLeft) {
                        left += dx
                    } else if (nearRight) {
                        right += dx
                    } else if (nearTop) {
                        top += dy
                    } else {
                        bottom += dy
                    }

                    if (right - left < minSize) {
                        if (nearLeft) left = right - minSize else right = left + minSize
                    }
                    if (bottom - top < minSize) {
                        if (nearTop) top = bottom - minSize else bottom = top + minSize
                    }

                    val width = right - left
                    val height = bottom - top
                    left = left.coerceIn(0f, 1f - width)
                    top = top.coerceIn(0f, 1f - height)
                    right = (left + width).coerceIn(minSize, 1f)
                    bottom = (top + height).coerceIn(minSize, 1f)

                    onFrameRectChange(Rect(left, top, right, bottom))
                }
            }
            .drawWithContent {
                drawContent()
                val left = frameRect.left * size.width
                val top = frameRect.top * size.height
                val right = frameRect.right * size.width
                val bottom = frameRect.bottom * size.height

                drawRect(overlayColor, topLeft = Offset(0f, 0f), size = Size(size.width, top))
                drawRect(overlayColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                drawRect(overlayColor, topLeft = Offset(0f, top), size = Size(left, bottom - top))
                drawRect(overlayColor, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))

                drawRect(
                    color = borderColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 3f)
                )
            }
    )
}

/**
 * Apply filter to bitmap
 */
private fun applyFilterToBitmap(bitmap: Bitmap, filter: PhotoFilter, thumbnailSize: Int = 0): Bitmap {
    val matrix = when (filter) {
        PhotoFilter.ORIGINAL -> ColorMatrix()
        PhotoFilter.BLACK_AND_WHITE -> ColorMatrix().apply {
            setSaturation(0f)
        }
        PhotoFilter.SEPIA -> ColorMatrix().apply {
            set(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        PhotoFilter.NEGATIVE -> ColorMatrix().apply {
            // Invert/negative effect
            set(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        PhotoFilter.HIGH_CONTRAST -> ColorMatrix().apply {
            set(
                floatArrayOf(
                    2f, 0f, 0f, 0f, -50f,
                    0f, 2f, 0f, 0f, -50f,
                    0f, 0f, 2f, 0f, -50f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        PhotoFilter.WARM -> ColorMatrix().apply {
            // Advanced warm filter with golden hour feel
            val warmth = 1.3f
            val redShift = 30f
            val greenShift = 15f
            val blueShift = -20f
            set(
                floatArrayOf(
                    warmth, 0.1f, 0f, 0f, redShift,
                    0.1f, 1.05f, 0.05f, 0f, greenShift,
                    0f, 0.1f, 0.85f, 0f, blueShift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            // Add slight contrast boost
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, -5f,
                        0f, 1.1f, 0f, 0f, -5f,
                        0f, 0f, 1.1f, 0f, -5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        PhotoFilter.COOL -> ColorMatrix().apply {
            // Advanced cool filter with icy blue tones
            val cool = 0.75f
            val redShift = -15f
            val greenShift = 5f
            val blueShift = 35f
            set(
                floatArrayOf(
                    cool, 0.05f, 0.1f, 0f, redShift,
                    0.1f, 1.0f, 0.1f, 0f, greenShift,
                    0.05f, 0.15f, 1.15f, 0f, blueShift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            // Slight contrast adjustment
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.05f, 0f, 0f, 0f, -3f,
                        0f, 1.05f, 0f, 0f, -3f,
                        0f, 0f, 1.05f, 0f, -3f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        PhotoFilter.VINTAGE -> ColorMatrix().apply {
            // Advanced vintage with film-like characteristics
            // Cross-processing effect
            set(
                floatArrayOf(
                    1.1f, 0.15f, -0.05f, 0f, 10f,
                    0.05f, 1.0f, 0.05f, 0f, 5f,
                    0.05f, -0.05f, 0.9f, 0f, 10f,
                    0f, 0f, 0f, 0.95f, 0f
                )
            )
            // Reduce saturation
            postConcat(ColorMatrix().apply { setSaturation(0.65f) })
            // Add warm tone overlay
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.05f, 0f, 0f, 0f, 8f,
                        0f, 1.02f, 0f, 0f, 4f,
                        0f, 0f, 0.95f, 0f, -2f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
            // Slight fade (lift blacks)
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, 25f,
                        0f, 0.9f, 0f, 0f, 25f,
                        0f, 0f, 0.9f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        PhotoFilter.POLAROID -> ColorMatrix().apply {
            // Polaroid effect - bright, slightly washed out, warm
            set(
                floatArrayOf(
                    1.2f, 0.1f, 0f, 0f, 20f,
                    0.05f, 1.15f, 0.05f, 0f, 10f,
                    0f, 0.1f, 1.1f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            // Reduce saturation slightly
            postConcat(ColorMatrix().apply { setSaturation(0.85f) })
            // Slight contrast reduction
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, 15f,
                        0f, 0.9f, 0f, 0f, 15f,
                        0f, 0f, 0.9f, 0f, 15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        PhotoFilter.LOMO -> ColorMatrix().apply {
            // LOMO filter - high contrast, saturated, dark vignette effect
            setSaturation(1.3f)
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, -20f,
                        0f, 1.2f, 0f, 0f, -20f,
                        0f, 0f, 1.2f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        PhotoFilter._1977 -> ColorMatrix().apply {
            // 1977 filter - vintage pink tint, desaturated, warm
            setSaturation(0.7f)
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.1f, 0.05f, 0.05f, 0f, 15f,
                        0.05f, 1.0f, 0.05f, 0f, 10f,
                        0.05f, 0.05f, 0.9f, 0f, 5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, 20f,
                        0f, 0.85f, 0f, 0f, 15f,
                        0f, 0f, 0.8f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        // NEW FILTERS
        PhotoFilter.RETRO -> ColorMatrix().apply {
            // 70s retro look with orange/teal shift
            set(
                floatArrayOf(
                    1.15f, 0.1f, -0.05f, 0f, 15f,
                    0.05f, 1.05f, 0.05f, 0f, 10f,
                    -0.05f, 0.1f, 1.1f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            // Boost contrast
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, -15f,
                        0f, 1.2f, 0f, 0f, -15f,
                        0f, 0f, 1.2f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
            // Slight saturation boost
            postConcat(ColorMatrix().apply { setSaturation(1.3f) })
        }
        PhotoFilter.NOIR -> ColorMatrix().apply {
            // Film noir - dramatic B&W with high contrast
            setSaturation(0f) // B&W
            // High contrast with crushed blacks
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.5f, 0f, 0f, 0f, -40f,
                        0f, 1.5f, 0f, 0f, -40f,
                        0f, 0f, 1.5f, 0f, -40f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
            // Slight blue tint for noir feel
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, 5f,
                        0f, 0.9f, 0f, 0f, 8f,
                        0f, 0f, 1.05f, 0f, 15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        PhotoFilter.FADE -> ColorMatrix().apply {
            // Instagram-like fade with lifted blacks and muted colors
            // Desaturate slightly
            postConcat(ColorMatrix().apply { setSaturation(0.75f) })
            // Lift blacks (fade)
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.85f, 0f, 0f, 0f, 40f,
                        0f, 0.85f, 0f, 0f, 40f,
                        0f, 0f, 0.85f, 0f, 40f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
            // Mute colors slightly
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.95f, 0.05f, 0f, 0f, 0f,
                        0.05f, 0.95f, 0f, 0f, 0f,
                        0f, 0.05f, 0.95f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
            // Warm tint
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.03f, 0f, 0f, 0f, 10f,
                        0f, 1.01f, 0f, 0f, 5f,
                        0f, 0f, 0.98f, 0f, -5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        PhotoFilter.VIVID -> ColorMatrix().apply {
            // Vibrant colors with enhanced saturation and contrast
            // High saturation
            postConcat(ColorMatrix().apply { setSaturation(1.6f) })
            // Boost contrast
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -20f,
                        0f, 1.25f, 0f, 0f, -20f,
                        0f, 0f, 1.25f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
            // Vibrance boost (enhance less saturated colors more)
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.15f, -0.05f, -0.05f, 0f, 5f,
                        -0.05f, 1.15f, -0.05f, 0f, 5f,
                        -0.05f, -0.05f, 1.15f, 0f, 5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }
        // New filter - Color Invert (swaps RGB values)
        PhotoFilter.COLOR_INVERT -> ColorMatrix().apply {
            // Swap RGB values - different from NEGATIVE
            set(
                floatArrayOf(
                    0f, 0f, 1f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    1f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
    }

    // Resize if thumbnail (use high quality filtering)
    val targetBitmap = if (thumbnailSize > 0) {
        val scale = thumbnailSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else bitmap

    val paint = android.graphics.Paint().apply {
        colorFilter = ColorMatrixColorFilter(matrix)
        // Enable high quality filtering
        isFilterBitmap = true
    }

    val result = createBitmap(targetBitmap.width, targetBitmap.height)
    android.graphics.Canvas(result).apply {
        drawBitmap(targetBitmap, 0f, 0f, paint)
    }

    return result
}

/**
 * Load bitmap from URI at ORIGINAL size for high quality
 */
private suspend fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            // Load at ORIGINAL dimensions for high quality processing
            .size(coil3.size.Dimension.Undefined, coil3.size.Dimension.Undefined)
            .build()

        val imageLoader = ImageLoader.Builder(context).build()
        val result = imageLoader.execute(request)
        result.image?.toBitmap()
    } catch (e: Exception) {
        android.util.Log.e("FilterScreen", "Failed to load bitmap from URI", e)
        null
    }
}

/**
 * Apply filter and save to temp file
 */
private suspend fun applyFilterAndSave(
    context: android.content.Context,
    bitmap: Bitmap,
    filter: PhotoFilter,
    cropEnabled: Boolean,
    cropFrameNormalized: Rect,
    cropScale: Float,
    cropOffsetPx: Offset,
    previewSize: IntSize
): Uri {
    return withContext(Dispatchers.IO) {
        val filteredBitmap = applyFilterToBitmap(bitmap, filter)
        val finalBitmap = if (cropEnabled) {
            cropBitmapByFrameAndTransform(
                source = filteredBitmap,
                frameRectNormalized = cropFrameNormalized,
                scale = cropScale,
                offsetPx = cropOffsetPx,
                previewSize = previewSize
            )
        } else {
            filteredBitmap
        }

        val tempFile = java.io.File.createTempFile("filtered_", ".jpg", context.cacheDir)
        java.io.FileOutputStream(tempFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }

        Uri.fromFile(tempFile)
    }
}

private fun cropBitmapByFrameAndTransform(
    source: Bitmap,
    frameRectNormalized: Rect,
    scale: Float,
    offsetPx: Offset,
    previewSize: IntSize
): Bitmap {
    if (previewSize.width <= 0 || previewSize.height <= 0) return source

    val containerW = previewSize.width.toFloat()
    val containerH = previewSize.height.toFloat()
    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()

    val baseScale = max(containerW / srcW, containerH / srcH)
    val baseW = srcW * baseScale
    val baseH = srcH * baseScale
    val baseOffsetX = (containerW - baseW) / 2f
    val baseOffsetY = (containerH - baseH) / 2f

    val cx = containerW / 2f
    val cy = containerH / 2f

    fun screenToSourceX(x: Float): Float {
        val beforeScale = ((x - offsetPx.x - cx) / scale) + cx
        return (beforeScale - baseOffsetX) / baseScale
    }

    fun screenToSourceY(y: Float): Float {
        val beforeScale = ((y - offsetPx.y - cy) / scale) + cy
        return (beforeScale - baseOffsetY) / baseScale
    }

    val leftPx = frameRectNormalized.left * containerW
    val topPx = frameRectNormalized.top * containerH
    val rightPx = frameRectNormalized.right * containerW
    val bottomPx = frameRectNormalized.bottom * containerH

    val srcLeft = screenToSourceX(leftPx).roundToInt().coerceIn(0, source.width - 1)
    val srcTop = screenToSourceY(topPx).roundToInt().coerceIn(0, source.height - 1)
    val srcRight = screenToSourceX(rightPx).roundToInt().coerceIn(srcLeft + 1, source.width)
    val srcBottom = screenToSourceY(bottomPx).roundToInt().coerceIn(srcTop + 1, source.height)

    return Bitmap.createBitmap(source, srcLeft, srcTop, srcRight - srcLeft, srcBottom - srcTop)
}

private fun clampCropOffsetToFrame(
    previewSize: IntSize,
    imageSize: IntSize,
    frameNormalized: Rect,
    scale: Float,
    offset: Offset
): Offset {
    if (previewSize.width == 0 || previewSize.height == 0 || imageSize.width == 0 || imageSize.height == 0) {
        return offset
    }

    val containerW = previewSize.width.toFloat()
    val containerH = previewSize.height.toFloat()
    val srcW = imageSize.width.toFloat()
    val srcH = imageSize.height.toFloat()

    val baseScale = max(containerW / srcW, containerH / srcH)
    val baseW = srcW * baseScale
    val baseH = srcH * baseScale

    val cx = containerW / 2f
    val cy = containerH / 2f
    val baseOffsetX = (containerW - baseW) / 2f
    val baseOffsetY = (containerH - baseH) / 2f

    val transformedBaseLeft = cx + (baseOffsetX - cx) * scale
    val transformedBaseTop = cy + (baseOffsetY - cy) * scale
    val transformedW = baseW * scale
    val transformedH = baseH * scale

    val frameLeft = frameNormalized.left * containerW
    val frameTop = frameNormalized.top * containerH
    val frameRight = frameNormalized.right * containerW
    val frameBottom = frameNormalized.bottom * containerH

    val minX = frameRight - transformedBaseLeft - transformedW
    val maxX = frameLeft - transformedBaseLeft
    val minY = frameBottom - transformedBaseTop - transformedH
    val maxY = frameTop - transformedBaseTop

    val clampedX = if (minX > maxX) (minX + maxX) / 2f else offset.x.coerceIn(minX, maxX)
    val clampedY = if (minY > maxY) (minY + maxY) / 2f else offset.y.coerceIn(minY, maxY)

    return Offset(clampedX, clampedY)
}
