package com.example.picflick.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.picflick.ui.theme.PicFlickBannerBackground

/**
 * Bottom navigation bar for main app navigation - GREY
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    unreadNotifications: Int = 0
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = PicFlickBannerBackground,  // GREY background
        tonalElevation = 3.dp
    ) {
        // Home
        NavigationBarItem(
            icon = {
                Icon(
                    if (currentRoute == "home") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text("Home") },
            selected = currentRoute == "home",
            onClick = { onNavigate("home") }
        )

        // Messages
        NavigationBarItem(
            icon = {
                Icon(
                    if (currentRoute == "chats") Icons.Filled.MailOutline else Icons.Outlined.MailOutline,
                    contentDescription = "Messages"
                )
            },
            label = { Text("Messages") },
            selected = currentRoute == "chats",
            onClick = { onNavigate("chats") }
        )

        // Upload (center elevated)
        NavigationBarItem(
            icon = {
                FloatingActionButton(
                    onClick = { onNavigate("upload") },
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Upload",
                        tint = MaterialTheme.colorScheme.onPrimary
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
                    contentDescription = "Friends"
                )
            },
            label = { Text("Friends") },
            selected = currentRoute == "friends",
            onClick = { onNavigate("friends") }
        )

        // Profile
        NavigationBarItem(
            icon = {
                Icon(
                    if (currentRoute == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile"
                )
            },
            label = { Text("Profile") },
            selected = currentRoute == "profile",
            onClick = { onNavigate("profile") }
        )
    }
}