package com.picflick.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * WhatsApp-style Chat Detail Screen
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ChatDetailScreen(
    chatSession: ChatSession,
    otherUserId: String,
    currentUser: UserProfile,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onUserProfileClick: (String) -> Unit = {}
) {
    val chatId = chatSession.id
    val otherUserName = chatSession.participantNames[otherUserId] ?: "Unknown"
    val otherUserPhoto = chatSession.participantPhotos[otherUserId] ?: ""

    var messageText by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isDarkMode = ThemeManager.isDarkMode.value

    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.loadMessages(chatId) }
    )

    val context = LocalContext.current

    // Image picker for sending photos
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            // Send photo message
            viewModel.sendPhotoMessage(
                chatId = chatId,
                imageUri = imageUri,
                senderId = currentUser.uid,
                recipientId = otherUserId,
                senderName = currentUser.displayName,
                senderPhotoUrl = currentUser.photoUrl,
                context = context,
                onComplete = {
                    // Photo sent successfully
                }
            )
        }
    }

    // Load messages
    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
        // Mark messages as read
        viewModel.markAsRead(chatId, currentUser.uid)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(viewModel.messages.size - 1)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Custom compact 48dp title bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(PicFlickBannerBackground),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Profile photo - clickable to view profile
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onUserProfileClick(otherUserId) }
                    ) {
                        if (otherUserPhoto.isNotEmpty()) {
                            AsyncImage(
                                model = otherUserPhoto,
                                contentDescription = otherUserName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // Online indicator
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366))
                                .align(Alignment.BottomEnd)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // User info column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = otherUserName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "online",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Message input area - WhatsApp style
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PicFlickBannerBackground,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    // Reply preview
                    if (replyToMessage != null) {
                        ReplyPreview(
                            message = replyToMessage!!,
                            onCancel = { replyToMessage = null }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Paperclip/Attachment button
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach photo",
                                tint = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))

                        // Text field with emoji placeholder
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text(if (replyToMessage != null) "Reply to message..." else "Message") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 120.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send button
                        FloatingActionButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendMessage(
                                        chatId = chatId,
                                        text = messageText.trim(),
                                        senderId = currentUser.uid,
                                        recipientId = otherUserId,
                                        senderName = currentUser.displayName,
                                        senderPhotoUrl = currentUser.photoUrl,
                                        replyToMessage = replyToMessage
                                    ) {
                                        messageText = ""
                                        replyToMessage = null
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White
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
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(isDarkModeBackground(isDarkMode))
            ) {
                // Error message display - compact snackbar style, only when NOT loading
                if (viewModel.errorMessage != null && !viewModel.isLoading) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { 
                                    viewModel.clearError()
                                    viewModel.loadMessages(chatId) 
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Retry", 
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // Messages list - takes remaining space with weight
                when {
                    viewModel.isLoading && viewModel.messages.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    viewModel.messages.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No messages yet",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Say hello!",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = viewModel.messages,
                                key = { it.id }
                            ) { message ->
                                val isMe = message.senderId == currentUser.uid
                                ChatBubble(
                                    message = message,
                                    isMe = isMe,
                                    otherUserPhoto = if (isMe) "" else otherUserPhoto,
                                    onReplyClick = { replyToMessage = message }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            // PullRefreshIndicator
            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    otherUserPhoto: String,
    onReplyClick: () -> Unit = {}
) {
    // Sexy gradient backgrounds for message bubbles
    val sentBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF667eea), // Soft purple
            Color(0xFF764ba2)  // Deep purple
        )
    )
    
    val receivedBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFf093fb), // Pink
            Color(0xFFf5576c)  // Coral
        )
    )
    
    val bubbleBrush = if (isMe) sentBrush else receivedBrush

    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 4.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = { onReplyClick() }
            ),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Other user's photo (for received messages)
        if (!isMe && otherUserPhoto.isNotEmpty()) {
            AsyncImage(
                model = otherUserPhoto,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (!isMe) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Message bubble with sexy gradient and shadow
        Column(
            modifier = Modifier
                .padding(horizontal = if (isMe) 8.dp else 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 4.dp,
                        shape = bubbleShape,
                        ambientColor = if (isMe) Color(0xFF667eea) else Color(0xFFf5576c),
                        spotColor = if (isMe) Color(0xFF667eea) else Color(0xFFf5576c)
                    )
                    .background(bubbleBrush, bubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Show quoted message if this is a reply
                    if (message.isReply()) {
                        QuotedMessage(
                            quotedSenderName = message.quotedSenderName ?: "Unknown",
                            quotedText = message.quotedText ?: "",
                            isMe = isMe
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        color = Color.White, // White text on gradient
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time and status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = formatMessageTime(message.timestamp),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            // Message status icon with sexy colors
                            when {
                                message.read -> {
                                    Text(
                                        text = "✓✓",
                                        fontSize = 12.sp,
                                        color = Color(0xFF00E5FF) // Cyan glow for read
                                    )
                                }
                                message.delivered -> {
                                    Text(
                                        text = "✓✓",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "✓",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Reply button for received messages
            if (!isMe) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onReplyClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Reply preview component shown above text input
 */
@Composable
private fun ReplyPreview(
    message: ChatMessage,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Replying to ${message.senderName}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message.text.take(50),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel reply",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Quoted message preview component for replies with sexy styling
 */
@Composable
private fun QuotedMessage(
    quotedSenderName: String,
    quotedText: String,
    isMe: Boolean
) {
    val quoteColor = if (isMe) Color(0xFF00E5FF) else Color(0xFFffd1ff) // Cyan or soft pink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.15f), 
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(quoteColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = quotedSenderName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = quoteColor
            )
            Text(
                text = quotedText,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}
