package com.example.picflick.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.picflick.data.Flick
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.ErrorMessage
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.components.PhotoGridShimmer
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.viewmodel.HomeViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import java.io.File

/**
 * Home screen with photo grid and bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userProfile: UserProfile,
    viewModel: HomeViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var showUploadDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFlick by remember { mutableStateOf<Flick?>(null) }
    var selectedFlickIndex by remember { mutableIntStateOf(0) }
    
    // Swipe refresh state
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = viewModel.isLoading)

    // Load data
    LaunchedEffect(Unit) {
        viewModel.loadFlicks()
    }

    LaunchedEffect(userProfile.uid) {
        viewModel.checkDailyUploads(userProfile.uid)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            showUploadDialog = false
            viewModel.uploadFlick(
                userId = userProfile.uid,
                userDisplayName = userProfile.displayName,
                userPhotoUrl = userProfile.photoUrl,
                imageUri = tempCameraUri!!,
                context = context,
                onComplete = { success ->
                    if (success) {
                        viewModel.checkDailyUploads(userProfile.uid)
                    }
                }
            )
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            showUploadDialog = false
            viewModel.uploadFlick(
                userId = userProfile.uid,
                userDisplayName = userProfile.displayName,
                userPhotoUrl = userProfile.photoUrl,
                imageUri = it,
                context = context,
                onComplete = { success ->
                    if (success) {
                        viewModel.checkDailyUploads(userProfile.uid)
                    }
                }
            )
        }
    }

    // Column WITHOUT verticalScroll (because LazyVerticalGrid has its own scroll)
    // NO BANNER HERE - banner is now in MainActivity's Scaffold topBar!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PicFlickBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Content directly - banner is in MainActivity
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Direct content - no SwipeRefresh wrapper for testing
            when {
                viewModel.isLoading && viewModel.flicks.isEmpty() -> PhotoGridShimmer()
                viewModel.errorMessage != null -> ErrorMessage(
                    message = viewModel.errorMessage ?: "Unknown error",
                    onRetry = { viewModel.loadFlicks() }
                )
                viewModel.flicks.isEmpty() -> EmptyState()
                else -> FlickGrid(
                    flicks = viewModel.flicks,
                    userProfile = userProfile,
                    onLikeClick = { flick -> viewModel.toggleLike(flick, userProfile.uid) },
                    onPhotoClick = { flick -> 
                        selectedFlick = flick
                        selectedFlickIndex = viewModel.flicks.indexOf(flick)
                    }
                )
            }

            // Upload mini FABs overlay
            if (showUploadDialog) {
                UploadOverlay(
                    onDismiss = { showUploadDialog = false },
                    onCameraClick = {
                        val photoFile = File(
                            context.cacheDir,
                            "photo_${System.currentTimeMillis()}.jpg"
                        )
                        tempCameraUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraLauncher.launch(tempCameraUri!!)
                        showUploadDialog = false
                    },
                    onGalleryClick = {
                        galleryLauncher.launch("image/*")
                        showUploadDialog = false
                    }
                )
            }
        }
    }

    // Full-screen photo viewer with comments
    selectedFlick?.let { flick ->
        FullScreenPhotoViewer(
            flick = flick,
            currentUser = userProfile,
            onDismiss = { selectedFlick = null },
            onLikeClick = {
                viewModel.toggleLike(flick, userProfile.uid)
                // Update local copy for UI
                selectedFlick = flick.copy(
                    likes = if (flick.likes.contains(userProfile.uid)) {
                        flick.likes - userProfile.uid
                    } else {
                        flick.likes + userProfile.uid
                    }
                )
            },
            onShareClick = {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Check out this photo on PicFlick: ${flick.imageUrl}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
            },
            canDelete = flick.userId == userProfile.uid,
            allPhotos = viewModel.flicks,
            currentIndex = selectedFlickIndex,
            onNavigateToPhoto = { index ->
                selectedFlickIndex = index
                selectedFlick = viewModel.flicks.getOrNull(index)
            }
        )
    }
}

@Composable
private fun UploadOverlay(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Upload Photo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Camera option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onCameraClick() }
                ) {
                    FloatingActionButton(
                        onClick = onCameraClick,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        text = "Camera",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                
                // Gallery option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onGalleryClick() }
                ) {
                    FloatingActionButton(
                        onClick = onGalleryClick,
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Gallery",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                    Text(
                        text = "Gallery",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FlickGrid(
    flicks: List<Flick>,
    userProfile: UserProfile,
    onLikeClick: (Flick) -> Unit,
    onPhotoClick: (Flick) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 1.dp,
            end = 1.dp,
            top = 1.dp,
            bottom = 80.dp  // Padding for bottom nav
        )
    ) {
        items(flicks) { flick ->
            FlickCard(
                flick = flick,
                userId = userProfile.uid,
                onLikeClick = { onLikeClick(flick) },
                onPhotoClick = { onPhotoClick(flick) }
            )
        }
    }
}

@Composable
private fun FlickCard(
    flick: Flick,
    userId: String,
    onLikeClick: () -> Unit,
    onPhotoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(2.dp)
            .clickable { onPhotoClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.DarkGray.copy(alpha = 0.3f)
        )
    ) {
        // CLEAN thumbnail - no overlay, just the photo
        AsyncImage(
            model = flick.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No photos yet. Upload one!",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

