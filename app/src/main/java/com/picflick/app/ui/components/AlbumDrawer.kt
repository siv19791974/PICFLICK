package com.picflick.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.FeedFilter
import com.picflick.app.data.FriendGroup

/**
 * Sexy slide-in Album Drawer from the left side
 * Shows all albums with friend counts and allows selection/filtering
 */
@Composable
fun AlbumDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    groups: List<FriendGroup>,
    selectedFilter: FeedFilter,
    onFilterSelected: (FeedFilter) -> Unit,
    onCreateAlbum: () -> Unit,
    onManageAlbums: () -> Unit,
    onEditAlbum: (FriendGroup) -> Unit,
    onDeleteAlbum: (FriendGroup) -> Unit,
    isDarkMode: Boolean = true
) {
    // Animation for background scrim
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0.6f else 0f,
        animationSpec = tween(300)
    )

    // Animation for drawer slide
    val drawerOffset by animateFloatAsState(
        targetValue = if (isOpen) 0f else -1f,
        animationSpec = tween(300)
    )

    // Only render if open or animating
    if (!isOpen && drawerOffset <= -1f) return
    
    val backgroundColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val dividerColor = if (isDarkMode) Color(0xFF333333) else Color(0xFFE0E0E0)

    // Overlay with scrim
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background scrim (click to close)
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(scrimAlpha)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures { onClose() }
                    }
            )
        }

        // Drawer content
        if (drawerOffset > -1f) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .offset(x = (drawerOffset * 300).dp)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)),
                color = backgroundColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Text(
                        text = "📸 Albums",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )

                    Text(
                        text = "Filter your feed by album",
                        fontSize = 14.sp,
                        color = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Home Feed Option (All Friends)
                    val isAllSelected = selectedFilter is FeedFilter.AllFriends
                    AlbumItem(
                        icon = "🏠",
                        name = "Home Feed",
                        subtitle = "All friends",
                        friendCount = null,
                        isSelected = isAllSelected,
                        onClick = {
                            onFilterSelected(FeedFilter.AllFriends)
                            onClose()
                        },
                        isDarkMode = isDarkMode
                    )

                    HorizontalDivider(
                        color = dividerColor,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    // Album List Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Albums",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        TextButton(onClick = onManageAlbums) {
                            Text(
                                text = "Edit",
                                fontSize = 14.sp,
                                color = Color(0xFF4FC3F7)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Album List
                    if (groups.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🗂️",
                                    fontSize = 48.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "No albums yet",
                                    fontSize = 16.sp,
                                    color = textColor.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Create your first album!",
                                    fontSize = 14.sp,
                                    color = textColor.copy(alpha = 0.4f)
                                )
                            }
                        }
                    } else {
                        LazyColumn {
                            items(groups) { group ->
                                val isSelected = selectedFilter is FeedFilter.ByGroup &&
                                        (selectedFilter as FeedFilter.ByGroup).group.id == group.id

                                AlbumItem(
                                    icon = group.icon,
                                    name = group.name,
                                    subtitle = "${group.friendIds.size} friends",
                                    friendCount = group.friendIds.size,
                                    isSelected = isSelected,
                                    onClick = {
                                        onFilterSelected(FeedFilter.ByGroup(group))
                                        onClose()
                                    },
                                    onLongClick = { onEditAlbum(group) },
                                    isDarkMode = isDarkMode,
                                    accentColor = parseColor(group.color)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    HorizontalDivider(color = dividerColor)

                    // Create New Album Button
                    Button(
                        onClick = onCreateAlbum,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4FC3F7)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Create New Album")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumItem(
    icon: String,
    name: String,
    subtitle: String,
    friendCount: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isDarkMode: Boolean = true,
    accentColor: Color? = null
) {
    val backgroundColor = when {
        isSelected -> accentColor ?: Color(0xFF4FC3F7)
        isDarkMode -> Color(0xFF2D2D2D)
        else -> Color(0xFFF5F5F5)
    }

    val textColor = if (isSelected && accentColor != null) {
        // Determine if accent color is light or dark
        val isLight = (accentColor.red * 0.299f + accentColor.green * 0.587f + accentColor.blue * 0.114f) > 0.5f
        if (isLight) Color.Black else Color.White
    } else if (isDarkMode) {
        Color.White
    } else {
        Color.Black
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
                    .background(Color.Transparent)
                    .padding(end = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon.startsWith("http")) {
                    AsyncImage(
                        model = icon,
                        contentDescription = "$name icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = icon,
                        fontSize = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            // Name and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
            }

            // Selection indicator or friend count badge
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = textColor.copy(alpha = 0.2f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "✓",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            } else if (friendCount != null && friendCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = textColor.copy(alpha = 0.1f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "$friendCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun parseColor(hex: String?): Color? {
    if (hex == null) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}
