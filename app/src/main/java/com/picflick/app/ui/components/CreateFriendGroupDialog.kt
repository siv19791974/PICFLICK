package com.picflick.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.picflick.app.data.FriendGroup
import com.picflick.app.data.UserProfile

/**
 * Dialog for creating a new friend group/album
 */
@Composable
fun CreateFriendGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, icon: String, selectedFriends: List<String>) -> Unit,
    friends: List<UserProfile>,
    isDarkMode: Boolean = true
) {
    var groupName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("👥") }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentPage by remember { mutableStateOf(0) } // 0: info, 1: select friends

    val backgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black

    val icons = listOf("👥", "👨‍👩‍👧‍👦", "💼", "🎓", "⭐", "✈️", "⚽", "🎨", "🏠", "🎵", "📚", "🎮")
    val colors = listOf(
        "#4FC3F7", // Light Blue
        "#FF6B6B", // Red
        "#4CAF50", // Green
        "#FFD93D", // Yellow
        "#FF9F43", // Orange
        "#A55EEA", // Purple
        "#FF6B9D", // Pink
        "#26D0CE", // Teal
        "#FD79A8", // Salmon
        "#FDCB6E"  // Gold
    )
    var selectedColor by remember { mutableStateOf(colors[0]) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = backgroundColor
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
                        text = if (currentPage == 0) "Create Album" else "Select Friends",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (currentPage) {
                    0 -> {
                        // Album Name
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Album Name") },
                            placeholder = { Text("e.g., Family, Work, School...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Icon Selection
                        Text(
                            text = "Choose Icon",
                            fontSize = 14.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        IconGrid(
                            items = icons,
                            selectedItem = selectedIcon,
                            onItemSelected = { selectedIcon = it },
                            isIcon = true,
                            isDarkMode = isDarkMode
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Color Selection
                        Text(
                            text = "Choose Color",
                            fontSize = 14.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        IconGrid(
                            items = colors,
                            selectedItem = selectedColor,
                            onItemSelected = { selectedColor = it },
                            isIcon = false,
                            isDarkMode = isDarkMode
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Next Button
                        Button(
                            onClick = { 
                                if (groupName.isNotBlank()) {
                                    currentPage = 1 
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = groupName.isNotBlank()
                        ) {
                            Text("Next: Select Friends (${selectedFriends.size})")
                        }
                    }

                    1 -> {
                        // Friend Selection
                        if (friends.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No friends yet. Add friends first!",
                                    color = textColor.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(300.dp)
                            ) {
                                items(friends) { friend ->
                                    val isSelected = selectedFriends.contains(friend.uid)
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedFriends = if (isSelected) {
                                                    selectedFriends - friend.uid
                                                } else {
                                                    selectedFriends + friend.uid
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedFriends = if (it) {
                                                    selectedFriends + friend.uid
                                                } else {
                                                    selectedFriends - friend.uid
                                                }
                                            }
                                        )
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        // Friend avatar (placeholder)
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = if (isDarkMode) Color.Gray else Color.LightGray
                                        ) {}
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Text(
                                            text = friend.displayName,
                                            fontSize = 16.sp,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { currentPage = 0 },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back")
                            }
                            
                            Button(
                                onClick = {
                                    onCreate(groupName, selectedIcon, selectedFriends.toList())
                                },
                                modifier = Modifier.weight(1f),
                                enabled = groupName.isNotBlank()
                            ) {
                                Text("Create Album")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple custom FlowRow using LazyVerticalGrid for icon/color selection
 */
@Composable
private fun IconGrid(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    isIcon: Boolean = true,
    isDarkMode: Boolean = true
) {
    LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
        modifier = Modifier.height(if (isIcon) 120.dp else 80.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items.size) { index ->
            val item = items[index]
            val isSelected = item == selectedItem
            
            if (isIcon) {
                // Icon button
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onItemSelected(item) },
                    shape = CircleShape,
                    color = if (isSelected) 
                        Color(0xFF4FC3F7)
                    else 
                        if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFF0F0F0)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = item,
                            fontSize = 24.sp
                        )
                    }
                }
            } else {
                // Color button
                val color = try {
                    Color(android.graphics.Color.parseColor(item))
                } catch (e: Exception) {
                    Color.Gray
                }
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onItemSelected(item) },
                    shape = CircleShape,
                    color = color,
                    border = if (isSelected) 
                        androidx.compose.foundation.BorderStroke(3.dp, Color.White)
                    else 
                        null
                ) {}
            }
        }
    }
}

private fun parseColor(hex: String): Color? {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}
