package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground

/**
 * Chats/Messages screen
 */
@Composable
fun ChatsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground)
    ) {
        // Logo banner at top with back button inside
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PicFlickBannerBackground)
                .padding(top = 36.dp, bottom = 8.dp)
        ) {
            // Back button on the left
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .clickable { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            
            // Logo centered
            LogoImage(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Content
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Messages coming soon!",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}