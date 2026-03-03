package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
                    .size(24.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .clickable { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            
            // Logo centered
            LogoImage(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        when {
            viewModel.isLoading -> FullScreenLoading()
            viewModel.errorMessage != null -> ErrorMessage(
                message = viewModel.errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadUserPhotos(userId) }
            )
            viewModel.photos.isEmpty() -> EmptyMyPhotosState()
            else -> PhotoGrid(photos = viewModel.photos)
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<Flick>) {
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
                    .aspectRatio(1f),
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
