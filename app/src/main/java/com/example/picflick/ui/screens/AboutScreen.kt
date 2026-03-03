package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground

/**
 * About screen with app information
 */
@Composable
fun AboutScreen(
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
                    .size(36.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .clickable { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            
            // Logo centered - CLICKABLE to go home
            LogoImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable { onBack() }
            )
        }
        
        // Content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ... rest of content
        }
    }
}