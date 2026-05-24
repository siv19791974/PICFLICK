package com.picflick.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun OnlineStatusBadge(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 9.dp
) {
    if (!isOnline) return

    Box(
        modifier = modifier
            .size(size)
            .background(Color(0xFF20D15A), CircleShape)
            .border(1.dp, Color.White, CircleShape)
    )
}
