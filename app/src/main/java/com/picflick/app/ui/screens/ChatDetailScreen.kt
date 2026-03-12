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
@OptIn(ExperimentalMaterial3Api::class)
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

    // Removed pull-to-refresh - Firebase provides real-time updates automatically
    
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
    
    // REAL-TIME READ RECEIPTS: Mark new messages as read immediately when they arrive
    // This ensures when User A sends a message and User B is already in chat, 
    // the dot turns green immediately on User A's screen
    LaunchedEffect(viewModel.messages) {
        val hasUnreadFromOther = viewModel.messages.any { 
            it.senderId != currentUser.uid && !it.read 
        }
        if (hasUnreadFromOther) {
            android.util.Log.d("ChatDetailScreen", "New unread messages from other user, marking as read")
            viewModel.markAsRead(chatId, currentUser.uid)
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
            // REMOVED: windowInsetsPadding - Scaffold handles keyboard automatically
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
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

                        // Text field with dark background matching input bar
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { 
                                Text(
                                    if (replyToMessage != null) "Reply to message..." else "Message",
                                    color = Color.Gray
                                ) 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 120.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1A1A1A),  // Dark grey matching black bar
                                unfocusedContainerColor = Color(0xFF1A1A1A),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White
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
                // Use all Scaffold padding for proper insets handling
                .padding(padding)
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

            // Removed PullRefreshIndicator - Firebase provides real-time updates automatically
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

        // Message bubble container - different layout for User A vs User B
        if (isMe) {
            // USER A (Right side): Timestamp INSIDE bubble at top-right
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f),
                contentAlignment = Alignment.TopEnd
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    // Message bubble with timestamp inside
                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .background(bubbleColor, bubbleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Row(
                            modifier = Modifier.wrapContentHeight(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.Top
                            ) {
                                if (message.isReply()) {
                                    QuotedMessage(
                                        quotedSenderName = message.quotedSenderName ?: "Unknown",
                                        quotedText = message.quotedText ?: "",
                                        isMe = isMe
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = message.text,
                                    fontSize = 15.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.wrapContentWidth(),
                                    softWrap = true
                                )
                            }
                            
                            // Timestamp inside bubble
                            Row(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,  // FORCE CENTER alignment
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = formatMessageTime(message.timestamp),
                                    fontSize = 10.sp,
                                    color = Color.Black.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val dotColor = if (message.read) Color(0xFF25D366) else Color.Red
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .align(Alignment.CenterVertically)  // FORCE dot to center
                                )
                            }
                        }
                    }
                    
                    // Emoji picker
                    if (showEmojiPicker) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
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
                
                // Reactions positioned at bottom-right corner of bubble
                if (message.reactions.isNotEmpty()) {
                    Text(
                        text = message.reactions.values.toSet().joinToString(" "),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = 4.dp)  // Closer to bubble edge
                    )
                }
            }
        } else {
            // USER B (Left side): Timestamp INSIDE bubble like User A
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f),
                contentAlignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 0.dp)
                ) {
                    // Message bubble with timestamp on LEFT side for User B
                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .background(bubbleColor, bubbleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Row(
                            modifier = Modifier.wrapContentHeight(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Timestamp on LEFT side for User B
                            Row(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = formatMessageTime(message.timestamp),
                                    fontSize = 10.sp,
                                    color = Color.Gray.copy(alpha = 0.7f)
                                )
                            }
                            
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.Top
                            ) {
                                if (message.isReply()) {
                                    QuotedMessage(
                                        quotedSenderName = message.quotedSenderName ?: "Unknown",
                                        quotedText = message.quotedText ?: "",
                                        isMe = isMe
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
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
                    }
                    
                    // Emoji picker
                    if (showEmojiPicker) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
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
                
                // Reactions positioned at bottom-left corner of bubble
                if (message.reactions.isNotEmpty()) {
                    Text(
                        text = message.reactions.values.toSet().joinToString(" "),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-8).dp, y = 4.dp)  // At the corner
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
