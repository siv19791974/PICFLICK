package com.example.picflick

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picflick.data.UserProfile
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
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Profile : Screen("profile")
    data object MyPhotos : Screen("my_photos")
    data object Friends : Screen("friends")
    data object Chats : Screen("chats")
    data object FindFriends : Screen("find_friends")
    data object About : Screen("about")
    data object Contact : Screen("contact")
    data object Notifications : Screen("notifications")
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

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = PicFlickBackground
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
    onSignOut: () -> Unit,
    onProfilePhotoSelected: (android.net.Uri) -> Unit = {}
) {
    Crossfade(
        targetState = currentScreen,
        label = "screen_transition"
    ) { screen ->
        when (screen) {
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
                onPhotoSelected = onProfilePhotoSelected
            )

            is Screen.MyPhotos -> MyPhotosScreen(
                viewModel = profileViewModel,
                userId = userProfile.uid,
                onBack = { onScreenChange(Screen.Home) }
            )

            is Screen.Friends -> FriendsScreen(
                userProfile = userProfile,
                viewModel = friendsViewModel,
                onBack = { onScreenChange(Screen.Home) }
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
}
