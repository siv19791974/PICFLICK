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
            // Message input area - WhatsApp style with keyboard padding
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
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
                // Error message display
                viewModel.errorMessage?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { 
                                    viewModel.clearError()
                                    viewModel.loadMessages(chatId) 
                                }
                            ) {
                                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // Messages list
                when {
                    viewModel.isLoading && viewModel.messages.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    viewModel.messages.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            state = listState,
                            contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 16.dp)
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
                                    currentUserId = currentUser.uid,
                                    onReplyClick = { replyToMessage = message },
                                    onReaction = { emoji ->
                                        viewModel.addReaction(chatId, message.id, currentUser.uid, emoji)
                                    }
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
    currentUserId: String,
    onReplyClick: () -> Unit = {},
    onReaction: (String) -> Unit = {}
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    
    // MID BLUE for User A (sender/me), GREY for User B (other)
    val sentColor = if (isDarkMode) Color(0xFF2A4A73) else Color(0xFFB8D4F0)      // Mid blue
    val receivedColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)  // Grey
    
    val bubbleColor = if (isMe) sentColor else receivedColor

    // Reaction state
    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiReactions = listOf("❤️", "😂", "😮", "😢", "👍", "🔥", "👏")

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
                onLongClick = { showEmojiPicker = !showEmojiPicker }
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
                    .background(bubbleColor, bubbleShape)
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
                    
                    // Message text with timestamp inline at top
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Message text - wraps naturally
                        Text(
                            text = message.text,
                            fontSize = 15.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.wrapContentWidth(),
                            softWrap = true
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Time and status - fixed at top right
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = formatMessageTime(message.timestamp),
                                fontSize = 11.sp,
                                color = Color.Black.copy(alpha = 0.7f)
                            )
                            
                            if (isMe) {
                                Spacer(modifier = Modifier.width(4.dp))
                                // Read status dot (green = read, red = unread)
                                val dotColor = if (message.read) Color(0xFF25D366) else Color.Red
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                    
                    // Reactions row
                    if (message.reactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            message.reactions.values.toSet().forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                    
                    // Emoji picker
                    if (showEmojiPicker) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            emojiReactions.forEach { emoji ->
                                TextButton(
                                    onClick = {
                                        onReaction(emoji)
                                        showEmojiPicker = false
                                    }
                                ) {
                                    Text(emoji, fontSize = 18.sp)
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
 * Quoted message preview component for replies
 */
@Composable
private fun QuotedMessage(
    quotedSenderName: String,
    quotedText: String,
    isMe: Boolean
) {
    val quoteColor = if (isMe) Color(0xFF8BC34A) else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Black.copy(alpha = 0.05f), shape = RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .background(quoteColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = quotedSenderName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = quoteColor
            )
            Text(
                text = quotedText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
