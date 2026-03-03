package com.example.picflick

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picflick.data.UserProfile
import com.example.picflick.ui.components.BottomNavBar
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.screens.AboutScreen
import com.example.picflick.ui.screens.ChatsScreen
import com.example.picflick.ui.screens.ContactScreen
import com.example.picflick.ui.screens.FindFriendsScreen
import com.example.picflick.ui.screens.FriendsScreen
import com.example.picflick.ui.screens.HomeScreen
import com.example.picflick.ui.screens.LoginScreen
import com.example.picflick.ui.screens.MyPhotosScreen
import com.example.picflick.ui.screens.NotificationsScreen
import com.example.picflick.ui.screens.ProfileScreen
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground
import com.example.picflick.ui.theme.PicFlickTheme
import com.example.picflick.viewmodel.AuthViewModel
import com.example.picflick.viewmodel.FriendsViewModel
import com.example.picflick.viewmodel.HomeViewModel
import com.example.picflick.viewmodel.ProfileViewModel

/**
 * Main Activity - Entry point for the PicFlick app
 * Uses clean architecture with ViewModels and separated concerns
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            PicFlickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PicFlickBackground
                ) {
                    MainScreen()
                }
            }
        }
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
    data object FindFriends : Screen()
    data object About : Screen()
    data object Contact : Screen()
    data object Notifications : Screen()
}

/**
 * Main screen with navigation and authentication state
 */
@Composable
fun MainScreen(
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    friendsViewModel: FriendsViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val currentUser = authViewModel.currentUser
    val userProfile = authViewModel.userProfile
    val context = LocalContext.current // Get context for Toast
    var showUploadDialog by remember { mutableStateOf(false) }

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
                        onClick = { /* TODO: Settings */ },
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
                    
                    // Notifications bell on right - LOWER to match logo, LIGHT GREY for contrast
                    IconButton(
                        onClick = { /* TODO: notifications */ },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 4.dp)  // Match logo's lower position
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.LightGray  // Light grey for contrast on black
                        )
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
                            "upload" -> showUploadDialog = true
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
                    onSignOut = { authViewModel.signOut() },
                    onProfilePhotoSelected = { uri ->
                        // TODO: Upload to Firebase Storage and update profile
                        Toast.makeText(context, "Photo selected: $uri\nUpload coming soon!", Toast.LENGTH_SHORT).show()
                    },
                    showUploadDialog = showUploadDialog,
                    onDismissUpload = { showUploadDialog = false }
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
    onSignOut: () -> Unit,
    onProfilePhotoSelected: (android.net.Uri) -> Unit = {},
    showUploadDialog: Boolean = false,
    onDismissUpload: () -> Unit = {}
) {
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
                onSignOut = onSignOut,
                onMyPhotosClick = { onScreenChange(Screen.MyPhotos) },
                onPhotoSelected = onProfilePhotoSelected
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
                onBack = { onScreenChange(Screen.Home) }
            )

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
    }
}
