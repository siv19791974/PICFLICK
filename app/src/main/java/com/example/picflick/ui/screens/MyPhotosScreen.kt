package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.picflick.data.Comment
import com.example.picflick.data.Flick
import com.example.picflick.data.UserProfile
import com.example.picflick.repository.FlickRepository
import com.example.picflick.ui.components.*
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.viewmodel.ProfileViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch

/**
 * Screen showing current user's photos - MATCHES HOMESCREEN EXACTLY
 */
@Composable
fun MyPhotosScreen(
    viewModel: ProfileViewModel,
    userId: String,
    currentUser: UserProfile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedPhoto by remember { mutableStateOf<Flick?>(null) }

    // Load user photos
    LaunchedEffect(userId) {
        viewModel.loadUserPhotos(userId)
    }

    // Swipe refresh state
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoading)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // NO BANNER - banner is now in MainActivity's Scaffold topBar!
        // Simple back button only
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onBack() },
                tint = Color.White
            )
        }

        // SwipeRefresh content
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.loadUserPhotos(userId) },
            indicator = { state, trigger ->
                SwipeRefreshIndicator(
                    state = state,
                    refreshTriggerDistance = trigger,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                viewModel.isLoading && viewModel.photos.isEmpty() -> PhotoGridShimmer()
                viewModel.errorMessage != null -> ErrorMessage(
                    message = viewModel.errorMessage ?: "Unknown error",
                    onRetry = { viewModel.loadUserPhotos(userId) }
                )
                viewModel.photos.isEmpty() -> EmptyMyPhotosState()
                else -> PhotoGrid(
                    photos = viewModel.photos,
                    userId = userId,
                    onPhotoClick = { photo -> selectedPhoto = photo }
                )
            }
        }
    }

    // Full-screen photo viewer with swipe/pinch - SAME AS HOMESCREEN
    selectedPhoto?.let { photo: Flick ->
        val currentIndex = viewModel.photos.indexOf(photo)
        
        FullScreenPhotoViewer(
            flick = photo,
            currentUser = currentUser,
            allPhotos = viewModel.photos,
            currentIndex = currentIndex,
            onDismiss = { selectedPhoto = null },
            onNavigateToPhoto = { index: Int -> 
                selectedPhoto = viewModel.photos.getOrNull(index)
            },
            onLikeClick = { 
                // Handle like in viewModel if needed
            },
            onShareClick = {
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out this photo: ${photo.imageUrl}")
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share photo"))
            },
            canDelete = photo.userId == currentUser.uid,
            onDeleteClick = { /* Handle delete in viewModel if needed */ }
        )
    }
}

@Composable
private fun PhotoGrid(
    photos: List<Flick>,
    userId: String,
    onPhotoClick: (Flick) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(photos) { photo ->
            PhotoCard(
                photo = photo,
                userId = userId,
                onPhotoClick = { onPhotoClick(photo) }
            )
        }
    }
}

@Composable
private fun PhotoCard(
    photo: Flick,
    userId: String,
    onPhotoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onPhotoClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Like count overlay - SAME AS HOMESCREEN
            if (photo.likes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatCount(photo.likes.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1000000 -> "${(count / 1000000)}M"
        count >= 1000 -> "${(count / 1000)}K"
        else -> count.toString()
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