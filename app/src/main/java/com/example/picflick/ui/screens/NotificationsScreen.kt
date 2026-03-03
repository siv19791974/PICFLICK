package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.components.TopBarWithBackButton
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground

/**
 * Notifications screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                title = "Notifications",
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PicFlickBackground)
                .padding(padding)
        ) {
            // Logo banner at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(top = 36.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                LogoImage()
            }
            
            // Content
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No notifications yet",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}