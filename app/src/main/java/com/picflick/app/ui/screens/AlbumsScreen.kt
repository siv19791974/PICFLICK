package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.Album
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.PhotoRepository
import com.picflick.app.ui.components.ActionSheetRow
import com.picflick.app.ui.components.AlbumCardShimmer
import com.picflick.app.ui.theme.ThemeManager

/**
 * Albums Screen - View and manage photo albums
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class)
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
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun loadAlbums(isManualRefresh: Boolean = false) {
        isLoading = true
        if (isManualRefresh) {
            isRefreshing = true
        }
        errorMessage = null

        photoRepository.getUserAlbums(userProfile.uid) { result ->
            when (result) {
                is com.picflick.app.data.Result.Success -> {
                    albums = result.data
                    isLoading = false
                    isRefreshing = false
                }
                is com.picflick.app.data.Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                    isRefreshing = false
                }
                else -> Unit
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { loadAlbums(isManualRefresh = true) }
    )

    val filteredAlbums = remember(albums, searchQuery) {
        if (searchQuery.isBlank()) {
            albums
        } else {
            val query = searchQuery.trim().lowercase()
            albums.filter { album ->
                album.name.lowercase().contains(query) || album.description.lowercase().contains(query)
            }
        }
    }

    // Load albums
    LaunchedEffect(userProfile.uid) {
        loadAlbums()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Albums (${filteredAlbums.size})",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Album",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search albums") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                shape = RoundedCornerShape(12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                    isLoading -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            items(6) {
                                AlbumCardShimmer()
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
                            Button(onClick = { loadAlbums() }) {
                                Text("Retry")
                            }
                        }
                    }
                    filteredAlbums.isEmpty() -> {
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
                                text = if (albums.isEmpty()) "No albums yet" else "No albums match your search",
                                color = subtitleColor,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (albums.isEmpty()) {
                                Button(onClick = { showCreateDialog = true }) {
                                    Text("Create Album")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = filteredAlbums,
                                key = { it.id },
                                contentType = { "album_row" }
                            ) { album ->
                                AlbumRowItem(
                                    album = album,
                                    isDarkMode = isDarkMode,
                                    onClick = { onAlbumClick(album) }
                                )
                            }
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = MaterialTheme.colorScheme.primary,
                    backgroundColor = backgroundColor
                )
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
 * Album row item (messages-style sizing)
 */
@Composable
private fun AlbumRowItem(
    album: Album,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val rowBackground = if (isDarkMode) Color(0xFF111319) else Color(0xFFD7E6F5)
    val titleColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtitleColor = if (isDarkMode) Color.LightGray else Color(0xFF4B5563)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(rowBackground)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(if (isDarkMode) Color(0xFF1F2937) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (album.coverPhotoUrl.isNotEmpty()) {
                AsyncImage(
                    model = album.coverPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = album.name.firstOrNull()?.uppercase() ?: "A",
                    color = if (isDarkMode) Color.White else Color(0xFF1F2937),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (album.photoCount == 1) "1 photo" else "${album.photoCount} photos",
                color = subtitleColor,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }

    HorizontalDivider(
        color = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color(0xFFB7CAE0),
        thickness = 1.dp,
        modifier = Modifier.padding(start = 94.dp, end = 12.dp)
    )
}

/**
 * Create Album Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF121212),
            dragHandle = { Surface(modifier = Modifier.padding(top = 8.dp).size(width = 44.dp, height = 5.dp), shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.28f)) {} }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Create New Album", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Album Name", color = Color(0xFFB7BDC9)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2A4A73), unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF2A4A73), unfocusedLabelColor = Color(0xFFB7BDC9),
                    cursorColor = Color(0xFF2A4A73)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)", color = Color(0xFFB7BDC9)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2A4A73), unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF2A4A73), unfocusedLabelColor = Color(0xFFB7BDC9),
                    cursorColor = Color(0xFF2A4A73)
                )
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionSheetRow(icon = Icons.Default.Close, title = "Cancel", accentColor = Color.Gray, onClick = onDismiss)
                ActionSheetRow(icon = Icons.Default.Create, title = "Create", accentColor = Color(0xFF4CAF50), onClick = { onCreate(name, description) })
            }
        }
    }
}
