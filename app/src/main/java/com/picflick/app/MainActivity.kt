package com.picflick.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.FriendGroup
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.navigation.Screen
import com.picflick.app.repository.FlickRepository
import com.picflick.app.data.Flick
import com.picflick.app.data.getDailyUploadLimit
import com.picflick.app.ui.components.ActionSheetOption
import com.picflick.app.ui.components.AddPhotoStyleActionSheet
import com.picflick.app.ui.components.BottomNavBar
import com.picflick.app.ui.components.LogoImage
import com.picflick.app.ui.components.UploadSourceDialog
import com.picflick.app.ui.screens.LoginScreen
import com.picflick.app.ui.screens.SplashScreen
import com.picflick.app.ui.theme.PicFlickTheme
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.utils.Analytics
import com.picflick.app.utils.LocaleHelper
import com.picflick.app.viewmodel.AuthViewModel
import com.picflick.app.viewmodel.BillingViewModel
import com.picflick.app.viewmodel.ChatViewModel
import com.picflick.app.viewmodel.FriendsViewModel
import com.picflick.app.viewmodel.HomeViewModel
import com.picflick.app.viewmodel.NotificationViewModel
import com.picflick.app.viewmodel.ProfileViewModel
import com.picflick.app.viewmodel.UploadViewModel
import kotlinx.coroutines.tasks.await

/**
 * Main Activity - Entry point for the PicFlick app
 * Uses clean architecture with ViewModels and separated concerns
 */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        // Apply saved locale to the context
        val context = LocaleHelper.applySavedLocale(newBase)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle push tap when app is launched from a killed/background state
        handlePushNotification(intent)
        // Handle images shared from other apps (Gallery -> Share -> PicFlick)
        handleShareIntent(intent)

        // Enforce visible system bars across all screens/devices
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())

        // Initialize theme manager before setting content
        ThemeManager.init(this)

        setContent {
            PicFlickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())
    }

    // Store push notification data for handling when app opens
    private var pendingPushData: android.os.Bundle? = null
    var pushEventVersion by mutableStateOf(0)
        private set

    // Store shared image URIs from external apps (Gallery -> Share -> PicFlick)
    private var pendingSharedImageUris by mutableStateOf<List<Uri>>(emptyList())
    var shareEventVersion by mutableStateOf(0)
        private set

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle push notification clicks
        handlePushNotification(intent)
        // Handle share intents while app is already running
        handleShareIntent(intent)
    }

    private fun android.os.Bundle.getFirstString(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> getString(key)?.takeIf { it.isNotBlank() } }
    }

    private fun handleShareIntent(intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val mimeType = intent.type.orEmpty()
        if (!mimeType.startsWith("image/")) return

        val sharedUris = mutableListOf<Uri>()

        if (action == Intent.ACTION_SEND) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            uri?.let { sharedUris.add(it) }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { sharedUris.addAll(it) }
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.let { sharedUris.addAll(it) }
            }
        }

        val uniqueUris = sharedUris.distinct()
        if (uniqueUris.isEmpty()) return

        pendingSharedImageUris = uniqueUris
        shareEventVersion++
    }

    private fun handlePushNotification(intent: Intent) {
        val extras = intent.extras ?: android.os.Bundle()
        val deepLinkData = intent.data

        val deepLinkLastSegment = deepLinkData?.lastPathSegment?.takeIf { it.isNotBlank() }
        val deepLinkHost = deepLinkData?.host?.lowercase()

        val flickId = extras.getFirstString("flickId", "flick_id", "postId", "post_id")
            ?: if (deepLinkHost == "photo") deepLinkLastSegment else null
        val groupId = extras.getFirstString("groupId", "group_id", "groupChatId", "group_chat_id")
        val groupName = extras.getFirstString("groupName", "group_name")
        val chatId = extras.getFirstString("chatId", "chat_id", "conversationId", "groupChatId", "group_chat_id", "threadId", "thread_id", "roomId", "room_id")
            ?: if (groupId.isNullOrBlank()) null else "group_$groupId"
            ?: if (deepLinkHost == "chat") deepLinkLastSegment else null
        val senderId = extras.getFirstString("senderId", "sender_id", "fromUserId", "userId")
            ?: if (deepLinkHost == "profile") deepLinkLastSegment else null
        val senderName = extras.getFirstString("senderName", "sender_name", "fromUserName", "userName")
        val notificationType = extras.getFirstString("type", "notificationType")
        val titleHint = extras.getFirstString("title", "notificationTitle", "gcm.notification.title")
        val bodyHint = extras.getFirstString("message", "body", "notificationBody", "gcm.notification.body")
        val notificationTypeLower = notificationType?.lowercase().orEmpty()
        val textHintLower = "${titleHint.orEmpty()} ${bodyHint.orEmpty()}".lowercase()
        val isCommentNotification = notificationTypeLower.contains("comment")
        val isFriendRequestNotification =
            (notificationTypeLower.contains("friend") || notificationTypeLower.contains("follow")) &&
                notificationTypeLower.contains("request")
        val isWelcomeFindFriendsNotification =
            notificationTypeLower.contains("welcome") ||
                notificationTypeLower.contains("onboarding") ||
                textHintLower.contains("find friends") ||
                textHintLower.contains("tap to find")

        val explicitTargetScreen = extras.getFirstString("targetScreen", "screen", "destination")
            ?: when {
                deepLinkHost == "photo" || isCommentNotification -> "photo"
                deepLinkHost == "chat" -> "chat"
                deepLinkHost == "find_friends" || deepLinkHost == "friends" -> "find_friends"
                deepLinkHost == "profile" -> "profile"
                isFriendRequestNotification || isWelcomeFindFriendsNotification -> "find_friends"
                else -> null
            }

        val hasPushRoutingData = !flickId.isNullOrBlank() ||
            !chatId.isNullOrBlank() ||
            !senderId.isNullOrBlank() ||
            !notificationType.isNullOrBlank() ||
            !explicitTargetScreen.isNullOrBlank() ||
            isCommentNotification ||
            isFriendRequestNotification ||
            isWelcomeFindFriendsNotification

        if (!hasPushRoutingData) {
            return
        }

        val targetScreen = when {
            isCommentNotification -> "photo"
            isFriendRequestNotification || isWelcomeFindFriendsNotification -> "find_friends"
            !explicitTargetScreen.isNullOrBlank() -> explicitTargetScreen
            !flickId.isNullOrBlank() -> "photo"
            !chatId.isNullOrBlank() -> "chat"
            !senderId.isNullOrBlank() -> "profile"
            notificationTypeLower.contains("message") -> "chat"
            notificationTypeLower.contains("mention") ||
                notificationTypeLower.contains("comment") ||
                notificationTypeLower.contains("reaction") ||
                notificationTypeLower.contains("like") -> "photo"
            notificationTypeLower.contains("follow") ||
                notificationTypeLower.contains("friend") ||
                notificationTypeLower.contains("profile") -> "profile"
            else -> "notifications"
        }

        android.util.Log.d(
            "MainActivity",
            "Push notification clicked: type=$notificationType, screen=$targetScreen, flickId=$flickId, chatId=$chatId, sender=$senderId, groupId=$groupId"
        )

        val normalizedExtras = android.os.Bundle(extras).apply {
            putString("targetScreen", targetScreen)
            putString("flickId", flickId ?: "")
            putString("chatId", chatId ?: "")
            putString("senderId", senderId ?: "")
            putString("senderName", senderName ?: "")
            putString("groupId", groupId ?: "")
            putString("groupName", groupName ?: "")
            putString("type", notificationType ?: "")
            putBoolean("openComments", isCommentNotification)
        }

        pendingPushData = normalizedExtras
        pushEventVersion++
    }

    /**
     * Call this from MainScreen to get any pending push notification data
     */
    fun consumePushData(): android.os.Bundle? {
        val data = pendingPushData
        pendingPushData = null
        return data
    }

    fun hasPendingPushData(): Boolean = pendingPushData != null

    fun consumeSharedImageUris(): List<Uri> {
        val uris = pendingSharedImageUris
        pendingSharedImageUris = emptyList()
        return uris
    }

    fun hasPendingSharedImages(): Boolean = pendingSharedImageUris.isNotEmpty()

    /**
     * Get current user ID from Firebase Auth
     */
    fun getCurrentUserId(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
}

/**
 * App content with splash screen first
 */
@Composable
fun AppContent() {
    var showSplash by remember { mutableStateOf(true) }
    val isDarkMode = ThemeManager.isDarkMode.value

    if (showSplash) {
        SplashScreen(
            isDarkMode = isDarkMode,
            onSplashComplete = { showSplash = false }
        )
    } else {
        MainScreen()
    }
}

/**
 * Main screen with navigation and authentication state
 */
@Composable
fun MainScreen(
    authViewModel: AuthViewModel = viewModel(),
    billingViewModel: BillingViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    friendsViewModel: FriendsViewModel = viewModel(),
    notificationViewModel: NotificationViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    uploadViewModel: UploadViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var openHomeGroupsManager by remember { mutableStateOf(false) }
    var wasPreviouslyLoggedOut by remember { mutableStateOf(true) }
    val appContext = LocalContext.current

    var forceHomeResetVersion by remember { mutableIntStateOf(0) }

    fun navigateTo(screen: Screen) {
        if (screen == currentScreen) return
        currentScreen = screen
    }

    fun parentScreenFor(screen: Screen): Screen? = when (screen) {
        is Screen.Home -> null
        is Screen.ChatDetail -> Screen.Chats
        is Screen.EditPhoto -> screen.returnTo
        is Screen.About,
        is Screen.Contact,
        is Screen.Privacy,
        is Screen.NotificationSettings,
        is Screen.ManageStorage,
        is Screen.SubscriptionStatus,
        is Screen.PlanOptions,
        is Screen.StreakAchievements,
        is Screen.PrivacyPolicy,
        is Screen.Philosophy,
        is Screen.Legal,
        is Screen.Developer -> Screen.Settings
        is Screen.FindFriends,
        is Screen.UserFriends,
        is Screen.UserProfile -> Screen.Friends
        is Screen.Profile,
        is Screen.MyPhotos,
        is Screen.Friends,
        is Screen.Chats,
        is Screen.Notifications,
        is Screen.Filter,
        is Screen.Explore,
        is Screen.Preview,
        is Screen.Settings -> Screen.Home
    }

    fun navigateBackOneScreen() {
        parentScreenFor(currentScreen)?.let { parent ->
            currentScreen = parent
        }
    }

    // State for push notification photo (opens FullScreenPhotoViewer directly)
    var pushPhoto by remember { mutableStateOf<Flick?>(null) }
    var pushPhotoOpenComments by remember { mutableStateOf(false) }

    // State for selected chat (for navigation to ChatDetail)
    var selectedChatSession by remember { mutableStateOf<ChatSession?>(null) }
    var selectedOtherUserId by remember { mutableStateOf<String>("") }

    // Track login state for analytics
    val activity = appContext as? MainActivity
    val currentUser = authViewModel.currentUser
    val lifecycleOwner = LocalLifecycleOwner.current

    val userProfile = authViewModel.userProfile

    // Shared upload/dialog states (declared early because they're used by effects below)
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var privateSharePhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showPrivateShareTargetDialog by remember { mutableStateOf(false) }
    var pendingSharedImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showSharedImportChoiceDialog by remember { mutableStateOf(false) }

    // On app foreground, default back to Home + reset feed top unless a push route is pending.
    DisposableEffect(lifecycleOwner, currentUser) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && currentUser != null) {
                val hasPushPending = activity?.hasPendingPushData() == true
                if (!hasPushPending) {
                    val shouldPreserveCurrentScreen =
                        currentScreen is Screen.EditPhoto ||
                            currentScreen is Screen.Filter ||
                            currentScreen is Screen.ChatDetail ||
                            currentScreen is Screen.UserProfile

                    if (!shouldPreserveCurrentScreen) {
                        navigateTo(Screen.Home)
                        forceHomeResetVersion += 1
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val context = LocalContext.current

    val pushEventVersion = activity?.pushEventVersion ?: 0
    val shareEventVersion = activity?.shareEventVersion ?: 0

    LaunchedEffect(shareEventVersion, currentUser?.uid, userProfile?.uid) {
        if (currentUser == null) return@LaunchedEffect

        val profile = userProfile ?: return@LaunchedEffect

        val sharedUris = activity?.consumeSharedImageUris().orEmpty().distinct()
        if (sharedUris.isEmpty()) return@LaunchedEffect

        val tier = profile.getEffectiveTier()
        val dailyLimit = tier.getDailyUploadLimit()
        val remainingDaily = if (dailyLimit == Int.MAX_VALUE) Int.MAX_VALUE else (dailyLimit - uploadViewModel.dailyUploadCount).coerceAtLeast(0)
        val allowedCount = if (tier == SubscriptionTier.ULTRA) minOf(100, remainingDaily) else remainingDaily

        if (allowedCount <= 0) {
            Toast.makeText(context, "Daily upload limit reached", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }

        val cappedUris = sharedUris.take(allowedCount)
        if (sharedUris.size > allowedCount) {
            Toast.makeText(
                context,
                "Shared ${sharedUris.size}. Using first $allowedCount due to daily limit.",
                Toast.LENGTH_LONG
            ).show()
        }

        pendingSharedImportUris = cappedUris

        if (cappedUris.size == 1) {
            selectedPhotoUri = cappedUris.first()
            navigateTo(Screen.Filter)
        } else {
            showSharedImportChoiceDialog = true
        }
    }

    LaunchedEffect(pushEventVersion, currentUser?.uid) {
        if (currentUser == null) return@LaunchedEffect

        // Check if there's pending push notification data
        val pushData = activity?.consumePushData()
        if (pushData != null) {
            val targetScreen = pushData.getString("targetScreen")
            val senderId = pushData.getString("senderId")
            val senderName = pushData.getString("senderName")

            android.util.Log.d("MainScreen", "Processing push data: screen=$targetScreen, sender=$senderId")

            when (targetScreen) {
                "notifications" -> {
                    currentScreen = Screen.Notifications
                }
                "chat" -> {
                    val currentUserId = activity.getCurrentUserId()
                    val pushedGroupId = pushData.getString("groupId").orEmpty()
                    val pushedGroupName = pushData.getString("groupName").orEmpty()
                    val rawPushedChatId = pushData.getString("chatId").orEmpty()

                    val isGroupHint = pushedGroupId.isNotBlank() || pushedGroupName.isNotBlank()
                    val chatIdCandidates = listOf(
                        rawPushedChatId,
                        if (rawPushedChatId.isNotBlank()) "group_$rawPushedChatId" else "",
                        if (pushedGroupId.isNotBlank()) "group_$pushedGroupId" else ""
                    ).filter { it.isNotBlank() }.distinct()

                    if (currentUserId != null && (chatIdCandidates.isNotEmpty() || isGroupHint)) {

                        val sessionById = chatIdCandidates
                            .asSequence()
                            .mapNotNull { candidateId -> chatViewModel.chatSessions.firstOrNull { it.id == candidateId } }
                            .firstOrNull()

                        val sessionByGroup = if (pushedGroupId.isNotBlank()) {
                            chatViewModel.chatSessions.firstOrNull { session ->
                                val sessionGroupId = session.groupId.ifBlank { session.id.removePrefix("group_") }
                                (session.isGroup || session.id.startsWith("group_") || session.groupId.isNotBlank()) &&
                                    sessionGroupId == pushedGroupId
                            }
                        } else {
                            null
                        }

                        val sessionFromVm = sessionById ?: sessionByGroup

                        val finalChatId = sessionFromVm?.id
                            ?: if (pushedGroupId.isNotBlank()) "group_$pushedGroupId" else chatIdCandidates.firstOrNull().orEmpty()

                        if (finalChatId.isBlank()) {
                            currentScreen = Screen.Chats
                            return@LaunchedEffect
                        }

                        val isGroupPush =
                            (sessionFromVm?.isGroup == true) ||
                                isGroupHint ||
                                finalChatId.startsWith("group_")

                        val fallbackGroupId = pushedGroupId.ifBlank {
                            if (finalChatId.startsWith("group_")) finalChatId.removePrefix("group_") else finalChatId
                        }

                        selectedChatSession = sessionFromVm ?: ChatSession(
                            id = finalChatId,
                            participants = if (isGroupPush) listOf(currentUserId) else listOf(currentUserId, senderId.orEmpty()),
                            participantNames = if (isGroupPush) {
                                mapOf(currentUserId to (authViewModel.userProfile?.displayName ?: "You"))
                            } else {
                                mapOf(
                                    currentUserId to (authViewModel.userProfile?.displayName ?: "You"),
                                    senderId.orEmpty() to (senderName?.ifBlank { null } ?: "Chat")
                                )
                            },
                            participantPhotos = mapOf(currentUserId to (authViewModel.userProfile?.photoUrl ?: "")),
                            isGroup = isGroupPush,
                            groupId = if (isGroupPush) fallbackGroupId else "",
                            groupName = if (isGroupPush) pushedGroupName.ifBlank { "Group" } else "",
                            groupIcon = if (isGroupPush) "👥" else "👥",
                            lastMessage = "",
                            lastTimestamp = System.currentTimeMillis(),
                            unreadCount = 0
                        )

                        selectedOtherUserId = if (isGroupPush) {
                            "group:$fallbackGroupId"
                        } else {
                            senderId.orEmpty()
                        }
                        currentScreen = Screen.ChatDetail
                    } else {
                        // Never auto-fallback to opening a direct user chat from push when chat/group id
                        // is missing; that caused wrong-chat opens for group notifications.
                        currentScreen = Screen.Chats
                    }
                }
                "profile" -> {
                    if (senderId != null) {
                        currentScreen = Screen.UserProfile(senderId)
                    }
                }
                "find_friends" -> {
                    currentScreen = Screen.FindFriends(priorityRequesterId = senderId?.takeIf { it.isNotBlank() })
                }
                "photo" -> {
                    val flickId = pushData.getString("flickId")
                    val openComments = pushData.getBoolean("openComments", false)
                    android.util.Log.d("MainActivity", "Opening photo from push: flickId=$flickId, openComments=$openComments")
                    if (!flickId.isNullOrBlank()) {
                        // Load the flick and open FullScreenPhotoViewer
                        val repository = FlickRepository.getInstance()
                        repository.getFlickById(flickId) { result ->
                            when (result) {
                                is com.picflick.app.data.Result.Success -> {
                                    android.util.Log.d("MainActivity", "Photo loaded: ${result.data.imageUrl}")
                                    pushPhotoOpenComments = openComments
                                    pushPhoto = result.data
                                }
                                is com.picflick.app.data.Result.Error -> {
                                    android.util.Log.e("MainActivity", "Failed to load photo from push: ${result.message}")
                                    // Fall back to notifications screen
                                    currentScreen = Screen.Notifications
                                }
                                else -> {}
                            }
                        }
                    } else {
                        // No flickId provided, open notifications
                        currentScreen = Screen.Notifications
                    }
                }
            }
        }
    }

    // Handle phone back: always go back exactly one parent level
    BackHandler(enabled = parentScreenFor(currentScreen) != null) {
        navigateBackOneScreen()
    }

    // Initialize billing client
    LaunchedEffect(Unit) {
        billingViewModel.initialize(context)
    }

    // Restore cached profile when app resumes (prevents data loss)
    LaunchedEffect(Unit) {
        authViewModel.restoreCachedProfile()
    }

    // Track screen views for analytics
    LaunchedEffect(currentScreen) {
        val screenName = when (currentScreen) {
            is Screen.Home -> "home"
            is Screen.Profile -> "profile"
            is Screen.Chats -> "chats"
            is Screen.Friends -> "friends"
            is Screen.UserFriends -> "user_friends"
            is Screen.Notifications -> "notifications"
            is Screen.Settings -> "settings"
            is Screen.UserProfile -> "user_profile"
            is Screen.Filter -> "filter"
            is Screen.Preview -> "preview"
            else -> "unknown"
        }
        Analytics.trackScreenView(screenName)
    }

    // Track login state changes
    LaunchedEffect(currentUser) {
        if (currentUser != null && wasPreviouslyLoggedOut) {
            Analytics.trackLogin()
            wasPreviouslyLoggedOut = false
        } else if (currentUser == null) {
            wasPreviouslyLoggedOut = true
        }
    }

    // State for profile photo upload
    var profilePhotoToUpload by remember { mutableStateOf<Uri?>(null) }

    // Handle profile photo upload
    LaunchedEffect(profilePhotoToUpload) {
        profilePhotoToUpload?.let { uri ->
            userProfile?.let { profile ->
                try {
                    val uid = profile.uid
                    val repository = FlickRepository.getInstance()

                    // Convert URI to bytes
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val imageBytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (imageBytes != null) {
                        // Upload to Firebase Storage
                        val result = repository.uploadFlickImage(uid, imageBytes)

                        when (result) {
                            is com.picflick.app.data.Result.Success -> {
                                // Update profile with new photo URL
                                authViewModel.updateProfilePhoto(result.data)

                                // Notify mutual friends that profile photo changed
                                val notificationResult = repository.createProfilePhotoUpdatedNotifications(
                                    userId = profile.uid,
                                    userName = profile.displayName,
                                    userPhotoUrl = result.data
                                )
                                if (notificationResult is com.picflick.app.data.Result.Error) {
                                    android.util.Log.w(
                                        "ProfilePhotoNotification",
                                        "Failed to create profile-photo update notifications: ${notificationResult.message}"
                                    )
                                }

                                Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                            }
                            is com.picflick.app.data.Result.Error -> {
                                Toast.makeText(context, "Failed to upload: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            // Reset after upload
            profilePhotoToUpload = null
        }
    }

    // Upload flow states
    var showUploadSourceDialog by remember { mutableStateOf(false) }

    // Android 13+ runtime permission for push notification display
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.d("FCM", "POST_NOTIFICATIONS granted: $granted")
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Load notifications when user is authenticated
    LaunchedEffect(userProfile?.uid) {
        android.util.Log.d("MainActivity", "LaunchedEffect triggered. userProfile.uid=${userProfile?.uid}")
        userProfile?.uid?.let { uid ->
            android.util.Log.d("MainActivity", "notificationViewModel is null: ${notificationViewModel == null}")
            android.util.Log.d("MainActivity", "Loading notifications for user: $uid")
            notificationViewModel.loadNotifications(uid)
        } ?: run {
            android.util.Log.w("MainActivity", "Cannot load notifications - userProfile or uid is null")
        }
    }

    // Load daily upload count when user is authenticated
    LaunchedEffect(userProfile?.uid) {
        userProfile?.let { profile ->
            uploadViewModel.loadDailyUploadCount(profile)
        }
    }

    // Observe unread chat count globally so bottom-nav badge updates on every screen.
    LaunchedEffect(userProfile?.uid) {
        userProfile?.uid?.let { uid ->
            chatViewModel.observeUnreadCount(uid)
        }
    }

    // Fetch and save FCM token when user is authenticated
    LaunchedEffect(userProfile?.uid) {
        userProfile?.uid?.let { uid ->
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            // Save token to Firestore
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .update("fcmToken", token)
                                .addOnSuccessListener {
                                    android.util.Log.d("FCM", "Token saved on app start: $token")
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("FCM", "Failed to save token on start: ${e.message}")
                                }
                        } else {
                            android.util.Log.e("FCM", "Failed to get token: ${task.exception?.message}")
                        }
                    }

                // Also save/update phone number on app start
                try {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_PHONE_STATE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val telephonyManager = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                        val phoneNumber = telephonyManager.line1Number?.replace("[^0-9]".toRegex(), "")

                        if (!phoneNumber.isNullOrEmpty() && userProfile?.phoneNumber.isNullOrEmpty()) {
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .update("phoneNumber", phoneNumber)
                                .addOnSuccessListener {
                                    android.util.Log.d("Phone", "Phone number saved on app start: $phoneNumber")
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("Phone", "Failed to save phone number: ${e.message}")
                                }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Phone", "Error getting phone number: ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("FCM", "Error fetching token: ${e.message}")
            }
        }
    }

    val unreadCount = notificationViewModel.unreadCount

    // Image pickers for upload flow
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        val selectedUris = uris.distinct()
        if (selectedUris.isEmpty()) return@rememberLauncherForActivityResult

        pendingSharedImportUris = selectedUris

        if (selectedUris.size == 1) {
            selectedPhotoUri = selectedUris.first()
            navigateTo(Screen.Filter)
            return@rememberLauncherForActivityResult
        }

        val profile = userProfile ?: return@rememberLauncherForActivityResult

        val tier = profile.getEffectiveTier()
        val dailyLimit = tier.getDailyUploadLimit()
        val remainingDaily = if (dailyLimit == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            (dailyLimit - uploadViewModel.dailyUploadCount).coerceAtLeast(0)
        }
        val batchCap = if (tier == SubscriptionTier.ULTRA) 100 else remainingDaily

        if (batchCap <= 0) {
            Toast.makeText(context, "Daily upload limit reached", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        val uploadUris = selectedUris.take(batchCap)
        if (selectedUris.size > batchCap) {
            Toast.makeText(
                context,
                "Selected ${selectedUris.size}. Uploading first $batchCap for your current limit.",
                Toast.LENGTH_LONG
            ).show()
        }

        Toast.makeText(
            context,
            "Batch upload uses original photos. You can edit/tag later in your profile.",
            Toast.LENGTH_LONG
        ).show()

        uploadViewModel.uploadPhotosBatch(
            context = context,
            photoUris = uploadUris,
            userProfile = profile,
            onOptimisticAdd = { optimisticFlick ->
                homeViewModel.addOptimisticFlick(optimisticFlick)
            },
            onOptimisticRemove = { flickId, uploadSucceeded ->
                if (!uploadSucceeded) {
                    homeViewModel.removeOptimisticFlick(flickId)
                }
            },
            onBatchSuccess = {
                homeViewModel.requestDebouncedFeedRefresh(profile.uid, 0L)
            }
        )

        pendingSharedImportUris = emptyList()
        currentScreen = Screen.Home
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && selectedPhotoUri != null) {
            currentScreen = Screen.Filter
        } else {
            Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    val privateShareGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        val selectedUris = uris.distinct()
        if (selectedUris.isEmpty()) return@rememberLauncherForActivityResult

        val profile = userProfile ?: return@rememberLauncherForActivityResult
        val tier = profile.getEffectiveTier()
        val dailyLimit = tier.getDailyUploadLimit()
        val remainingDaily = if (dailyLimit == Int.MAX_VALUE) Int.MAX_VALUE else (dailyLimit - uploadViewModel.dailyUploadCount).coerceAtLeast(0)
        val allowedCount = if (tier == SubscriptionTier.ULTRA) minOf(100, remainingDaily) else remainingDaily

        if (allowedCount <= 0) {
            Toast.makeText(context, "Daily upload limit reached", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        val sendUris = selectedUris.take(allowedCount)
        if (selectedUris.size > allowedCount) {
            Toast.makeText(context, "Selected ${selectedUris.size}. Sending first $allowedCount due to daily limit.", Toast.LENGTH_LONG).show()
        }

        privateSharePhotoUris = sendUris
        showPrivateShareTargetDialog = true
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            try {
                val tempFile = java.io.File.createTempFile("camera_", ".jpg", context.cacheDir)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
                selectedPhotoUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error creating file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
        }
    }

    // Handle upload state silently for smoother optimistic UX
    // IMPORTANT: only auto-return to Home from upload flow screens.
    LaunchedEffect(uploadViewModel.uploadSuccess, currentScreen) {
        if (uploadViewModel.uploadSuccess) {
            uploadViewModel.resetUploadState()
                                if (currentScreen is Screen.Filter) {
                        navigateTo(Screen.Home)
                    }
        }
    }

    LaunchedEffect(uploadViewModel.uploadError) {
        uploadViewModel.uploadError?.let {
            uploadViewModel.resetUploadState()
        }
    }

    // Upload Source Dialog
    if (showUploadSourceDialog && userProfile != null) {
        UploadSourceDialog(
            onDismiss = { showUploadSourceDialog = false },
            onCameraClick = {
                showUploadSourceDialog = false
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            },
            onGalleryClick = {
                showUploadSourceDialog = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onSharePrivatelyClick = {
                showUploadSourceDialog = false
                privateShareGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    // Snackbar state for error feedback
    val snackbarHostState = remember { SnackbarHostState() }

    if (showSharedImportChoiceDialog && pendingSharedImportUris.isNotEmpty() && userProfile != null) {
        AddPhotoStyleActionSheet(
            title = "Import shared photos",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.Outlined.Menu,
                    title = "Post to PicFlick",
                    subtitle = "Add ${pendingSharedImportUris.size} photo(s) to your feed",
                    accentColor = Color(0xFF2E86DE),
                    onClick = {
                        val profile = userProfile
                        if (profile != null) {
                            val uploadUris = pendingSharedImportUris
                            uploadViewModel.uploadPhotosBatch(
                                context = context,
                                photoUris = uploadUris,
                                userProfile = profile,
                                onOptimisticAdd = { optimisticFlick ->
                                    homeViewModel.addOptimisticFlick(optimisticFlick)
                                },
                                onOptimisticRemove = { flickId, uploadSucceeded ->
                                    if (!uploadSucceeded) {
                                        homeViewModel.removeOptimisticFlick(flickId)
                                    }
                                },
                                onBatchSuccess = {
                                    homeViewModel.requestDebouncedFeedRefresh(profile.uid, 0L)
                                }
                            )
                            navigateTo(Screen.Home)
                        }
                        showSharedImportChoiceDialog = false
                        pendingSharedImportUris = emptyList()
                    }
                ),
                ActionSheetOption(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "Send privately",
                    subtitle = "Share to individual or group chat",
                    accentColor = Color(0xFF2E86DE),
                    onClick = {
                        privateSharePhotoUris = pendingSharedImportUris
                        showPrivateShareTargetDialog = true
                        showSharedImportChoiceDialog = false
                    }
                )
            ),
            onDismiss = {
                showSharedImportChoiceDialog = false
                pendingSharedImportUris = emptyList()
            },
            cancelSubtitle = "Close import"
        )
    }

    if (showPrivateShareTargetDialog && privateSharePhotoUris.isNotEmpty() && userProfile != null) {
        PrivateShareTargetDialog(
            friends = friendsViewModel.followingUsers,
            groups = homeViewModel.friendGroups,
            onDismiss = {
                showPrivateShareTargetDialog = false
                privateSharePhotoUris = emptyList()
                pendingSharedImportUris = emptyList()
            },
            onShareToFriend = { friend ->
                val imageUris = privateSharePhotoUris
                chatViewModel.startChat(
                    userId = userProfile.uid,
                    otherUserId = friend.uid,
                    userName = userProfile.displayName,
                    otherUserName = friend.displayName,
                    userPhoto = userProfile.photoUrl,
                    otherUserPhoto = friend.photoUrl
                ) { chatId ->
                    imageUris.forEach { imageUri ->
                        chatViewModel.sendPhotoMessage(
                            chatId = chatId,
                            imageUri = imageUri,
                            senderId = userProfile.uid,
                            recipientId = friend.uid,
                            senderName = userProfile.displayName,
                            senderPhotoUrl = userProfile.photoUrl,
                            context = context,
                            onComplete = {
                                uploadViewModel.consumeDailyUploadSlot(userProfile.uid)
                            }
                        )
                    }
                    Toast.makeText(context, "Shared privately (${imageUris.size})", Toast.LENGTH_SHORT).show()
                }
                showPrivateShareTargetDialog = false
                privateSharePhotoUris = emptyList()
                pendingSharedImportUris = emptyList()
            },
            onShareToGroup = { group ->
                val imageUris = privateSharePhotoUris
                chatViewModel.startGroupChat(
                    ownerUserId = userProfile.uid,
                    ownerName = userProfile.displayName,
                    ownerPhoto = userProfile.photoUrl,
                    groupId = group.id,
                    groupName = group.name,
                    groupIcon = group.icon,
                    memberIds = group.friendIds
                ) { chatId ->
                    imageUris.forEach { imageUri ->
                        chatViewModel.sendPhotoMessage(
                            chatId = chatId,
                            imageUri = imageUri,
                            senderId = userProfile.uid,
                            recipientId = "group:$chatId",
                            senderName = userProfile.displayName,
                            senderPhotoUrl = userProfile.photoUrl,
                            context = context,
                            onComplete = {
                                uploadViewModel.consumeDailyUploadSlot(userProfile.uid)
                            }
                        )
                    }
                    Toast.makeText(context, "Shared privately (${imageUris.size})", Toast.LENGTH_SHORT).show()
                }
                showPrivateShareTargetDialog = false
                privateSharePhotoUris = emptyList()
            }
        )
    }
    
    // Track screen views for analytics
    LaunchedEffect(currentScreen) {
        val screenName = when (currentScreen) {
            is Screen.Home -> "home"
            is Screen.Profile -> "profile"
            is Screen.Chats -> "chats"
            is Screen.Friends -> "friends"
            is Screen.UserFriends -> "user_friends"
            is Screen.Notifications -> "notifications"
            is Screen.Settings -> "settings"
            is Screen.UserProfile -> "user_profile"
            is Screen.Filter -> "filter"
            is Screen.Preview -> "preview"
            else -> "unknown"
        }
        Analytics.trackScreenView(screenName)
    }

    // Track login state changes
    LaunchedEffect(currentUser) {
        if (currentUser != null && wasPreviouslyLoggedOut) {
            Analytics.trackLogin()
        }

        // On logout, clear any stale errors so we don't show permission-denied snackbars/toasts.
        if (currentUser == null) {
            homeViewModel.clearError()
            chatViewModel.clearError()
            notificationViewModel.clearError()
            friendsViewModel.clearError()
        }
    }

    // Collect errors from all ViewModels and show in Snackbar + track analytics
    LaunchedEffect(
        currentUser,
        homeViewModel.errorMessage,
        chatViewModel.errorMessage,
        notificationViewModel.errorMessage,
        friendsViewModel.errorMessage
    ) {
        // Ignore transient background errors while signed out.
        if (currentUser == null) return@LaunchedEffect

        val errors = listOfNotNull(
            homeViewModel.errorMessage,
            chatViewModel.errorMessage,
            notificationViewModel.errorMessage,
            friendsViewModel.errorMessage
        )

        errors.firstOrNull()?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            // Track error analytics
            Analytics.trackError(error)
            // Clear errors after showing
            homeViewModel.clearError()
            chatViewModel.clearError()
            notificationViewModel.clearError()
            friendsViewModel.clearError()
        }
    }

    val isChatDetailScreen = currentScreen is Screen.ChatDetail
    val isSettingsStackScreen = when (currentScreen) {
        is Screen.Settings,
        is Screen.ManageStorage,
        is Screen.PlanOptions,
        is Screen.NotificationSettings,
        is Screen.Privacy,
        is Screen.PrivacyPolicy,
        is Screen.Philosophy,
        is Screen.Legal,
        is Screen.About -> true
        else -> false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Outer Scaffold with bottom navigation for non-chat screens
        Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Keep header height stable even while userProfile is still loading
            if (currentUser != null) {
                val profileReady = userProfile != null
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    // Settings wheel on LEFT
                    IconButton(
                        onClick = { if (profileReady) navigateTo(Screen.Settings) },
                        enabled = profileReady,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(y = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = if (profileReady) Color.LightGray else Color.DarkGray
                        )
                    }

                    // Notifications bell on right - RED when unread, clickable
                    IconButton(
                        onClick = { if (profileReady) navigateTo(Screen.Notifications) },
                        enabled = profileReady,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(y = 4.dp)
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = when {
                                    !profileReady -> Color.DarkGray
                                    unreadCount > 0 -> Color.Red
                                    else -> Color.LightGray
                                }
                            )
                            // Red badge for unread notifications
                            if (profileReady && unreadCount > 0) {
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    containerColor = Color.Red,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    // Logo centered
                    LogoImage(
                        modifier = Modifier
                            .align(Alignment.Center)
                    )
                }
            }
        },
        bottomBar = {
            // Keep bottom bar stable while profile is loading (prevents layout jump)
            if (currentUser != null && !isChatDetailScreen) {
                val profileReady = userProfile != null
                BottomNavBar(
                    currentRoute = when (currentScreen) {
                        is Screen.Home -> "home"
                        is Screen.Chats -> "chats"
                        is Screen.Friends -> "friends"
                        is Screen.UserFriends -> "user_friends"
                        is Screen.Profile -> "profile"
                        is Screen.UserProfile -> "user_profile"
                        else -> "other"
                    },
                    onNavigate = { route ->
                        if (profileReady) {
                            when (route) {
                                "home" -> {
                                    if (currentScreen is Screen.Home) {
                                        openHomeGroupsManager = true
                                    } else {
                                        navigateTo(Screen.Home)
                                    }
                                }
                                "chats" -> navigateTo(Screen.Chats)
                                "upload" -> showUploadSourceDialog = true
                                "friends" -> navigateTo(Screen.Friends)
                                "profile" -> navigateTo(Screen.Profile)
                            }
                        }
                    },
                    unreadMessages = if (profileReady) chatViewModel.unreadCount else 0
                )
            }
        }
    ) { padding ->
        // Content area - each screen handles its own background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when {
                        isChatDetailScreen -> {
                            Modifier.padding(top = padding.calculateTopPadding())
                        }
                        isSettingsStackScreen -> {
                            Modifier.padding(top = padding.calculateTopPadding())
                        }
                        else -> {
                            Modifier.padding(padding)
                        }
                    }
                )
        ) {
            if (currentUser == null) {
                // Not authenticated - show login
                LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = {}
                )
            } else if (userProfile != null) {
                // Authenticated - show main content with navigation
                AuthenticatedContent(
                    currentScreen = currentScreen,
                    onScreenChange = { target -> navigateTo(target) },
                    userProfile = userProfile,
                    billingViewModel = billingViewModel,
                    homeViewModel = homeViewModel,
                    profileViewModel = profileViewModel,
                    friendsViewModel = friendsViewModel,
                    notificationViewModel = notificationViewModel,
                    chatViewModel = chatViewModel,
                    uploadViewModel = uploadViewModel,
                    authViewModel = authViewModel,
                    homeResetVersion = forceHomeResetVersion,
                    openGroupsManager = openHomeGroupsManager,
                    onOpenGroupsManagerConsumed = { openHomeGroupsManager = false },
                    selectedChatSession = selectedChatSession,
                    selectedOtherUserId = selectedOtherUserId,
                    onSetSelectedChat = { session, userId ->
                        selectedChatSession = session
                        selectedOtherUserId = userId
                    },
                    onSignOut = { authViewModel.signOut(appContext) },
                    selectedPhotoUri = selectedPhotoUri,
                    onPhotoSelected = { uri ->
                        profilePhotoToUpload = uri
                    },
                    onOpenUploadSourceDialog = {
                        showUploadSourceDialog = true
                    },
                    pushPhoto = pushPhoto,
                    pushPhotoOpenComments = pushPhotoOpenComments,
                    onPushPhotoConsumed = {
                        pushPhoto = null
                        pushPhotoOpenComments = false
                    }
                )
            } else {
                // Loading state - profile not loaded yet
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            }
        }

    }
}

@Composable
private fun PrivateShareTargetDialog(
    friends: List<com.picflick.app.data.UserProfile>,
    groups: List<FriendGroup>,
    onDismiss: () -> Unit,
    onShareToFriend: (com.picflick.app.data.UserProfile) -> Unit,
    onShareToGroup: (FriendGroup) -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val screenBg = isDarkModeBackground(isDarkMode)
    val primaryText = if (isDarkMode) Color.White else Color.Black
    val secondaryText = if (isDarkMode) Color.White.copy(alpha = 0.75f) else Color(0xFF5F6368)

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
            shape = RoundedCornerShape(0.dp),
            color = screenBg
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Share privately",
                        color = primaryText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = primaryText)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (groups.isNotEmpty()) {
                    Text(
                        text = "Groups",
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(modifier = Modifier.height((groups.size.coerceAtMost(3) * 64).dp)) {
                        items(groups, key = { it.id }) { group ->
                            PrivateShareListRow(
                                title = group.name.ifBlank { "Group" },
                                avatarUrl = group.icon.takeIf { it.startsWith("http") },
                                avatarEmoji = group.icon.takeIf { !it.startsWith("http") },
                                fallbackLetter = group.name.firstOrNull()?.uppercase(),
                                buttonLabel = "Share",
                                onClick = { onShareToGroup(group) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (friends.isNotEmpty()) {
                    Text(
                        text = "Individuals",
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
                        items(friends, key = { it.uid }) { friend ->
                            PrivateShareListRow(
                                title = friend.displayName.ifBlank { "Friend" },
                                avatarUrl = friend.photoUrl.takeIf { it.isNotBlank() },
                                avatarEmoji = null,
                                fallbackLetter = friend.displayName.firstOrNull()?.uppercase(),
                                buttonLabel = "Share",
                                onClick = { onShareToFriend(friend) }
                            )
                        }
                    }
                }

                if (groups.isEmpty() && friends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No groups or friends available",
                            color = secondaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateShareListRow(
    title: String,
    avatarUrl: String?,
    avatarEmoji: String?,
    fallbackLetter: String?,
    buttonLabel: String,
    onClick: () -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val primaryText = if (isDarkMode) Color.White else Color.Black
    val avatarRing = if (isDarkMode) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
    val avatarBg = if (isDarkMode) Color.Gray.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarBg)
                    .border(2.dp, avatarRing, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !avatarUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    !avatarEmoji.isNullOrBlank() -> Text(text = avatarEmoji)
                    !fallbackLetter.isNullOrBlank() -> Text(text = fallbackLetter, color = primaryText, fontWeight = FontWeight.Bold)
                    else -> Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = primaryText)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                color = primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = onClick,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFF87CEEB)
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = buttonLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}
