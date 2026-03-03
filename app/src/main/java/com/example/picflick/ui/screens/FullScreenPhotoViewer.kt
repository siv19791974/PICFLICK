package com.example.picflick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.picflick.data.Comment
import com.example.picflick.data.Flick
import com.example.picflick.data.UserProfile
import com.example.picflick.repository.FlickRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPhotoViewer(
    flick: Flick,
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    canDelete: Boolean = false,
    onCaptionUpdated: (String) -> Unit = {}
) {
    val repository = remember { FlickRepository.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var newCommentText by remember { mutableStateOf("") }
    var isLoadingComments by remember { mutableStateOf(true) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditCaption by remember { mutableStateOf(false) }
    var editCaptionText by remember { mutableStateOf(flick.description) }
    var currentDescription by remember { mutableStateOf(flick.description) }
    
    // Load comments
    LaunchedEffect(flick.id) {
        repository.getComments(flick.id) { result ->
            when (result) {
                is com.example.picflick.data.Result.Success -> {
                    comments = result.data
                    isLoadingComments = false
                }
                else -> isLoadingComments = false
            }
        }
    }
    
    // Edit caption dialog
    if (showEditCaption && canDelete) {
        AlertDialog(
            onDismissRequest = { showEditCaption = false },
            title = { Text("Edit Caption") },
            text = {
                OutlinedTextField(
                    value = editCaptionText,
                    onValueChange = { editCaptionText = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.updateFlickDescription(flick.id, editCaptionText)
                            currentDescription = editCaptionText
                            onCaptionUpdated(editCaptionText)
                            showEditCaption = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCaption = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to delete this photo? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar with actions
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Share button
                        IconButton(onClick = onShareClick) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                        // Delete button (only for owner)
                        if (canDelete) {
                            IconButton(onClick = onDeleteClick) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                
                // Photo
                AsyncImage(
                    model = flick.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentScale = ContentScale.Fit
                )
                
                // Bottom info panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.9f))
                ) {
                    // Caption
                    if (flick.description.isNotEmpty()) {
                        Text(
                            text = flick.description,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    // Action buttons row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Like
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = onLikeClick) {
                                Icon(
                                    imageVector = if (flick.likes.contains(currentUser.uid)) 
                                        Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (flick.likes.contains(currentUser.uid)) 
                                        Color.Red else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "${flick.likes.size} likes",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        
                        // Comments count
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.MailOutline,
                                contentDescription = "Comments",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "${flick.commentCount} comments",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                    
                    // Comments section
                    Text(
                        text = "Comments",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    // Comments list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        if (isLoadingComments) {
                            item {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        } else if (comments.isEmpty()) {
                            item {
                                Text(
                                    text = "No comments yet. Be the first!",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            items(comments) { comment ->
                                CommentItem(comment = comment)
                            }
                        }
                    }
                    
                    // Add comment input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User avatar
                        AsyncImage(
                            model = currentUser.photoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Text input
                        TextField(
                            value = newCommentText,
                            onValueChange = { newCommentText = it },
                            placeholder = { Text("Add a comment...", color = Color.Gray) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        
                        // Send button
                        IconButton(
                            onClick = {
                                if (newCommentText.isNotBlank()) {
                                    coroutineScope.launch {
                                        val comment = Comment(
                                            flickId = flick.id,
                                            userId = currentUser.uid,
                                            userName = currentUser.displayName,
                                            userPhotoUrl = currentUser.photoUrl,
                                            text = newCommentText.trim()
                                        )
                                        repository.addComment(comment)
                                        newCommentText = ""
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        AsyncImage(
            model = comment.userPhotoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            // Username
            Text(
                text = comment.userName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
            // Comment text
            Text(
                text = comment.text,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}