package com.picflick.app.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Upload loading state
    var isUploading by remember { mutableStateOf(false) }
    
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
        PhotoFilter.VINTAGE,
        // NEW FILTERS
        PhotoFilter.RETRO,
        PhotoFilter.NOIR,
        PhotoFilter.FADE,
        PhotoFilter.VIVID
    )
    
    // Calculate remaining uploads
    val remainingUploads = maxDailyUploads - dailyUploadCount
    val canUpload = remainingUploads > 0

    // Upload function with loading state
    fun triggerUpload() {
        if (canUpload && !isUploading && bitmap != null) {
            isUploading = true
            scope.launch {
                try {
                    val filteredUri = applyFilterAndSave(context, bitmap!!, selectedFilter)
                    onUpload(filteredUri, selectedFilter, taggedFriends.map { it.uid }, description.trim())
                    // Show success toast
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, context.getString(R.string.upload_complete), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, context.getString(R.string.upload_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isUploading = false
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    // Centered countdown box
                    val remainingUploads = maxDailyUploads - dailyUploadCount
                    val isLimitReached = remainingUploads <= 0
                    
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isLimitReached) Color.Red else if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFF87CEEB),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isLimitReached) {
                                    "SEE YOU TOMORROW!"
                                } else {
                                    "$remainingUploads PHOTOS REMAINING TODAY"
                                },
                                color = if (isDarkMode) Color.White else Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    // Camera icon (revert to camera)
                    IconButton(
                        onClick = onNavigateToCamera,
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (isDarkMode) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.filter_back_camera),
                            tint = if (isDarkMode) Color.White else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    val canUpload = (maxDailyUploads - dailyUploadCount) > 0
                    
                    // Upload tick button
                    IconButton(
                        onClick = { triggerUpload() },
                        enabled = !isLoading && bitmap != null && canUpload && !isUploading,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (canUpload && !isLoading && bitmap != null) {
                                    if (isDarkMode) Color(0xFF1565C0) else Color(0xFFD7ECFF)
                                } else Color.DarkGray,
                                CircleShape
                            )
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = if (isDarkMode) Color.White else Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Upload",
                                tint = if (canUpload && !isLoading && bitmap != null) {
                                    if (isDarkMode) Color.White else Color.Black
                                } else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) Color.Black else Color(0xFF1565C0)
                )
            )
        },
        containerColor = if (isDarkMode) Color.Black else Color(0xFFD7ECFF)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color.Black else Color(0xFFD7ECFF))
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Uploading Overlay (when uploading)
                        if (isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDarkMode) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)),
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
                            // Show currently selected filter as large preview
                            val previewBitmap = remember(bmp, selectedFilter) {
                                applyFilterToBitmap(bmp, selectedFilter, thumbnailSize = 1024) // High quality preview
                            }
                            Image(
                                painter = BitmapPainter(previewBitmap.asImageBitmap()),
                                contentDescription = selectedFilter.displayName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(3.dp, if (isDarkMode) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                    .padding(3.dp),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Bottom Panel with BIG filter thumbnails
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDarkMode) Color.Black else Color(0xFFE3F2FD))
                                .padding(vertical = 16.dp)
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
                                    color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (friends.isEmpty()) "No friends to tag" else "Tag Friends (${taggedFriends.size})",
                                    color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                                )
                            }
                            
                            // Description/Caption Input Field
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                placeholder = { Text(stringResource(R.string.filter_caption_placeholder), color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = if (isDarkMode) Color.White else Color.Black,
                                    unfocusedTextColor = if (isDarkMode) Color.White else Color.Black,
                                    focusedBorderColor = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0),
                                    unfocusedBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                                    focusedContainerColor = if (isDarkMode) Color(0xFF2C2C2E) else Color.White,
                                    unfocusedContainerColor = if (isDarkMode) Color(0xFF2C2C2E) else Color.White
                                ),
                                maxLines = 2,
                                singleLine = false
                            )
                            
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
                .background(if (isDarkMode) Color(0xFF2C2C2E) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                // Show actual filtered thumbnail with MUCH higher quality
                val thumbnailBitmap = remember(bitmap, filter) {
                    applyFilterToBitmap(bitmap, filter, thumbnailSize = 300)  // Higher quality filter icons
                }
                Image(
                    painter = BitmapPainter(thumbnailBitmap.asImageBitmap()),
                    contentDescription = filter.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    // Use high quality filtering
                    alignment = Alignment.Center
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
