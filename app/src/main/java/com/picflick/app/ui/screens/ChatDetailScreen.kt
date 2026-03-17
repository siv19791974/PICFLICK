package com.picflick.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat Detail Screen - Restored with Working Keyboard Fix
 * Uses imeInsets minus navInsets for accurate keyboard height
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatDetailScreen(
    chatSession: ChatSession,
    otherUserId: String,
    currentUser: UserProfile,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    onPhotoClick: (ChatMessage) -> Unit = {}
) {
    val chatId = chatSession.id
    var otherUserPhoto by remember { mutableStateOf(chatSession.participantPhotos[otherUserId] ?: "") }
    val context = LocalContext.current

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
            } catch (_: Exception) {
// Keep empty if fetch fails
            }
        }
    }
    
    var messageText by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var activeReactionMessageId by remember { mutableStateOf<String?>(null) }
    val selectedMessageIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showBlockUserConfirm by remember { mutableStateOf(false) }
val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var upwardPullDistance by remember { mutableStateOf(0f) }
    var isRefreshingByPullUp by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableStateOf(0) }

    // Composer positioning: sit on nav bar when closed, move with keyboard when open
    val density = LocalDensity.current
    val composerHeightDp = with(density) { composerHeightPx.toDp() }
    val listBottomPadding = composerHeightDp + if (activeReactionMessageId != null) 220.dp else 16.dp
    val pullUpRefreshThreshold = with(density) { 96.dp.toPx() }

    fun triggerPullUpRefresh() {
        if (isRefreshingByPullUp) return
        isRefreshingByPullUp = true
        viewModel.loadMessages(chatId)
        viewModel.markAsRead(chatId, currentUser.uid)
        scope.launch {
            delay(700)
            isRefreshingByPullUp = false
        }
    }

    val pullUpRefreshConnection = remember(chatId, currentUser.uid, isRefreshingByPullUp) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
if (available.y < 0f) {
                        upwardPullDistance += -available.y
                        if (upwardPullDistance >= pullUpRefreshThreshold) {
                            upwardPullDistance = 0f
                            triggerPullUpRefresh()
                        }
                    } else if (available.y > 0f) {
                        upwardPullDistance = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    fun sendHeyMessage() {
        viewModel.sendMessage(
            chatId = chatId,
            text = "Hey 👋",
            senderId = currentUser.uid,
            recipientId = otherUserId,
            senderName = currentUser.displayName,
            senderPhotoUrl = currentUser.photoUrl,
            replyToMessage = null
        ) { }
    }

    // Image picker for sending photos
val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
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
        viewModel.markAsRead(chatId, currentUser.uid)
    }

    suspend fun scrollToBottom() {
        if (viewModel.messages.isNotEmpty()) {
            listState.scrollToItem(viewModel.messages.lastIndex)
        }
    }

    // Auto-scroll to bottom when new messages arrive (disabled during selection to prevent jumps)
    LaunchedEffect(viewModel.messages.size, isSelectionMode) {
        if (!isSelectionMode && viewModel.messages.isNotEmpty()) {
            delay(120)
            scrollToBottom()
        }
    }

    // Keep latest message visible when keyboard/composer changes (disabled during selection)
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, composerHeightPx, isSelectionMode) {
        if (!isSelectionMode && viewModel.messages.isNotEmpty()) {
            delay(100)
            scrollToBottom()
        }
    }
    
    // Real-time read receipts
    LaunchedEffect(viewModel.messages) {
        val hasUnreadFromOther = viewModel.messages.any { 
            it.senderId != currentUser.uid && !it.read 
        }
        if (hasUnreadFromOther) {
            viewModel.markAsRead(chatId, currentUser.uid)
        }
    }

    LaunchedEffect(isSelectionMode, selectedMessageIds.size) {
        if (!isSelectionMode || selectedMessageIds.size != 1) {
            activeReactionMessageId = null
        }
    }

    // Keep last message + reaction choices visible when reaction picker is opened
    // (do not auto-scroll while selecting to avoid jumpy long-press behavior)
    LaunchedEffect(activeReactionMessageId, viewModel.messages.size, composerHeightPx, isSelectionMode) {
        if (!isSelectionMode && activeReactionMessageId != null && viewModel.messages.isNotEmpty()) {
            delay(120)
            scrollToBottom()
        }
    }

    // LAYOUT: Box + Column with keyboard-aware input
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD9E7F5))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with other user's info (56dp fixed height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(PicFlickBannerBackground),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    // Other user's profile pic
                    if (otherUserPhoto.isNotEmpty()) {
                        AsyncImage(
                            model = otherUserPhoto,
                            contentDescription = "User profile photo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))

                    if (isSelectionMode) {
                        Text(
                            text = "Selected (${selectedMessageIds.size})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (selectedMessageIds.isNotEmpty()) {
                                        showDeleteSelectedConfirm = true
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete selected",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedMessageIds.clear()
                                    activeReactionMessageId = null
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel selection",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { showHeaderMenu = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Chat menu",
                                    tint = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = showHeaderMenu,
                                onDismissRequest = { showHeaderMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View profile") },
                                    onClick = {
                                        showHeaderMenu = false
                                        onUserProfileClick(otherUserId)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Select messages") },
                                    onClick = {
                                        showHeaderMenu = false
                                        isSelectionMode = true
                                    }
                                )

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = { Text("Clear chat", color = Color.Red) },
                                    onClick = {
                                        showHeaderMenu = false
                                        showClearChatConfirm = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Block user", color = Color.Red) },
                                    onClick = {
                                        showHeaderMenu = false
                                        showBlockUserConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Messages area (takes remaining space with weight = 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(pullUpRefreshConnection)
            ) {
Column(modifier = Modifier.fillMaxSize()) {
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
                                    AssistChip(
                                        onClick = { sendHeyMessage() },
                                        label = { Text("Say hello") }
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
                                contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = listBottomPadding)
                            ) {
                                itemsIndexed(
                                    items = viewModel.messages,
                                    key = { _, item -> item.id }
                                ) { _, message ->
                                    val isMe = message.senderId == currentUser.uid
                                    ChatBubble(
                                        message = message,
                                        isMe = isMe,
                                        otherUserPhoto = if (isMe) "" else otherUserPhoto,
                                        currentUserId = currentUser.uid,
                                        onReplyClick = { replyToMessage = message },
                                        onReaction = { emoji ->
                                            viewModel.addReaction(chatId, message.id, currentUser.uid, emoji, currentUser.displayName, currentUser.photoUrl)
                                        },
                                        onPhotoClick = { onPhotoClick(message) },
                                        isSelectionMode = isSelectionMode,
                                        isSelected = message.id in selectedMessageIds,
                                        onToggleSelection = {
                                            if (message.id in selectedMessageIds) {
                                                selectedMessageIds.remove(message.id)
                                                if (selectedMessageIds.isEmpty()) {
                                                    isSelectionMode = false
                                                }
                                            } else {
                                                selectedMessageIds.add(message.id)
                                                isSelectionMode = true
                                            }
                                        },
                                        onLongPressSelect = {
                                            if (message.id !in selectedMessageIds) {
                                                selectedMessageIds.add(message.id)
                                            }
                                            isSelectionMode = true
                                            activeReactionMessageId = message.id
                                        },
                                        showSelectionReactionPicker = isSelectionMode && selectedMessageIds.size == 1 && activeReactionMessageId == message.id,
                                        reactionLiftPx = if (isSelectionMode && selectedMessageIds.size == 1 && activeReactionMessageId == message.id) 12 else 0,
                                        onReactionPickerToggle = { opened ->
                                            activeReactionMessageId = if (opened) message.id else null
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                            }
                        }
                    }
                }
            }

        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { composerHeightPx = it.height },
            color = Color.Black,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (replyToMessage != null) {
                    ReplyPreview(
                        message = replyToMessage!!,
                        onCancel = { replyToMessage = null }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (viewModel.messages.isEmpty() && messageText.isBlank() && replyToMessage == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AssistChip(
                            onClick = { sendHeyMessage() },
                            label = { Text("Send Hey") }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            focusedContainerColor = Color(0xFF1A1A1A),
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
                                    scope.launch {
                                        delay(80)
                                        scrollToBottom()
                                    }
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

    if (showDeleteSelectedConfirm) {
AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("Delete selected messages?") },
            text = { Text("This will permanently delete ${selectedMessageIds.size} selected message(s).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedConfirm = false
                        viewModel.deleteSelectedMessages(chatId, selectedMessageIds.toSet()) { success ->
                            if (success) {
                                selectedMessageIds.clear()
                                isSelectionMode = false
                            }
                        }
                    }
                ) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text("Clear chat?") },
            text = { Text("This will delete the full conversation for you.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatConfirm = false
                        viewModel.deleteChat(chatId)
                        onBack()
                    }
                ) { Text("Clear", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showBlockUserConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockUserConfirm = false },
            title = { Text("Block user?") },
            text = { Text("This will report and block this user instantly, and remove this chat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockUserConfirm = false
                        viewModel.blockAndReportUser(currentUser.uid, otherUserId, chatId)
                        onBack()
                    }
                ) { Text("Block", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockUserConfirm = false }) { Text("Cancel") }
            }
        )
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
    onReaction: (String) -> Unit = {},
    onPhotoClick: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onLongPressSelect: () -> Unit = {},
    showSelectionReactionPicker: Boolean = false,
    reactionLiftPx: Int = 0,
    onReactionPickerToggle: (Boolean) -> Unit = {}
) {
    // Fixed chat palette across devices (independent of local theme toggle)
    val sentColor = Color(0xFFB8D4F0)
    val receivedColor = Color(0xFFE0E0E0)

    val bubbleColor = if (isMe) sentColor else receivedColor

    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiReactions = listOf("❤️", "😂", "😮", "😢", "👍", "🔥", "👏")

    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }
    val isPhotoOnly = message.imageUrl.isNotBlank() && message.text.isBlank() && !message.isReply()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0x332196F3) else Color.Transparent)
            .offset { IntOffset(0, -reactionLiftPx) }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelection()
                },
                onLongClick = {
                    onLongPressSelect()
                }
            ),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (isMe) {
            // USER A (Right side)
            Box(
                modifier = Modifier.fillMaxWidth(0.85f),
                contentAlignment = Alignment.TopEnd
            ) {
                Column(modifier = Modifier.padding(horizontal = if (isPhotoOnly) 2.dp else 8.dp)) {
                    Box(
modifier = Modifier
                            .align(Alignment.End)
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .background(bubbleColor, bubbleShape)
                            .padding(horizontal = if (isPhotoOnly) 4.dp else 10.dp, vertical = if (isPhotoOnly) 4.dp else 6.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
Box(
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(end = if (message.imageUrl.isNotBlank()) 4.dp else 44.dp, bottom = 2.dp),
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
                                if (message.imageUrl.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 120.dp, max = 260.dp)
                                    ) {
                                        AsyncImage(
                                            model = message.imageUrl,
                                            contentDescription = "Sent photo",
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) onToggleSelection() else onPhotoClick()
                                                    },
                                                    onLongClick = { onLongPressSelect() }
                                                ),
                                            contentScale = ContentScale.Crop
                                        )

                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 8.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = formatMessageTime(message.timestamp),
                                                fontSize = 10.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            val dotColor = if (message.read) Color(0xFF25D366) else Color.Red
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                            )
                                        }
                                    }
                                    if (message.text.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }

                                if (message.text.isNotBlank()) {
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

                            if (message.imageUrl.isBlank()) {
                                Row(
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    verticalAlignment = Alignment.CenterVertically,
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
                                    )
                                }
                            }
}
                    }

                    if (showEmojiPicker || showSelectionReactionPicker) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            emojiReactions.take(5).forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onReaction(emoji)
                                            showEmojiPicker = false
                                            onReactionPickerToggle(false)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
}

                if (message.reactions.isNotEmpty()) {
                    Text(
                        text = message.reactions.values.toSet().joinToString(" "),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                    )
                }
            }
        } else {
            // USER B (Left side)
            Box(
                modifier = Modifier.fillMaxWidth(0.85f),
                contentAlignment = Alignment.TopStart
            ) {
                Column(modifier = Modifier.padding(horizontal = 0.dp)) {
                    Box(
modifier = Modifier
                            .align(Alignment.Start)
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .background(bubbleColor, bubbleShape)
                            .padding(
                                horizontal = if (isPhotoOnly) 4.dp else 10.dp,
                                vertical = if (isPhotoOnly) 4.dp else 6.dp
                            ),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Box(
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(end = if (message.imageUrl.isNotBlank()) 4.dp else 52.dp, bottom = 2.dp),
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
                                if (message.imageUrl.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 120.dp, max = 260.dp)
                                    ) {
                                        AsyncImage(
                                            model = message.imageUrl,
                                            contentDescription = "Sent photo",
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) onToggleSelection() else onPhotoClick()
                                                    },
                                                    onLongClick = { onLongPressSelect() }
                                                ),
                                            contentScale = ContentScale.Crop
                                        )

                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 8.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = formatMessageTime(message.timestamp),
                                                fontSize = 10.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Transparent)
                                            )
                                        }
                                    }
                                    if (message.text.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }

                                if (message.text.isNotBlank()) {
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

                            if (message.imageUrl.isBlank()) {
                                Row(
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = formatMessageTime(message.timestamp),
                                        fontSize = 10.sp,
                                        color = Color.Black.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                    )
                                }
                            }
}
                    }

                    if (showEmojiPicker || showSelectionReactionPicker) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.align(Alignment.Start),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            emojiReactions.take(5).forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onReaction(emoji)
                                            showEmojiPicker = false
                                            onReactionPickerToggle(false)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
}

                if (message.reactions.isNotEmpty()) {
                    Text(
                        text = message.reactions.values.toSet().joinToString(" "),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-8).dp, y = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

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