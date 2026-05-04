package com.picflick.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.R

/**
 * Google Sign In button with loading state
 */
@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Sign in with Google",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Loading button with progress indicator
 */
@Composable
fun LoadingButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
        }
        Text(text = text)
    }
}
}

/**
 * Reusable action-sheet row used across bottom-sheet popups (Report, Block, Mute, Delete, etc.)
 */
@Composable
fun ActionSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accentColor: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1F26))
            .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color(0xFFB7BDC9),
                    fontSize = 14.sp
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * PicFlick-coloured stat circle showing Mythic streak percentage.
 * Background cycles through the logo rainbow (Blue→Green→Yellow→Orange→Red)
 * based on progress. Matches the other profile stat circles.
 */
@Composable
fun MythicProgressRing(
    streak: Int,
    threshold: Int,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val progress = (streak.toFloat() / threshold).coerceIn(0f, 1f)
    val achieved = streak >= threshold

    // PicFlick logo colour spectrum: P-i-c-F-l-(c/k)
    val bgColor = if (achieved) {
        Color(0xFFFFB300) // Gold when 100 % achieved
    } else {
        mythicSpectrumColor(progress)
    }

    val valueColor = if (isDarkMode) Color.White else Color(0xFF0D2A45)
    val labelColor = if (isDarkMode) Color(0xFFBFD6EA) else Color(0xFF1F4D74)
    val percentText = "${(progress * 100).toInt()}%"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .border(1.dp, Color.Black, CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = percentText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor,
                    lineHeight = 18.sp
                )
                Text(
                    text = "MYTHIC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    lineHeight = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "ACHEIVEMENTS",
            fontSize = 11.sp,
            color = labelColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Interpolates through the PicFlick logo rainbow based on 0..1 progress. */
private fun mythicSpectrumColor(progress: Float): Color {
    val colors = listOf(
        Color(0xFF1565C0), // P  – Dark Blue
        Color(0xFF42A5F5), // i  – Light Blue
        Color(0xFF4CAF50), // c  – Green
        Color(0xFFFFC107), // F  – Yellow
        Color(0xFFFF9800), // l  – Orange
        Color(0xFFF44336)  // c/k – Red
    )
    val scaled = progress * (colors.size - 1)
    val index = scaled.toInt().coerceIn(0, colors.size - 2)
    val fraction = (scaled - index).coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.lerp(colors[index], colors[index + 1], fraction)
}

@Preview(name = "MythicProgressRing - Low")
@Composable
fun MythicProgressRingLowPreview() {
    MythicProgressRing(streak = 12, threshold = 100, isDarkMode = true)
}

@Preview(name = "MythicProgressRing - Mid")
@Composable
fun MythicProgressRingMidPreview() {
    MythicProgressRing(streak = 53, threshold = 100, isDarkMode = true)
}

@Preview(name = "MythicProgressRing - Achieved")
@Composable
fun MythicProgressRingAchievedPreview() {
    MythicProgressRing(streak = 100, threshold = 100, isDarkMode = true)
}
