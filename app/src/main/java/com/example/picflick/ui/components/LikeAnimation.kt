package com.example.picflick.ui.components

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