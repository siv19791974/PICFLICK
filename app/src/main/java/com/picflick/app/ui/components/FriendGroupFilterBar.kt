package com.picflick.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.data.FeedFilter
import com.picflick.app.data.FriendGroup

/**
 * Horizontal filter bar for friend groups/albums
 * Shows "All Friends" chip + user-created groups + Add button
 */
@Composable
fun FriendGroupFilterBar(
    groups: List<FriendGroup>,
    selectedFilter: FeedFilter,
    onFilterSelected: (FeedFilter) -> Unit,
    onAddGroupClick: () -> Unit,
    isDarkMode: Boolean = true
) {
    val backgroundColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val selectedBackground = if (isDarkMode) Color.White else Color.Black
    val unselectedBackground = if (isDarkMode) Color(0xFF2D2D2D) else Color.White
    val selectedTextColor = if (isDarkMode) Color.Black else Color.White
    val unselectedTextColor = if (isDarkMode) Color.White else Color.Black

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All Friends" chip (always first)
        item {
            val isSelected = selectedFilter is FeedFilter.AllFriends
            FilterChip(
                icon = "👥",
                label = "All",
                isSelected = isSelected,
                selectedBackgroundColor = selectedBackground,
                unselectedBackgroundColor = unselectedBackground,
                selectedTextColor = selectedTextColor,
                unselectedTextColor = unselectedTextColor,
                onClick = { onFilterSelected(FeedFilter.AllFriends) }
            )
        }

        // User's custom groups
        items(groups) { group ->
            val isSelected = selectedFilter is FeedFilter.ByGroup && 
                           (selectedFilter as FeedFilter.ByGroup).group.id == group.id
            FilterChip(
                icon = group.icon,
                label = group.name,
                isSelected = isSelected,
                selectedBackgroundColor = parseColor(group.color) ?: selectedBackground,
                unselectedBackgroundColor = unselectedBackground,
                selectedTextColor = if (isLightColor(group.color)) Color.Black else Color.White,
                unselectedTextColor = unselectedTextColor,
                onClick = { onFilterSelected(FeedFilter.ByGroup(group)) }
            )
        }

        // Add new group button
        item {
            AddGroupChip(
                isDarkMode = isDarkMode,
                onClick = onAddGroupClick
            )
        }
    }
}

@Composable
private fun FilterChip(
    icon: String,
    label: String,
    isSelected: Boolean,
    selectedBackgroundColor: Color,
    unselectedBackgroundColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) selectedBackgroundColor else unselectedBackgroundColor
    val textColor = if (isSelected) selectedTextColor else unselectedTextColor

    Surface(
        modifier = Modifier
            .clickable { onClick() }
            .height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        shadowElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

@Composable
private fun AddGroupChip(
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF2D2D2D) else Color.White
    val iconColor = if (isDarkMode) Color.White else Color.Black

    Surface(
        modifier = Modifier
            .clickable { onClick() }
            .size(36.dp),
        shape = CircleShape,
        color = backgroundColor,
        shadowElevation = 1.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create new album",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Parse hex color string to Color
 */
private fun parseColor(hex: String): Color? {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}

/**
 * Determine if a color is light (for text contrast)
 */
private fun isLightColor(hex: String): Boolean {
    return try {
        val color = android.graphics.Color.parseColor(hex)
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        // Calculate perceived brightness
        val brightness = (red * 299 + green * 587 + blue * 114) / 1000
        brightness > 128
    } catch (e: Exception) {
        false
    }
}
