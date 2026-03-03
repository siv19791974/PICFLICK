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
                caption = "",
                onComplete = { success ->
                    if (success) {
                        Toast.makeText(context, "Photo uploaded!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            showUploadDialog = false
            viewModel.uploadFlick(
                userId = userProfile.uid,
                userDisplayName = userProfile.displayName,
                userPhotoUrl = userProfile.photoUrl,
                imageUri = it,
                context = context,
                caption = "",
                onComplete = { success ->
                    if (success) {
                        Toast.makeText(context, "Photo uploaded!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
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
            // Logo banner - extends to top of screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(top = 36.dp, bottom = 8.dp), // Logo slightly higher
                contentAlignment = Alignment.Center
            ) {
                LogoImage()
            }
            
            // MENU BANNER - Same grey, with hamburger, friends, and profile
            var profileMenuExpanded by remember { mutableStateOf(false) }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(vertical = 8.dp)
            ) {
                // LEFT: Hamburger menu icon
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // CENTER: My Friends icon (custom two people)
                IconButton(
                    onClick = { onNavigate("friends") },
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                ) {
                    // Custom friends icon - two person icons
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background person (slightly offset)
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(20.dp)
                                .offset(x = (-4).dp, y = (-2).dp)
                        )
                        // Foreground person
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "My Friends",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(22.dp)
                                .offset(x = 4.dp, y = 2.dp)
                        )
                    }
                }
                
                // RIGHT: Profile picture (or default icon)
                IconButton(
                    onClick = { profileMenuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    if (userProfile.photoUrl.isNotEmpty()) {
                        // Show actual profile picture (CIRCULAR)
                        AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = "My Profile",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape) // CIRCLE CLIP
                                .background(Color.White, androidx.compose.foundation.shape.CircleShape),
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
            .clickable { },
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
