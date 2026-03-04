package com.example.picflick

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picflick.data.Flick
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.BottomNavBar
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.components.UploadSourceDialog
import com.example.picflick.ui.screens.AboutScreen
import com.example.picflick.ui.screens.ChatDetailScreen
import com.example.picflick.ui.screens.ChatsScreen
import com.example.picflick.ui.screens.ContactScreen
import com.example.picflick.ui.screens.ExploreScreen
import com.example.picflick.ui.screens.FilterScreen
import com.example.picflick.ui.screens.FindFriendsScreen
import com.example.picflick.ui.screens.FriendsScreen
import com.example.picflick.ui.screens.HomeScreen
import com.example.picflick.ui.screens.LoginScreen
import com.example.picflick.ui.screens.MyPhotosScreen
import com.example.picflick.ui.screens.NotificationsScreen
import com.example.picflick.ui.screens.ProfileScreen
import com.example.picflick.ui.screens.SettingsScreen
import com.example.picflick.ui.screens.SplashScreen
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.ui.theme.PicFlickTheme
import com.example.picflick.viewmodel.AuthViewModel
import com.example.picflick.viewmodel.FriendsViewModel
import com.example.picflick.viewmodel.HomeViewModel
import com.example.picflick.viewmodel.ChatViewModel
import com.example.picflick.viewmodel.NotificationViewModel
import com.example.picflick.viewmodel.ProfileViewModel
import com.example.picflick.viewmodel.UploadViewModel

/**
 * Main Activity - Entry point for the PicFlick app
 * Uses clean architecture with ViewModels and separated concerns
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PicFlickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PicFlickBackground
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

    if (showSplash) {
        SplashScreen(
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
}

/**
 * Main screen with navigation and authentication state
 */
@Composable
fun MainScreen(
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    friendsViewModel: FriendsViewModel = viewModel(),
    notificationViewModel: NotificationViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    uploadViewModel: UploadViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val currentUser = authViewModel.currentUser
    val userProfile = authViewModel.userProfile
    val context = LocalContext.current // Get context for Toast
    
    // Upload flow states
    var showUploadSourceDialog by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    // State for selected chat (for navigation to ChatDetail)
    var selectedChatSession by remember { mutableStateOf<com.example.picflick.data.ChatSession?>(null) }
    var selectedOtherUserId by remember { mutableStateOf<String>("") }

    // Load notifications when user is authenticated
    LaunchedEffect(userProfile?.uid) {
        userProfile?.uid?.let { uid ->
            notificationViewModel.loadNotifications(uid)
        }
    }
    
    // Load daily upload count when user is authenticated
    LaunchedEffect(userProfile?.uid) {
        userProfile?.uid?.let { uid ->
            uploadViewModel.loadDailyUploadCount(uid)
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
        containerColor = PicFlickBannerBackground,
        contentColor = PicFlickBannerBackground,
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
                            "home" -> currentScreen = Screen.Home
                            "chats" -> currentScreen = Screen.Chats
                            "upload" -> showUploadSourceDialog = true
                            "friends" -> currentScreen = Screen.Friends
                            "profile" -> currentScreen = Screen.Profile
                        }
                    }
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
                    homeViewModel = homeViewModel,
                    profileViewModel = profileViewModel,
                    friendsViewModel = friendsViewModel,
                    notificationViewModel = notificationViewModel,
                    chatViewModel = chatViewModel,
                    uploadViewModel = uploadViewModel,
                    selectedChatSession = selectedChatSession,
                    selectedOtherUserId = selectedOtherUserId,
                    onSetSelectedChat = { session, userId ->
                        selectedChatSession = session
                        selectedOtherUserId = userId
                    },
                    onSignOut = { authViewModel.signOut() },
                    selectedPhotoUri = selectedPhotoUri,
                    onPhotoSelected = { uri ->
                        selectedPhotoUri = uri
                    }
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
    homeViewModel: HomeViewModel,
    profileViewModel: ProfileViewModel,
    friendsViewModel: FriendsViewModel,
    notificationViewModel: NotificationViewModel,
    chatViewModel: ChatViewModel,
    uploadViewModel: UploadViewModel,
    selectedChatSession: com.example.picflick.data.ChatSession?,
    selectedOtherUserId: String,
    onSetSelectedChat: (com.example.picflick.data.ChatSession, String) -> Unit,
    onSignOut: () -> Unit,
    selectedPhotoUri: Uri?,
    onPhotoSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
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
                onSignOut = onSignOut
            )

            is Screen.Profile -> ProfileScreen(
                userProfile = userProfile,
                photoCount = profileViewModel.photoCount,
                onBack = { onScreenChange(Screen.Home) },
                onMyPhotosClick = { onScreenChange(Screen.MyPhotos) },
                onPhotoSelected = onPhotoSelected
            )

            is Screen.MyPhotos -> MyPhotosScreen(
                viewModel = profileViewModel,
                userId = userProfile.uid,
                currentUser = userProfile,
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.Friends -> FriendsScreen(
                userProfile = userProfile,
                viewModel = friendsViewModel,
                onBack = { onScreenChange(Screen.Home) },
                onFindFriendsClick = { onScreenChange(Screen.FindFriends) }
            )

            is Screen.Chats -> ChatsScreen(
                userProfile = userProfile,
                viewModel = chatViewModel,
                onBack = { onScreenChange(Screen.Home) },
                onChatClick = { session, otherUserId ->
                    onSetSelectedChat(session, otherUserId)
                    onScreenChange(Screen.ChatDetail)
                }
            )

            is Screen.ChatDetail -> {
                if (selectedChatSession != null) {
                    ChatDetailScreen(
                        chatSession = selectedChatSession,
                        otherUserId = selectedOtherUserId,
                        currentUser = userProfile,
                        viewModel = chatViewModel,
                        onBack = { onScreenChange(Screen.Chats) }
                    )
                } else {
                    // Fallback if no chat selected
                    onScreenChange(Screen.Chats)
                }
            }

            is Screen.FindFriends -> FindFriendsScreen(
                viewModel = friendsViewModel,
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.About -> AboutScreen(
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.Contact -> ContactScreen(
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.Notifications -> NotificationsScreen(
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.Settings -> SettingsScreen(
                userProfile = userProfile,
                onBack = { onScreenChange(Screen.Home) },
                onSignOut = onSignOut,
                onEditProfile = { onScreenChange(Screen.Profile) },
                onPrivacySettings = { /* TODO */ },
                onNotificationsSettings = { onScreenChange(Screen.Notifications) },
                onHelpSupport = { onScreenChange(Screen.Contact) },
                onAbout = { onScreenChange(Screen.About) }
            )

            is Screen.Filter -> {
                selectedPhotoUri?.let { uri ->
                    // Load following users if not already loaded
                    friendsViewModel.loadFollowingUsers(userProfile.following)
                    
                    FilterScreen(
                        photoUri = uri,
                        currentUser = userProfile,
                        friends = friendsViewModel.followingUsers,
                        dailyUploadCount = uploadViewModel.dailyUploadCount,
                        maxDailyUploads = 5,
                        onBack = { onScreenChange(Screen.Home) },
                        onUpload = { filteredUri, filter, taggedFriends ->
                            uploadViewModel.uploadPhoto(
                                context = context,
                                photoUri = filteredUri,
                                userProfile = userProfile,
                                filter = filter,
                                taggedFriends = taggedFriends
                            )
                        }
                    )
                } ?: run {
                    // If no photo selected, go back to home
                    onScreenChange(Screen.Home)
                }
            }

            is Screen.Explore -> ExploreScreen(
                userProfile = userProfile,
                viewModel = homeViewModel,
                onPhotoClick = { flick: Flick ->
                    // TODO: Navigate to full screen photo viewer
                },
                onUserClick = { userId: String ->
                    // TODO: Navigate to user profile
                }
            )
    }
}
