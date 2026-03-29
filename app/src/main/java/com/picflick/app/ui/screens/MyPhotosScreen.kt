package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.Flick
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.ChatRepository
import com.picflick.app.ui.components.*
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Screen showing current user's photos - MATCHES HOMESCREEN EXACTLY
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MyPhotosScreen(
    viewModel: ProfileViewModel,
    userId: String,
    currentUser: UserProfile,
    onBack: () -> Unit,
    onEditPhotoClick: (Flick) -> Unit = {} // Add edit photo callback
) {
    val context = LocalContext.current
    val isDarkMode = ThemeManager.isDarkMode.value
    var selectedPhoto by remember { mutableStateOf<Flick?>(null) }
    val scope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }

    // Load user photos
    LaunchedEffect(userId) {
        viewModel.loadUserPhotos(userId)
    }

    // Modern PullRefresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.loadUserPhotos(userId) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode)),
    ) {
        // Modern PullRefresh content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
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
                    onPhotoClick = { photo -> selectedPhoto = photo }
                )
            }

            // Modern PullRefreshIndicator
            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
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
            onReaction = { reactionType ->
                // Handle reaction in viewModel if needed
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
            onDeleteClick = { /* Handle delete in viewModel if needed */ },
            onShareToFriend = { flickId, friendId ->
                val flickToSend = viewModel.photos.firstOrNull { it.id == flickId } ?: photo
                if (flickToSend.imageUrl.isBlank()) {
                    android.widget.Toast.makeText(context, "Photo unavailable to share", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        when (val sessionResult = chatRepository.getOrCreateChatSession(
                            userId1 = currentUser.uid,
                            userId2 = friendId,
                            user1Name = currentUser.displayName,
                            user2Name = "Friend",
                            user1Photo = currentUser.photoUrl,
                            user2Photo = ""
                        )) {
                            is com.picflick.app.data.Result.Success -> {
                                val message = ChatMessage(
                                    chatId = sessionResult.data,
                                    senderId = currentUser.uid,
                                    senderName = currentUser.displayName,
                                    senderPhotoUrl = currentUser.photoUrl,
                                    text = "",
                                    imageUrl = flickToSend.imageUrl,
                                    flickId = flickToSend.id,
                                    timestamp = System.currentTimeMillis(),
                                    read = false,
                                    delivered = false
                                )
                                when (val sendResult = chatRepository.sendMessage(sessionResult.data, message, friendId)) {
                                    is com.picflick.app.data.Result.Success -> android.widget.Toast.makeText(context, "Photo sent", android.widget.Toast.LENGTH_SHORT).show()
                                    is com.picflick.app.data.Result.Error -> android.widget.Toast.makeText(context, sendResult.message, android.widget.Toast.LENGTH_SHORT).show()
                                    is com.picflick.app.data.Result.Loading -> Unit
                                }
                            }
                            is com.picflick.app.data.Result.Error -> android.widget.Toast.makeText(context, sessionResult.message, android.widget.Toast.LENGTH_SHORT).show()
                            is com.picflick.app.data.Result.Loading -> Unit
                        }
                    }
                }
            },
            onEditPhotoClick = { flick ->
                // Navigate to edit photo screen
                onEditPhotoClick(flick)
            }
        )
    }
}

@Composable
private fun PhotoGrid(
    photos: List<Flick>,
    onPhotoClick: (Flick) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // Calculate height for 4 rows with tiny gap at bottom for light blue BG
        val rowHeight = this.maxHeight / 4.1f
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 1.dp,
                end = 1.dp,
                top = 4.dp,  // Slight top gap to match bottom
                bottom = 0.dp
            ),
            userScrollEnabled = true // Enable scrolling for pull-to-refresh
        ) {
            // Show all items (scrollable)
            items(
                items = photos,
                key = { it.id },
                contentType = { "photo" }
            ) { photo ->
                PhotoCard(
                    photo = photo,
                    onPhotoClick = { onPhotoClick(photo) },
                    rowHeight = rowHeight
                )
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: Flick,
    onPhotoClick: () -> Unit,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    Card(
        modifier = Modifier
            .padding(1.dp) // Smaller padding
            .height(rowHeight) // Fixed height for exact 4 rows
            .clickable { onPhotoClick() },
        shape = RectangleShape, // NO rounded corners
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // No elevation
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // Transparent background
        )
    ) {
        // CLEAN thumbnail - no overlay, no rounded corners
        AsyncImage(
            model = photo.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(), // Fill the exact height
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun EmptyMyPhotosState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No photos yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Upload your first photo to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
