package com.picflick.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.Flick
import com.picflick.app.data.FriendGroup
import com.picflick.app.data.Result
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import com.picflick.app.ui.components.ActionSheetOption
import com.picflick.app.ui.components.ActionSheetRow
import com.picflick.app.ui.components.AddPhotoStyleActionSheet
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.util.rememberChatImageModel
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.util.rememberLiveUserTierColor
import com.picflick.app.viewmodel.ChatViewModel
import com.picflick.app.viewmodel.FriendsViewModel
import com.picflick.app.viewmodel.HomeViewModel
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    onQuickSwitchChat: (ChatSession, String) -> Unit = { _, _ -> },
    friendsViewModel: FriendsViewModel? = null,
    homeViewModel: HomeViewModel? = null
) {
    val chatId = chatSession.id
    val isDarkMode = ThemeManager.isDarkMode.value
    val chatBackground = isDarkModeBackground(isDarkMode)
    val isGroupChat = chatSession.isGroup || otherUserId.startsWith("group:")
    val otherUserPhoto = if (isGroupChat) {
        ""
    } else {
        rememberLiveUserPhotoUrl(
            userId = otherUserId,
            fallbackPhotoUrl = chatSession.participantPhotos[otherUserId]
        )
    }
    
    var messageText by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showGroupInfoDialog by remember { mutableStateOf(false) }
    var activeReactionMessageId by remember { mutableStateOf<String?>(null) }
    val selectedMessageIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showBlockUserConfirm by remember { mutableStateOf(false) }
    var showMuteDurationDialog by remember { mutableStateOf(false) }
    var showMyFlickPicker by remember { mutableStateOf(false) }
    var isLoadingMyFlicks by remember { mutableStateOf(false) }
    var myFlicks by remember { mutableStateOf<List<Flick>>(emptyList()) }
    val selectedMyFlickIds = remember { mutableStateListOf<String>() }
    val flickRepository = remember { FlickRepository.getInstance() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()

    val currentGroupId = remember(chatSession) {
        chatSession.groupId.ifBlank { chatSession.id.removePrefix("group_") }
    }
    val fallbackGroupModel = remember(chatSession, currentGroupId, currentUser.uid) {
        FriendGroup(
            id = currentGroupId,
            userId = currentUser.uid,
            ownerId = chatSession.participants.firstOrNull().orEmpty(),
            name = chatSession.groupName.ifBlank { "Group" },
            friendIds = chatSession.participants.filter { it != currentUser.uid },
            memberIds = chatSession.participants.distinct(),
            adminIds = emptyList(),
            icon = chatSession.groupIcon.ifBlank { "👥" }
        )
    }
    val groupModel = remember(homeViewModel?.friendGroups, currentGroupId, fallbackGroupModel) {
        homeViewModel?.friendGroups?.firstOrNull { it.id == currentGroupId } ?: fallbackGroupModel
    }

    var editableGroupName by remember(chatSession.id, chatSession.groupName) {
        mutableStateOf(chatSession.groupName.ifBlank { "Group" })
    }
    var editableGroupIcon by remember(chatSession.id, chatSession.groupIcon) {
        mutableStateOf(chatSession.groupIcon.ifBlank { "👥" })
    }
    var selectedInviteeIds by remember(chatSession.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedRemovableIds by remember(chatSession.id) { mutableStateOf<Set<String>>(emptySet()) }

    val isOwner = remember(groupModel, currentUser.uid) { groupModel.isOwner(currentUser.uid) }
    val isAdmin = remember(groupModel, currentUser.uid) { groupModel.isAdmin(currentUser.uid) }

    val availableInviteCandidates = remember(friendsViewModel?.followingUsers, groupModel) {
        (friendsViewModel?.followingUsers ?: emptyList())
            .filter { !groupModel.isMember(it.uid) }
    }
    val removableMembers = remember(friendsViewModel?.followingUsers, groupModel) {
        val byId = (friendsViewModel?.followingUsers ?: emptyList()).associateBy { it.uid }
        groupModel.membersExcludingOwner()
            .mapNotNull { byId[it] }
    }
    val scope = rememberCoroutineScope()
    var composerHeightPx by remember { mutableStateOf(0) }
    var hasInitialBottomSnap by remember(chatId) { mutableStateOf(false) }

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
        selectedMyFlickIds.clear()
        isLoadingMyFlicks = true
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

    LaunchedEffect(chatId, isGroupChat, friendsViewModel, currentUser.following) {
        if (isGroupChat) {
            friendsViewModel?.loadFollowingUsers(currentUser.following)
        }
    }

    suspend fun scrollToBottom() {
        if (viewModel.messages.isNotEmpty()) {
            listState.scrollToItem(viewModel.messages.lastIndex)
        }
    }

    // Initial open: snap immediately to newest before any delayed scroll effects run
    LaunchedEffect(chatId, viewModel.messages.size) {
        if (!hasInitialBottomSnap && viewModel.messages.isNotEmpty()) {
            scrollToBottom()
            hasInitialBottomSnap = true
        }
    }

    // Auto-scroll only when message count changes (prevents jump on reaction/selection state changes)
    LaunchedEffect(viewModel.messages.size, hasInitialBottomSnap) {
        if (hasInitialBottomSnap && !isSelectionMode && viewModel.messages.isNotEmpty()) {
            delay(120)
            scrollToBottom()
        }
    }

    // Keep latest message visible when keyboard/composer changes (do not react to selection/reaction toggles)
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, composerHeightPx, hasInitialBottomSnap) {
        if (hasInitialBottomSnap && !isSelectionMode && viewModel.messages.isNotEmpty()) {
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
    LaunchedEffect(activeReactionMessageId, viewModel.messages.size, composerHeightPx, hasInitialBottomSnap) {
        if (hasInitialBottomSnap && !isSelectionMode && activeReactionMessageId != null && viewModel.messages.isNotEmpty()) {
            delay(120)
            scrollToBottom()
        }
    }

    // Keep typing indicator bubble visible at bottom when other user starts typing
    LaunchedEffect(viewModel.otherUserTyping, viewModel.messages.size, hasInitialBottomSnap) {
        if (hasInitialBottomSnap && !isSelectionMode && viewModel.otherUserTyping && viewModel.messages.isNotEmpty()) {
            delay(100)
            scrollToBottom()
        }
    }

    val selectedMessageForEdit = viewModel.messages.firstOrNull { it.id in selectedMessageIds }
    val canEditSelectedMessage = selectedMessageForEdit?.let { selected ->
        selectedMessageIds.size == 1 &&
            selected.senderId == currentUser.uid &&
            !selected.read &&
            selected.imageUrl.isBlank() &&
            selected.text.isNotBlank()
    } == true

    LaunchedEffect(editingMessageId, viewModel.messages, currentUser.uid) {
        val editingId = editingMessageId ?: return@LaunchedEffect
        val liveMessage = viewModel.messages.firstOrNull { it.id == editingId }
        val canStillEdit = liveMessage != null &&
            liveMessage.senderId == currentUser.uid &&
            !liveMessage.read &&
            liveMessage.imageUrl.isBlank() &&
            liveMessage.text.isNotBlank()

        if (!canStillEdit) {
            editingMessageId = null
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

                                if (canEditSelectedMessage) {
                                    IconButton(
                                        onClick = {
                                            val messageToEdit = selectedMessageForEdit
                                            editingMessageId = messageToEdit.id
                                            messageText = messageToEdit.text
                                            replyToMessage = null
                                            isSelectionMode = false
                                            selectedMessageIds.clear()
                                            activeReactionMessageId = null
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit selected",
                                            tint = Color.White
                                        )
                                    }
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
                                    if (isGroupChat) {
                                        DropdownMenuItem(
                                            text = { Text("Mute chat") },
                                            onClick = {
                                                showHeaderMenu = false
                                                showMuteDurationDialog = true
                                            }
                                        )

                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("View profile") },
                                            onClick = {
                                                showHeaderMenu = false
                                                onUserProfileClick(otherUserId)
                                            }
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text("Select messages") },
                                        onClick = {
                                            showHeaderMenu = false
                                            isSelectionMode = true
                                        }
                                    )

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isGroupChat) "Exit group conversation" else "Delete conversation",
                                                color = Color.Red
                                            )
                                        },
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
                                Text(
                                    text = "No messages yet",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
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

                if (editingMessageId != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Editing message",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = {
                                editingMessageId = null
                                messageText = ""
                            }) {
                                Text("Cancel", color = Color.LightGray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
                                when {
                                    editingMessageId != null -> "Edit message..."
                                    replyToMessage != null -> "Reply to message..."
                                    else -> "Message"
                                },
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

                    if (replyToMessage != null || editingMessageId != null) {
                        IconButton(
                            onClick = {
                                replyToMessage = null
                                editingMessageId = null
                                if (messageText.isBlank()) {
                                    messageText = ""
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel compose state",
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
                                val messageToSubmit = messageText.trim()
                                val editingId = editingMessageId
                                if (editingId != null) {
                                    viewModel.editMessage(
                                        chatId = chatId,
                                        messageId = editingId,
                                        currentUserId = currentUser.uid,
                                        newText = messageToSubmit
                                    ) { success ->
                                        if (success) {
                                            messageText = ""
                                            editingMessageId = null
                                            viewModel.stopTyping(chatId, currentUser.uid)
                                        } else {
                                            editingMessageId = null
                                        }
                                    }
                                } else {
                                    viewModel.sendMessage(
                                        chatId = chatId,
                                        text = messageToSubmit,
                                        senderId = currentUser.uid,
                                        recipientId = if (isGroupChat) "group:${chatSession.groupId.ifBlank { chatSession.id }}" else otherUserId,
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
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (editingMessageId != null) "Save edit" else "Send",
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
        Dialog(
            onDismissRequest = { showMyFlickPicker = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                color = Color(0xFF121212)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share from PicFlick",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedMyFlickIds.isNotEmpty()) {
                                Text(
                                    text = "${selectedMyFlickIds.size} selected",
                                    color = Color(0xFF87CEEB),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        TextButton(onClick = {
                            showMyFlickPicker = false
                            selectedMyFlickIds.clear()
                        }) {
                            Text("Close", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when {
                        isLoadingMyFlicks -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF87CEEB))
                            }
                        }

                        myFlicks.isEmpty() -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "No photos available yet.",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Upload a photo first, then share it here.",
                                    color = Color(0xFFB7BDC9)
                                )

                            }
                        }

                        else -> {
                            val configuration = LocalConfiguration.current
                            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                            Text(
                                text = "Tap to share • Long press for multi-select",
                                color = Color(0xFFB7BDC9),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (selectedMyFlickIds.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val selected = myFlicks.filter { it.id in selectedMyFlickIds }
                                        selected.forEach { sendExistingFlickPhoto(it) }
                                        showMyFlickPicker = false
                                        selectedMyFlickIds.clear()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2A4A73),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share Selected (${selectedMyFlickIds.size})", color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                val availableGridHeight = this.maxHeight
                                val homeLikeRowHeight = if (isLandscape) {
                                    availableGridHeight / 1.09f
                                } else {
                                    availableGridHeight / 4.03f
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = homeLikeRowHeight),
                                    contentPadding = PaddingValues(
                                        start = 1.dp,
                                        end = 1.dp,
                                        top = 4.dp,
                                        bottom = 1.dp
                                    )
                                ) {
                                    items(
                                        items = myFlicks,
                                        key = { it.id },
                                        contentType = { "myFlick" }
                                    ) { flick ->
                                        val isSelectionMode = selectedMyFlickIds.isNotEmpty()
                                        val isSelected = selectedMyFlickIds.contains(flick.id)

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(homeLikeRowHeight)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            if (isSelected) selectedMyFlickIds.remove(flick.id) else selectedMyFlickIds.add(flick.id)
                                                        } else {
                                                            showMyFlickPicker = false
                                                            sendExistingFlickPhoto(flick)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (!isSelectionMode) {
                                                            selectedMyFlickIds.add(flick.id)
                                                        }
                                                    }
                                                )
                                        ) {
                                            AsyncImage(
                                                model = flick.thumbnailUrl512.ifBlank { flick.imageUrl },
                                                contentDescription = "PicFlick photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )

                                            if (isSelectionMode) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(22.dp)
                                                        .background(
                                                            if (isSelected) Color(0xFF4CAF50) else Color.Black.copy(alpha = 0.45f),
                                                            CircleShape
                                                        )
                                                        .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
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
            }
        }
    }

    if (showDeleteSelectedConfirm) {
        Dialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 10.dp,
                shadowElevation = 18.dp,
                color = if (isDarkMode) Color(0xFF151922) else Color.White
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Delete selected messages?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color(0xFF111827)
                    )

                    Text(
                        text = deleteDialogText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color(0xFFBFC7D5) else Color(0xFF4B5563),
                        lineHeight = 20.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                            Text(
                                "Cancel",
                                color = if (isDarkMode) Color(0xFF9FB0C8) else Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
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
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(deleteConfirmLabel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showClearChatConfirm) {
        Dialog(
            onDismissRequest = { showClearChatConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 10.dp,
                shadowElevation = 18.dp,
                color = if (isDarkMode) Color(0xFF151922) else Color.White
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isGroupChat) "Exit group conversation?" else "Delete conversation?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkMode) Color.White else Color.Black
                    )
                    Text(
                        text = if (isGroupChat) {
                            "This removes this group conversation from your inbox only. Other members will still see it."
                        } else {
                            "This removes this conversation from your inbox only. Other participants will still see it."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color(0xFFBFC7D9) else Color(0xFF475569)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showClearChatConfirm = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showClearChatConfirm = false
                                viewModel.deleteChat(chatId, currentUser.uid)
                                Toast.makeText(
                                    context,
                                    if (isGroupChat) "Group conversation removed from your inbox" else "Conversation removed from your inbox",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A4A73),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showBlockUserConfirm) {
        ModalBottomSheet(
            onDismissRequest = { showBlockUserConfirm = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF121212),
            dragHandle = { Surface(modifier = Modifier.padding(top = 8.dp).size(width = 44.dp, height = 5.dp), shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.28f)) {} }
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Text("Block user?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp).align(Alignment.CenterHorizontally))
                Text("This will report and block this user instantly, and remove this chat.", color = Color(0xFFB7BDC9), fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                ActionSheetRow(icon = Icons.Default.Close, title = "Cancel", accentColor = Color.Gray, onClick = { showBlockUserConfirm = false })
                Spacer(Modifier.height(8.dp))
                ActionSheetRow(icon = Icons.Default.Block, title = "Block & Report", accentColor = Color(0xFFFF4444), onClick = {
                    showBlockUserConfirm = false
                    viewModel.blockAndReportUser(currentUser.uid, otherUserId, chatId)
                    onBack()
                })
            }
        }
    }

    if (showMuteDurationDialog && isGroupChat) {
        val now = System.currentTimeMillis()
        AddPhotoStyleActionSheet(
            title = "Mute Chat",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.Default.NotificationsOff,
                    title = "Mute for 1 hour",
                    subtitle = "Temporarily silence this group",
                    accentColor = Color(0xFF4FC3F7),
                    onClick = {
                        showMuteDurationDialog = false
                        viewModel.muteChat(currentUser.uid, chatId, now + (1L * 60L * 60L * 1000L))
                        Toast.makeText(context, "Chat muted for 1 hour", Toast.LENGTH_SHORT).show()
                    }
                ),
                ActionSheetOption(
                    icon = Icons.Default.NotificationsOff,
                    title = "Mute for 8 hours",
                    subtitle = "Good for the rest of your day",
                    accentColor = Color(0xFF4FC3F7),
                    onClick = {
                        showMuteDurationDialog = false
                        viewModel.muteChat(currentUser.uid, chatId, now + (8L * 60L * 60L * 1000L))
                        Toast.makeText(context, "Chat muted for 8 hours", Toast.LENGTH_SHORT).show()
                    }
                ),
                ActionSheetOption(
                    icon = Icons.Default.NotificationsOff,
                    title = "Mute for 24 hours",
                    subtitle = "Silence notifications until tomorrow",
                    accentColor = Color(0xFF4FC3F7),
                    onClick = {
                        showMuteDurationDialog = false
                        viewModel.muteChat(currentUser.uid, chatId, now + (24L * 60L * 60L * 1000L))
                        Toast.makeText(context, "Chat muted for 24 hours", Toast.LENGTH_SHORT).show()
                    }
                ),
                ActionSheetOption(
                    icon = Icons.Default.NotificationsOff,
                    title = "Mute for 7 days",
                    subtitle = "Silence this group for a week",
                    accentColor = Color(0xFF4FC3F7),
                    onClick = {
                        showMuteDurationDialog = false
                        viewModel.muteChat(currentUser.uid, chatId, now + (7L * 24L * 60L * 60L * 1000L))
                        Toast.makeText(context, "Chat muted for 7 days", Toast.LENGTH_SHORT).show()
                    }
                )
            ),
            onDismiss = { showMuteDurationDialog = false },
            cancelTitle = "Cancel",
            cancelSubtitle = "Keep notifications on",
            cancelIcon = Icons.Default.Close,
            cancelAccentColor = Color(0xFF4B5563)
        )
    }

    if (showGroupInfoDialog && isGroupChat) {
        val groupName = editableGroupName.ifBlank { "Group" }
        val groupIcon = editableGroupIcon.ifBlank { "👥" }
        val groupIconUrl = if (groupIcon.startsWith("http", ignoreCase = true)) groupIcon else ""
        val memberIds = groupModel.effectiveMemberIds().ifEmpty { chatSession.participants.distinct() }

        Dialog(
            onDismissRequest = { showGroupInfoDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize(),
                color = Color(0xFF121212),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 0.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2A2A)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (groupIconUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = groupIconUrl,
                                        contentDescription = groupName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = groupIcon,
                                        color = Color.White,
                                        fontSize = 24.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = groupName,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${memberIds.size} members",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        IconButton(onClick = { showGroupInfoDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    if ((isAdmin || isOwner) && homeViewModel != null) {
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = editableGroupName,
                            onValueChange = { editableGroupName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Group name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4FC3F7),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                focusedLabelColor = Color(0xFF4FC3F7),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editableGroupIcon,
                            onValueChange = { editableGroupIcon = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Group icon (emoji or URL)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4FC3F7),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                focusedLabelColor = Color(0xFF4FC3F7),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.75f)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val cleanName = editableGroupName.trim().ifBlank { "Group" }
                                val cleanIcon = editableGroupIcon.trim().ifBlank { "👥" }
                                homeViewModel.updateFriendGroup(
                                    userId = currentUser.uid,
                                    groupId = currentGroupId,
                                    name = cleanName,
                                    icon = cleanIcon,
                                    friendIds = groupModel.membersExcludingOwner(),
                                    color = "#4FC3F7"
                                ) { success ->
                                    Toast.makeText(
                                        context,
                                        if (success) "Group updated" else "Failed to update group",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A4A73),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Save group details")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if ((isAdmin || isOwner) && homeViewModel != null && friendsViewModel != null && availableInviteCandidates.isNotEmpty()) {
                        Text(
                            text = "Add members",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(availableInviteCandidates) { _, profile ->
                                val selected = selectedInviteeIds.contains(profile.uid)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) Color(0xFF1F3240) else Color(0xFF1D1D1D))
                                        .clickable {
                                            selectedInviteeIds = if (selected) selectedInviteeIds - profile.uid else selectedInviteeIds + profile.uid
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = profile.displayName.ifBlank { "User" },
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4FC3F7))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                selectedInviteeIds.forEach { inviteeId ->
                                    homeViewModel.inviteFriendToGroup(
                                        inviterId = currentUser.uid,
                                        inviterName = currentUser.displayName.ifBlank { "User" },
                                        groupId = currentGroupId,
                                        inviteeId = inviteeId
                                    ) { _, _ -> }
                                }
                                Toast.makeText(context, "Invites sent", Toast.LENGTH_SHORT).show()
                                selectedInviteeIds = emptySet()
                            },
                            enabled = selectedInviteeIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A4A73),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Invite selected")
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = "Members",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(memberIds) { _, memberId ->
                            val memberName = chatSession.participantNames[memberId]
                                ?.takeIf { it.isNotBlank() }
                                ?: if (memberId == currentUser.uid) "You" else "Member"

                            val fallbackPhoto = chatSession.participantPhotos[memberId].orEmpty()
                            val memberPhotoUrl = rememberLiveUserPhotoUrl(
                                userId = memberId,
                                fallbackPhotoUrl = fallbackPhoto
                            )
                            val canRemove = (isAdmin || isOwner) && memberId != currentUser.uid && memberId != groupModel.effectiveOwnerId()
                            val markedForRemoval = selectedRemovableIds.contains(memberId)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (markedForRemoval) Color(0xFF3B1F1F) else Color(0xFF1D1D1D))
                                    .clickable { onUserProfileClick(memberId) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (memberPhotoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = memberPhotoUrl,
                                        contentDescription = memberName,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3A3A3A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = memberName.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = memberName,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            memberId == groupModel.effectiveOwnerId() -> "Owner"
                                            groupModel.isAdmin(memberId) -> "Admin"
                                            else -> "Member"
                                        },
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 12.sp
                                    )
                                }

                                if (canRemove) {
                                    TextButton(
                                        onClick = {
                                            selectedRemovableIds = if (markedForRemoval) {
                                                selectedRemovableIds - memberId
                                            } else {
                                                selectedRemovableIds + memberId
                                            }
                                        }
                                    ) {
                                        Text(if (markedForRemoval) "Undo" else "Remove", color = Color.Red)
                                    }
                                }
                            }
                        }
                    }

                    if ((isAdmin || isOwner) && homeViewModel != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val newFriendIds = groupModel.membersExcludingOwner()
                                    .filter { it !in selectedRemovableIds }
                                homeViewModel.updateFriendGroup(
                                    userId = currentUser.uid,
                                    groupId = currentGroupId,
                                    name = editableGroupName.ifBlank { "Group" },
                                    icon = editableGroupIcon.ifBlank { "👥" },
                                    friendIds = newFriendIds,
                                    color = "#4FC3F7"
                                ) { success ->
                                    Toast.makeText(
                                        context,
                                        if (success) "Members updated" else "Failed to update members",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (success) selectedRemovableIds = emptySet()
                                }
                            },
                            enabled = selectedRemovableIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7A2626),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Remove selected members")
                        }
                    }

                    if (!(isAdmin || isOwner)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Only owner/admin can edit this group",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
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
    val currentTierRingColor = rememberLiveUserTierColor(currentOtherUserId)

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
            otherUserName = if (currentChatSession.isGroup) currentChatSession.groupName.ifBlank { currentName } else currentName,
            otherUserPhoto = if (currentChatSession.isGroup) "" else currentOtherUserPhoto
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
                            .border(2.dp, currentTierRingColor, CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                } else {
                    val isGroupItem = item.chatSession.isGroup || item.otherUserId.startsWith("group:")
                    val groupIcon = item.chatSession.groupIcon
                    val groupIconUrl = if (isGroupItem && groupIcon.startsWith("http", ignoreCase = true)) groupIcon else ""
                    val quickSwitchPhoto = if (isGroupItem) {
                        if (groupIconUrl.isNotBlank()) groupIconUrl else item.otherUserPhoto
                    } else rememberLiveUserPhotoUrl(
                        userId = item.otherUserId,
                        fallbackPhotoUrl = item.otherUserPhoto
                    )
                    val avatarTierRingColor = if (isGroupItem) Color(0xFF4FC3F7) else rememberLiveUserTierColor(item.otherUserId)
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .border(2.dp, avatarTierRingColor, CircleShape)
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
                                    .clip(CircleShape)
                                    .border(2.dp, avatarTierRingColor, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = if (isGroupItem) item.chatSession.groupIcon.takeIf { !it.startsWith("http", ignoreCase = true) }?.ifBlank { "👥" } ?: "👥" else "",
                                fontSize = if (isCenter) 18.sp else 14.sp,
                                color = Color.White
                            )
                            if (!isGroupItem) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isCenter) 22.dp else 18.dp)
                                )
                            }
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