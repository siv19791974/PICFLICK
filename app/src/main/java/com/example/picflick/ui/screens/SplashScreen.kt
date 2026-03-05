package com.example.picflick.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.theme.PicFlickBackground
import kotlinx.coroutines.delay

/**
 * Custom Splash Screen
 * Uses LogoImage (same as home screen banner - 40dp height)
 * Light blue background (PicFlickBackground)
 * Simple and clean - just logo + loading dots
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    // Animation states
    var startAnimation by remember { mutableStateOf(false) }

    // Animate scale (zoom in effect)
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.7f,
        animationSpec = tween(
            durationMillis = 600,
            easing = EaseOutBack
        ),
        label = "scale"
    )

    // Animate alpha (fade in)
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            easing = LinearEasing
        ),
        label = "alpha"
    )

    // Start animation immediately
    LaunchedEffect(Unit) {
        startAnimation = true
        // Wait for animation + hold time
        delay(2000) // 2 seconds total
        onSplashComplete()
    }

    // Splash Screen UI - Simple and clean
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground),
        contentAlignment = Alignment.Center
    ) {
        // Logo - SAME as home screen (40dp height)
        Box(
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
        ) {
            LogoImage() // Uses 40dp height - same as home screen banner
        }

        // Loading dots at bottom
        LoadingDots(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .alpha(alpha)
        )
    }
}

/**
 * Animated loading dots
 */
@Composable
private fun LoadingDots(modifier: Modifier = Modifier) {
    val dotCount = 3
    val delayBetweenDots = 200

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(dotCount) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(offsetMillis = index * delayBetweenDots)
                ),
                label = "dotScale$index"
            )

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(scale)
                    .background(
                        color = Color(0xFF1976D2),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun SplashScreenPreview() {
    // Preview - just logo on light blue background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground),
        contentAlignment = Alignment.Center
    ) {
        // Logo placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF1976D2), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // PF text representing logo
            androidx.compose.material3.Text(
                text = "PF",
                color = Color.White,
                fontSize = 20.sp
            )
        }

        // Loading dots at bottom
        LoadingDots(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )
    }
}