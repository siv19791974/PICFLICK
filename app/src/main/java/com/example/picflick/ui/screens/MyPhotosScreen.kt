package com.example.picflick.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.picflick.ui.components.TopBarWithBackButton
import com.example.picflick.viewmodel.ProfileViewModel

/**
 * Screen showing current user's photos
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                title = "My Photos",
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
