package com.example.picflick.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.picflick.R
import com.example.picflick.data.Flick
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.ErrorMessage
import com.example.picflick.ui.components.FullScreenLoading
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.viewmodel.HomeViewModel
import java.io.File

/**
 * Home screen with photo grid and upload functionality
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
            Toast.makeText(context, "Photo captured! Upload coming soon...", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            showUploadDialog = false
            Toast.makeText(context, "Photo selected! Upload coming soon...", Toast.LENGTH_SHORT).show()
        }
    }

    // Show error if any
    viewModel.errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Show remaining count
                if (viewModel.todayUploadCount > 0) {
                    Text(
                        text = "${15 - viewModel.todayUploadCount}",
                        fontSize = 12.sp,
                        color = if (viewModel.todayUploadCount >= 15) Color.Red else Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Mini FABs that appear when expanded
                if (showUploadDialog) {
                    // Gallery mini FAB
                    SmallFloatingActionButton(
                        onClick = {
                            galleryLauncher.launch("image/*")
                            showUploadDialog = false
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Default.AccountBox, contentDescription = "Gallery")
                    }

                    // Camera mini FAB
                    SmallFloatingActionButton(
                        onClick = {
                            if (viewModel.todayUploadCount >= 15) {
                                Toast.makeText(context, "Daily limit reached! (15/15)", Toast.LENGTH_LONG).show()
                                showUploadDialog = false
                            } else {
                                val tempFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                                showUploadDialog = false
                            }
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Camera")
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = {
                        if (viewModel.todayUploadCount >= 15 && !showUploadDialog) {
                            Toast.makeText(context, "Daily limit reached! (15/15)", Toast.LENGTH_LONG).show()
                        } else {
                            showUploadDialog = !showUploadDialog
                        }
                    },
                    containerColor = if (viewModel.todayUploadCount >= 15) Color.Gray else MaterialTheme.colorScheme.primary
                ) {
                    if (viewModel.todayUploadCount >= 15) {
                        Icon(Icons.Default.Lock, contentDescription = "Upload locked", tint = Color.White)
                    } else {
                        Icon(
                            if (showUploadDialog) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = if (showUploadDialog) "Close" else "Upload"
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PicFlickBackground), // FORCE BACKGROUND COLOR
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Logo in light grey banner - lower on screen
            Spacer(modifier = Modifier.height(48.dp)) // Push down from top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                LogoImage()
            }
            
            // Content BELOW logo - HAS PADDING
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Dismiss mini FABs when clicking outside
                if (showUploadDialog) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showUploadDialog = false }
                    )
                }

                // Content
                when {
                    viewModel.isLoading -> FullScreenLoading()
                    viewModel.errorMessage != null -> ErrorMessage(
                        message = viewModel.errorMessage ?: "Unknown error",
                        onRetry = { viewModel.loadFlicks() }
                    )
                    viewModel.flicks.isEmpty() -> EmptyState()
                    else -> FlickGrid(
                        flicks = viewModel.flicks,
                        userProfile = userProfile,
                        onLikeClick = { flick -> viewModel.toggleLike(flick, userProfile.uid) }
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
    onLikeClick: (Flick) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize()
    ) {
        items(flicks) { flick ->
            FlickCard(
                flick = flick,
                userId = userProfile.uid,
                onLikeClick = { onLikeClick(flick) }
            )
        }
    }
}

@Composable
private fun FlickCard(
    flick: Flick,
    userId: String,
    onLikeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(2.dp)
            .clickable { }
    ) {
        Column {
            AsyncImage(
                model = flick.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Row(modifier = Modifier.padding(4.dp)) {
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (flick.likes.contains(userId)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (flick.likes.contains(userId)) "Unlike" else "Like",
                        tint = if (flick.likes.contains(userId)) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "${flick.likes.size}",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    fontSize = 12.sp
                )
            }
        }
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
