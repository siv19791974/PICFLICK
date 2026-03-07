package com.picflick.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.picflick.app.data.ReactionType

/**
 * Animated reaction picker that shows on long press
 */
@Composable
fun AnimatedReactionPicker(
    onDismiss: () -> Unit,
    onReactionSelected: (ReactionType) -> Unit,
    currentReaction: ReactionType? = null
) {
    val reactions = listOf(
        ReactionType.LIKE to "❤️",
        ReactionType.LOVE to "😍",
        ReactionType.LAUGH to "😂",
        ReactionType.WOW to "😮",
        ReactionType.FIRE to "🔥"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Reaction bubbles
            Row(
                modifier = Modifier
                    .background(
                        Color(0xFF1C1C1E),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reactions.forEachIndexed { index, (reactionType, emoji) ->
                    val isSelected = currentReaction == reactionType

                    AnimatedReactionBubble(
                        emoji = emoji,
                        reactionType = reactionType,
                        isSelected = isSelected,
                        delay = index * 50,
                        onClick = {
                            onReactionSelected(reactionType)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedReactionBubble(
    emoji: String,
    reactionType: ReactionType,
    isSelected: Boolean,
    delay: Int,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reaction_scale"
    )

    val selectedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selected_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale * selectedScale)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(if (isSelected) 64.dp else 56.dp)
                .background(
                    if (isSelected) Color(0xFFD7ECFF) else Color(0xFF2C2C2E),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )
        }

        if (isSelected) {
            Text(
                text = reactionType.name.lowercase().replaceFirstChar { it.uppercase() },
                color = Color(0xFFD7ECFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
