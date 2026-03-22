package com.picflick.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bottom navigation bar for main app navigation - BLACK with clean 5-icon layout.
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    unreadMessages: Int = 0
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.Black,
        contentColor = Color.LightGray,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "home") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home",
                    tint = if (currentRoute == "home") Color.White else Color.LightGray
                )
            },
            label = null,
            alwaysShowLabel = false,
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF1565C0))
        )

        NavigationBarItem(
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadMessages > 0) {
                            Badge(containerColor = Color(0xFF1565C0), contentColor = Color.White) {
                                Text(if (unreadMessages > 99) "99+" else unreadMessages.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentRoute == "chats") Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Messages",
                        tint = if (currentRoute == "chats") Color.White else Color.LightGray
                    )
                }
            },
            label = null,
            alwaysShowLabel = false,
            selected = currentRoute == "chats",
            onClick = { onNavigate("chats") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF1565C0))
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Upload",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            },
            label = null,
            alwaysShowLabel = false,
            selected = false,
            onClick = { onNavigate("upload") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "friends") Icons.Filled.People else Icons.Outlined.People,
                    contentDescription = "Friends",
                    tint = if (currentRoute == "friends") Color.White else Color.LightGray
                )
            },
            label = null,
            alwaysShowLabel = false,
            selected = currentRoute == "friends",
            onClick = { onNavigate("friends") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF1565C0))
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile",
                    tint = if (currentRoute == "profile") Color.White else Color.LightGray
                )
            },
            label = null,
            alwaysShowLabel = false,
            selected = currentRoute == "profile",
            onClick = { onNavigate("profile") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF1565C0))
        )
    }
}
