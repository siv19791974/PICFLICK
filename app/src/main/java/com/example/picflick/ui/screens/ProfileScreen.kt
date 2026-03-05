package com.example.picflick.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.picflick.data.Flick
import com.example.picflick.data.UserProfile
import com.example.picflick.data.toEmoji
import com.example.picflick.ui.theme.PicFlickBackground

/**
 * Modern Profile screen with enhanced UI
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    photos: List<Flick>,
    photoCount: Int,
    totalReactions: Int,
    currentStreak: Int,
    onBack: () -> Unit,
    onPhotoSelected: (Uri) -> Unit = {},
    onBioUpdated: (String) -> Unit = {},
    onPhotoClick: (Flick, Int) -> Unit = { _, _ -> }
) {
    // Image picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPhotoSelected(it) }
    }
    
    // Bio edit dialog state
    var showBioDialog by remember { mutableStateOf(false) }
    var bioText by remember { mutableStateOf(userProfile.bio) }
    
    // Bio edit dialog
    if (showBioDialog) {
        AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text("Edit Bio") },
            text = {
                OutlinedTextField(
                    value = bioText,
                    onValueChange = { bioText = it },
                    label = { Text("Bio") },
                    placeholder = { Text("Tell people about yourself...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBioUpdated(bioText.trim())
                        showBioDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBioDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // NO BANNER - banner is now in MainActivity's Scaffold topBar!

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Photo with better styling
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.DarkGray.copy(alpha = 0.3f))
                .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .clickable { imagePicker.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = userProfile.photoUrl,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                error = painterResource(id = android.R.drawable.ic_menu_myplaces),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            // Edit icon overlay - positioned at bottom right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset((-8).dp, (-8).dp)
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(3.dp, Color.Black, CircleShape)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Change Photo",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Name with better typography
        Text(
            text = userProfile.displayName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Bio - clickable to edit, light blue color
        if (userProfile.bio.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = userProfile.bio,
                fontSize = 14.sp,
                color = Color(0xFF87CEEB), // Light blue
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .clickable { 
                        bioText = userProfile.bio
                        showBioDialog = true 
                    }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Actions Chips - Visual flair without duplication
        if (userProfile.bio.isEmpty()) {
            Text(
                text = "✏️ Add a bio to tell people about yourself",
                color = Color(0xFF87CEEB), // Light blue
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { 
                        bioText = ""
                        showBioDialog = true 
                    }
                    .padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // MODERN STATS GRID - Horizontal layout like Instagram (5 items)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModernStatItem(
                value = photoCount.toString(),
                label = "Posts"
            )
            ModernStatItem(
                value = formatNumber(totalReactions),
                label = "Reactions"
            )
            ModernStatItem(
                value = userProfile.followers.size.toString(),
                label = "Followers"
            )
            ModernStatItem(
                value = userProfile.following.size.toString(),
                label = "Following"
            )
            ModernStatItem(
                value = currentStreak.toString(),
                label = "Streak"
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // MY PHOTOS GRID - 3 column grid matching home feed style
        if (photos.isNotEmpty()) {
            Text(
                text = "My Photos ($photoCount)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp)  // Match reduced grid padding
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Photo grid - match Home Feed exactly
            // Home calculates: (screen - banner - nav - status) / 4.1f = ~148dp
            val rowCount = ((photos.size + 2) / 3).coerceAtLeast(4)
            val rowHeight = 148.dp // Fine-tuned to match Home exactly
            val gridHeight = (rowCount * rowHeight.value).dp + 8.dp
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .background(PicFlickBackground)
            ) {
                // 3-column grid matching Home Feed exactly
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 1.dp,
                        end = 1.dp,
                        top = 4.dp,
                        bottom = 0.dp
                    ),
                    userScrollEnabled = false
                ) {
                    items(photos, key = { it.id }) { flick ->
                        MyPhotoCard(
                            flick = flick,
                            userProfile = userProfile,
                            onPhotoClick = { 
                                val index = photos.indexOf(flick)
                                onPhotoClick(flick, index) 
                            },
                            rowHeight = rowHeight
                        )
                    }
                }
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
                    .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No photos yet",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Upload your first photo!",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// Photo sizes for dynamic grid
private enum class PhotoSize {
    SMALL,   // 1x1 - normal
    MEDIUM,  // 1x2 - portrait (taller)
    LARGE,   // 2x2 - big square
    WIDE     // 2x1 - landscape (wider)
}

private data class PhotoGridPosition(
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val colSpan: Int
)

/**
 * Dynamic masonry-style photo grid with mixed sizes
 * Creates visual interest with varied photo dimensions
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DynamicPhotoGrid(
    photos: List<Flick>,
    onPhotoClick: (Flick, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create varied layout pattern based on photo index
    val positions = remember(photos) {
        calculateMixedPositions(photos.size)
    }
    
    val totalRows = positions.maxOfOrNull { it.row + it.rowSpan } ?: 2
    val gridHeight = (totalRows * 120 + (totalRows - 1) * 4).dp
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight)
    ) {
        photos.forEachIndexed { index, flick ->
            if (index >= positions.size) return@forEachIndexed
            
            val pos = positions[index]
            
            // Calculate size based on spans
            val baseCellSize = 120.dp
            val spacing = 4.dp
            
            val width = pos.colSpan * baseCellSize.value + (pos.colSpan - 1) * spacing.value
            val height = pos.rowSpan * baseCellSize.value + (pos.rowSpan - 1) * spacing.value
            val xOffset = pos.column * (baseCellSize.value + spacing.value)
            val yOffset = pos.row * (baseCellSize.value + spacing.value)
            
            Card(
                modifier = Modifier
                    .offset(xOffset.dp, yOffset.dp)
                    .width(width.dp)
                    .height(height.dp)
                    .combinedClickable(onClick = { onPhotoClick(flick, index) }),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = flick.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Show reaction count if any
                    if (flick.getTotalReactions() > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = flick.getTotalReactions().toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Size indicator for debugging (optional)
                    val sizeLabel = when {
                        pos.colSpan == 2 && pos.rowSpan == 2 -> "LARGE"
                        pos.colSpan == 2 && pos.rowSpan == 1 -> "WIDE"
                        pos.colSpan == 1 && pos.rowSpan == 2 -> "TALL"
                        else -> ""
                    }
                    if (sizeLabel.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sizeLabel,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculate positions for mixed-size photo grid
 * Creates visual variety with different photo sizes
 */
private fun calculateMixedPositions(count: Int): List<PhotoGridPosition> {
    val positions = mutableListOf<PhotoGridPosition>()
    val occupied = mutableListOf<MutableList<Boolean>>()
    
    fun ensureRows(row: Int) {
        while (occupied.size <= row + 1) {
            occupied.add(mutableListOf(false, false))
        }
    }
    
    fun isFree(row: Int, col: Int, w: Int, h: Int): Boolean {
        if (col + w > 2) return false
        for (r in row until row + h) {
            ensureRows(r)
            for (c in col until col + w) {
                if (occupied[r][c]) return false
            }
        }
        return true
    }
    
    fun occupy(row: Int, col: Int, w: Int, h: Int) {
        for (r in row until row + h) {
            ensureRows(r)
            for (c in col until col + w) {
                occupied[r][c] = true
            }
        }
    }
    
    // Pattern: Creates visual rhythm with varied sizes
    val patterns = listOf(
        // Row 1: Large (2x2) covers whole row
        listOf(PhotoGridPosition(0, 0, 2, 2)),
        // Row 2: Wide (2x1) + skip
        listOf(PhotoGridPosition(2, 0, 1, 2)),
        // Row 3: Small + Small or Medium
        listOf(
            PhotoGridPosition(3, 0, 1, 1),
            PhotoGridPosition(3, 1, 1, 1)
        ),
        // Row 4: Medium (tall) next row
        listOf(PhotoGridPosition(4, 0, 2, 1), PhotoGridPosition(4, 1, 1, 1)),
        // Row 5-6: Mixed
        listOf(
            PhotoGridPosition(5, 0, 1, 1),
            PhotoGridPosition(5, 1, 2, 1),
            PhotoGridPosition(6, 0, 1, 1)
        )
    )
    
    var currentRow = 0
    var photoIndex = 0
    
    while (photoIndex < count) {
        // Determine pattern based on position for variety
        val patternType = (photoIndex / 3) % 5
        
        when (patternType) {
            0 -> {
                // Large 2x2 photo
                if (isFree(currentRow, 0, 2, 2)) {
                    positions.add(PhotoGridPosition(currentRow, 0, 2, 2))
                    occupy(currentRow, 0, 2, 2)
                    photoIndex++
                    currentRow += 2
                } else {
                    currentRow++
                }
            }
            1 -> {
                // Wide landscape 2x1
                if (isFree(currentRow, 0, 2, 1)) {
                    positions.add(PhotoGridPosition(currentRow, 0, 1, 2))
                    occupy(currentRow, 0, 2, 1)
                    photoIndex++
                    currentRow++
                } else {
                    currentRow++
                }
            }
            2 -> {
                // Two small squares
                if (isFree(currentRow, 0, 1, 1) && isFree(currentRow, 1, 1, 1)) {
                    positions.add(PhotoGridPosition(currentRow, 0, 1, 1))
                    occupy(currentRow, 0, 1, 1)
                    photoIndex++
                    
                    if (photoIndex < count) {
                        positions.add(PhotoGridPosition(currentRow, 1, 1, 1))
                        occupy(currentRow, 1, 1, 1)
                        photoIndex++
                    }
                    currentRow++
                } else {
                    currentRow++
                }
            }
            3 -> {
                // Tall portrait 1x2
                if (isFree(currentRow, 0, 2, 1)) {
                    positions.add(PhotoGridPosition(currentRow, 0, 2, 1))
                    occupy(currentRow, 0, 2, 1)
                    photoIndex++
                    
                    // Fill remaining with small
                    if (photoIndex < count && isFree(currentRow, 1, 1, 1)) {
                        positions.add(PhotoGridPosition(currentRow, 1, 1, 1))
                        occupy(currentRow, 1, 1, 1)
                        photoIndex++
                    }
                    currentRow++
                } else {
                    currentRow++
                }
            }
            else -> {
                // Mixed: one tall, rest small
                if (isFree(currentRow, 0, 2, 1)) {
                    positions.add(PhotoGridPosition(currentRow, 0, 2, 1))
                    occupy(currentRow, 0, 2, 1)
                    photoIndex++
                }
                if (photoIndex < count && isFree(currentRow, 1, 1, 1)) {
                    positions.add(PhotoGridPosition(currentRow, 1, 1, 1))
                    occupy(currentRow, 1, 1, 1)
                    photoIndex++
                }
                currentRow++
            }
        }
        
        // Safety check to prevent infinite loop
        if (currentRow > count + 10) break
    }
    
    return positions.take(count)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    flick: Flick,
    onPhotoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f) // Square aspect ratio
            .combinedClickable(
                onClick = onPhotoClick
            ),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = flick.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfilePhotoGridItem(
    flick: Flick,
    rowHeight: androidx.compose.ui.unit.Dp,
    onPhotoClick: () -> Unit
) {
    val userReaction = flick.reactions.entries.firstOrNull()?.value
    
    Card(
        modifier = Modifier
            .height(rowHeight)
            .combinedClickable(onClick = onPhotoClick),
        shape = androidx.compose.ui.graphics.RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = flick.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Tiny reaction overlay (top right)
            userReaction?.let { reaction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (reaction) {
                            "LIKE" -> "❤️"
                            "LOVE" -> "❤️"
                            "FIRE" -> "🔥"
                            "COOL" -> "😎"
                            "WOW" -> "😮"
                            else -> "❤️"
                        },
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernStatItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 1000000 -> "${number / 1000000}M"
        number >= 1000 -> "${number / 1000}K"
        else -> number.toString()
    }
}

// Grid position data class for masonry layout
private data class PhotoGridPos(
    val index: Int,
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val colSpan: Int
)

/**
 * STRICT gap-free masonry grid - forces 100% fill rate
 * Every single cell must be occupied, no exceptions
 */
private fun calculateGapFreePhotoGridPositions(count: Int): List<PhotoGridPos> {
    val positions = mutableListOf<PhotoGridPos>()
    val random = java.util.Random()
    
    // Track occupied cells: row -> set of occupied columns
    val occupied = mutableMapOf<Int, MutableSet<Int>>()
    
    fun isOccupied(row: Int, col: Int): Boolean {
        return occupied.getOrDefault(row, mutableSetOf()).contains(col)
    }
    
    fun markOccupied(row: Int, col: Int, w: Int, h: Int) {
        for (r in row until row + h) {
            occupied.getOrPut(r) { mutableSetOf() }.addAll(col until col + w)
        }
    }
    
    fun findNextFreeCell(): Pair<Int, Int>? {
        for (row in 0..count) {
            for (col in 0..2) {
                if (!isOccupied(row, col)) return row to col
            }
        }
        return null
    }
    
    var photoIndex = 0
    
    while (photoIndex < count) {
        val (row, col) = findNextFreeCell() ?: break
        
        val remainingCols = 3 - col
        val sizeRoll = random.nextFloat()
        
        // Decide size - STRICT fitting only
        var (colSpan, rowSpan) = when {
            col == 0 && remainingCols == 3 -> {
                when {
                    sizeRoll < 0.03f -> 3 to 1
                    sizeRoll < 0.08f -> 2 to 2
                    sizeRoll < 0.20f -> 2 to 1
                    sizeRoll < 0.35f -> 1 to 2
                    else -> 1 to 1
                }
            }
            remainingCols >= 2 -> {
                when {
                    sizeRoll < 0.15f -> 2 to 1
                    sizeRoll < 0.30f -> 1 to 2
                    else -> 1 to 1
                }
            }
            else -> 1 to 1
        }
        
        // FORCE fit - if doesn't fit, downgrade to 1x1
        if (col + colSpan > 3 || (rowSpan == 2 && isOccupied(row + 1, col))) {
            colSpan = 1
            rowSpan = 1
        }
        
        positions.add(PhotoGridPos(
            index = photoIndex,
            row = row,
            column = col,
            rowSpan = rowSpan,
            colSpan = colSpan
        ))
        
        markOccupied(row, col, colSpan, rowSpan)
        photoIndex++
    }
    
    return positions.sortedWith(compareBy({ it.row }, { it.column }))
}

/**
 * Photo card for My Photos grid - matches Home Feed FlickCard exactly
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MyPhotoCard(
    flick: Flick,
    userProfile: UserProfile,
    onPhotoClick: () -> Unit,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    Card(
        modifier = Modifier
            .padding(1.dp)
            .height(rowHeight)
            .combinedClickable(onClick = onPhotoClick),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Photo only - clean, no overlays
            AsyncImage(
                model = flick.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}