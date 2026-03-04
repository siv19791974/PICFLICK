package com.example.picflick.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.picflick.data.UserProfile

/**
 * Modern Profile screen with enhanced UI
 */
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    photoCount: Int,
    onBack: () -> Unit,
    onMyPhotosClick: () -> Unit = {},
    onPhotoSelected: (Uri) -> Unit = {}
) {
    // Image picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPhotoSelected(it) }
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

        // Name & Email with better typography
        Text(
            text = userProfile.displayName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = userProfile.email,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Bio
        if (userProfile.bio.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = userProfile.bio,
                fontSize = 14.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Actions Chips - Visual flair without duplication
        if (userProfile.bio.isEmpty()) {
            Text(
                text = "✏️ Add a bio to tell people about yourself",
                color = Color(0xFF00D09C),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { /* Goes to Settings > Edit Profile */ }
                    .padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // MODERN STATS GRID - Horizontal layout like Instagram
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModernStatItem(
                value = photoCount.toString(),
                label = "Posts"
            )
            ModernStatItem(
                value = formatNumber(userProfile.totalLikes),
                label = "Likes"
            )
            ModernStatItem(
                value = userProfile.followers.size.toString(),
                label = "Followers"
            )
            ModernStatItem(
                value = userProfile.following.size.toString(),
                label = "Following"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // FRIEND PREVIEW - Show first 5 friends
        if (userProfile.following.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Following ${userProfile.following.size} people",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Friend avatars row would go here - simplified for now
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ACHIEVEMENT BADGES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            if (photoCount > 0) {
                BadgeItem(emoji = "📸", label = "Photographer")
            }
            if (userProfile.totalLikes >= 10) {
                BadgeItem(emoji = "❤️", label = "Liked")
            }
            if (userProfile.following.size >= 5) {
                BadgeItem(emoji = "👥", label = "Socialite")
            }
            if (photoCount >= 5) {
                BadgeItem(emoji = "🔥", label = "Active")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // MY PHOTOS SECTION - Enhanced Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onMyPhotosClick() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.DarkGray.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "My Photos",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$photoCount ${if (photoCount == 1) "photo" else "photos"} uploaded",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // App Version at bottom
        Text(
            text = "PicFlick v1.0",
            fontSize = 12.sp,
            color = Color.Gray.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))
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

@Composable
private fun BadgeItem(emoji: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFF2C2C2E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 28.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
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