package com.picflick.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.picflick.app.data.Flick
import com.picflick.app.data.PhotoFilter
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.PicFlickLightBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for editing previously uploaded photos
 * Similar to FilterScreen but without tag friends and daily upload counter
 * Changes do not count toward daily limits
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoScreen(
    flick: Flick,
    _currentUser: UserProfile,
    _cloudName: String = "",
    onBack: () -> Unit,
    onSave: (Flick, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode = ThemeManager.isDarkMode.value
    
    // Photo editing state
    var selectedFilter by remember { mutableStateOf(PhotoFilter.ORIGINAL) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Caption/description state - start with existing description
    var description by remember { mutableStateOf(flick.description) }
    
    // Load the existing photo from URL
    LaunchedEffect(flick.imageUrl) {
        scope.launch(Dispatchers.IO) {
            try {
                val loadedBitmap = loadBitmapFromUrl(context, flick.imageUrl)
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
                    onSave(flick, filterTransformation, description.trim())
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, 
                            "Photo updated!", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
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
                        modifier = Modifier.fillMaxSize()
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
                                .weight(1.2f)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewBitmap = remember(bmp, selectedFilter) {
                                applyFilterToBitmap(bmp, selectedFilter, thumbnailSize = 0)
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

                        // Bottom Panel with filter thumbnails and caption
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(if (isDarkMode) Color(0xFF1C1C1E) else PicFlickLightBackground)
                                .padding(vertical = 8.dp)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            // Filter thumbnails - 2 rows with horizontal scrolling
                            val row1Filters = localFilters.take(8)
                            val row2Filters = localFilters.drop(8)
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                // First row - first 8 filters
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    items(row1Filters) { filter ->
                                        EditFilterIcon(
                                            filter = filter,
                                            isSelected = selectedFilter == filter,
                                            onClick = { 
                                                selectedFilter = filter
                                            },
                                            bitmap = bmp,
                                            isDarkMode = isDarkMode
                                        )
                                    }
                                }
                                
                                // Second row - remaining filters (scrollable)
                                if (row2Filters.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        items(row2Filters) { filter ->
                                            EditFilterIcon(
                                                filter = filter,
                                                isSelected = selectedFilter == filter,
                                                onClick = { 
                                                    selectedFilter = filter
                                                },
                                                bitmap = bmp,
                                                isDarkMode = isDarkMode
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Description/Caption Input Field
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
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                maxLines = 3,
                                singleLine = false
                            )
                        }
                    }
                }
            }
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
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
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
