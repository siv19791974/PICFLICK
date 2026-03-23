package com.picflick.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.Flick
import com.picflick.app.data.Result
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.util.rememberChatImageModel
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat Detail Screen - Restored with Working Keyboard Fix
 * Uses imeInsets minus navInsets for accurate keyboard height
 */
data class QuickSwitchChatItem(
    val chatSession: ChatSession,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserPhoto: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatDetailScreen(
    chatSession: ChatSession,
    otherUserId: String,
    currentUser: UserProfile,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    onPhotoClick: (ChatMessage) -> Unit = {},
    onAddNewPhoto: () -> Unit = {},
    quickSwitchChats: List<QuickSwitchChatItem> = emptyList(),
    onQuickSwitchChat: (ChatSession, String) -> Unit = { _, _ -> }
) {
    val chatId = chatSession.id
    val isDarkMode = ThemeManager.isDarkMode.value
    val chatBackground = isDarkModeBackground(isDarkMode)
    val otherUserPhoto = rememberLiveUserPhotoUrl(
        userId = otherUserId,
        fallbackPhotoUrl = chatSession.participantPhotos[otherUserId]
    )
    
    var messageText by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var activeReactionMessageId by remember { mutableStateOf<String?>(null) }
    val selectedMessageIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showBlockUserConfirm by remember { mutableStateOf(false) }
    var showMyFlickPicker by remember { mutableStateOf(false) }
    var isLoadingMyFlicks by remember { mutableStateOf(false) }
    var myFlickPickerError by remember { mutableStateOf<String?>(null) }
    var myFlicks by remember { mutableStateOf<List<Flick>>(emptyList()) }
    val flickRepository = remember { FlickRepository.getInstance() }
val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var composerHeightPx by remember { mutableStateOf(0) }

    // Composer positioning: sit on nav bar when closed, move with keyboard when open
    val density = LocalDensity.current
    val composerHeightDp = with(density) { composerHeightPx.toDp() }
    val listBottomPadding = composerHeightDp + if (activeReactionMessageId != null) 220.dp else 16.dp


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

    fun sendExistingFlickPhoto(flick: Flick) {
        viewModel.sendMessage(
            chatId = chatId,
            text = "",
            senderId = currentUser.uid,
            recipientId = otherUserId,
            senderName = currentUser.displayName,
            senderPhotoUrl = currentUser.photoUrl,
            imageUrl = flick.imageUrl,
            flickId = flick.id,
            replyToMessage = null
        ) {
            viewModel.stopTyping(chatId, currentUser.uid)
            scope.launch {
                delay(80)
                if (viewModel.messages.isNotEmpty()) {
                    listState.scrollToItem(viewModel.messages.lastIndex)
                }
            }
        }
    }

    fun openMyFlickPicker() {
        showMyFlickPicker = true
        isLoadingMyFlicks = true
        myFlickPickerError = null
        scope.launch {
            when (val result = flickRepository.getFlicksForUserPaginated(
                userId = currentUser.uid,
                lastTimestamp = null,
                lastFlickId = null,
                pageSize = 60
            )) {
                is Result.Success -> {
                    val candidateFlicks = result.data.filter { it.imageUrl.isNotBlank() }
                    val ownerIdsToCheck = candidateFlicks
                        .map { it.userId }
                        .filter { it.isNotBlank() && it != currentUser.uid }
                        .toSet()

                    val ownerCanShareToRecipient = mutableMapOf<String, Boolean>()
                    ownerIdsToCheck.forEach { ownerId ->
                        ownerCanShareToRecipient[ownerId] = flickRepository.areFriends(ownerId, otherUserId)
                    }

                    myFlicks = candidateFlicks.filter { flick ->
                        flick.userId == currentUser.uid || ownerCanShareToRecipient[flick.userId] == true
                    }
                    isLoadingMyFlicks = false
                }
                is Result.Error -> {
                    myFlicks = emptyList()
                    isLoadingMyFlicks = false
                    myFlickPickerError = result.message
                }
                is Result.Loading -> Unit
            }
        }
    }


    // Load messages and typing listener
    LaunchedEffect(chatId, otherUserId) {
        viewModel.loadMessages(chatId, currentUser.uid)
        viewModel.observeTypingStatus(chatId, otherUserId)
        viewModel.markAsRead(chatId, currentUser.uid)
    }

    // Publish local typing state with debounce handled in ViewModel
    LaunchedEffect(chatId, messageText) {
        viewModel.updateTypingStatus(
            chatId = chatId,
            currentUserId = currentUser.uid,
            isTyping = messageText.isNotBlank()
        )
    }

    DisposableEffect(chatId, currentUser.uid) {
        onDispose {
            viewModel.stopTyping(chatId, currentUser.uid)
        }
    }

    suspend fun scrollToBottom() {
        if (viewModel.messages.isNotEmpty()) {
            listState.scrollToItem(viewModel.messages.lastIndex)
        }
    }

    // Auto-scroll only when message count changes (prevents jump on reaction/selection state changes)
    LaunchedEffect(viewModel.messages.size) {
        if (!isSelectionMode && viewModel.messages.isNotEmpty()) {
            delay(120)
            scrollToBottom()
        }
    }

    // Keep latest message visible when keyboard/composer changes (do not react to selection/reaction toggles)
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, composerHeightPx) {
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
    // (do not let selection-mode changes trigger scroll)
    LaunchedEffect(activeReactionMessageId, viewModel.messages.size, composerHeightPx) {
        if (!isSelectionMode && activeReactionMessageId != null && viewModel.messages.isNotEmpty()) {
            delay(120)
            scrollToBottom()
        }
    }

    // Keep typing indicator bubble visible at bottom when other user starts typing
    LaunchedEffect(viewModel.otherUserTyping, viewModel.messages.size) {
        if (!isSelectionMode && viewModel.otherUserTyping && viewModel.messages.isNotEmpty()) {
            delay(100)
            scrollToBottom()
        }
    }

    // LAYOUT: Box + Column with keyboard-aware input
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chatBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header + quick switch bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
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

                        if (isSelectionMode) {
                            Spacer(modifier = Modifier.weight(1f))
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

                                Text(
                                    text = selectedMessageIds.size.toString(),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )

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
                            QuickSwitchChatBar(
                                modifier = Modifier.weight(1f),
                                currentChatSession = chatSession,
                                currentOtherUserId = otherUserId,
                                currentOtherUserPhoto = otherUserPhoto,
                                quickSwitchChats = quickSwitchChats,
                                onSwitchChat = onQuickSwitchChat
                            )

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
            }

            // Messages area (takes remaining space with weight = 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                                        viewModel.loadMessages(chatId, currentUser.uid) 
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
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> "chat_message" }
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
                                            if (isSelectionMode) {
                                                selectedMessageIds.clear()
                                                isSelectionMode = false
                                                activeReactionMessageId = null
                                            }
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

                                if (viewModel.otherUserTyping && !isSelectionMode) {
                                    item(key = "typing-indicator-bubble") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Surface(
                                                color = Color(0xFFE0E0E0),
                                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                                            ) {
                                                TypingDotsIndicator(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    dotColor = Color.Black.copy(alpha = 0.65f)
                                                )
                                            }
                                        }
                                    }
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
                        onClick = { openMyFlickPicker() },
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

                    if (replyToMessage != null) {
                        IconButton(
                            onClick = { replyToMessage = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                tint = Color.LightGray
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

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
                                    viewModel.stopTyping(chatId, currentUser.uid)
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

    val deleteForEveryoneWindowMs = 10 * 60 * 1000L
    val nowForDeleteDialog = System.currentTimeMillis()
    val selectedMessagesForDelete = viewModel.messages.filter { it.id in selectedMessageIds }
    val olderThanWindowCount = selectedMessagesForDelete.count {
        (nowForDeleteDialog - it.timestamp) > deleteForEveryoneWindowMs
    }
    val deleteDialogText = when {
        selectedMessagesForDelete.isEmpty() -> "No messages selected."
        olderThanWindowCount == selectedMessagesForDelete.size ->
            "These messages are older than 10 minutes and will be deleted only for you."
        olderThanWindowCount > 0 ->
            "$olderThanWindowCount selected message(s) are older than 10 minutes and will be deleted only for you. Newer ones will be deleted for everyone."
        else ->
            "These selected message(s) will be deleted for everyone."
    }
    val deleteConfirmLabel = if (olderThanWindowCount > 0) "Delete only for you" else "Delete"

    if (showMyFlickPicker) {
        AlertDialog(
            onDismissRequest = { showMyFlickPicker = false },
            title = { Text("Share from PicFlick") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    myFlickPickerError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    when {
                        isLoadingMyFlicks -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        myFlicks.isEmpty() -> {
                            Text("No photos are available to share with this user right now. Tap Open Add Photo screen to upload your own photo first.")
                        }

                        else -> {
                            Text(
                                text = "Choose an existing PicFlick photo shareable with this user",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(
                                    items = myFlicks,
                                    key = { it.id },
                                    contentType = { "flick" }
                                ) { flick ->
                                    AsyncImage(
                                        model = flick.imageUrl,
                                        contentDescription = "My photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                showMyFlickPicker = false
                                                sendExistingFlickPhoto(flick)
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMyFlickPicker = false
                        onAddNewPhoto()
                    }
                ) { Text("Open Add Photo screen") }
            },
            dismissButton = {
                TextButton(onClick = { showMyFlickPicker = false }) { Text("Close") }
            }
        )
    }

    if (showDeleteSelectedConfirm) {
AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("Delete selected messages?") },
            text = { Text(deleteDialogText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedConfirm = false
                        viewModel.deleteSelectedMessages(
                            chatId = chatId,
                            messageIds = selectedMessageIds.toSet(),
                            currentUserId = currentUser.uid
                        ) { success ->
                            if (success) {
                                selectedMessageIds.clear()
                                isSelectionMode = false
                            }
                        }
                    }
                ) { Text(deleteConfirmLabel, color = Color.Red) }
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
    val chatImageModel = rememberChatImageModel(message.imageUrl, message.timestamp)
    var swipeOffsetX by remember(message.id) { mutableStateOf(0f) }
    var imageAspectRatio by remember(message.id) { mutableFloatStateOf(1f) }
    val isPortraitPhoto = imageAspectRatio <= 1f
    val photoBoxModifier = if (isPortraitPhoto) {
        Modifier
            .fillMaxWidth(0.66f)
            .heightIn(min = 180.dp, max = 360.dp)
            .aspectRatio(3f / 4f)
    } else {
        Modifier
            .fillMaxWidth(0.9f)
            .heightIn(min = 120.dp, max = 260.dp)
            .aspectRatio(4f / 3f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0x332196F3) else Color.Transparent)
            .offset { IntOffset(swipeOffsetX.roundToInt(), -reactionLiftPx) }
            .pointerInput(message.id, isSelectionMode) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (!isSelectionMode && swipeOffsetX > 104f) {
                            onReplyClick()
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = {
                        swipeOffsetX = 0f
                    }
                ) { _, dragAmount ->
                    if (!isSelectionMode && dragAmount > 0f) {
                        swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(0f, 128f)
                    }
                }
            }
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
                Column(
                    modifier = Modifier.padding(
                        start = if (isPhotoOnly) 2.dp else 8.dp,
                        end = 2.dp
                    )
                ) {
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
                                        quotedImageUrl = message.quotedImageUrl,
                                        isMe = isMe
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                if (message.imageUrl.isNotBlank()) {
                                    Box(modifier = photoBoxModifier) {
                                        AsyncImage(
                                            model = chatImageModel,
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
                                            contentScale = ContentScale.Crop,
                                            onSuccess = { success ->
                                                val width = success.result.image.width
                                                val height = success.result.image.height
                                                if (width > 0 && height > 0) {
                                                    imageAspectRatio = width.toFloat() / height.toFloat()
                                                }
                                            }
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
                                        quotedImageUrl = message.quotedImageUrl,
                                        isMe = isMe
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                if (message.imageUrl.isNotBlank()) {
                                    Box(modifier = photoBoxModifier) {
                                        AsyncImage(
                                            model = chatImageModel,
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
                                            contentScale = ContentScale.Crop,
                                            onSuccess = { success ->
                                                val width = success.result.image.width
                                                val height = success.result.image.height
                                                if (width > 0 && height > 0) {
                                                    imageAspectRatio = width.toFloat() / height.toFloat()
                                                }
                                            }
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
                text = when {
                    message.text.isNotBlank() -> message.text.take(50)
                    message.imageUrl.isNotBlank() -> "📷 Photo"
                    else -> "Message"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )
        }

        if (message.imageUrl.isNotBlank()) {
            AsyncImage(
                model = rememberChatImageModel(message.imageUrl, message.timestamp),
                contentDescription = "Quoted photo preview",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(4.dp))
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
    quotedImageUrl: String? = null,
    isMe: Boolean
) {
    val quoteColor = if (isMe) Color(0xFF8BC34A) else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Black.copy(alpha = 0.05f), shape = RoundedCornerShape(4.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .background(quoteColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
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

        if (!quotedImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = quotedImageUrl,
                contentDescription = "Quoted photo preview",
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun QuickSwitchChatBar(
    modifier: Modifier = Modifier,
    currentChatSession: ChatSession,
    currentOtherUserId: String,
    currentOtherUserPhoto: String,
    quickSwitchChats: List<QuickSwitchChatItem>,
    onSwitchChat: (ChatSession, String) -> Unit
) {
    val currentName = currentChatSession.participantNames[currentOtherUserId]
        ?.takeIf { it.isNotBlank() }
        ?: "Chat"

    val sortedOthers = quickSwitchChats
        .filter { it.otherUserId != currentOtherUserId }
        .sortedByDescending { it.chatSession.lastTimestamp }
        .take(4)

    val slots = listOf(
        sortedOthers.getOrNull(2),
        sortedOthers.getOrNull(0),
        QuickSwitchChatItem(
            chatSession = currentChatSession,
            otherUserId = currentOtherUserId,
            otherUserName = currentName,
            otherUserPhoto = currentOtherUserPhoto
        ),
        sortedOthers.getOrNull(1),
        sortedOthers.getOrNull(3)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        slots.forEachIndexed { index, item ->
            val isCenter = index == 2
            val avatarSize = if (isCenter) 40.dp else 30.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(52.dp)
            ) {
                if (item == null) {
                    Spacer(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                } else {
                    val quickSwitchPhoto = rememberLiveUserPhotoUrl(
                        userId = item.otherUserId,
                        fallbackPhotoUrl = item.otherUserPhoto
                    )
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (isCenter) 0.35f else 0.2f))
                            .clickable {
                                if (!isCenter) {
                                    onSwitchChat(item.chatSession, item.otherUserId)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (quickSwitchPhoto.isNotBlank()) {
                            AsyncImage(
                                model = quickSwitchPhoto,
                                contentDescription = item.otherUserName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isCenter) 22.dp else 18.dp)
                            )
                        }
                    }
                }

                val firstName = item?.otherUserName
                    ?.trim()
                    ?.split(" ")
                    ?.firstOrNull()
                    .orEmpty()

                Text(
                    text = firstName,
                    color = Color.White,
                    fontSize = if (isCenter) 9.sp else 8.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingDotsIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White
) {
    val transition = rememberInfiniteTransition(label = "typingDots")
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, delayMillis = 140, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, delayMillis = 280, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor.copy(alpha = dot1Alpha)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor.copy(alpha = dot2Alpha)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor.copy(alpha = dot3Alpha)))
    }
}