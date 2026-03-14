package com.picflick.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.picflick.app.data.*
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Professional Filter Screen using GPUImage
 * High-quality GPU-accelerated photo filters
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalFilterScreen(
    photoUri: Uri,
    currentUser: UserProfile,
    friends: List<UserProfile>,
    dailyUploadCount: Int,
    onBack: () -> Unit,
    onUpload: (Uri, String, List<String>, String) -> Unit, // Returns URI, filter name, tagged friends, description
    onNavigateToFindFriends: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(ProfessionalFilter.ORIGINAL) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val isDarkMode = ThemeManager.isDarkMode.value
    
    val maxDailyUploads = currentUser.subscriptionTier.getDailyUploadLimit()
    var taggedFriends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var showFriendPicker by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var showCountdownAnimation by remember { mutableStateOf(false) }
    
    // Load the bitmap
    LaunchedEffect(photoUri) {
        scope.launch(Dispatchers.IO) {
            try {
                val loadedBitmap = loadBitmapFromUriPro(context, photoUri)
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

    val filtersByCategory = remember {
        FilterCategory.values().associateWith { category ->
            ProfessionalFilter.getFiltersByCategory(category)
        }
    }
    
    var selectedCategory by remember { mutableStateOf(FilterCategory.BASIC) }
    
    val remainingUploads = maxDailyUploads - dailyUploadCount
    val canUpload = remainingUploads > 0

    fun triggerUpload() {
        if (canUpload && !isUploading && bitmap != null) {
            showCountdownAnimation = true
            isUploading = true
            scope.launch {
                try {
                    // Apply filter and save
                    val filteredBitmap = applyProFilter(bitmap!!, selectedFilter, context)
                    val filteredUri = saveBitmapToTemp(context, filteredBitmap)
                    onUpload(
                        filteredUri,
                        selectedFilter.name,
                        taggedFriends.map { it.uid },
                        description.trim()
                    )
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, "Upload complete!", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, "Upload failed", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    isUploading = false
                    kotlinx.coroutines.delay(1000)
                    showCountdownAnimation = false
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
                    IconButton(
                        onClick = onNavigateToCamera,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    val remainingUploads = maxDailyUploads - dailyUploadCount
                    val isLimitReached = remainingUploads <= 0
                    
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val backgroundColor by animateColorAsState(
                            targetValue = when {
                                showCountdownAnimation -> Color(0xFF00C853)
                                isLimitReached -> Color.Red
                                isDarkMode -> Color(0xFF2C2C2E)
                                else -> Color.White
                            },
                            animationSpec = tween(300),
                            label = "backgroundColor"
                        )
                        
                        val textColor by animateColorAsState(
                            targetValue = when {
                                showCountdownAnimation -> Color.White
                                isLimitReached -> Color.White
                                isDarkMode -> Color.White
                                else -> Color(0xFF1565C0)
                            },
                            animationSpec = tween(300),
                            label = "textColor"
                        )
                        
                        Box(
                            modifier = Modifier
                                .background(backgroundColor, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            AnimatedContent(
                                targetState = if (showCountdownAnimation) remainingUploads + 1 else remainingUploads,
                                transitionSpec = {
                                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> -height } + fadeOut())
                                },
                                label = "countdownAnimation"
                            ) { targetCount ->
                                Text(
                                    text = if (isLimitReached) {
                                        "SEE YOU TOMORROW!"
                                    } else {
                                        val count = if (showCountdownAnimation && targetCount > remainingUploads) remainingUploads else targetCount
                                        if (showCountdownAnimation) "✓ $count PHOTOS LEFT" else "$count PHOTOS LEFT"
                                    },
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
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
                        modifier = Modifier.fillMaxSize()
                    ) {
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

                        // Main Filter Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.2f)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewBitmap = remember(bmp, selectedFilter) {
                                applyProFilter(bmp, selectedFilter, context)
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

                        // Bottom Panel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(if (isDarkMode) Color(0xFF1C1C1E) else Color.White)
                                .padding(vertical = 8.dp)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            // Category tabs
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                items(FilterCategory.values().toList()) { category ->
                                    CategoryTab(
                                        category = category,
                                        isSelected = selectedCategory == category,
                                        onClick = { selectedCategory = category },
                                        isDarkMode = isDarkMode
                                    )
                                }
                            }
                            
                            // Filter thumbnails
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                items(filtersByCategory[selectedCategory] ?: emptyList()) { filter ->
                                    ProFilterIcon(
                                        filter = filter,
                                        isSelected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        bitmap = bmp,
                                        context = context,
                                        isDarkMode = isDarkMode
                                    )
                                }
                            }
                            
                            // Tagged friends
                            if (taggedFriends.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    items(taggedFriends) { friend ->
                                        TaggedFriendChipPro(
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
                                onClick = { 
                                    if (friends.isEmpty()) {
                                        onNavigateToFindFriends()
                                    } else {
                                        showFriendPicker = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "+",
                                    color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (friends.isEmpty()) "Find Friends to Tag" else "Tag Friends (${taggedFriends.size})",
                                    color = if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                                )
                            }
                            
                            // Description input
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
                            ) {
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    placeholder = { 
                                        Text(
                                            "Add a caption...",
                                            color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                                        ) 
                                    },
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
                            }
                            
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
    
    if (showFriendPicker) {
        FriendPickerDialogPro(
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
private fun CategoryTab(
    category: FilterCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDarkMode: Boolean
) {
    val name = when (category) {
        FilterCategory.BASIC -> "Basic"
        FilterCategory.ADJUSTMENT -> "Adjust"
        FilterCategory.COLOR -> "Color"
        FilterCategory.ARTISTIC -> "Art"
        FilterCategory.EFFECTS -> "FX"
        FilterCategory.BLEND -> "Blend"
    }
    
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                if (isSelected) {
                    if (isDarkMode) Color(0xFF87CEEB) else Color(0xFF1565C0)
                } else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            color = if (isSelected) {
                if (isDarkMode) Color.Black else Color.White
            } else {
                if (isDarkMode) Color.White else Color.Black
            },
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ProFilterIcon(
    filter: ProfessionalFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    bitmap: Bitmap? = null,
    context: Context,
    isDarkMode: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
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
            if (bitmap != null && filter != ProfessionalFilter.ORIGINAL) {
                val thumbnailBitmap = remember(bitmap, filter) {
                    applyProFilter(bitmap, filter, context, thumbnailSize = 256)
                }
                Image(
                    painter = BitmapPainter(thumbnailBitmap.asImageBitmap()),
                    contentDescription = filter.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
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
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TaggedFriendChipPro(
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
private fun FriendPickerDialogPro(
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
                            Text("Find Friends")
                        }
                    }
                }
            } else {
                friends.filter { it.uid !in alreadyTagged }.forEach { friend ->
                    FriendPickerItemPro(
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
private fun FriendPickerItemPro(
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
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.4f))
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (friend.photoUrl.isNotBlank()) {
                androidx.compose.foundation.Image(
                    painter = coil3.compose.rememberAsyncImagePainter(friend.photoUrl),
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

// Helper functions
private fun loadBitmapFromUriPro(context: Context, uri: Uri): Bitmap? {
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
        null
    }
}

private fun applyProFilter(
    bitmap: Bitmap,
    filter: ProfessionalFilter,
    context: Context,
    thumbnailSize: Int = 0
): Bitmap {
    val gpuImage = GPUImage(context)
    
    val targetBitmap = if (thumbnailSize > 0) {
        val scale = thumbnailSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else bitmap
    
    gpuImage.setImage(targetBitmap)
    gpuImage.setFilter(createGPUFilter(filter))
    return gpuImage.bitmapWithFilterApplied
}

private fun createGPUFilter(filter: ProfessionalFilter): GPUImageFilter {
    return when (filter) {
        ProfessionalFilter.ORIGINAL -> GPUImageFilter()
        
        // Basic
        ProfessionalFilter.AUTO_ENHANCE -> GPUImageFilterGroup(listOf(
            GPUImageBrightnessFilter(0.1f),
            GPUImageContrastFilter(1.2f),
            GPUImageSaturationFilter(1.15f)
        ))
        ProfessionalFilter.BRIGHTNESS -> GPUImageBrightnessFilter(0.2f)
        ProfessionalFilter.CONTRAST -> GPUImageContrastFilter(1.4f)
        ProfessionalFilter.SATURATION -> GPUImageSaturationFilter(1.5f)
        ProfessionalFilter.WARMTH -> GPUImageWhiteBalanceFilter(5000f, 0f)
        ProfessionalFilter.COOL -> GPUImageWhiteBalanceFilter(3000f, 0f)
        
        // Color
        ProfessionalFilter.SEPIA -> GPUImageSepiaFilter()
        ProfessionalFilter.GRAYSCALE -> GPUImageGrayscaleFilter()
        ProfessionalFilter.INVERT -> GPUImageColorInvertFilter()
        ProfessionalFilter.MONOCHROME -> GPUImageMonochromeFilter()
        
        // Artistic
        ProfessionalFilter.VINTAGE -> GPUImageFilterGroup(listOf(
            GPUImageSepiaFilter(),
            GPUImageSaturationFilter(0.7f),
            GPUImageBrightnessFilter(-0.05f)
        ))
        ProfessionalFilter.RETRO -> GPUImageFilterGroup(listOf(
            GPUImageContrastFilter(1.25f),
            GPUImageSaturationFilter(1.3f)
        ))
        ProfessionalFilter.SKETCH -> GPUImageSketchFilter()
        ProfessionalFilter.TOON -> GPUImageSmoothToonFilter()
        ProfessionalFilter.POSTERIZE -> GPUImagePosterizeFilter(4)
        ProfessionalFilter.HALFTONE -> GPUImageHalftoneFilter(0.01f, 0.2f)
        
        // Effects
        ProfessionalFilter.VIGNETTE -> GPUImageVignetteFilter(
            floatArrayOf(0.5f, 0.5f),
            floatArrayOf(0.3f, 0.3f),
            0.3f,
            floatArrayOf(0.9f, 0.8f, 0.7f)
        )
        ProfessionalFilter.GAUSSIAN_BLUR -> GPUImageGaussianBlurFilter(2.0f)
        ProfessionalFilter.SHARPEN -> GPUImageSharpenFilter(1.0f)
        ProfessionalFilter.EDGE_DETECT -> GPUImageSobelEdgeDetectionFilter()
        ProfessionalFilter.EMBOSS -> GPUImageEmbossFilter(1.0f)
        ProfessionalFilter.CROSSHATCH -> GPUImageCrosshatchFilter(0.03f, 0.004f)
        
        // Blend
        ProfessionalFilter.OVERLAY -> GPUImageOverlayBlendFilter()
        ProfessionalFilter.HARD_LIGHT -> GPUImageHardLightBlendFilter()
        ProfessionalFilter.SOFT_LIGHT -> GPUImageSoftLightBlendFilter()
        ProfessionalFilter.DARKEN -> GPUImageDarkenBlendFilter()
        ProfessionalFilter.LIGHTEN -> GPUImageLightenBlendFilter()
    }
}

private fun saveBitmapToTemp(context: Context, bitmap: Bitmap): Uri {
    val tempFile = java.io.File.createTempFile("filtered_", ".jpg", context.cacheDir)
    java.io.FileOutputStream(tempFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    return Uri.fromFile(tempFile)
}
