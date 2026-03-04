package com.example.picflick.ui.screens

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.picflick.data.PhotoFilter
import com.example.picflick.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    maxDailyUploads: Int = 5,
    onBack: () -> Unit,
    onUpload: (Uri, PhotoFilter, List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(PhotoFilter.ORIGINAL) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Friend tagging state
    var taggedFriends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var showFriendPicker by remember { mutableStateOf(false) }
    
    // Countdown animation
    var showCountdown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(5) }
    
    // Load the bitmap
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
        PhotoFilter.HIGH_CONTRAST,
        PhotoFilter.WARM,
        PhotoFilter.COOL,
        PhotoFilter.VINTAGE
    )
    
    // Calculate remaining uploads
    val remainingUploads = maxDailyUploads - dailyUploadCount
    val canUpload = remainingUploads > 0

    // Countdown effect
    LaunchedEffect(showCountdown) {
        if (showCountdown) {
            countdownValue = 5
            while (countdownValue > 0) {
                kotlinx.coroutines.delay(1000)
                countdownValue--
            }
            // Countdown done, trigger upload
            showCountdown = false
            bitmap?.let { bmp ->
                scope.launch {
                    val filteredUri = applyFilterAndSave(context, bmp, selectedFilter)
                    onUpload(filteredUri, selectedFilter, taggedFriends.map { it.uid })
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Photo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Upload count indicator
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                if (canUpload) Color(0xFF00D09C) else Color.Red,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$remainingUploads/$maxDailyUploads",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Upload button
                    IconButton(
                        onClick = {
                            if (canUpload) {
                                showCountdown = true
                            }
                        },
                        enabled = !isLoading && bitmap != null && canUpload && !showCountdown
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Upload",
                            tint = when {
                                !canUpload -> Color.Red
                                isLoading || bitmap == null -> Color.Gray
                                showCountdown -> Color(0xFF00D09C)
                                else -> Color(0xFF00D09C)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else {
                bitmap?.let { bmp ->
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Main Photo Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                            FilteredImage(
                                bitmap = bmp,
                                filter = selectedFilter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            
                            // Countdown Overlay
                            if (showCountdown) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = countdownValue.toString(),
                                        color = Color.White,
                                        fontSize = 72.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Bottom Panel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C1C1E))
                                .padding(vertical = 12.dp)
                        ) {
                            // Filter Icons (simplified - no text)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                items(filters) { filter ->
                                    FilterIcon(
                                        filter = filter,
                                        isSelected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter }
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
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Tag Friends Button
                            TextButton(
                                onClick = { showFriendPicker = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                enabled = friends.isNotEmpty()
                            ) {
                                Text(
                                    text = "+",
                                    color = Color(0xFF00D09C),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (friends.isEmpty()) "No friends to tag" else "Tag Friends (${taggedFriends.size})",
                                    color = Color(0xFF00D09C)
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
            }
        )
    }
}

@Composable
private fun FilterIcon(
    filter: PhotoFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) Color(0xFF00D09C) else Color(0xFF2C2C2E)
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFF00D09C) else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = filter.icon,
            fontSize = 28.sp
        )
    }
}

@Composable
private fun TaggedFriendChip(
    friend: UserProfile,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF00D09C))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = friend.displayName.take(15),
                color = Color.Black,
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
                    tint = Color.Black,
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
    onFriendSelected: (UserProfile) -> Unit
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
                    Text(
                        text = "No friends yet",
                        color = Color.Gray
                    )
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
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = friend.displayName.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = friend.displayName,
            color = Color.White,
            fontSize = 16.sp
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
            set(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1f, 0f, 0f, 10f,
                    0f, 0f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        PhotoFilter.COOL -> ColorMatrix().apply {
            set(
                floatArrayOf(
                    0.8f, 0f, 0f, 0f, -10f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        PhotoFilter.VINTAGE -> ColorMatrix().apply {
            set(
                floatArrayOf(
                    0.9f, 0.1f, 0f, 0f, 0f,
                    0.1f, 0.9f, 0f, 0f, 0f,
                    0f, 0.1f, 0.9f, 0f, 0f,
                    0f, 0f, 0f, 0.9f, 0f
                )
            )
            postConcat(ColorMatrix().apply { setSaturation(0.6f) })
        }
    }

    // Resize if thumbnail
    val targetBitmap = if (thumbnailSize > 0) {
        val scale = thumbnailSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else bitmap

    val paint = android.graphics.Paint().apply {
        colorFilter = ColorMatrixColorFilter(matrix)
    }

    val result = createBitmap(targetBitmap.width, targetBitmap.height)
    android.graphics.Canvas(result).apply {
        drawBitmap(targetBitmap, 0f, 0f, paint)
    }

    return result
}

/**
 * Load bitmap from URI
 */
private suspend fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .build()

        val imageLoader = ImageLoader.Builder(context).build()
        val result = imageLoader.execute(request)
        result.image?.toBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Apply filter and save to temp file
 */
private suspend fun applyFilterAndSave(
    context: android.content.Context,
    bitmap: Bitmap,
    filter: PhotoFilter
): Uri {
    return withContext(Dispatchers.IO) {
        val filteredBitmap = applyFilterToBitmap(bitmap, filter)

        // Save to temp file
        val tempFile = java.io.File.createTempFile("filtered_", ".jpg", context.cacheDir)
        java.io.FileOutputStream(tempFile).use { out ->
            filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        Uri.fromFile(tempFile)
    }
}
