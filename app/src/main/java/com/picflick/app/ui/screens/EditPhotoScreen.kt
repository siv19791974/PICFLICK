package com.picflick.app.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.net.Uri
import android.graphics.ColorMatrixColorFilter
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.picflick.app.data.Flick
import com.picflick.app.data.PhotoFilter
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.data.UserProfile
import com.yalantis.ucrop.UCrop
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.PicFlickLightBackground
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Screen for editing previously uploaded photos
 * Similar to FilterScreen but without tag friends and daily upload counter
 * Changes do not count toward daily limits
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoScreen(
    flick: Flick,
    currentUser: UserProfile,
    onBack: () -> Unit,
    onSave: (Flick, String, String, List<String>, Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode = ThemeManager.isDarkMode.value
    
    // Photo editing state
    var selectedFilter by remember { mutableStateOf(PhotoFilter.ORIGINAL) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(flick.description) }
    var taggedFriendIds by remember { mutableStateOf(flick.taggedFriends) }
    var showTagDialog by remember { mutableStateOf(false) }
    var followingUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isCropMode by remember { mutableStateOf(false) }
    var cropApplied by remember { mutableStateOf(false) }
    var cropScale by remember { mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropFrameRect by remember { mutableStateOf(Rect(0.1f, 0.1f, 0.9f, 0.9f)) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outputUri = result.data?.let { UCrop.getOutput(it) }
            if (outputUri != null) {
                scope.launch {
                    val cropped = withContext(Dispatchers.IO) { loadBitmapFromUri(context, outputUri) }
                    if (cropped != null) {
                        bitmap = cropped
                        cropApplied = true
                        isCropMode = false
                    }
                }
            }
        }
    }

    // Load the existing photo from URL
    LaunchedEffect(flick.imageUrl) {
        scope.launch(Dispatchers.IO) {
            try {
                val loadedBitmap = loadBitmapFromUrl(context, flick.imageUrl)
                withContext(Dispatchers.Main) {
                    bitmap = loadedBitmap
                    isLoading = false
                }
            } catch (_: Exception) {
withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    // Load following users for Tag Friends
    LaunchedEffect(currentUser.uid, currentUser.following) {
        if (currentUser.following.isEmpty()) {
            followingUsers = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val users = mutableListOf<UserProfile>()
            val db = FirebaseFirestore.getInstance()
            currentUser.following.forEach { uid ->
                try {
                    val doc = db.collection("users").document(uid).get().await()
                    doc.toObject(UserProfile::class.java)?.let { users.add(it) }
                } catch (_: Exception) {
                    // Ignore failed profile fetches
                }
            }
            withContext(Dispatchers.Main) {
                followingUsers = users
            }
        }
    }

    val taggedFriends = remember(taggedFriendIds, followingUsers) {
        followingUsers.filter { it.uid in taggedFriendIds }
    }

    // Local filters list - all 16 filters (removed non-working blur filters)
val localFilters = listOf(
        PhotoFilter.ORIGINAL,
        PhotoFilter.BLACK_AND_WHITE,
        PhotoFilter.SEPIA,
        PhotoFilter.NEGATIVE,
        PhotoFilter.HIGH_CONTRAST,
        PhotoFilter.WARM,
        PhotoFilter.COOL,
        PhotoFilter.VINTAGE,
        PhotoFilter.RETRO,
        PhotoFilter.POLAROID,
        PhotoFilter.LOMO,
        PhotoFilter._1977,
        PhotoFilter.NOIR,
        PhotoFilter.FADE,
        PhotoFilter.VIVID,
        PhotoFilter.COLOR_INVERT
    )
    
    // Save function
    fun triggerSave() {
        if (!isSaving && bitmap != null) {
            isSaving = true
            scope.launch {
                try {
                    val filterTransformation = selectedFilter.name
                    val finalBitmap = applyFilterToBitmap(bitmap!!, selectedFilter, thumbnailSize = 0)
                    onSave(flick, filterTransformation, description.trim(), taggedFriendIds, finalBitmap)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, 
                            "Photo updated!", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (_: Exception) {
withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, 
                            "Failed to update photo", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    isSaving = false
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Compact title bar with back and save
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
                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Title
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Edit Photo",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Save button
                    IconButton(
                        onClick = { triggerSave() },
                        enabled = !isLoading && bitmap != null && !isSaving,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (!isLoading && bitmap != null) {
                                        if (isDarkMode) Color(0xFF4CAF50) else Color(0xFF1565C0)
                                    } else Color.Gray,
                                    RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
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
                        // Saving Overlay
                        if (isSaving) {
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
                                        text = "Saving...",
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Main Filter Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.45f)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewBitmap = remember(bmp, selectedFilter) {
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
                                    contentScale = ContentScale.Fit
                                )

                            }
                        }

                        // Bottom Panel with filter thumbnails + metadata
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(if (isDarkMode) Color(0xFF1C1C1E) else PicFlickLightBackground)
                                .padding(vertical = 8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Filter thumbnails - single row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier
                                    .height(118.dp)
                                    .padding(bottom = 8.dp)
                            ) {
                                items(localFilters) { filter ->
                                    EditFilterIcon(
                                        filter = filter,
                                        isSelected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        bitmap = bmp,
                                        isDarkMode = isDarkMode
                                    )
                                }
                            }

                            if (taggedFriends.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    items(taggedFriends) { friend ->
                                        EditTaggedFriendChip(
                                            friend = friend,
                                            isDarkMode = isDarkMode,
                                            onRemove = {
                                                taggedFriendIds = taggedFriendIds - friend.uid
                                            }
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
                                    onClick = { showTagDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (followingUsers.isEmpty()) {
                                            "No friends"
                                        } else {
                                            "Tag Friends (${taggedFriendIds.size})"
                                        },
                                        color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        val srcBitmap = bitmap ?: return@TextButton
                                        scope.launch {
                                            val sourceUri = withContext(Dispatchers.IO) {
                                                saveBitmapToTempUri(context, srcBitmap)
                                            }
                                            val destUri = withContext(Dispatchers.IO) {
                                                createCropOutputUri(context)
                                            }
                                            val options = UCrop.Options().apply {
                                                setFreeStyleCropEnabled(true)
                                                setShowCropGrid(true)
                                                setCompressionFormat(Bitmap.CompressFormat.JPEG)
                                                setCompressionQuality(98)
                                                setToolbarTitle("Crop Photo")
                                            }
                                            val intent = UCrop.of(sourceUri, destUri)
                                                .withOptions(options)
                                                .getIntent(context)
                                                .apply {
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                                }
                                            cropLauncher.launch(intent)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (cropApplied) "Edit Crop" else "Crop Photo",
                                        color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                                placeholder = { Text("Add a description...") },
                                minLines = 2,
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    if (isCropMode) {
                        val cropBitmap = bmp
                        FullScreenCropDialog(
                            previewBitmap = cropBitmap,
                            cropScale = cropScale,
                            cropOffset = cropOffset,
                            cropFrameRect = cropFrameRect,
                            onCropFrameChange = { newFrame ->
                                val imageBounds = computeVisibleImageBoundsNormalized(
                                    previewSize = previewSize,
                                    imageSize = IntSize(cropBitmap.width, cropBitmap.height),
                                    scale = cropScale,
                                    offset = cropOffset
                                )
                                cropFrameRect = clampFrameToBounds(newFrame, imageBounds)
                                cropOffset = clampCropOffsetToFrame(
                                    previewSize = previewSize,
                                    imageSize = IntSize(cropBitmap.width, cropBitmap.height),
                                    frameNormalized = cropFrameRect,
                                    scale = cropScale,
                                    offset = cropOffset
                                )
                            },
                            onSetOrientation = { isVertical ->
                                val imageBounds = computeVisibleImageBoundsNormalized(
                                    previewSize = previewSize,
                                    imageSize = IntSize(cropBitmap.width, cropBitmap.height),
                                    scale = cropScale,
                                    offset = cropOffset
                                )
                                val targetAspect = if (isVertical) 3f / 4f else 4f / 3f
                                cropFrameRect = createAspectFrameInBounds(imageBounds, targetAspect)
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

    if (showTagDialog) {
        ModalBottomSheet(
            onDismissRequest = { showTagDialog = false },
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

                if (followingUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No friends available to tag yet.", color = Color.Gray)
                    }
                } else {
                    followingUsers.forEach { friend ->
                        val isTagged = taggedFriendIds.contains(friend.uid)
                        EditFriendPickerItem(
                            friend = friend,
                            isTagged = isTagged,
                            onClick = {
                                taggedFriendIds = if (isTagged) taggedFriendIds - friend.uid else taggedFriendIds + friend.uid
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTaggedFriendChip(
    friend: UserProfile,
    isDarkMode: Boolean,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = friend.displayName.take(15),
                color = if (isDarkMode) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
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

@Composable
private fun EditFriendPickerItem(
    friend: UserProfile,
    isTagged: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.3f)),
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
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = friend.displayName,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isTagged) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF87CEEB)
            )
        }
    }
}

@Composable
private fun EditFilterIcon(
filter: PhotoFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    bitmap: Bitmap? = null,
    isDarkMode: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .height(112.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
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
                val thumbnailBitmap = remember(bitmap, filter) {
                    applyFilterToBitmap(bitmap, filter, thumbnailSize = 768)
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
                Text(
                    text = filter.icon,
                    fontSize = 28.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = filter.displayName,
            color = if (isSelected) {
                if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
            } else {
                if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
            },
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FullScreenCropDialog(
    previewBitmap: Bitmap,
    cropScale: Float,
    cropOffset: Offset,
    cropFrameRect: Rect,
    onCropFrameChange: (Rect) -> Unit,
    onSetOrientation: (Boolean) -> Unit,
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
            var localPreviewSize by remember { mutableStateOf(IntSize.Zero) }
            val imageBounds = remember(localPreviewSize, previewBitmap.width, previewBitmap.height, cropScale, cropOffset) {
                computeVisibleImageBoundsNormalized(
                    previewSize = localPreviewSize,
                    imageSize = IntSize(previewBitmap.width, previewBitmap.height),
                    scale = cropScale,
                    offset = cropOffset
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 16.dp)
                    .onSizeChanged {
                        localPreviewSize = it
                        onPreviewSizeChange(it)
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
                    contentScale = ContentScale.Fit
                )

                CropOverlay(
                    frameRect = clampFrameToBounds(cropFrameRect, imageBounds),
                    boundsRect = imageBounds,
                    overlayColor = Color.Black.copy(alpha = 0.52f),
                    borderColor = Color.White,
                    onFrameRectChange = onCropFrameChange
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { onSetOrientation(true) }) {
                    Text("Vertical")
                }
                OutlinedButton(onClick = { onSetOrientation(false) }) {
                    Text("Horizontal")
                }
                Button(onClick = onDone) {
                    Text("Done Crop")
                }
            }
        }
    }
}

@Composable
private fun CropOverlay(
    frameRect: Rect,
    boundsRect: Rect,
    overlayColor: Color,
    borderColor: Color,
    onFrameRectChange: (Rect) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(boundsRect) {
                var startFrame = frameRect
                var accumDx = 0f
                var accumDy = 0f
                detectDragGestures(
                    onDragStart = {
                        startFrame = clampFrameToBounds(frameRect, boundsRect)
                        accumDx = 0f
                        accumDy = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumDx += dragAmount.x / size.width
                        accumDy += dragAmount.y / size.height
                        val width = startFrame.right - startFrame.left
                        val height = startFrame.bottom - startFrame.top

                        val newLeft = (startFrame.left + accumDx).coerceIn(boundsRect.left, boundsRect.right - width)
                        val newTop = (startFrame.top + accumDy).coerceIn(boundsRect.top, boundsRect.bottom - height)
                        onFrameRectChange(Rect(newLeft, newTop, newLeft + width, newTop + height))
                    }
                )
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

private fun computeVisibleImageBoundsNormalized(
    previewSize: IntSize,
    imageSize: IntSize,
    scale: Float,
    offset: Offset
): Rect {
    if (previewSize.width <= 0 || previewSize.height <= 0 || imageSize.width <= 0 || imageSize.height <= 0) {
        return Rect(0f, 0f, 1f, 1f)
    }

    val containerW = previewSize.width.toFloat()
    val containerH = previewSize.height.toFloat()
    val srcW = imageSize.width.toFloat()
    val srcH = imageSize.height.toFloat()

    val baseScale = min(containerW / srcW, containerH / srcH)
    val baseW = srcW * baseScale
    val baseH = srcH * baseScale
    val baseOffsetX = (containerW - baseW) / 2f
    val baseOffsetY = (containerH - baseH) / 2f
    val cx = containerW / 2f
    val cy = containerH / 2f

    val left = cx + (baseOffsetX - cx) * scale + offset.x
    val top = cy + (baseOffsetY - cy) * scale + offset.y
    val width = baseW * scale
    val height = baseH * scale

    return Rect(
        left = (left / containerW).coerceIn(0f, 1f),
        top = (top / containerH).coerceIn(0f, 1f),
        right = ((left + width) / containerW).coerceIn(0f, 1f),
        bottom = ((top + height) / containerH).coerceIn(0f, 1f)
    )
}

private fun clampFrameToBounds(frame: Rect, bounds: Rect, minSize: Float = 0.18f): Rect {
    val boundsWidth = (bounds.right - bounds.left).coerceAtLeast(minSize)
    val boundsHeight = (bounds.bottom - bounds.top).coerceAtLeast(minSize)
    val width = (frame.right - frame.left).coerceAtMost(boundsWidth)
    val height = (frame.bottom - frame.top).coerceAtMost(boundsHeight)

    val left = frame.left.coerceIn(bounds.left, bounds.right - width)
    val top = frame.top.coerceIn(bounds.top, bounds.bottom - height)
    return Rect(left, top, left + width, top + height)
}

private fun createAspectFrameInBounds(bounds: Rect, targetAspect: Float): Rect {
    val boundsWidth = bounds.right - bounds.left
    val boundsHeight = bounds.bottom - bounds.top
    val boundsAspect = boundsWidth / boundsHeight

    val (frameW, frameH) = if (boundsAspect > targetAspect) {
        val h = boundsHeight * 0.92f
        Pair(h * targetAspect, h)
    } else {
        val w = boundsWidth * 0.92f
        Pair(w, w / targetAspect)
    }

    val left = bounds.left + (boundsWidth - frameW) / 2f
    val top = bounds.top + (boundsHeight - frameH) / 2f
    return Rect(left, top, left + frameW, top + frameH)
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

    val baseScale = min(containerW / srcW, containerH / srcH)
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

/**
 * Apply filter to bitmap - same implementation as FilterScreen
 */
private fun applyFilterToBitmap(bitmap: Bitmap, filter: PhotoFilter, thumbnailSize: Int = 0): Bitmap {
    val matrix = when (filter) {
        PhotoFilter.ORIGINAL -> ColorMatrix()
        PhotoFilter.BLACK_AND_WHITE -> ColorMatrix().apply { setSaturation(0f) }
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
            set(
                floatArrayOf(
                    1.1f, 0.15f, -0.05f, 0f, 10f,
                    0.05f, 1.0f, 0.05f, 0f, 5f,
                    0.05f, -0.05f, 0.9f, 0f, 10f,
                    0f, 0f, 0f, 0.95f, 0f
                )
            )
            postConcat(ColorMatrix().apply { setSaturation(0.65f) })
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
        PhotoFilter.RETRO -> ColorMatrix().apply {
            set(
                floatArrayOf(
                    1.15f, 0.1f, -0.05f, 0f, 15f,
                    0.05f, 1.05f, 0.05f, 0f, 10f,
                    -0.05f, 0.1f, 1.1f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
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
            postConcat(ColorMatrix().apply { setSaturation(1.3f) })
        }
        PhotoFilter.NOIR -> ColorMatrix().apply {
            setSaturation(0f)
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
            postConcat(ColorMatrix().apply { setSaturation(0.75f) })
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
            postConcat(ColorMatrix().apply { setSaturation(1.6f) })
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
        PhotoFilter.NEGATIVE -> ColorMatrix().apply {
            set(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        PhotoFilter.POLAROID -> ColorMatrix().apply {
            set(
                floatArrayOf(
                    1.2f, 0.1f, 0f, 0f, 20f,
                    0.05f, 1.15f, 0.05f, 0f, 10f,
                    0f, 0.1f, 1.1f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            postConcat(ColorMatrix().apply { setSaturation(0.85f) })
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
        PhotoFilter.COLOR_INVERT -> ColorMatrix().apply {
            // Different from NEGATIVE - this swaps RGB values
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

    val targetBitmap = if (thumbnailSize > 0) {
        val scale = thumbnailSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else bitmap

    val paint = android.graphics.Paint().apply {
        colorFilter = ColorMatrixColorFilter(matrix)
        isFilterBitmap = true
    }

    val result = createBitmap(targetBitmap.width, targetBitmap.height)
    android.graphics.Canvas(result).apply {
        drawBitmap(targetBitmap, 0f, 0f, paint)
    }

    return result
}

/**
 * Load bitmap from URL (for existing photos)
 */
private suspend fun loadBitmapFromUrl(context: android.content.Context, url: String): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(coil3.size.Dimension.Undefined, coil3.size.Dimension.Undefined)
            .build()

        val imageLoader = ImageLoader.Builder(context).build()
        val result = imageLoader.execute(request)
        result.image?.toBitmap()
    } catch (e: Exception) {
        android.util.Log.e("EditPhotoScreen", "Failed to load bitmap from URL", e)
        null
    }
}

private suspend fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .size(coil3.size.Dimension.Undefined, coil3.size.Dimension.Undefined)
            .build()

        val imageLoader = ImageLoader.Builder(context).build()
        val result = imageLoader.execute(request)
        result.image?.toBitmap()
    } catch (e: Exception) {
        android.util.Log.e("EditPhotoScreen", "Failed to load bitmap from URI", e)
        null
    }
}

private fun saveBitmapToTempUri(context: android.content.Context, bitmap: Bitmap): Uri {
    val tempFile = File.createTempFile("crop_src", ".jpg", context.cacheDir)
    java.io.FileOutputStream(tempFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}

private fun createCropOutputUri(context: android.content.Context): Uri {
    val outputFile = File(context.cacheDir, "crop_out_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
}
