package com.example.picflick.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MailOutline
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
import com.example.picflick.ui.components.FullScreenLoading
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.viewmodel.HomeViewModel
import java.io.File

/**
 * Menu items for the hamburger navigation
 */
private val menuItems = listOf(
    "Home" to "home",
    "My Photos" to "my_photos",
    "Find Friends" to "find_friends",
    "Messages" to "chats",
    "Notifications" to "notifications",
    "About" to "about",
    "Contact Us" to "contact"
)

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
    var menuExpanded by remember { mutableStateOf(false) } // Hamburger menu state
    var selectedFlick by remember { mutableStateOf<Flick?>(null) } // For full-screen photo view
    var profileMenuExpanded by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Mini FABs that appear when main FAB is clicked
                if (showUploadDialog) {
                    // Gallery option
                    SmallFloatingActionButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Text("Gallery", fontSize = 12.sp)
                    }

                    // Camera option
                    SmallFloatingActionButton(
                        onClick = {
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
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Text("Camera", fontSize = 12.sp)
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = { showUploadDialog = !showUploadDialog }
                ) {
                    Icon(
                        if (showUploadDialog) Icons.Default.Close else Icons.Default.Add,
                        if (showUploadDialog) "Close" else "Upload"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PicFlickBackground)
        ) {
            // Logo banner at VERY TOP (with space from notification bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(top = 36.dp, bottom = 8.dp)
            ) {
                LogoImage(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // MENU BANNER - Same grey, with hamburger, friends, and profile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // LEFT: Hamburger menu
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // CENTER: My Friends icon (custom two people)
                IconButton(
                    onClick = { onNavigate("friends") },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Two overlapping person icons for "friends" look
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "My Friends",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = (-4).dp, y = 2.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(26.dp)
                                .offset(x = 4.dp, y = (-2).dp)
                        )
                    }
                }

                // RIGHT: Profile picture (or default icon)
                IconButton(
                    onClick = { profileMenuExpanded = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    if (userProfile.photoUrl.isNotEmpty()) {
                        // Show actual profile photo
                        AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = "My Profile",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Default icon
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "My Profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Main Dropdown menu (from hamburger)
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    menuItems.forEach { (label, route) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontSize = 14.sp
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNavigate(route)
                            }
                        )
                    }
                }

                // Profile Dropdown menu (from profile icon)
                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    // My Profile option
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "My Profile",
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            profileMenuExpanded = false
                            onNavigate("profile")
                        }
                    )
                    // Sign Out option
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sign Out",
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            profileMenuExpanded = false
                            onSignOut()
                        }
                    )
                }
            }

            // Content BELOW menu - HAS PADDING
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
                        onLikeClick = { flick -> viewModel.toggleLike(flick, userProfile.uid) },
                        onPhotoClick = { flick -> selectedFlick = flick }
                    )
                }
            }
        }
    }

    // Full-screen photo viewer dialog
    selectedFlick?.let { flick ->
        FullScreenPhotoDialog(
            flick = flick,
            userId = userProfile.uid,
            onDismiss = { selectedFlick = null },
            onLikeClick = { viewModel.toggleLike(flick, userProfile.uid) }
        )
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
        modifier = Modifier.fillMaxSize()
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
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
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
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like button
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (flick.likes.contains(userId)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (flick.likes.contains(userId)) "Unlike" else "Like",
                        tint = if (flick.likes.contains(userId)) Color.Red else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "${flick.likes.size}",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )

                // Comment icon and count
                Icon(
                    imageVector = Icons.Outlined.MailOutline,
                    contentDescription = "Comments",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${flick.commentCount}",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp)
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

/**
 * Full-screen photo viewer dialog
 */
@Composable
private fun FullScreenPhotoDialog(
    flick: Flick,
    userId: String,
    onDismiss: () -> Unit,
    onLikeClick: () -> Unit
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
                model = flick.imageUrl,
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
                if (flick.description.isNotEmpty()) {
                    Text(
                        text = flick.description,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Like and comment row
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onLikeClick) {
                        Icon(
                            imageVector = if (flick.likes.contains(userId)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (flick.likes.contains(userId)) Color.Red else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "${flick.likes.size} likes",
                        color = Color.White,
                        modifier = Modifier.padding(end = 16.dp)
                    )

                    Icon(
                        imageVector = Icons.Outlined.MailOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "${flick.commentCount} comments",
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}