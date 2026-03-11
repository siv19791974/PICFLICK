package com.picflick.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.ui.theme.PicFlickTheme

/**
 * Google Play Store Icon Preview (512x512)
 * 
 * This preview shows how the 512x512 Play Store icon should look.
 * To export:
 * 1. Run this preview in Android Studio
 * 2. Right-click preview → "Save Image"
 * 3. Use as Google Play Console app icon
 */
@Composable
fun PlayStoreIconPreview() {
    Box(
        modifier = Modifier
            .size(512.dp)
            .background(Color(0xFFD7ECFF)), // PicFlick light blue
        contentAlignment = Alignment.Center
    ) {
        // Main "PICFLICK" text centered
        Text(
            text = "PICFLICK",
            fontSize = 120.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black,
            letterSpacing = 4.sp
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 512,
    heightDp = 512
)
@Composable
fun PlayStoreIconPreviewPreview() {
    PicFlickTheme {
        PlayStoreIconPreview()
    }
}

/**
 * Feature Graphic Preview (1024x500)
 * 
 * This preview shows how the feature graphic banner should look.
 * To export:
 * 1. Run this preview in Android Studio
 * 2. Right-click preview → "Save Image"
 * 3. Upload to Google Play Console as Feature Graphic
 */
@Composable
fun FeatureGraphicPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .background(Color(0xFFD7ECFF)), // PicFlick light blue
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Text(
                text = "PICFLICK",
                fontSize = 100.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                letterSpacing = 6.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Tagline
            Text(
                text = "Share Photos with Friends",
                fontSize = 40.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 1024,
    heightDp = 500
)
@Composable
fun FeatureGraphicPreviewPreview() {
    PicFlickTheme {
        FeatureGraphicPreview()
    }
}
