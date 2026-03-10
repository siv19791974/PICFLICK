package com.picflick.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.picflick.app.data.Flick
import com.picflick.app.data.UserProfile
import com.picflick.app.data.ChatSession
import com.picflick.app.data.PhotoFilter
import com.picflick.app.data.NotificationPreferences
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.repository.ChatRepository
import com.picflick.app.ui.components.BottomNavBar
import com.picflick.app.utils.LocaleHelper
import com.picflick.app.ui.components.LogoImage
import com.picflick.app.ui.components.UploadSourceDialog
import com.picflick.app.ui.screens.AboutScreen
import com.picflick.app.ui.screens.ChatDetailScreen
import com.picflick.app.ui.screens.ChatsScreen
import com.picflick.app.ui.screens.ContactScreen
import com.picflick.app.ui.screens.ExploreScreen
import com.picflick.app.ui.screens.FilterScreen
import com.picflick.app.ui.screens.FindFriendsScreen
import com.picflick.app.ui.screens.FriendsScreen
import com.picflick.app.ui.screens.FullScreenPhotoViewer
import com.picflick.app.ui.screens.HomeScreen
import com.picflick.app.ui.screens.LoginScreen
import com.picflick.app.ui.screens.ManageStorageScreen
import com.picflick.app.ui.screens.MyPhotosScreen
import com.picflick.app.ui.screens.NotificationsScreen
import com.picflick.app.ui.screens.NotificationSettingsScreen
import com.picflick.app.ui.screens.PlanOptionsScreen
import com.picflick.app.ui.screens.ProfileScreen
import com.picflick.app.ui.screens.SettingsScreen
import com.picflick.app.ui.screens.PrivacyScreen
import com.picflick.app.ui.screens.PrivacyPolicyScreen
import com.picflick.app.ui.screens.PhilosophyScreen
import com.picflick.app.ui.screens.LegalScreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.sp
import com.picflick.app.ui.screens.SplashScreen
import com.picflick.app.ui.screens.SubscriptionStatusScreen
import com.picflick.app.ui.screens.UserProfileScreen
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.ui.theme.PicFlickTheme
import com.picflick.app.viewmodel.AuthViewModel
import com.picflick.app.viewmodel.BillingViewModel
import com.picflick.app.viewmodel.SubscriptionProduct
import com.picflick.app.viewmodel.FriendsViewModel
import com.picflick.app.viewmodel.HomeViewModel
import com.picflick.app.viewmodel.ChatViewModel
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
 * Sealed class for type-safe navigation
 */
sealed class Screen {
    data object Home : Screen()
    data object Profile : Screen()
    data class UserProfile(val userId: String) : Screen() // View another user's profile
    data object MyPhotos : Screen()
    data object Friends : Screen()
    data object Chats : Screen()
    data object ChatDetail : Screen()
    data object FindFriends : Screen()
    data object About : Screen()
    data object Contact : Screen()
    data object Notifications : Screen()
    data object Filter : Screen()
    data object Settings : Screen()
    data object Explore : Screen()
    data object Privacy : Screen()
    data object NotificationSettings : Screen()
    data object ManageStorage : Screen()           // NEW: Storage management
    data object SubscriptionStatus : Screen()        // NEW: Subscription details
    data object PlanOptions : Screen()              // NEW: Plan comparison and purchase
    data object PrivacyPolicy : Screen()            // NEW: Privacy Policy screen
    data object Philosophy : Screen()                 // NEW: Our Philosophy screen
    data object Legal : Screen()                      // NEW: Legal/Terms screen
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
    
    // Album drawer state - shared between MainActivity and HomeScreen
    var showAlbumDrawer by remember { mutableStateOf(false) }
    
    // Handle back button - navigate within app instead of exiting
    BackHandler(enabled = currentScreen != Screen.Home || showAlbumDrawer) {
        when {
            // Close album drawer first if open
            showAlbumDrawer -> {
                showAlbumDrawer = false
            }
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
            currentScreen is Screen.Filter ||
            currentScreen is Screen.Philosophy ||
            currentScreen is Screen.Legal -> {
                currentScreen = Screen.Home
            }
            // FindFriends goes back to Friends (where the button was clicked)
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
    
    val currentUser = authViewModel.currentUser
    val userProfile = authViewModel.userProfile
    val context = LocalContext.current // Get context for Toast
    val scope = rememberCoroutineScope()
    val repository = com.picflick.app.repository.FlickRepository.getInstance()
    
    // Capture billingViewModel to fix scope issues in when blocks
    val billingViewModelInstance = billingViewModel
    
    // Initialize billing client
    LaunchedEffect(Unit) {
        billingViewModelInstance.initialize(context)
    }
    
    // Restore cached profile when app resumes (prevents data loss)
    LaunchedEffect(Unit) {
        authViewModel.restoreCachedProfile()
    }
    
    // State for profile photo upload
    var profilePhotoToUpload by remember { mutableStateOf<Uri?>(null) }
    
    // Handle profile photo upload
    LaunchedEffect(profilePhotoToUpload) {
        profilePhotoToUpload?.let { uri ->
            userProfile?.let { profile ->
                try {
                    val uid = profile.uid
                    
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
    
    // State for selected chat (for navigation to ChatDetail)
    var selectedChatSession by remember { mutableStateOf<com.picflick.app.data.ChatSession?>(null) }
    var selectedOtherUserId by remember { mutableStateOf<String>("") }

    // Load notifications when user is authenticated
    LaunchedEffect(userProfile?.uid) {
        userProfile?.uid?.let { uid ->
            notificationViewModel.loadNotifications(uid)
        }
    }
    
    // Load daily upload count when user is authenticated
    LaunchedEffect(userProfile?.uid) {
        userProfile?.let { profile ->
            uploadViewModel.loadDailyUploadCount(profile)
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
                    
                // Also save/update phone number on app start (for existing users without phone number)
                try {
                    // Check permission before accessing phone number
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
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPhotoUri = it
            currentScreen = Screen.Filter
        }
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
            // Permission granted, launch camera
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

    // Handle upload success/error
    LaunchedEffect(uploadViewModel.uploadSuccess) {
        if (uploadViewModel.uploadSuccess) {
            Toast.makeText(context, "Photo uploaded successfully!", Toast.LENGTH_SHORT).show()
            uploadViewModel.resetUploadState()
            currentScreen = Screen.Home
        }
    }

    LaunchedEffect(uploadViewModel.uploadError) {
        uploadViewModel.uploadError?.let { error ->
            Toast.makeText(context, "Upload failed: $error", Toast.LENGTH_LONG).show()
            uploadViewModel.resetUploadState()
        }
    }

    // Upload Source Dialog
    if (showUploadSourceDialog && userProfile != null) {
        UploadSourceDialog(
            onDismiss = { showUploadSourceDialog = false },
            onCameraClick = {
                showUploadSourceDialog = false
                // Check and request camera permission
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            },
            onGalleryClick = {
                showUploadSourceDialog = false
                galleryLauncher.launch("image/*")
            }
        )
    }

    // Outer Scaffold with bottom navigation for all screens
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            // SHARED logo banner - never moves when switching screens!
            if (currentUser != null && userProfile != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)  // BLACK banner for test
                        .padding(top = 32.dp, bottom = 12.dp)
                ) {
                    // Settings wheel on LEFT
                    IconButton(
                        onClick = { currentScreen = Screen.Settings },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color.LightGray
                        )
                    }
                    
                    // Notifications bell on right - RED when unread, clickable
                    IconButton(
                        onClick = { currentScreen = Screen.Notifications },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 4.dp)
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = if (unreadCount > 0) Color.Red else Color.LightGray
                            )
                            // Red badge for unread notifications
                            if (unreadCount > 0) {
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
                    
                    // Logo centered - slightly lower with offset
                    LogoImage(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 4.dp)  // Bring logo slightly lower
                    )
                }
            }
        },
        bottomBar = {
            // Only show bottom nav when authenticated
            if (currentUser != null && userProfile != null) {
                val isOnHomeScreen = currentScreen is Screen.Home
                BottomNavBar(
                    currentRoute = when (currentScreen) {
                        is Screen.Home -> "home"
                        is Screen.Chats -> "chats"
                        is Screen.Friends -> "friends"
                        is Screen.Profile -> "profile"
                        else -> "home"
                    },
                    onNavigate = { route ->
                        when (route) {
                            "home" -> {
                                if (currentScreen is Screen.Home) {
                                    // Already on home, toggle album drawer
                                    showAlbumDrawer = !showAlbumDrawer
                                } else {
                                    currentScreen = Screen.Home
                                    showAlbumDrawer = false
                                }
                            }
                            "chats" -> {
                                currentScreen = Screen.Chats
                                showAlbumDrawer = false
                            }
                            "upload" -> showUploadSourceDialog = true
                            "friends" -> {
                                currentScreen = Screen.Friends
                                showAlbumDrawer = false
                            }
                            "profile" -> {
                                currentScreen = Screen.Profile
                                showAlbumDrawer = false
                            }
                        }
                    },
                    isOnHomeScreen = isOnHomeScreen
                )
            }
        }
    ) { padding ->
        // Content area - each screen handles its own background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (currentUser == null) {
                // Not authenticated - show login
                LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = {
                        // Will trigger recomposition with currentUser != null
                    }
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
                    selectedChatSession = selectedChatSession,
                    selectedOtherUserId = selectedOtherUserId,
                    onSetSelectedChat = { session, userId ->
                        selectedChatSession = session
                        selectedOtherUserId = userId
                    },
                    onSignOut = { authViewModel.signOut() },
                    selectedPhotoUri = selectedPhotoUri,
                    onPhotoSelected = { uri ->
                        // Trigger profile photo upload via LaunchedEffect
                        profilePhotoToUpload = uri
                    },
                    showAlbumDrawer = showAlbumDrawer,
                    onAlbumDrawerChange = { showAlbumDrawer = it }
                )
            }
        }
    }
}

/**
 * Content shown when user is authenticated
 */
@Composable
private fun AuthenticatedContent(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    homeViewModel: HomeViewModel,
    profileViewModel: ProfileViewModel,
    friendsViewModel: FriendsViewModel,
    notificationViewModel: NotificationViewModel,
    chatViewModel: ChatViewModel,
    uploadViewModel: UploadViewModel,
    authViewModel: AuthViewModel,
    selectedChatSession: com.picflick.app.data.ChatSession?,
    selectedOtherUserId: String,
    onSetSelectedChat: (com.picflick.app.data.ChatSession, String) -> Unit,
    onSignOut: () -> Unit,
    selectedPhotoUri: Uri?,
    onPhotoSelected: (Uri) -> Unit,
    showAlbumDrawer: Boolean = false,
    onAlbumDrawerChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = com.picflick.app.repository.FlickRepository.getInstance()
    
    // Capture billingViewModel locally for use in when blocks
    val bvm = billingViewModel
    
    // Direct screen switching - NO animation (fixes banner position shift)
    when (currentScreen) {
        is Screen.Home -> HomeScreen(
                userProfile = userProfile,
                viewModel = homeViewModel,
                onNavigate = { route ->
                    // Convert route string to Screen object
                    val targetScreen = when (route) {
                        "profile" -> Screen.Profile
                        "my_photos" -> Screen.MyPhotos
                        "friends" -> Screen.Friends
                        "chats" -> Screen.Chats
                        "find_friends" -> Screen.FindFriends
                        "about" -> Screen.About
                        "contact" -> Screen.Contact
                        "notifications" -> Screen.Notifications
                        else -> Screen.Home
                    }
                    onScreenChange(targetScreen)
                },
                onSignOut = onSignOut,
                onUserProfileClick = { userId ->
                    if (userId == userProfile.uid) {
                        onScreenChange(Screen.Profile)
                    } else {
                        onScreenChange(Screen.UserProfile(userId))
                    }
                },
                showAlbumDrawer = showAlbumDrawer,
                onAlbumDrawerChange = onAlbumDrawerChange
            )

            is Screen.Profile -> {
                // Load photos when profile screen is shown
                LaunchedEffect(userProfile.uid) {
                    profileViewModel.loadUserPhotos(userProfile.uid)
                }
                
                // State for fullscreen photo viewer
                var selectedPhoto by remember { mutableStateOf<Flick?>(null) }
                var selectedPhotoIndex by remember { mutableIntStateOf(0) }
                
                ProfileScreen(
                    userProfile = userProfile,
                    photos = profileViewModel.photos,
                    photoCount = profileViewModel.photoCount,
                    totalReactions = profileViewModel.totalReactions,
                    currentStreak = profileViewModel.currentStreak,
                    onBack = { onScreenChange(Screen.Home) },
                    onPhotoSelected = onPhotoSelected,
                    onBioUpdated = { newBio ->
                        authViewModel.updateBio(newBio)
                    },
                    onPhotoClick = { flick, index ->
                        selectedPhoto = flick
                        selectedPhotoIndex = index
                    },
                    onProfilePhotoClick = {
                        // Create a Flick from profile photo to view in FullScreenPhotoViewer
                        if (userProfile.photoUrl.isNotEmpty()) {
                            selectedPhoto = Flick(
                                id = "profile_${userProfile.uid}",
                                userId = userProfile.uid,
                                userName = userProfile.displayName,
                                userPhotoUrl = userProfile.photoUrl,
                                imageUrl = userProfile.photoUrl,
                                description = "", // No description for profile photos
                                timestamp = System.currentTimeMillis(),
                                reactions = emptyMap()
                            )
                            selectedPhotoIndex = 0
                        }
                    },
                    onRefresh = {
                        // Reload user photos and stats, AND refresh profile data
                        profileViewModel.loadUserPhotos(userProfile.uid)
                        authViewModel.reloadUserProfile() // Force refresh profile pic and bio
                    },
                    onPlanOptions = { onScreenChange(Screen.PlanOptions) },
                    isLoading = profileViewModel.isLoading
                )
                
                // FullScreenPhotoViewer when photo selected
                selectedPhoto?.let { flick ->
                    // Handle back button to close photo viewer
                    BackHandler {
                        selectedPhoto = null
                    }
                    
                    // Check if this is a profile photo
                    val isProfilePhoto = flick.id.startsWith("profile_")
                    
                    FullScreenPhotoViewer(
                        flick = flick,
                        currentUser = userProfile,
                        onDismiss = { selectedPhoto = null },
                        onReaction = { reactionType ->
                            profileViewModel.toggleReaction(flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, reactionType)
                        },
                        onShareClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Check out my photo on PicFlick: ${flick.imageUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                        },
                        onDeleteClick = {
                            profileViewModel.deletePhoto(flick.id) { success ->
                                if (success) {
                                    selectedPhoto = null
                                }
                            }
                        },
                        canDelete = true,
                        onCaptionUpdated = { newCaption ->
                            profileViewModel.updateCaption(flick.id, newCaption)
                        },
                        allPhotos = if (isProfilePhoto) listOf(flick) else profileViewModel.photos,
                        currentIndex = if (isProfilePhoto) 0 else selectedPhotoIndex,
                        onNavigateToPhoto = { index ->
                            if (!isProfilePhoto) {
                                selectedPhotoIndex = index
                                selectedPhoto = profileViewModel.photos.getOrNull(index)
                            }
                        },
                        onNavigateToFindFriends = { 
                            selectedPhoto = null
                            onScreenChange(Screen.FindFriends) 
                        },
                        onUserProfileClick = { userId ->
                            selectedPhoto = null
                            if (userId == userProfile.uid) {
                                onScreenChange(Screen.Profile)
                            } else {
                                onScreenChange(Screen.UserProfile(userId))
                            }
                        }
                    )
                }
            }

            is Screen.MyPhotos -> MyPhotosScreen(
                viewModel = profileViewModel,
                userId = userProfile.uid,
                currentUser = userProfile,
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.Friends -> {
                FriendsScreen(
                    userProfile = userProfile,
                    viewModel = friendsViewModel,
                    onBack = { onScreenChange(Screen.Home) },
                    onFindFriendsClick = { onScreenChange(Screen.FindFriends) },
                    onProfilePhotoClick = { friend ->
                        // Navigate to user's profile page
                        onScreenChange(Screen.UserProfile(friend.uid))
                    }
                )
            }

            is Screen.Chats -> ChatsScreen(
                userProfile = userProfile,
                viewModel = chatViewModel,
                onBack = { onScreenChange(Screen.Home) },
                onChatClick = { session, otherUserId ->
                    onSetSelectedChat(session, otherUserId)
                    onScreenChange(Screen.ChatDetail)
                },
                onUserProfileClick = { userId ->
                    onScreenChange(Screen.UserProfile(userId))
                }
            )

            is Screen.ChatDetail -> {
                if (selectedChatSession != null) {
                    ChatDetailScreen(
                        chatSession = selectedChatSession,
                        otherUserId = selectedOtherUserId,
                        currentUser = userProfile,
                        viewModel = chatViewModel,
                        onBack = { onScreenChange(Screen.Chats) },
                        onUserProfileClick = { userId ->
                            onScreenChange(Screen.UserProfile(userId))
                        }
                    )
                } else {
                    // Fallback if no chat selected
                    onScreenChange(Screen.Chats)
                }
            }

            is Screen.FindFriends -> FindFriendsScreen(
                viewModel = friendsViewModel,
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Home) },
                onNavigateToProfile = { userId ->
                    onScreenChange(Screen.UserProfile(userId))
                },
                onProfileRefresh = { authViewModel.reloadUserProfile() } // Refresh profile after follow/unfollow
            )

            is Screen.About -> AboutScreen(
                onBack = { onScreenChange(Screen.Settings) }
            )

            is Screen.Contact -> ContactScreen(
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.Notifications -> {
                // State for fullscreen photo viewer from notifications
                var selectedNotificationPhoto by remember { mutableStateOf<Flick?>(null) }
                
                NotificationsScreen(
                    userProfile = userProfile,
                    onBack = { onScreenChange(Screen.Home) },
                    onUserProfileClick = { userId ->
                        if (userId == userProfile.uid) {
                            onScreenChange(Screen.Profile)
                        } else {
                            onScreenChange(Screen.UserProfile(userId))
                        }
                    },
                    onPhotoClick = { flickId, imageUrl, userId ->
                        // Create a Flick from notification data
                        selectedNotificationPhoto = Flick(
                            id = flickId,
                            userId = userId,
                            userName = "",
                            userPhotoUrl = "",
                            imageUrl = imageUrl ?: "",
                            description = "",
                            timestamp = System.currentTimeMillis(),
                            reactions = emptyMap(),
                            commentCount = 0,
                            privacy = "friends",
                            taggedFriends = emptyList(),
                            reportCount = 0
                        )
                    },
                    onChatClick = { _, _, _, _ ->
                        // Navigate to chats screen
                        onScreenChange(Screen.Chats)
                    }
                )
                
                // FullScreenPhotoViewer for notification photos
                selectedNotificationPhoto?.let { flick ->
                    // Handle back button to close photo viewer
                    BackHandler {
                        selectedNotificationPhoto = null
                    }
                    
                    FullScreenPhotoViewer(
                        flick = flick,
                        currentUser = userProfile,
                        onDismiss = { selectedNotificationPhoto = null },
                        onReaction = { reactionType ->
                            reactionType?.let { 
                                homeViewModel.toggleReaction(flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, it)
                            }
                        },
                        onDeleteClick = {
                            selectedNotificationPhoto = null
                        },
                        onUserProfileClick = { clickedUserId ->
                            if (clickedUserId == userProfile.uid) {
                                onScreenChange(Screen.Profile)
                            } else {
                                onScreenChange(Screen.UserProfile(clickedUserId))
                            }
                        }
                    )
                }
            }

            is Screen.Settings -> SettingsScreen(
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Home) },
                onSignOut = onSignOut,
                onDeleteAccount = {
                    authViewModel.deleteAccount { success, error ->
                        if (success) {
                            onSignOut()
                        } else {
                            Toast.makeText(context, error ?: "Failed to delete account", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onManageStorage = { onScreenChange(Screen.ManageStorage) },
                onPrivacySettings = { onScreenChange(Screen.Privacy) },
                onNotificationsSettings = { onScreenChange(Screen.NotificationSettings) },
                onPlanOptions = { onScreenChange(Screen.PlanOptions) },
                onHelpSupport = { onScreenChange(Screen.Contact) },
                onAbout = { onScreenChange(Screen.About) },
                onPhilosophy = { onScreenChange(Screen.Philosophy) },
                onLegal = { onScreenChange(Screen.Legal) },
                onProfileClick = { onScreenChange(Screen.Profile) }
            )

            is Screen.ManageStorage -> {
                val activity = context as? Activity
                ManageStorageScreen(
                    userProfile = userProfile,
                    billingViewModel = bvm,
                    onBack = { onScreenChange(Screen.Settings) },
                    onUpgrade = { tier: SubscriptionTier ->
                        activity?.let { act: Activity ->
                            val product: SubscriptionProduct? = bvm.getProductForTier(tier)
                            if (product != null) {
                                bvm.purchaseSubscription(act, product)
                            } else {
                                // Show error if product not found
                                android.widget.Toast.makeText(
                                    context,
                                    "Subscription products not loaded yet. Please try again in a moment.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                // Retry loading products
                                bvm.queryProducts()
                            }
                        }
                    }
                )
            }

            is Screen.SubscriptionStatus -> {
                val activity = context as? Activity
                SubscriptionStatusScreen(
                    userProfile = userProfile,
                    billingViewModel = bvm,
                    onBack = { onScreenChange(Screen.Settings) },
                    onUpgrade = { tier: SubscriptionTier ->
                        activity?.let { act: Activity ->
                            val product: SubscriptionProduct? = bvm.getProductForTier(tier)
                            if (product != null) {
                                bvm.purchaseSubscription(act, product)
                            } else {
                                // Show error if product not found
                                android.widget.Toast.makeText(
                                    context,
                                    "Subscription products not loaded yet. Please try again in a moment.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                // Retry loading products
                                bvm.queryProducts()
                            }
                        }
                    },
                    onDowngrade = { tier: SubscriptionTier ->
                        activity?.let { act: Activity ->
                            val product: SubscriptionProduct? = bvm.getProductForTier(tier)
                            if (product != null) {
                                bvm.purchaseSubscription(act, product)
                            } else {
                                // Show error if product not found
                                android.widget.Toast.makeText(
                                    context,
                                    "Subscription products not loaded yet. Please try again in a moment.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                // Retry loading products
                                bvm.queryProducts()
                            }
                        }
                    },
                    onManagePayment = {
                        Toast.makeText(context, "Payment management coming soon!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            is Screen.PlanOptions -> {
                val activity = context as? Activity
                PlanOptionsScreen(
                    userProfile = userProfile,
                    billingViewModel = bvm,
                    onBack = { onScreenChange(Screen.Settings) },
                    onPurchase = { tier: SubscriptionTier ->
                        activity?.let { act: Activity ->
                            val product: SubscriptionProduct? = bvm.getProductForTier(tier)
                            if (product != null) {
                                bvm.purchaseSubscription(act, product)
                            } else {
                                // Show error if product not found
                                android.widget.Toast.makeText(
                                    context,
                                    "Subscription products not loaded yet. Please try again in a moment.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                // Retry loading products
                                bvm.queryProducts()
                            }
                        }
                    }
                )
            }

            is Screen.Filter -> {
                selectedPhotoUri?.let { uri ->
                    // Load following users if not already loaded
                    friendsViewModel.loadFollowingUsers(userProfile.following)
                    
                    FilterScreen(
                        photoUri = uri,
                        currentUser = userProfile,
                        friends = friendsViewModel.followingUsers,
                        dailyUploadCount = uploadViewModel.dailyUploadCount,
                        onBack = { onScreenChange(Screen.Home) },
                        onUpload = { filteredUri, filter, taggedFriends, description ->
                            uploadViewModel.uploadPhoto(
                                context = context,
                                photoUri = filteredUri,
                                userProfile = userProfile,
                                filter = filter,
                                taggedFriends = taggedFriends,
                                description = description
                            )
                        },
                        onNavigateToFindFriends = { onScreenChange(Screen.FindFriends) },
                        onNavigateToCamera = { 
                            // Go to home (user can then click Add to retake)
                            onScreenChange(Screen.Home)
                        }
                    )
                } ?: run {
                    // If no photo selected, go back to home
                    onScreenChange(Screen.Home)
                }
            }

            is Screen.Explore -> {
                // State for fullscreen photo viewer
                var selectedPhoto by remember { mutableStateOf<Flick?>(null) }
                var selectedPhotoIndex by remember { mutableIntStateOf(0) }
                
                ExploreScreen(
                    userProfile = userProfile,
                    viewModel = homeViewModel,
                    onPhotoClick = { flick: Flick ->
                        selectedPhoto = flick
                        selectedPhotoIndex = homeViewModel.exploreFlicks.indexOf(flick)
                    },
                    onUserClick = { userId: String ->
                        onScreenChange(Screen.UserProfile(userId))
                    }
                )
                
                // FullScreenPhotoViewer when photo selected
                selectedPhoto?.let { flick ->
                    // Handle back button to close photo viewer
                    BackHandler {
                        selectedPhoto = null
                    }
                    
                    FullScreenPhotoViewer(
                        flick = flick,
                        currentUser = userProfile,
                        onDismiss = { selectedPhoto = null },
                        onReaction = { reactionType ->
                            homeViewModel.toggleReaction(flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, reactionType)
                        },
                        onShareClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Check out this photo on PicFlick: ${flick.imageUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                        },
                        onDeleteClick = { /* Can't delete from Explore */ },
                        canDelete = false,
                        onCaptionUpdated = { /* Can't edit from Explore */ },
                        allPhotos = homeViewModel.exploreFlicks,
                        currentIndex = selectedPhotoIndex,
                        onNavigateToPhoto = { index ->
                            selectedPhotoIndex = index
                            selectedPhoto = homeViewModel.exploreFlicks.getOrNull(index)
                        },
                        onUserProfileClick = { userId ->
                            selectedPhoto = null
                            onScreenChange(Screen.UserProfile(userId))
                        },
                        onNavigateToFindFriends = {
                            selectedPhoto = null
                            onScreenChange(Screen.FindFriends)
                        }
                    )
                }
            }

            is Screen.UserProfile -> {
                // State for viewing user's photos in profile
                var selectedUserPhoto by remember { mutableStateOf<Flick?>(null) }
                var selectedUserPhotoIndex by remember { mutableIntStateOf(0) }
                
                // Load target user's profile
                var targetUser by remember { mutableStateOf<UserProfile?>(null) }
                var targetUserPhotos by remember { mutableStateOf<List<Flick>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                
                val targetUserId = (currentScreen as Screen.UserProfile).userId
                
                // Check if this is the current user
                val isCurrentUser = targetUserId == userProfile.uid
                
                // Check if they are friends (mutual following)
                val isFriend = userProfile.following.contains(targetUserId) && 
                              targetUser?.followers?.contains(userProfile.uid) == true
                
                // Check friend request states
                val hasSentRequest = userProfile.sentFollowRequests.contains(targetUserId)
                val hasReceivedRequest = userProfile.pendingFollowRequests.contains(targetUserId)
                
                LaunchedEffect(targetUserId) {
                    isLoading = true
                    // Load target user's profile
                    repository.getUserProfile(targetUserId) { result ->
                        when (result) {
                            is com.picflick.app.data.Result.Success<UserProfile> -> {
                                targetUser = result.data
                                // If friends, load their photos
                                if (userProfile.following.contains(targetUserId)) {
                                    repository.getUserFlicks(targetUserId) { photosResult ->
                                        when (photosResult) {
                                            is com.picflick.app.data.Result.Success<List<Flick>> -> {
                                                targetUserPhotos = photosResult.data
                                                isLoading = false
                                            }
                                            else -> {
                                                targetUserPhotos = emptyList()
                                                isLoading = false
                                            }
                                        }
                                    }
                                } else {
                                    isLoading = false
                                }
                            }
                            else -> {
                                isLoading = false
                            }
                        }
                    }
                }
                
                targetUser?.let { target ->
                    UserProfileScreen(
                        userProfile = target,
                        currentUser = userProfile,
                        photos = targetUserPhotos,
                        isFriend = isFriend,
                        hasSentRequest = hasSentRequest,
                        hasReceivedRequest = hasReceivedRequest,
                        isLoading = isLoading,
                        onBack = { onScreenChange(Screen.Home) },
                        onRefresh = {
                            // Reload target user profile and photos
                            target.uid.let { uid ->
                                isLoading = true
                                repository.getUserProfile(uid) { result ->
                                    if (result is com.picflick.app.data.Result.Success<UserProfile>) {
                                        targetUser = result.data
                                        // Reload photos
                                        repository.getUserFlicks(uid) { photosResult ->
                                            if (photosResult is com.picflick.app.data.Result.Success<List<Flick>>) {
                                                targetUserPhotos = photosResult.data
                                            }
                                            isLoading = false
                                        }
                                    } else {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        onPhotoClick = { flick, index ->
                            selectedUserPhoto = flick
                            selectedUserPhotoIndex = index
                        },
                        onProfilePhotoClick = {
                            // View profile photo in fullscreen
                            if (target.photoUrl.isNotEmpty()) {
                                selectedUserPhoto = Flick(
                                    id = "profile_${target.uid}",
                                    userId = target.uid,
                                    userName = target.displayName,
                                    userPhotoUrl = target.photoUrl,
                                    imageUrl = target.photoUrl,
                                    description = "", // No description for profile photos
                                    timestamp = System.currentTimeMillis(),
                                    reactions = emptyMap()
                                )
                                selectedUserPhotoIndex = 0
                            }
                        },
                        onAddFriend = {
                            // Send friend request
                            friendsViewModel.sendFollowRequest(
                                userProfile.uid,
                                target,
                                userProfile
                            )
                        },
                        onAcceptRequest = {
                            // Accept friend request from this user
                            friendsViewModel.acceptFollowRequest(userProfile.uid, target)
                            // Refresh profile so new friend appears in Friends list
                            authViewModel.reloadUserProfile()
                        },
                        onMessageClick = {
                            // Navigate to chat with this user
                            if (isFriend && target != null) {
                                scope.launch {
                                    val chatRepository = ChatRepository()
                                    val result = chatRepository.getOrCreateChatSession(
                                        userId1 = userProfile.uid,
                                        userId2 = target.uid,
                                        user1Name = userProfile.displayName,
                                        user2Name = target.displayName
                                    )
                                    when (result) {
                                        is com.picflick.app.data.Result.Success<String> -> {
                                            // Create a ChatSession object for navigation
                                            val session = ChatSession(
                                                id = result.data,
                                                participants = listOf(userProfile.uid, target.uid),
                                                participantNames = mapOf(
                                                    userProfile.uid to userProfile.displayName,
                                                    target.uid to target.displayName
                                                ),
                                                lastMessage = "",
                                                lastTimestamp = System.currentTimeMillis(),
                                                unreadCount = 0
                                            )
                                            onSetSelectedChat(session, target.uid)
                                            onScreenChange(Screen.ChatDetail)
                                        }
                                        is com.picflick.app.data.Result.Error -> {
                                            Toast.makeText(
                                                context, 
                                                result.message ?: "Cannot start chat", 
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
                        onBlockUser = {
                            // Block the user
                            repository.blockUser(userProfile.uid, target.uid) { result ->
                                when (result) {
                                    is com.picflick.app.data.Result.Success -> {
                                        Toast.makeText(
                                            context,
                                            "${target.displayName} has been blocked",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        // Navigate back
                                        onScreenChange(Screen.Home)
                                    }
                                    is com.picflick.app.data.Result.Error -> {
                                        Toast.makeText(
                                            context,
                                            result.message ?: "Failed to block user",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    else -> {}
                                }
                            }
                        },
                        onUnfriend = {
                            // Unfriend/delete the user
                            friendsViewModel.unfollowUser(userProfile.uid, target.uid)
                            Toast.makeText(
                                context,
                                "${target.displayName} has been removed from friends",
                                Toast.LENGTH_LONG
                            ).show()
                            // Navigate back
                            onScreenChange(Screen.Home)
                        }
                    )
                    
                    // FullScreenPhotoViewer for photos
                    selectedUserPhoto?.let { flick ->
                        // Handle back button to close photo viewer
                        BackHandler {
                            selectedUserPhoto = null
                        }
                        
                        val isUserProfilePhoto = flick.id.startsWith("profile_")
                        
                        FullScreenPhotoViewer(
                            flick = flick,
                            currentUser = userProfile,
                            onDismiss = { selectedUserPhoto = null },
                            onReaction = { reactionType ->
                                if (isUserProfilePhoto) {
                                    // Can't react to profile photos
                                } else {
                                    profileViewModel.toggleReaction(
                                        flick, 
                                        userProfile.uid, 
                                        userProfile.displayName, 
                                        userProfile.photoUrl, 
                                        reactionType
                                    )
                                }
                            },
                            onShareClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Check out this photo on PicFlick: ${flick.imageUrl}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                            },
                            canDelete = isCurrentUser && !isUserProfilePhoto,
                            onDeleteClick = {
                                if (!isUserProfilePhoto) {
                                    profileViewModel.deletePhoto(flick.id) { success ->
                                        if (success) {
                                            selectedUserPhoto = null
                                        }
                                    }
                                }
                            },
                            onCaptionUpdated = { newCaption ->
                                if (!isUserProfilePhoto) {
                                    profileViewModel.updateCaption(flick.id, newCaption)
                                }
                            },
                            allPhotos = if (isUserProfilePhoto) listOf(flick) else targetUserPhotos,
                            currentIndex = if (isUserProfilePhoto) 0 else selectedUserPhotoIndex,
                            onNavigateToPhoto = { index ->
                                if (!isUserProfilePhoto) {
                                    selectedUserPhotoIndex = index
                                    selectedUserPhoto = targetUserPhotos.getOrNull(index)
                                }
                            },
                            onNavigateToFindFriends = {
                                selectedUserPhoto = null
                                onScreenChange(Screen.FindFriends)
                            },
                            onUserProfileClick = { userId ->
                                selectedUserPhoto = null
                                if (userId == userProfile.uid) {
                                    onScreenChange(Screen.Profile)
                                } else if (userId != targetUserId) {
                                    onScreenChange(Screen.UserProfile(userId))
                                }
                            }
                        )
                    }
                } ?: run {
                    // Loading or error state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            Text(
                                text = "User not found",
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            is Screen.Privacy -> PrivacyScreen(
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Settings) },
                onPrivacyPolicy = { onScreenChange(Screen.PrivacyPolicy) }
            )

            is Screen.NotificationSettings -> NotificationSettingsScreen(
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Settings) },
                onSavePreferences = { newPreferences ->
                    authViewModel.updateNotificationPreferences(newPreferences)
                    Toast.makeText(context, "Notification settings saved!", Toast.LENGTH_SHORT).show()
                    onScreenChange(Screen.Settings)
                }
            )

            is Screen.PrivacyPolicy -> PrivacyPolicyScreen(
                onBack = { onScreenChange(Screen.Privacy) },
                onContactUs = { onScreenChange(Screen.Settings) }
            )

            is Screen.Philosophy -> PhilosophyScreen(
                onBack = { onScreenChange(Screen.Settings) }
            )

            is Screen.Legal -> LegalScreen(
                onBack = { onScreenChange(Screen.Settings) }
            )
    }
}

