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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.Comment
import com.picflick.app.data.Flick
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.PhotoRepository
import com.picflick.app.ui.theme.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comments Screen - View and add comments on a photo
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    flick: Flick,
    currentUser: UserProfile,
    photoRepository: PhotoRepository = PhotoRepository.getInstance(),
    onBack: () -> Unit,
    onUserProfileClick: (String) -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var newCommentText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Load comments
    LaunchedEffect(flick.id) {
        photoRepository.getComments(flick.id) { result ->
            when (result) {
                is com.picflick.app.data.Result.Success -> {
                    comments = result.data
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
                title = { Text("Comments", color = textColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        bottomBar = {
            // Comment input field
            Surface(
                color = backgroundColor,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // User avatar
                    if (currentUser.photoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = currentUser.photoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp),
                            tint = subtitleColor
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text field
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text("Add a comment...", color = subtitleColor) },
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = subtitleColor.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send button
                    IconButton(
                        onClick = {
                            if (newCommentText.isNotBlank() && !isSending) {
                                isSending = true
                                photoRepository.addComment(
                                    flickId = flick.id,
                                    userId = currentUser.uid,
                                    userName = currentUser.displayName,
                                    userPhotoUrl = currentUser.photoUrl,
                                    text = newCommentText.trim()
                                ) { result ->
                                    isSending = false
                                    when (result) {
                                        is com.picflick.app.data.Result.Success -> {
                                            comments = listOf(result.data) + comments
                                            newCommentText = ""
                                        }
                                        is com.picflick.app.data.Result.Error -> {
                                            errorMessage = result.message
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
                        enabled = newCommentText.isNotBlank() && !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (newCommentText.isNotBlank()) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    subtitleColor
                            )
                        }
                    }
                }
            }
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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Failed to load comments",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { 
                            isLoading = true
                            errorMessage = null
                            photoRepository.getComments(flick.id) { result ->
                                when (result) {
                                    is com.picflick.app.data.Result.Success -> {
                                        comments = result.data
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
                comments.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = subtitleColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No comments yet",
                            color = subtitleColor,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Be the first to comment!",
                            color = subtitleColor.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            CommentItem(
                                comment = comment,
                                currentUserId = currentUser.uid,
                                isDarkMode = isDarkMode,
                                onUserClick = { onUserProfileClick(comment.userId) },
                                onLikeClick = {
                                    photoRepository.toggleCommentLike(comment.id, currentUser.uid) { _ -> }
                                },
                                onReplyClick = {
                                    newCommentText = "@${comment.userName} "
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single comment item UI
 */
@Composable
private fun CommentItem(
    comment: Comment,
    currentUserId: String,
    isDarkMode: Boolean,
    onUserClick: () -> Unit,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val isLiked = comment.likedBy.contains(currentUserId)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // User avatar
        if (comment.userPhotoUrl.isNotEmpty()) {
            AsyncImage(
                model = comment.userPhotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onUserClick() }
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
                    .clickable { onUserClick() },
                tint = subtitleColor
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Username and timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.userName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor,
                    modifier = Modifier.clickable { onUserClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(comment.timestamp),
                    fontSize = 12.sp,
                    color = subtitleColor.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment text
            Text(
                text = comment.text,
                fontSize = 14.sp,
                color = textColor,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Actions row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLikeClick() }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        modifier = Modifier.size(16.dp),
                        tint = if (isLiked) Color.Red else subtitleColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = comment.likeCount.toString(),
                        fontSize = 12.sp,
                        color = if (isLiked) Color.Red else subtitleColor
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Reply button
                Text(
                    text = "Reply",
                    fontSize = 12.sp,
                    color = subtitleColor,
                    modifier = Modifier.clickable { onReplyClick() }
                )

                if (comment.replyCount > 0) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${comment.replyCount} replies",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { /* Show replies */ }
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp to relative time
 */
private fun formatTimestamp(date: Date?): String {
    if (date == null) return ""
    
    val now = Date()
    val diffMs = now.time - date.time
    val diffSecs = diffMs / 1000
    val diffMins = diffSecs / 60
    val diffHours = diffMins / 60
    val diffDays = diffHours / 24

    return when {
        diffSecs < 60 -> "Just now"
        diffMins < 60 -> "${diffMins}m"
        diffHours < 24 -> "${diffHours}h"
        diffDays < 7 -> "${diffDays}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
