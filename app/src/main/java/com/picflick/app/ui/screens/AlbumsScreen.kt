package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.Album
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.PhotoRepository
import com.picflick.app.ui.components.ListItemShimmer
import com.picflick.app.ui.theme.ThemeManager

/**
 * Albums Screen - View and manage photo albums
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    userProfile: UserProfile,
    photoRepository: PhotoRepository = PhotoRepository.getInstance(),
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onCreateAlbum: () -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Load albums
    LaunchedEffect(userProfile.uid) {
        photoRepository.getUserAlbums(userProfile.uid) { result ->
            when (result) {
                is com.picflick.app.data.Result.Success -> {
                    albums = result.data
                    isLoading = false
                }
                is com.picflick.app.data.Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums", color = textColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Album",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(6) {
                            ListItemShimmer()
                        }
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Failed to load albums",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            isLoading = true
                            errorMessage = null
                            photoRepository.getUserAlbums(userProfile.uid) { result ->
                                when (result) {
                                    is com.picflick.app.data.Result.Success -> {
                                        albums = result.data
                                        isLoading = false
                                    }
                                    is com.picflick.app.data.Result.Error -> {
                                        errorMessage = result.message
                                        isLoading = false
                                    }
                                    else -> {}
                                }
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                albums.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = subtitleColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No albums yet",
                            color = subtitleColor,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Text("Create Album")
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(albums, key = { it.id }) { album ->
                            AlbumItem(
                                album = album,
                                isDarkMode = isDarkMode,
                                onClick = { onAlbumClick(album) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Album Dialog
    if (showCreateDialog) {
        CreateAlbumDialog(
            isDarkMode = isDarkMode,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                photoRepository.createAlbum(
                    userId = userProfile.uid,
                    name = name,
                    description = description
                ) { result ->
                    when (result) {
                        is com.picflick.app.data.Result.Success -> {
                            albums = albums + result.data
                        }
                        else -> {}
                    }
                }
                showCreateDialog = false
            }
        )
    }
}

/**
 * Album grid item
 */
@Composable
private fun AlbumItem(
    album: Album,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val cardBackground = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Album cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        if (album.coverPhotoUrl.isEmpty())
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            Color.Transparent
                    )
            ) {
                if (album.coverPhotoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = album.coverPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = subtitleColor.copy(alpha = 0.5f)
                    )
                }

                // Photo count badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${album.photoCount}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Album info
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = album.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = textColor,
                    maxLines = 1
                )
                if (album.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = album.description,
                        fontSize = 12.sp,
                        color = subtitleColor,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/**
 * Create Album Dialog
 */
@Composable
private fun CreateAlbumDialog(
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = backgroundColor,
        title = {
            Text(
                text = "Create New Album",
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, description) },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = if (name.isNotBlank()) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.5f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor)
            }
        }
    )
}
