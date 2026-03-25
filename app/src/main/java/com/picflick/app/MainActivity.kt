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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picflick.app.data.ChatSession
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.navigation.Screen
import com.picflick.app.repository.FlickRepository
import com.picflick.app.data.Flick
import com.picflick.app.data.getDailyUploadLimit
import com.picflick.app.ui.components.BottomNavBar
import com.picflick.app.ui.components.LogoImage
import com.picflick.app.ui.components.UploadSourceDialog
import com.picflick.app.ui.screens.LoginScreen
import com.picflick.app.ui.screens.SplashScreen
import com.picflick.app.ui.theme.PicFlickTheme
import com.picflick.app.ui.theme.ThemeManager
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle push notification clicks
        handlePushNotification(intent)
    }

    private fun android.os.Bundle.getFirstString(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> getString(key)?.takeIf { it.isNotBlank() } }
    }

    private fun handlePushNotification(intent: Intent) {
        val extras = intent.extras ?: android.os.Bundle()
        val deepLinkData = intent.data

        val deepLinkLastSegment = deepLinkData?.lastPathSegment?.takeIf { it.isNotBlank() }
        val deepLinkHost = deepLinkData?.host?.lowercase()

        val flickId = extras.getFirstString("flickId", "flick_id", "postId", "post_id")
            ?: if (deepLinkHost == "photo") deepLinkLastSegment else null
        val chatId = extras.getFirstString("chatId", "chat_id", "conversationId")
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
            else -> "notifications"
        }

        android.util.Log.d(
            "MainActivity",
            "Push notification clicked: type=$notificationType, screen=$targetScreen, flickId=$flickId, chatId=$chatId, sender=$senderId"
        )

        val normalizedExtras = android.os.Bundle(extras).apply {
            putString("targetScreen", targetScreen)
            putString("flickId", flickId ?: "")
            putString("chatId", chatId ?: "")
            putString("senderId", senderId ?: "")
            putString("senderName", senderName ?: "")
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
    var wasPreviouslyLoggedOut by remember { mutableStateOf(true) }
    val appContext = LocalContext.current

    var forceHomeResetVersion by remember { mutableIntStateOf(0) }

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

    // On app foreground, default back to Home + reset feed top unless a push route is pending.
    DisposableEffect(lifecycleOwner, currentUser) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && currentUser != null) {
                val hasPushPending = activity?.hasPendingPushData() == true
                if (!hasPushPending) {
                    currentScreen = Screen.Home
                    forceHomeResetVersion += 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pushEventVersion = activity?.pushEventVersion ?: 0

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
                    val currentUserId = activity?.getCurrentUserId()
                    if (currentUserId != null && !senderId.isNullOrBlank()) {
                        val resolvedSenderName = senderName?.ifBlank { null } ?: "Chat"
                        chatViewModel.startChat(
                            userId = currentUserId,
                            otherUserId = senderId,
                            userName = resolvedSenderName,
                            otherUserName = resolvedSenderName,
                            onChatReady = { chatId ->
                                selectedOtherUserId = senderId
                                selectedChatSession = ChatSession(
                                    id = chatId,
                                    participants = listOf(currentUserId, senderId),
                                    participantNames = mapOf(
                                        currentUserId to (authViewModel.userProfile?.displayName ?: "You"),
                                        senderId to resolvedSenderName
                                    ),
                                    lastMessage = "",
                                    lastTimestamp = System.currentTimeMillis(),
                                    unreadCount = 0
                                )
                                currentScreen = Screen.ChatDetail
                            }
                        )
                    } else {
                        currentScreen = Screen.Chats
                    }
                }
                "profile" -> {
                    if (senderId != null) {
                        currentScreen = Screen.UserProfile(senderId)
                    }
                }
                "find_friends" -> {
                    currentScreen = Screen.FindFriends
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

    // Handle back button - navigate within app instead of exiting
    BackHandler(enabled = currentScreen != Screen.Home) {
        when {
            // Most screens go back to Home
            currentScreen is Screen.Profile ||
            currentScreen is Screen.MyPhotos ||
            currentScreen is Screen.Friends ||
            currentScreen is Screen.Chats ||
            currentScreen is Screen.Contact ||
            currentScreen is Screen.Notifications ||
            currentScreen is Screen.Explore ||
            currentScreen is Screen.Settings ||
            currentScreen is Screen.Privacy ||
            currentScreen is Screen.NotificationSettings ||
            currentScreen is Screen.ManageStorage ||
            currentScreen is Screen.SubscriptionStatus ||
            currentScreen is Screen.PlanOptions ||
            currentScreen is Screen.StreakAchievements ||
            currentScreen is Screen.Filter ||
            currentScreen is Screen.Philosophy ||
            currentScreen is Screen.Legal -> {
                currentScreen = Screen.Home
            }
            // FindFriends goes back to Friends
            currentScreen is Screen.FindFriends -> {
                currentScreen = Screen.Friends
            }
            // About goes back to Settings
            currentScreen is Screen.About -> {
                currentScreen = Screen.Settings
            }
            // UserProfile goes back to Friends
            currentScreen is Screen.UserProfile -> {
                currentScreen = Screen.Friends
            }
            // ChatDetail goes back to Chats list
            currentScreen is Screen.ChatDetail -> {
                currentScreen = Screen.Chats
            }
            // Already on Home - let Android handle (exit app)
            else -> {}
        }
    }

    val context = LocalContext.current

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
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

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

        if (selectedUris.size == 1) {
            selectedPhotoUri = selectedUris.first()
            currentScreen = Screen.Filter
            return@rememberLauncherForActivityResult
        }

        val profile = userProfile
        if (profile == null) {
            Toast.makeText(context, "Profile not ready yet", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

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
                if (uploadSucceeded) {
                    homeViewModel.requestDebouncedFeedRefresh(profile.uid)
                } else {
                    homeViewModel.removeOptimisticFlick(flickId)
                }
            }
        )

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
    LaunchedEffect(uploadViewModel.uploadSuccess) {
        if (uploadViewModel.uploadSuccess) {
            uploadViewModel.resetUploadState()
            currentScreen = Screen.Home
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
            }
        )
    }

    // Snackbar state for error feedback
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Track screen views for analytics
    LaunchedEffect(currentScreen) {
        val screenName = when (currentScreen) {
            is Screen.Home -> "home"
            is Screen.Profile -> "profile"
            is Screen.Chats -> "chats"
            is Screen.Friends -> "friends"
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
                        .padding(top = 32.dp, bottom = 12.dp)
                ) {
                    // Settings wheel on LEFT
                    IconButton(
                        onClick = { if (profileReady) currentScreen = Screen.Settings },
                        enabled = profileReady,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = if (profileReady) Color.LightGray else Color.DarkGray
                        )
                    }

                    // Notifications bell on right - RED when unread, clickable
                    IconButton(
                        onClick = { if (profileReady) currentScreen = Screen.Notifications },
                        enabled = profileReady,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 4.dp)
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
                            .padding(top = 4.dp)
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
                        is Screen.Profile -> "profile"
                        else -> "home"
                    },
                    onNavigate = { route ->
                        if (profileReady) {
                            when (route) {
                                "home" -> currentScreen = Screen.Home
                                "chats" -> currentScreen = Screen.Chats
                                "upload" -> showUploadSourceDialog = true
                                "friends" -> currentScreen = Screen.Friends
                                "profile" -> currentScreen = Screen.Profile
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
                    onScreenChange = { currentScreen = it },
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

