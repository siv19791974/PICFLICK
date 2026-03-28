package com.picflick.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet dialog for selecting upload source - Camera or Gallery
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSourceDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onSharePrivatelyClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add Photo",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Camera Option
            UploadOptionRow(
                icon = Icons.Outlined.Add,
                title = "Take Photo",
                subtitle = "Use your camera",
                onClick = onCameraClick,
                backgroundColor = Color(0xFF5FB9FF)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Gallery Option
            UploadOptionRow(
                icon = Icons.Outlined.Menu,
                title = "Choose from Gallery",
                subtitle = "Select existing photo",
                onClick = onGalleryClick,
                backgroundColor = Color(0xFF6EC2FF)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Private share option
            UploadOptionRow(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "Share privately 🎭",
                subtitle = "Send to individual or group only",
                onClick = onSharePrivatelyClick,
                backgroundColor = Color(0xFF7BC9FF)
            )

            Spacer(modifier = Modifier.height(16.dp))

            UploadOptionRow(
                icon = Icons.Outlined.Menu,
                title = "Cancel",
                subtitle = "Close add photo",
                onClick = onDismiss,
                backgroundColor = Color(0xFF89D0FF)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UploadOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    backgroundColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}
