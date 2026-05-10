package com.picflick.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import com.picflick.app.data.FriendGroup
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import com.picflick.app.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold)
)

/**
 * Bottom navigation bar for main app navigation - BLACK with clean 5-icon layout.
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    unreadMessages: Int = 0,
    activeAlbum: FriendGroup? = null,
    userPhotoUrl: String = "",
    userDisplayName: String = ""
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.Black,
        contentColor = Color.LightGray,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            icon = {
                if (currentRoute == "home") {
                    val album = activeAlbum
                    if (album != null) {
                        val albumColor = try {
                            Color(android.graphics.Color.parseColor(album.color))
                        } catch (_: Exception) {
                            Color(0xFF1565C0)
                        }
                        val iconValue = album.icon.takeIf { it.isNotBlank() } ?: "👥"
                        val iconText = if (iconValue.startsWith("http")) {
                            album.name.firstOrNull()?.uppercase()?.toString() ?: "👥"
                        } else {
                            iconValue
                        }
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = albumColor.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = iconText,
                                fontSize = if (iconText.length == 1) 20.sp else 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        val initials = userDisplayName.take(1).uppercase()
                        if (userPhotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = userPhotoUrl,
                                contentDescription = "Home",
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else if (initials.isNotBlank()) {
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color(0xFF1565C0),
                                modifier = Modifier.size(28.dp)
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        fontFamily = PoppinsFontFamily
                                    )
                                }
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Home,
                                contentDescription = "Home",
                                tint = Color.LightGray
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = "Home",
                        tint = Color.LightGray
                    )
                }
            },
            label = null,
            alwaysShowLabel = false,
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )

        NavigationBarItem(
            icon = {
                if (unreadMessages > 0) {
                    android.util.Log.d("BottomNavBar", "Messages badge: unreadMessages=$unreadMessages")
                }
                BadgedBox(
                    badge = {
                        if (unreadMessages > 0) {
                            Badge(
                                containerColor = Color(0xFF1565C0),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = if (unreadMessages > 99) "99+" else unreadMessages.toString(),
                                    fontWeight = FontWeight.Bold
                                )
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
