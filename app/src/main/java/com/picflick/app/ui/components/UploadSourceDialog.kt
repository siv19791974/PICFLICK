package com.picflick.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.ui.graphics.vector.ImageVector
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
data class ActionSheetOption(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPhotoStyleActionSheet(
    title: String,
    options: List<ActionSheetOption>,
    onDismiss: () -> Unit,
    cancelTitle: String = "Cancel",
    cancelSubtitle: String,
    cancelIcon: ImageVector = Icons.Outlined.Menu,
    cancelAccentColor: Color = Color(0xFF4B5563)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF121212),
        contentColor = Color.White,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 44.dp, height = 5.dp),
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.28f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            options.forEachIndexed { index, option ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                UploadOptionRow(
                    icon = option.icon,
                    title = option.title,
                    subtitle = option.subtitle,
                    onClick = option.onClick,
                    accentColor = option.accentColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            UploadOptionRow(
                icon = cancelIcon,
                title = cancelTitle,
                subtitle = cancelSubtitle,
                onClick = onDismiss,
                accentColor = cancelAccentColor
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSourceDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    AddPhotoStyleActionSheet(
        title = "Add Photo",
        options = listOf(
            ActionSheetOption(
                icon = Icons.Default.CameraAlt,
                title = "Take Photo",
                subtitle = "Use your camera",
                accentColor = Color(0xFF2E86DE),
                onClick = onCameraClick
            ),
            ActionSheetOption(
                icon = Icons.Default.PhotoLibrary,
                title = "Choose from Gallery",
                subtitle = "Select existing photo",
                accentColor = Color(0xFF2E86DE),
                onClick = onGalleryClick
            ),

        ),
        onDismiss = onDismiss,
        cancelSubtitle = "Cancel"
    )
}

@Composable
private fun UploadOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: Color
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

        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color(0xFFB7BDC9),
                fontSize = 14.sp
            )
        }
    }
}
