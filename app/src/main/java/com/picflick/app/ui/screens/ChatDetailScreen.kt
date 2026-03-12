package com.picflick.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.tasks.await
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
    var otherUserPhoto by remember { mutableStateOf(chatSession.participantPhotos[otherUserId] ?: "") }

    // Fetch photo from users collection if not in chat session (for old chats)
    LaunchedEffect(otherUserId) {
        if (otherUserPhoto.isEmpty()) {
            try {
                val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(otherUserId)
                    .get()
                    .await()
                otherUserPhoto = userDoc.getString("photoUrl") ?: ""
            } catch (e: Exception) {
                // Keep empty if fetch fails
            }
        }
    }
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
                    // Space for symmetry (removed logo - YELLOW fix)
                    Spacer(modifier = Modifier.weight(1f))
                    // Space for symmetry (same width as back button)
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        bottomBar = {
            // Message input area - WhatsApp style
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),  // Moves up with keyboard, no gap
                color = Color.Black,
                tonalElevation = 0.dp  // Flat black bar
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)  // Reduced vertical padding
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
                .pullRefresh(pullRefreshState)  // Pull-to-refresh on the main content
                .padding(top = padding.calculateTopPadding())
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
                            contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 80.dp)  // YELLOW: Increased bottom padding
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
                                        viewModel.addReaction(chatId, message.id, currentUser.uid, emoji, currentUser.displayName, currentUser.photoUrl)
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            // PullRefreshIndicator - visible at top when pulling
            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
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
        // GREEN: User B profile pics REMOVED completely

        // Message bubble - max 85% width, wraps to content size, aligns top
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f),  // Container allows up to 85%
            contentAlignment = if (isMe) Alignment.TopEnd else Alignment.TopStart
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = if (isMe) 8.dp else 0.dp)
            ) {
                // DARK BLUE: Box aligns to TOP and shrinks to fit content
                Box(
                    modifier = Modifier
                        .wrapContentWidth()  // WRAPS to text content
                        .wrapContentHeight()  // MINIMUM height for text
                        .background(bubbleColor, bubbleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),  // Minimal padding
                    contentAlignment = Alignment.TopStart  // Aligns to TOP
                ) {
                    Column(
                        modifier = Modifier.wrapContentHeight(),  // Shrinks to fit
                        verticalArrangement = Arrangement.Top  // Content at TOP
                    ) {
                        // Show quoted message if this is a reply
                        if (message.isReply()) {
                            QuotedMessage(
                                quotedSenderName = message.quotedSenderName ?: "Unknown",
                                quotedText = message.quotedText ?: "",
                                isMe = isMe
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        
                        // Message text - at TOP of box
                        Text(
                            text = message.text,
                            fontSize = 15.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.wrapContentWidth(),
                            softWrap = true
                        )
                    }
                }
                
                // RED: Timestamp and dot - FORCED to bottom of message area
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    // Timestamp first
                    Text(
                        text = formatMessageTime(message.timestamp),
                        fontSize = 10.sp,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Read status dot - for my messages only
                    if (isMe) {
                        val dotColor = if (message.read) Color(0xFF25D366) else Color.Red
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
                
                // YELLOW/RED/GREEN: Reactions STICKING to bottom corner of message bubble
                if (message.reactions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .fillMaxWidth(),  // Take full width to align properly
                        contentAlignment = if (isMe) Alignment.BottomEnd else Alignment.BottomStart
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = if (isMe) 12.dp else (-12).dp,
                                    y = (-8).dp
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = message.reactions.values.toSet().joinToString(" "),
                                fontSize = 12.sp
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
