package com.picflick.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.Flick
import com.picflick.app.data.ReactionType
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.data.UserProfile
import com.picflick.app.navigation.Screen
import com.picflick.app.repository.ChatRepository
import com.picflick.app.repository.FlickRepository
import com.picflick.app.ui.screens.AboutScreen
import com.picflick.app.ui.screens.ChatDetailScreen
import com.picflick.app.ui.screens.ChatsScreen
import com.picflick.app.ui.screens.ContactScreen
import com.picflick.app.ui.screens.EditPhotoScreen
import com.picflick.app.ui.screens.ExploreScreen
import com.picflick.app.ui.screens.FilterScreen
import com.picflick.app.ui.screens.FindFriendsScreen
import com.picflick.app.ui.screens.FriendsScreen
import com.picflick.app.ui.screens.FullScreenPhotoViewer
import com.picflick.app.ui.screens.HomeScreen
import com.picflick.app.ui.screens.LegalScreen
import com.picflick.app.ui.screens.ManageStorageScreen
import com.picflick.app.ui.screens.MyPhotosScreen
import com.picflick.app.ui.screens.NotificationSettingsScreen
import com.picflick.app.ui.screens.NotificationsScreen
import com.picflick.app.ui.screens.PhilosophyScreen
import com.picflick.app.ui.screens.PlanOptionsScreen
import com.picflick.app.ui.screens.PrivacyPolicyScreen
import com.picflick.app.ui.screens.PrivacyScreen
import com.picflick.app.ui.screens.ProfileScreen
import com.picflick.app.ui.screens.SettingsScreen
import com.picflick.app.ui.screens.StreakAchievementsScreen
import com.picflick.app.ui.screens.SubscriptionStatusScreen
import com.picflick.app.ui.screens.UserProfileScreen
import com.picflick.app.viewmodel.AuthViewModel
import com.picflick.app.viewmodel.BillingViewModel
import com.picflick.app.viewmodel.ChatViewModel
import com.picflick.app.viewmodel.FriendsViewModel
import com.picflick.app.viewmodel.HomeViewModel
import com.picflick.app.viewmodel.NotificationViewModel
import com.picflick.app.viewmodel.ProfileViewModel
import com.picflick.app.viewmodel.SubscriptionProduct
import com.picflick.app.viewmodel.UploadViewModel
import kotlinx.coroutines.launch

/**
 * Content shown when user is authenticated.
 * Handles navigation between all authenticated screens.
 */
@Composable
fun AuthenticatedContent(
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
    selectedChatSession: ChatSession?,
    selectedOtherUserId: String,
    onSetSelectedChat: (ChatSession, String) -> Unit,
    onSignOut: () -> Unit,
    selectedPhotoUri: Uri?,
    onPhotoSelected: (Uri) -> Unit,
    onOpenUploadSourceDialog: () -> Unit,
    pushPhoto: Flick? = null,
    onPushPhotoConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FlickRepository.getInstance() }

    // Capture billingViewModel locally for use in when blocks
    val bvm = billingViewModel

    // Direct screen switching - NO animation (fixes banner position shift)
    when (currentScreen) {
        is Screen.Home -> HomeScreenContent(
            userProfile = userProfile,
            homeViewModel = homeViewModel,
            friendsViewModel = friendsViewModel,
            onScreenChange = onScreenChange,
            onSignOut = onSignOut
        )

        is Screen.Profile -> ProfileScreenContent(
            userProfile = userProfile,
            profileViewModel = profileViewModel,
            authViewModel = authViewModel,
            homeViewModel = homeViewModel,
            onScreenChange = onScreenChange,
            onPhotoSelected = onPhotoSelected
        )

        is Screen.MyPhotos -> MyPhotosScreen(
            viewModel = profileViewModel,
            userId = userProfile.uid,
            currentUser = userProfile,
            onBack = { onScreenChange(Screen.Home) },
            onEditPhotoClick = { flick ->
                onScreenChange(Screen.EditPhoto(flick))
            }
        )

        is Screen.Friends -> FriendsScreenContent(
            userProfile = userProfile,
            friendsViewModel = friendsViewModel,
            onScreenChange = onScreenChange
        )

        is Screen.Chats -> ChatsScreenContent(
            userProfile = userProfile,
            chatViewModel = chatViewModel,
            friendsViewModel = friendsViewModel,
            onScreenChange = onScreenChange,
            onSetSelectedChat = onSetSelectedChat
        )

        is Screen.ChatDetail -> ChatDetailScreenContent(
            selectedChatSession = selectedChatSession,
            selectedOtherUserId = selectedOtherUserId,
            userProfile = userProfile,
            chatViewModel = chatViewModel,
            friendsViewModel = friendsViewModel,
            onScreenChange = onScreenChange,
            onSetSelectedChat = onSetSelectedChat,
            onOpenUploadSourceDialog = onOpenUploadSourceDialog
        )

        is Screen.FindFriends -> FindFriendsScreenContent(
            userProfile = userProfile,
            friendsViewModel = friendsViewModel,
            authViewModel = authViewModel,
            onScreenChange = onScreenChange
        )

        is Screen.About -> AboutScreen(
            onBack = { onScreenChange(Screen.Settings) }
        )

        is Screen.Contact -> ContactScreen(
            userProfile = userProfile,
            onBack = { onScreenChange(Screen.Home) }
        )

        is Screen.Notifications -> NotificationsScreenContent(
            userProfile = userProfile,
            homeViewModel = homeViewModel,
            onScreenChange = onScreenChange
        )

        is Screen.Settings -> SettingsScreenContent(
            userProfile = userProfile,
            authViewModel = authViewModel,
            onScreenChange = onScreenChange,
            onSignOut = onSignOut
        )

        is Screen.ManageStorage -> ManageStorageScreenContent(
            userProfile = userProfile,
            billingViewModel = bvm,
            context = context,
            onScreenChange = onScreenChange
        )

        is Screen.SubscriptionStatus -> SubscriptionStatusScreenContent(
            userProfile = userProfile,
            billingViewModel = bvm,
            context = context,
            onScreenChange = onScreenChange
        )

        is Screen.PlanOptions -> PlanOptionsScreenContent(
            userProfile = userProfile,
            billingViewModel = bvm,
            context = context,
            onScreenChange = onScreenChange
        )

        is Screen.StreakAchievements -> StreakAchievementsScreen(
            currentStreak = profileViewModel.currentStreak,
            onBack = { onScreenChange(Screen.Profile) }
        )

        is Screen.Filter -> FilterScreenContent(
            selectedPhotoUri = selectedPhotoUri,
            userProfile = userProfile,
            friendsViewModel = friendsViewModel,
            uploadViewModel = uploadViewModel,
            context = context,
            onScreenChange = onScreenChange
        )

        is Screen.Explore -> ExploreScreenContent(
            userProfile = userProfile,
            homeViewModel = homeViewModel,
            onScreenChange = onScreenChange
        )

        is Screen.UserProfile -> UserProfileScreenContent(
            currentScreen = currentScreen,
            userProfile = userProfile,
            friendsViewModel = friendsViewModel,
            authViewModel = authViewModel,
            homeViewModel = homeViewModel,
            onScreenChange = onScreenChange
        )

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

        is Screen.Preview -> {
            // Preview screen - for now navigate to Home
            // TODO: Implement proper preview screen
            onScreenChange(Screen.Home)
        }

        is Screen.EditPhoto -> EditPhotoScreen(
            flick = currentScreen.flick,
            _currentUser = userProfile,
            _cloudName = "", // TODO: Get from BuildConfig or settings
            onBack = { onScreenChange(Screen.Home) },
            onSave = { flick, filterType, description, taggedFriends, filteredBitmap ->
                scope.launch {
                    val stream = java.io.ByteArrayOutputStream()
                    filteredBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, stream)
                    val bytes = stream.toByteArray()

                    val uploadResult = repository.uploadFlickImage(flick.userId, bytes)
                    when (uploadResult) {
                        is com.picflick.app.data.Result.Success -> {
                            val result = repository.updateFlickWithFilter(
                                flickId = flick.id,
                                description = description,
                                filterType = filterType,
                                newImageUrl = uploadResult.data,
                                taggedFriends = taggedFriends
                            )
                            when (result) {
                                is com.picflick.app.data.Result.Success -> {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Photo updated!",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onScreenChange(Screen.Home)
                                }
                                is com.picflick.app.data.Result.Error -> {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to update: ${result.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                else -> {}
                            }
                        }
                        is com.picflick.app.data.Result.Error -> {
                            android.widget.Toast.makeText(
                                context,
                                "Image upload failed: ${uploadResult.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> {}
                    }
                }
            }
        )
    }

    // FullScreenPhotoViewer for push notification photos (overlays any screen)
    pushPhoto?.let { flick ->
        // Use key to force recomposition when pushPhoto changes
        key(flick.id) {
            PhotoViewerWrapper(
                selectedPhoto = flick,
                currentUser = userProfile,
                allPhotos = listOf(flick), // Single photo list for navigation context
                currentIndex = 0,
                friendProfiles = friendsViewModel.followingUsers.associateBy { it.uid },
                onDismiss = { onPushPhotoConsumed() },
                onNavigateToPhoto = { },
                onNavigateToFindFriends = {
                    onPushPhotoConsumed()
                    onScreenChange(Screen.FindFriends)
                },
                onUserProfileClick = { userId ->
                    onPushPhotoConsumed()
                    onScreenChange(
                        if (userId == userProfile.uid) Screen.Profile
                        else Screen.UserProfile(userId)
                    )
                },
                onReaction = { f, reactionType ->
                    reactionType?.let {
                        homeViewModel.toggleReaction(
                            f, userProfile.uid, userProfile.displayName, userProfile.photoUrl, it
                        )
                    }
                },
                onShareClick = { },
                onDeleteClick = { onPushPhotoConsumed() },
                canDelete = flick.userId == userProfile.uid,
                onCaptionUpdated = { _, _ -> }
            )
        }
    }
}

// ============== Screen Content Composables ==============

@Composable
private fun HomeScreenContent(
    userProfile: UserProfile,
    homeViewModel: HomeViewModel,
    friendsViewModel: FriendsViewModel,
    onScreenChange: (Screen) -> Unit,
    onSignOut: () -> Unit
) {
    HomeScreen(
        userProfile = userProfile,
        viewModel = homeViewModel,
        onNavigate = { route ->
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
        friends = friendsViewModel.followingUsers, // Pass friends for profile picture lookup
        onEditPhotoClick = { flick ->
            onScreenChange(Screen.EditPhoto(flick))
        }
    )
}

@Composable
private fun ProfileScreenContent(
    userProfile: UserProfile,
    profileViewModel: ProfileViewModel,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    onScreenChange: (Screen) -> Unit,
    onPhotoSelected: (Uri) -> Unit
) {
    val context = LocalContext.current

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
        onBioUpdated = { newBio -> authViewModel.updateBio(newBio) },
        onPhotoClick = { flick, index ->
            selectedPhoto = flick
            selectedPhotoIndex = index
        },
        onProfilePhotoClick = {
            if (userProfile.photoUrl.isNotEmpty()) {
                selectedPhoto = Flick(
                    id = "profile_${userProfile.uid}",
                    userId = userProfile.uid,
                    userName = userProfile.displayName,
                    userPhotoUrl = userProfile.photoUrl,
                    imageUrl = userProfile.photoUrl,
                    description = "",
                    timestamp = System.currentTimeMillis(),
                    reactions = emptyMap()
                )
                selectedPhotoIndex = 0
            }
        },
        onRefresh = {
            profileViewModel.loadUserPhotos(userProfile.uid)
            authViewModel.reloadUserProfile()
        },
        onPlanOptions = { onScreenChange(Screen.PlanOptions) },
        onFriendsClick = { onScreenChange(Screen.Friends) },
        onStreakClick = { onScreenChange(Screen.StreakAchievements) },
        onReaction = { flick, reactionType ->
            reactionType?.let {
                homeViewModel.toggleReaction(
                    flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, it
                )
            }
        },
        isLoading = profileViewModel.isLoading
    )

    // FullScreenPhotoViewer when photo selected
    // Use key to force recomposition when photos list changes (reactions update)
    val photosHash = profileViewModel.photos.sumOf { it.getTotalReactions() }
    key(photosHash) {
        PhotoViewerWrapper(
            selectedPhoto = selectedPhoto,
            currentUser = userProfile,
            allPhotos = profileViewModel.photos,
            currentIndex = selectedPhotoIndex,
            onDismiss = { selectedPhoto = null },
            onNavigateToPhoto = { index ->
                selectedPhotoIndex = index
                selectedPhoto = profileViewModel.photos.getOrNull(index)
            },
            onNavigateToFindFriends = {
                selectedPhoto = null
                onScreenChange(Screen.FindFriends)
            },
            onUserProfileClick = { userId ->
                selectedPhoto = null
                onScreenChange(
                    if (userId == userProfile.uid) Screen.Profile
                    else Screen.UserProfile(userId)
                )
            },
            onReaction = { flick, reactionType ->
                profileViewModel.toggleReaction(
                    flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, reactionType
                )
            },
            onShareClick = { flick ->
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Check out my photo on PicFlick: ${flick.imageUrl}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
            },
            onDeleteClick = { flick ->
                profileViewModel.deletePhoto(flick.id) { success ->
                    if (success) selectedPhoto = null
                }
            },
            canDelete = true,
            onCaptionUpdated = { flick, newCaption ->
                profileViewModel.updateCaption(flick.id, newCaption)
            },
            onEditPhotoClick = { flick ->
                onScreenChange(Screen.EditPhoto(flick))
            }
        )
    }
}

@Composable
private fun FriendsScreenContent(
    userProfile: UserProfile,
    friendsViewModel: FriendsViewModel,
    onScreenChange: (Screen) -> Unit
) {
    FriendsScreen(
        userProfile = userProfile,
        viewModel = friendsViewModel,
        onBack = { onScreenChange(Screen.Home) },
        onFindFriendsClick = { onScreenChange(Screen.FindFriends) },
        onProfilePhotoClick = { friend ->
            onScreenChange(Screen.UserProfile(friend.uid))
        }
    )
}

@Composable
private fun ChatsScreenContent(
    userProfile: UserProfile,
    chatViewModel: ChatViewModel,
    friendsViewModel: FriendsViewModel,
    onScreenChange: (Screen) -> Unit,
    onSetSelectedChat: (ChatSession, String) -> Unit
) {
    ChatsScreen(
        userProfile = userProfile,
        viewModel = chatViewModel,
        friendsViewModel = friendsViewModel,
        onBack = { onScreenChange(Screen.Home) },
        onChatClick = { session, otherUserId ->
            onSetSelectedChat(session, otherUserId)
            onScreenChange(Screen.ChatDetail)
        },
        onStartNewChat = { friendId, friendName, friendPhoto ->
            // Start chat with friend
            chatViewModel.startChat(
                userId = userProfile.uid,
                otherUserId = friendId,
                userName = userProfile.displayName,
                otherUserName = friendName,
                userPhoto = userProfile.photoUrl,
                otherUserPhoto = friendPhoto,
                onChatReady = { chatId ->
                    // Navigate to chat detail
                    onScreenChange(Screen.ChatDetail)
                }
            )
        },
        onUserProfileClick = { userId ->
            onScreenChange(Screen.UserProfile(userId))
        },
        onBottomNavNavigate = { route ->
            when (route) {
                "home" -> onScreenChange(Screen.Home)
                "chats" -> onScreenChange(Screen.Chats)
                "friends" -> onScreenChange(Screen.Friends)
                "profile" -> onScreenChange(Screen.Profile)
                "upload" -> onScreenChange(Screen.Home)
            }
        }
    )
}

@Composable
private fun ChatDetailScreenContent(
    selectedChatSession: ChatSession?,
    selectedOtherUserId: String,
    userProfile: UserProfile,
    chatViewModel: ChatViewModel,
    friendsViewModel: FriendsViewModel,
    onScreenChange: (Screen) -> Unit,
    onSetSelectedChat: (ChatSession, String) -> Unit,
    onOpenUploadSourceDialog: () -> Unit
) {
    var selectedChatPhoto by remember { mutableStateOf<Flick?>(null) }
    val flickRepository = remember { FlickRepository.getInstance() }

    // Clear chat when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.clearCurrentChat()
        }
    }

    if (selectedChatSession != null) {
        LaunchedEffect(userProfile.uid) {
            chatViewModel.loadChatSessions(userProfile.uid)
        }

        val tenDaysAgo = remember { System.currentTimeMillis() - (10L * 24L * 60L * 60L * 1000L) }
        val quickSwitchChats = remember(chatViewModel.chatSessions, userProfile.uid, tenDaysAgo) {
            chatViewModel.chatSessions
                .asSequence()
                .filter { session ->
                    session.participants.contains(userProfile.uid) && session.lastTimestamp >= tenDaysAgo
                }
                .mapNotNull { session ->
                    val otherId = session.participants.firstOrNull { it != userProfile.uid } ?: return@mapNotNull null
                    val otherName = session.participantNames[otherId]?.takeIf { it.isNotBlank() } ?: "Chat"
                    val otherPhoto = session.participantPhotos[otherId] ?: ""
                    com.picflick.app.ui.screens.QuickSwitchChatItem(
                        chatSession = session,
                        otherUserId = otherId,
                        otherUserName = otherName,
                        otherUserPhoto = otherPhoto
                    )
                }
                .distinctBy { it.otherUserId }
                .sortedByDescending { it.chatSession.lastTimestamp }
                .toList()
        }

        ChatDetailScreen(
            chatSession = selectedChatSession,
            otherUserId = selectedOtherUserId,
            currentUser = userProfile,
            viewModel = chatViewModel,
            onBack = { onScreenChange(Screen.Chats) },
            onUserProfileClick = { userId ->
                onScreenChange(Screen.UserProfile(userId))
            },
            onAddNewPhoto = onOpenUploadSourceDialog,
            quickSwitchChats = quickSwitchChats,
            onQuickSwitchChat = { session, otherId ->
                onSetSelectedChat(session, otherId)
                onScreenChange(Screen.ChatDetail)
            },
            onPhotoClick = { message ->
                if (message.imageUrl.isNotBlank()) {
                    val sharedFallbackId = "chat_photo_${message.imageUrl.substringBefore("?").hashCode()}"
                    val fallbackFlick = Flick(
                        id = sharedFallbackId,
                        userId = message.senderId,
                        userName = message.senderName,
                        userPhotoUrl = message.senderPhotoUrl,
                        imageUrl = message.imageUrl,
                        description = message.text,
                        timestamp = message.timestamp,
                        reactions = emptyMap(),
                        commentCount = 0,
                        privacy = "friends",
                        taggedFriends = emptyList(),
                        reportCount = 0
                    )

                    fun backfillMessageFlickId(resolvedFlickId: String) {
                        if (message.id.isBlank() || resolvedFlickId.isBlank() || message.flickId == resolvedFlickId) return
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("chatSessions")
                            .document(message.chatId)
                            .collection("messages")
                            .document(message.id)
                            .update("flickId", resolvedFlickId)
                            .addOnSuccessListener {
                                android.util.Log.d("ChatPhotoBackfill", "flickId backfilled for message=${message.id} -> $resolvedFlickId")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("ChatPhotoBackfill", "FAILED flickId backfill for message=${message.id}: ${e.message}")
                            }
                    }

                    fun resolveByImageUrlWithBackfill() {
                        flickRepository.getFlickByImageUrl(message.imageUrl, message.senderId) { byUrlResult ->
                            selectedChatPhoto = when (byUrlResult) {
                                is com.picflick.app.data.Result.Success -> {
                                    backfillMessageFlickId(byUrlResult.data.id)
                                    byUrlResult.data
                                }
                                else -> fallbackFlick
                            }
                        }
                    }

                    if (message.flickId.isNotBlank()) {
                        flickRepository.getFlickById(message.flickId) { byIdResult ->
                            when (byIdResult) {
                                is com.picflick.app.data.Result.Success -> {
                                    selectedChatPhoto = byIdResult.data
                                }
                                else -> {
                                    resolveByImageUrlWithBackfill()
                                }
                            }
                        }
                    } else {
                        resolveByImageUrlWithBackfill()
                    }
                }
            }
        )

        PhotoViewerWrapper(
            selectedPhoto = selectedChatPhoto,
            currentUser = userProfile,
            allPhotos = emptyList(),
            currentIndex = 0,
            onDismiss = { selectedChatPhoto = null },
            onNavigateToPhoto = { },
            onNavigateToFindFriends = {
                selectedChatPhoto = null
                onScreenChange(Screen.FindFriends)
            },
            onUserProfileClick = { userId ->
                selectedChatPhoto = null
                onScreenChange(
                    if (userId == userProfile.uid) Screen.Profile
                    else Screen.UserProfile(userId)
                )
            },
            onReaction = { _, _ -> },
            onShareClick = { },
            onDeleteClick = { selectedChatPhoto = null },
            canDelete = false,
            onCaptionUpdated = { _, _ -> },
            friendProfiles = friendsViewModel.followingUsers.associateBy { it.uid }
        )
    } else {
        onScreenChange(Screen.Chats)
    }
}

@Composable
private fun FindFriendsScreenContent(
    userProfile: UserProfile,
    friendsViewModel: FriendsViewModel,
    authViewModel: AuthViewModel,
    onScreenChange: (Screen) -> Unit
) {
    FindFriendsScreen(
        viewModel = friendsViewModel,
        userProfile = userProfile,
        onBack = { onScreenChange(Screen.Home) },
        onNavigateToProfile = { userId ->
            onScreenChange(Screen.UserProfile(userId))
        },
        onProfileRefresh = { authViewModel.reloadUserProfile() }
    )
}

@Composable
private fun NotificationsScreenContent(
    userProfile: UserProfile,
    homeViewModel: HomeViewModel,
    onScreenChange: (Screen) -> Unit
) {
    var selectedNotificationPhoto by remember { mutableStateOf<Flick?>(null) }

    NotificationsScreen(
        userProfile = userProfile,
        onBack = { onScreenChange(Screen.Home) },
        onUserProfileClick = { userId ->
            onScreenChange(
                if (userId == userProfile.uid) Screen.Profile
                else Screen.UserProfile(userId)
            )
        },
        onPhotoClick = { flickId, imageUrl, userId ->
            if (!imageUrl.isNullOrBlank()) {
                selectedNotificationPhoto = Flick(
                    id = flickId,
                    userId = userId,
                    userName = "",
                    userPhotoUrl = "",
                    imageUrl = imageUrl,
                    description = "",
                    timestamp = System.currentTimeMillis(),
                    reactions = emptyMap(),
                    commentCount = 0,
                    privacy = "friends",
                    taggedFriends = emptyList(),
                    reportCount = 0
                )
            }

            com.picflick.app.repository.FlickRepository.getInstance().getFlickById(flickId) { result ->
                when (result) {
                    is com.picflick.app.data.Result.Success -> {
                        selectedNotificationPhoto = result.data
                    }
                    else -> Unit
                }
            }
        },
        onChatClick = { _, _, _, _ ->
            onScreenChange(Screen.Chats)
        }
    )

    // FullScreenPhotoViewer for notification photos
    PhotoViewerWrapper(
        selectedPhoto = selectedNotificationPhoto,
        currentUser = userProfile,
        allPhotos = emptyList(),
        currentIndex = 0,
        onDismiss = { selectedNotificationPhoto = null },
        onNavigateToPhoto = { },
        onNavigateToFindFriends = {
            selectedNotificationPhoto = null
            onScreenChange(Screen.FindFriends)
        },
        onUserProfileClick = { userId ->
            selectedNotificationPhoto = null
            onScreenChange(
                if (userId == userProfile.uid) Screen.Profile
                else Screen.UserProfile(userId)
            )
        },
        onReaction = { flick, reactionType ->
            reactionType?.let {
                homeViewModel.toggleReaction(
                    flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, it
                )
            }
        },
        onShareClick = { },
        onDeleteClick = { selectedNotificationPhoto = null },
        canDelete = false,
        onCaptionUpdated = { _, _ -> }
    )
}

@Composable
private fun SettingsScreenContent(
    userProfile: UserProfile,
    authViewModel: AuthViewModel,
    onScreenChange: (Screen) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    SettingsScreen(
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
}

@Composable
private fun ManageStorageScreenContent(
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    context: android.content.Context,
    onScreenChange: (Screen) -> Unit
) {
    val activity = context as? Activity
    val bvm = billingViewModel

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
                    Toast.makeText(
                        context,
                        "Subscription products not loaded yet. Please try again in a moment.",
                        Toast.LENGTH_LONG
                    ).show()
                    bvm.queryProducts()
                }
            }
        }
    )
}

@Composable
private fun SubscriptionStatusScreenContent(
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    context: android.content.Context,
    onScreenChange: (Screen) -> Unit
) {
    val activity = context as? Activity
    val bvm = billingViewModel

    SubscriptionStatusScreen(
        userProfile = userProfile,
        billingViewModel = bvm,
        onBack = { onScreenChange(Screen.Settings) },
        onUpgrade = { tier: SubscriptionTier ->
            handlePurchase(context, act = activity, bvm = bvm, tier = tier)
        },
        onDowngrade = { tier: SubscriptionTier ->
            handlePurchase(context, act = activity, bvm = bvm, tier = tier)
        },
        onManagePayment = {
            Toast.makeText(context, "Payment management coming soon!", Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
private fun PlanOptionsScreenContent(
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    context: android.content.Context,
    onScreenChange: (Screen) -> Unit
) {
    val activity = context as? Activity
    val bvm = billingViewModel

    PlanOptionsScreen(
        userProfile = userProfile,
        billingViewModel = bvm,
        onBack = { onScreenChange(Screen.Settings) },
        onPurchase = { tier: SubscriptionTier ->
            handlePurchase(context, act = activity, bvm = bvm, tier = tier)
        }
    )
}

@Composable
private fun FilterScreenContent(
    selectedPhotoUri: Uri?,
    userProfile: UserProfile,
    friendsViewModel: FriendsViewModel,
    uploadViewModel: UploadViewModel,
    context: android.content.Context,
    onScreenChange: (Screen) -> Unit
) {
    val repository = remember { FlickRepository.getInstance() }

    selectedPhotoUri?.let { uri ->
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
            onNavigateToCamera = { onScreenChange(Screen.Home) }
        )
    } ?: run {
        onScreenChange(Screen.Home)
    }
}

@Composable
private fun ExploreScreenContent(
    userProfile: UserProfile,
    homeViewModel: HomeViewModel,
    onScreenChange: (Screen) -> Unit
) {
    val context = LocalContext.current
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
    // Use key to force recomposition when reactions change
    val exploreHash = homeViewModel.exploreFlicks.sumOf { it.getTotalReactions() }
    key(exploreHash) {
        PhotoViewerWrapper(
            selectedPhoto = selectedPhoto,
            currentUser = userProfile,
            allPhotos = homeViewModel.exploreFlicks,
            currentIndex = selectedPhotoIndex,
            onDismiss = { selectedPhoto = null },
            onNavigateToPhoto = { index ->
                selectedPhotoIndex = index
                selectedPhoto = homeViewModel.exploreFlicks.getOrNull(index)
            },
            onNavigateToFindFriends = {
                selectedPhoto = null
                onScreenChange(Screen.FindFriends)
            },
            onUserProfileClick = { userId ->
                selectedPhoto = null
                onScreenChange(Screen.UserProfile(userId))
            },
            onReaction = { flick, reactionType ->
                homeViewModel.toggleReaction(
                    flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, reactionType
                )
            },
            onShareClick = { flick ->
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Check out this photo on PicFlick: ${flick.imageUrl}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
            },
            onDeleteClick = { },
            canDelete = false,
            onCaptionUpdated = { _, _ -> }
        )
    }
}

@Composable
private fun UserProfileScreenContent(
    currentScreen: Screen,
    userProfile: UserProfile,
    friendsViewModel: FriendsViewModel,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    onScreenChange: (Screen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FlickRepository.getInstance() }

    var selectedUserPhoto by remember { mutableStateOf<Flick?>(null) }
    var selectedUserPhotoIndex by remember { mutableIntStateOf(0) }
    var targetUser by remember { mutableStateOf<UserProfile?>(null) }
    var targetUserPhotos by remember { mutableStateOf<List<Flick>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val targetUserId = (currentScreen as Screen.UserProfile).userId
    val isCurrentUser = targetUserId == userProfile.uid
    val isFriend = userProfile.following.contains(targetUserId) &&
            targetUser?.followers?.contains(userProfile.uid) == true
    val hasSentRequest = userProfile.sentFollowRequests.contains(targetUserId)
    val hasReceivedRequest = userProfile.pendingFollowRequests.contains(targetUserId)

    LaunchedEffect(targetUserId) {
        isLoading = true
        repository.getUserProfile(targetUserId) { result ->
            when (result) {
                is com.picflick.app.data.Result.Success<UserProfile> -> {
                    targetUser = result.data
                    if (userProfile.following.contains(targetUserId)) {
                        repository.getUserFlicks(targetUserId) { photosResult ->
                            when (photosResult) {
                                is com.picflick.app.data.Result.Success<List<Flick>> -> {
                                    targetUserPhotos = photosResult.data
                                }
                                else -> targetUserPhotos = emptyList()
                            }
                            isLoading = false
                        }
                    } else {
                        isLoading = false
                    }
                }
                else -> isLoading = false
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
                isLoading = true
                repository.getUserProfile(target.uid) { result ->
                    if (result is com.picflick.app.data.Result.Success<UserProfile>) {
                        targetUser = result.data
                        repository.getUserFlicks(target.uid) { photosResult ->
                            if (photosResult is com.picflick.app.data.Result.Success<List<Flick>>) {
                                targetUserPhotos = photosResult.data
                            }
                            isLoading = false
                        }
                    } else {
                        isLoading = false
                    }
                }
            },
            onPhotoClick = { flick, index ->
                selectedUserPhoto = flick
                selectedUserPhotoIndex = index
            },
            onProfilePhotoClick = {
                if (target.photoUrl.isNotEmpty()) {
                    selectedUserPhoto = Flick(
                        id = "profile_${target.uid}",
                        userId = target.uid,
                        userName = target.displayName,
                        userPhotoUrl = target.photoUrl,
                        imageUrl = target.photoUrl,
                        description = "",
                        timestamp = System.currentTimeMillis(),
                        reactions = emptyMap()
                    )
                    selectedUserPhotoIndex = 0
                }
            },
            onAddFriend = {
                friendsViewModel.sendFollowRequest(userProfile.uid, target, userProfile)
            },
            onAcceptRequest = {
                friendsViewModel.acceptFollowRequest(userProfile.uid, target)
                authViewModel.reloadUserProfile()
            },
            onMessageClick = {
                if (isFriend) {
                    scope.launch {
                        val chatRepository = ChatRepository()
                        val result = chatRepository.getOrCreateChatSession(
                            userId1 = userProfile.uid,
                            userId2 = target.uid,
                            user1Name = userProfile.displayName,
                            user2Name = target.displayName,
                            user1Photo = userProfile.photoUrl,
                            user2Photo = target.photoUrl
                        )
                        when (result) {
                            is com.picflick.app.data.Result.Success<String> -> {
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
                                // Navigate to chat via callback
                                onScreenChange(Screen.ChatDetail)
                            }
                            is com.picflick.app.data.Result.Error -> {
                                Toast.makeText(context, result.message ?: "Cannot start chat", Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                    }
                }
            },
            onBlockUser = {
                repository.blockUser(userProfile.uid, target.uid) { result ->
                    when (result) {
                        is com.picflick.app.data.Result.Success -> {
                            Toast.makeText(context, "${target.displayName} has been blocked", Toast.LENGTH_LONG).show()
                            onScreenChange(Screen.Home)
                        }
                        is com.picflick.app.data.Result.Error -> {
                            Toast.makeText(context, result.message ?: "Failed to block user", Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
            },
            onUnfriend = {
                friendsViewModel.unfollowUser(userProfile.uid, target.uid)
                Toast.makeText(context, "${target.displayName} has been removed from friends", Toast.LENGTH_LONG).show()
                onScreenChange(Screen.Home)
            },
            onReaction = { flick, reactionType ->
                reactionType?.let {
                    homeViewModel.toggleReaction(
                        flick, userProfile.uid, userProfile.displayName, userProfile.photoUrl, it
                    )
                }
            }
        )

        // FullScreenPhotoViewer for photos
        UserProfilePhotoViewer(
            selectedPhoto = selectedUserPhoto,
            currentUser = userProfile,
            isCurrentUser = isCurrentUser,
            targetUser = target,
            targetUserPhotos = targetUserPhotos,
            selectedIndex = selectedUserPhotoIndex,
            onDismiss = { selectedUserPhoto = null },
            onNavigateToPhoto = { index ->
                selectedUserPhotoIndex = index
                selectedUserPhoto = targetUserPhotos.getOrNull(index)
            },
            onNavigateToFindFriends = {
                selectedUserPhoto = null
                onScreenChange(Screen.FindFriends)
            },
            onUserProfileClick = { userId ->
                selectedUserPhoto = null
                if (userId == userProfile.uid) {
                    onScreenChange(Screen.Profile)
                } else if (userId != target.uid) {
                    onScreenChange(Screen.UserProfile(userId))
                }
            },
            onReaction = { _, _ -> /* Can't react to other users' photos */ }
        )
    } ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            } else {
                androidx.compose.material3.Text(
                    text = "User not found",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp
                )
            }
        }
    }
}

// ============== Helper Composables and Functions ==============

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Wrapper for FullScreenPhotoViewer with common functionality.
 */
@Composable
private fun PhotoViewerWrapper(
selectedPhoto: Flick?,
    currentUser: UserProfile,
    allPhotos: List<Flick>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onNavigateToPhoto: (Int) -> Unit,
    onNavigateToFindFriends: () -> Unit,
    onUserProfileClick: (String) -> Unit,
    onReaction: (Flick, ReactionType?) -> Unit,
    onShareClick: (Flick) -> Unit,
    onDeleteClick: (Flick) -> Unit,
    canDelete: Boolean,
    onCaptionUpdated: (Flick, String) -> Unit,
    friendProfiles: Map<String, UserProfile> = emptyMap(), // Map of userId -> UserProfile for looking up profile pics
    onEditPhotoClick: (Flick) -> Unit = {} // Navigate to edit photo screen
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }
    val activity = context.findActivity()

    selectedPhoto?.let { flick ->
val isProfilePhoto = flick.id.startsWith("profile_")

        val dismissAndLockPortrait = {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onDismiss()
        }

        BackHandler { dismissAndLockPortrait() }

        val useSinglePhotoMode = isProfilePhoto || allPhotos.isEmpty()

        FullScreenPhotoViewer(
            flick = flick,
            currentUser = currentUser,
            onDismiss = dismissAndLockPortrait,
onReaction = { reactionType ->
                if (!isProfilePhoto) onReaction(flick, reactionType)
            },
            onShareClick = { onShareClick(flick) },
            onDeleteClick = {
                if (!isProfilePhoto) onDeleteClick(flick)
            },
            canDelete = canDelete && !isProfilePhoto,
            onCaptionUpdated = { newCaption ->
                if (!isProfilePhoto) onCaptionUpdated(flick, newCaption)
            },
            allPhotos = if (useSinglePhotoMode) listOf(flick) else allPhotos,
            currentIndex = if (useSinglePhotoMode) 0 else currentIndex,
            onNavigateToPhoto = { index ->
                if (!useSinglePhotoMode) onNavigateToPhoto(index)
            },
            onNavigateToFindFriends = onNavigateToFindFriends,
            onUserProfileClick = onUserProfileClick,
            onShareToFriend = { flickId, friendId ->
                val flickToSend = allPhotos.firstOrNull { it.id == flickId } ?: flick
                if (flickToSend.imageUrl.isBlank()) {
                    Toast.makeText(context, "Photo unavailable to share", Toast.LENGTH_SHORT).show()
                } else {
                    coroutineScope.launch {
                        val friendProfile = friendProfiles[friendId]
                        val friendName = friendProfile?.displayName?.ifBlank { "Friend" } ?: "Friend"
                        val friendPhoto = friendProfile?.photoUrl ?: ""

                        when (val sessionResult = chatRepository.getOrCreateChatSession(
                            userId1 = currentUser.uid,
                            userId2 = friendId,
                            user1Name = currentUser.displayName,
                            user2Name = friendName,
                            user1Photo = currentUser.photoUrl,
                            user2Photo = friendPhoto
                        )) {
                            is com.picflick.app.data.Result.Success -> {
                                val message = ChatMessage(
                                    chatId = sessionResult.data,
                                    senderId = currentUser.uid,
                                    senderName = currentUser.displayName,
                                    senderPhotoUrl = currentUser.photoUrl,
                                    text = "",
                                    imageUrl = flickToSend.imageUrl,
                                    flickId = flickToSend.id,
                                    timestamp = System.currentTimeMillis(),
                                    read = false,
                                    delivered = false
                                )
                                when (val sendResult = chatRepository.sendMessage(sessionResult.data, message, friendId)) {
                                    is com.picflick.app.data.Result.Success -> {
                                        Toast.makeText(context, "Photo sent", Toast.LENGTH_SHORT).show()
                                    }
                                    is com.picflick.app.data.Result.Error -> {
                                        Toast.makeText(context, sendResult.message, Toast.LENGTH_SHORT).show()
                                    }
                                    is com.picflick.app.data.Result.Loading -> Unit
                                }
                            }
                            is com.picflick.app.data.Result.Error -> {
                                Toast.makeText(context, sessionResult.message, Toast.LENGTH_SHORT).show()
                            }
                            is com.picflick.app.data.Result.Loading -> Unit
                        }
                    }
                }
            },
            friendProfiles = friendProfiles, // Pass friend profiles for User B profile pics
            onEditPhotoClick = onEditPhotoClick // Pass edit photo callback
        )
}
}

/**
 * Photo viewer specifically for UserProfileScreen with limited functionality.
 */
@Composable
private fun UserProfilePhotoViewer(
    selectedPhoto: Flick?,
    currentUser: UserProfile,
    isCurrentUser: Boolean,
    targetUser: UserProfile,
    targetUserPhotos: List<Flick>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onNavigateToPhoto: (Int) -> Unit,
    onNavigateToFindFriends: () -> Unit,
    onUserProfileClick: (String) -> Unit,
    onReaction: (Flick, ReactionType?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }
    selectedPhoto?.let { flick ->
        val isUserProfilePhoto = flick.id.startsWith("profile_")

        BackHandler { onDismiss() }

        FullScreenPhotoViewer(
            flick = flick,
            currentUser = currentUser,
            onDismiss = onDismiss,
            onReaction = { if (!isUserProfilePhoto) onReaction(flick, it) },
            onShareClick = {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Check out this photo on PicFlick: ${flick.imageUrl}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
            },
            canDelete = isCurrentUser && !isUserProfilePhoto,
            onDeleteClick = { onDismiss() },
            onCaptionUpdated = { },
            allPhotos = if (isUserProfilePhoto) listOf(flick) else targetUserPhotos,
            currentIndex = if (isUserProfilePhoto) 0 else selectedIndex,
            onNavigateToPhoto = { index ->
                if (!isUserProfilePhoto) onNavigateToPhoto(index)
            },
            onNavigateToFindFriends = onNavigateToFindFriends,
            onUserProfileClick = onUserProfileClick,
            onShareToFriend = { flickId, friendId ->
                val flickToSend = targetUserPhotos.firstOrNull { it.id == flickId } ?: flick
                if (flickToSend.imageUrl.isBlank()) {
                    Toast.makeText(context, "Photo unavailable to share", Toast.LENGTH_SHORT).show()
                } else {
                    coroutineScope.launch {
                        when (val sessionResult = chatRepository.getOrCreateChatSession(
                            userId1 = currentUser.uid,
                            userId2 = friendId,
                            user1Name = currentUser.displayName,
                            user2Name = "Friend",
                            user1Photo = currentUser.photoUrl,
                            user2Photo = ""
                        )) {
                            is com.picflick.app.data.Result.Success -> {
                                val message = ChatMessage(
                                    chatId = sessionResult.data,
                                    senderId = currentUser.uid,
                                    senderName = currentUser.displayName,
                                    senderPhotoUrl = currentUser.photoUrl,
                                    text = "",
                                    imageUrl = flickToSend.imageUrl,
                                    flickId = flickToSend.id,
                                    timestamp = System.currentTimeMillis(),
                                    read = false,
                                    delivered = false
                                )
                                when (val sendResult = chatRepository.sendMessage(sessionResult.data, message, friendId)) {
                                    is com.picflick.app.data.Result.Success -> Toast.makeText(context, "Photo sent", Toast.LENGTH_SHORT).show()
                                    is com.picflick.app.data.Result.Error -> Toast.makeText(context, sendResult.message, Toast.LENGTH_SHORT).show()
                                    is com.picflick.app.data.Result.Loading -> Unit
                                }
                            }
                            is com.picflick.app.data.Result.Error -> Toast.makeText(context, sessionResult.message, Toast.LENGTH_SHORT).show()
                            is com.picflick.app.data.Result.Loading -> Unit
                        }
                    }
                }
            }
        )
    }
}

/**
 * Helper function to handle subscription purchases.
 */
private fun handlePurchase(
    context: android.content.Context,
    act: Activity?,
    bvm: BillingViewModel,
    tier: SubscriptionTier
) {
    act?.let { activity ->
        val product: SubscriptionProduct? = bvm.getProductForTier(tier)
        if (product != null) {
            bvm.purchaseSubscription(activity, product)
        } else {
            Toast.makeText(
                context,
                "Subscription products not loaded yet. Please try again in a moment.",
                Toast.LENGTH_LONG
            ).show()
            bvm.queryProducts()
        }
    }
}
