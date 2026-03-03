package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.picflick.data.Flick
import com.example.picflick.ui.components.ErrorMessage
import com.example.picflick.ui.components.FullScreenLoading
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.viewmodel.ProfileViewModel

/**
 * Screen showing current user's photos
 */
@Composable
fun MyPhotosScreen(
    viewModel: ProfileViewModel,
    userId: String,
    onBack: () -> Unit
) {
    // Load user photos
    LaunchedEffect(userId) {
        viewModel.loadUserPhotos(userId)
    }

    var selectedPhoto by remember { mutableStateOf<Flick?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground)
    ) {
        // Logo banner at top with back button inside
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PicFlickBannerBackground)
                .padding(top = 36.dp, bottom = 8.dp)
        ) {
            // Back button on the left
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .clickable { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )

            // Logo centered - CLICKABLE to go home
            LogoImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable { onBack() }
            )
        }

        when {
            viewModel.isLoading -> FullScreenLoading()
            viewModel.errorMessage != null -> ErrorMessage(
                message = viewModel.errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadUserPhotos(userId) }
            )
            viewModel.photos.isEmpty() -> EmptyMyPhotosState()
            else -> PhotoGrid(
                photos = viewModel.photos,
                onPhotoClick = { photo -> selectedPhoto = photo }
            )
        }
    }

    // Full-screen photo viewer dialog
    selectedPhoto?.let { photo ->
        MyPhotoFullScreenDialog(
            photo = photo,
            userId = userId,
            onDismiss = { selectedPhoto = null }
        )
    }
}

@Composable
private fun PhotoGrid(
    photos: List<Flick>,
    onPhotoClick: (Flick) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos) { photo ->
            AsyncImage(
                model = photo.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .padding(2.dp)
                    .aspectRatio(1f)
                    .clickable { onPhotoClick(photo) },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun EmptyMyPhotosState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No photos yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Full-screen photo viewer dialog for My Photos
 */
@Composable
private fun MyPhotoFullScreenDialog(
    photo: Flick,
    userId: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Photo
            AsyncImage(
                model = photo.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Bottom info bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                // Description if available
                if (photo.description.isNotEmpty()) {
                    Text(
                        text = photo.description,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Likes and comments row
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (photo.likes.contains(userId)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Likes",
                        tint = if (photo.likes.contains(userId)) Color.Red else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "${photo.likes.size} likes",
                        color = Color.White,
                        modifier = Modifier.padding(end = 16.dp, start = 4.dp)
                    )

                    Icon(
                        imageVector = Icons.Outlined.MailOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "${photo.commentCount} comments",
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}