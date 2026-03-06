package com.example.picflick.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bottom navigation bar for main app navigation - BLACK with contrasting icons
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    unreadNotifications: Int = 0
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.Black,  // BLACK background
        contentColor = Color.LightGray,  // Light grey for unselected
        tonalElevation = 0.dp
    ) {
        // Home
        NavigationBarItem(
            icon = {
                Icon(
                    if (currentRoute == "home") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home",
                    tint = if (currentRoute == "home") Color.White else Color.LightGray  // White when selected
                )
            },
            label = { Text("Home", color = if (currentRoute == "home") Color.White else Color.LightGray) },
            selected = currentRoute == "home",
            onClick = { onNavigate("home") }
        )

        // Messages
        NavigationBarItem(
            icon = {
                Icon(
                    if (currentRoute == "chats") Icons.Filled.MailOutline else Icons.Outlined.MailOutline,
                    contentDescription = "Messages",
                    tint = if (currentRoute == "chats") Color.White else Color.LightGray
                )
            },
            label = { Text("Messages", color = if (currentRoute == "chats") Color.White else Color.LightGray) },
            selected = currentRoute == "chats",
            onClick = { onNavigate("chats") }
        )

        // Upload (center elevated)
        NavigationBarItem(
            icon = {
                FloatingActionButton(
                    onClick = { onNavigate("upload") },
                    modifier = Modifier.size(40.dp),
                    containerColor = Color.DarkGray  // Dark grey for contrast
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Upload",
                        tint = Color.White
                    )
                }
            },
            label = { Text("") },
            selected = false,
            onClick = { onNavigate("upload") }
        )

        // Friends
        NavigationBarItem(
            icon = {
                Icon(
                    if (currentRoute == "friends") Icons.Filled.Share else Icons.Outlined.Share,
                    contentDescription = "Friends",
                    tint = if (currentRoute == "friends") Color.White else Color.LightGray
                )
            },
            label = { Text("Friends", color = if (currentRoute == "friends") Color.White else Color.LightGray) },
            selected = currentRoute == "friends",
            onClick = { onNavigate("friends") }
        )

        // Profile
        NavigationBarItem(
            icon = {
                Icon(
                    if (currentRoute == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile",
                    tint = if (currentRoute == "profile") Color.White else Color.LightGray
                )
            },
            label = { Text("Profile", color = if (currentRoute == "profile") Color.White else Color.LightGray) },
            selected = currentRoute == "profile",
            onClick = { onNavigate("profile") }
        )
    }
}
