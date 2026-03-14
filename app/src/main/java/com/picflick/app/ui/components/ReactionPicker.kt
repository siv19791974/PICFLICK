package com.picflick.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.data.ReactionType
import com.picflick.app.data.toEmoji

/**
 * Animated reaction picker - shows on long press or tap
 * Facebook/Instagram style reaction bar
 */
@Composable
fun ReactionPicker(
    currentReaction: ReactionType?,
    onReactionSelected: (ReactionType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reactions = ReactionType.entries.toList()
    
    // Animation for the popup appearing
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reaction_scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .background(
                color = Color.Black.copy(alpha = 0.8f), // Dark transparent background
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp) // Smaller padding to fit all emojis
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp), // Smaller spacing
            verticalAlignment = Alignment.CenterVertically
        ) {
            reactions.forEach { reaction ->
                ReactionButton(
                    reaction = reaction,
                    isSelected = currentReaction == reaction,
                    onSelected = {
                        onReactionSelected(reaction)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ReactionButton(
    reaction: ReactionType,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    // Bounce animation when pressed
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.4f else if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reaction_button_scale"
    )
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    isPressed = true
                    onSelected()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reaction.toEmoji(),
            fontSize = 24.sp
        )
    }
    
    // Reset pressed state after animation
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

/**
 * Compact reaction bar showing reaction counts
 * Used below photos or in lists
 */
@Composable
fun ReactionBar(
    reactionCounts: Map<ReactionType, Int>,
    userReaction: ReactionType?,
    totalCount: Int,
    onShowPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onShowPicker),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show top 3 reactions with icons
        val topReactions = reactionCounts
            .toList()
            .sortedByDescending { it.second }
            .take(3)
        
        if (topReactions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-4).dp),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                topReactions.forEachIndexed { index, (type, count) ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .padding(1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.toEmoji(),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        
        // Show total count
        Text(
            text = "$totalCount",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

/**
 * Animated reaction button for main action bar
 * Shows current reaction or default like
 */
@Composable
fun AnimatedReactionButton(
    currentReaction: ReactionType?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reaction_button_animation"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .size(44.dp)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (currentReaction != null) {
            // Show user's current reaction
            Text(
                text = currentReaction.toEmoji(),
                fontSize = 28.sp
            )
        } else {
            // Default like icon (outline)
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "React",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
