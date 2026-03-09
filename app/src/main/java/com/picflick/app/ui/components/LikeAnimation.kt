package com.picflick.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Animated heart that pops when liked
 */
@Composable
fun LikeAnimation(
    isLiked: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isLiked) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "like_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isLiked) 1f else 0.6f,
        animationSpec = tween(300),
        label = "like_alpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Like",
            tint = Color.Red.copy(alpha = alpha),
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
        )
    }
}

/**
 * Heart particles burst animation
 */
@Composable
fun HeartBurstAnimation(
    trigger: Boolean,
    onComplete: () -> Unit = {}
) {
    var showBurst by remember { mutableStateOf(false) }
    
    LaunchedEffect(trigger) {
        if (trigger) {
            showBurst = true
            kotlinx.coroutines.delay(600)
            showBurst = false
            onComplete()
        }
    }
    
    if (showBurst) {
        val infiniteTransition = rememberInfiniteTransition(label = "burst")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "burst_scale"
        )
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "burst_alpha"
        )
        
        // Small hearts bursting outward
        repeat(6) { i ->
            val angle = (i * 60) * (Math.PI / 180)
            val offsetX = kotlin.math.cos(angle).toFloat() * 30 * scale
            val offsetY = kotlin.math.sin(angle).toFloat() * 30 * scale
            
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.Red.copy(alpha = alpha),
                modifier = Modifier
                    .size(12.dp)
                    .offset(offsetX.dp, offsetY.dp)
            )
        }
    }
}

/**
 * Big heart animation for double-tap like (Instagram style)
 */
@Composable
fun DoubleTapHeartAnimation(
    key: Int,
    isLiked: Boolean,
    onAnimationComplete: () -> Unit
) {
    var animationState by remember(key) { mutableStateOf(0) }
    
    LaunchedEffect(key) {
        animationState = 1 // Start animation
        kotlinx.coroutines.delay(800) // Animation duration
        animationState = 2 // Complete
        onAnimationComplete()
    }
    
    if (animationState > 0) {
        val scale by animateFloatAsState(
            targetValue = when (animationState) {
                1 -> 1.5f // Big heart
                else -> 0f // Fade out
            },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "double_tap_scale"
        )
        
        val alpha by animateFloatAsState(
            targetValue = when (animationState) {
                1 -> 1f
                else -> 0f
            },
            animationSpec = tween(400),
            label = "double_tap_alpha"
        )
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // White outline (half size: was 200, now 100)
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = alpha * 0.3f),
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale * 1.1f)
            )
            
            // Main heart - RED when liking, WHITE when unliking
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = if (isLiked) "Liked" else "Unliked",
                tint = if (isLiked) Color.Red.copy(alpha = alpha) else Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .size(90.dp)
                    .scale(scale)
            )
        }
    }
}
