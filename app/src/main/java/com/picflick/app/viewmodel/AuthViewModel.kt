package com.picflick.app.viewmodel

import android.content.Context
import android.telephony.TelephonyManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.Result
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import com.picflick.app.utils.Analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen and authentication state
 */
class AuthViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var currentUser by mutableStateOf<FirebaseUser?>(auth.currentUser)
        private set

    var userProfile by mutableStateOf<UserProfile?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var profileListener: ListenerRegistration? = null

    init {
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser

            if (currentUser == null) {
                profileListener?.remove()
                profileListener = null
                userProfile = null
                return@addAuthStateListener
            }

            val uid = currentUser!!.uid
            if (profileListener == null || userProfile?.uid != uid) {
                startProfileListener(uid)
            }

            // Keep one-time load as fallback for first render
            if (userProfile == null) {
                loadUserProfile(uid)
            }
        }
    }

    private fun startProfileListener(uid: String) {
        profileListener?.remove()
        profileListener = repository.listenToUserProfile(uid) { result ->
            when (result) {
                is Result.Success -> {
                    userProfile = result.data
                    isLoading = false
                    errorMessage = null
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
                is Result.Loading -> {
                    isLoading = true
                }
            }
        }
    }

    /**
     * Restore cached profile immediately (call this when app resumes)
     */
    fun restoreCachedProfile() {
        // If we have a user but no profile, reload it
        if (currentUser != null && userProfile == null) {
            val uid = currentUser!!.uid
            if (profileListener == null) {
                startProfileListener(uid)
            }
            loadUserProfile(uid)
        }
    }

    /**
     * Public function to reload user profile from Firestore
     * Call this when you want to force a refresh (e.g., pull-to-refresh)
     */
    fun reloadUserProfile() {
        currentUser?.let { loadUserProfile(it.uid) }
    }

    /**
     * Load user profile from Firestore
     */
    private fun loadUserProfile(uid: String) {
        isLoading = true
        android.util.Log.d("AuthViewModel", "Loading profile for uid: $uid")
        repository.getUserProfile(uid) { result ->
            android.util.Log.d("AuthViewModel", "Profile load result: $result")
            when (result) {
                is Result.Success -> {
                    userProfile = result.data
                    isLoading = false
                    errorMessage = null
                    android.util.Log.d("AuthViewModel", "Profile loaded successfully: ${result.data.displayName}")
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                    android.util.Log.e("AuthViewModel", "Profile load failed: ${result.message}")
                }
                is Result.Loading -> { /* Do nothing */ }
            }
        }
    }

    /**
     * Get phone number from device SIM
     */
    private fun getDevicePhoneNumber(context: Context): String {
        return try {
            // Check permission first
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_NUMBERS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return "" // Permission not granted, return empty
            }

            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val phoneNumber = telephonyManager.line1Number

            // Clean up phone number (remove non-digits)
            phoneNumber?.replace("[^0-9]".toRegex(), "") ?: ""
        } catch (e: Exception) {
            // Failed to get phone number (dual SIM, eSIM, or permission denied)
            ""
        }
    }

    /**
     * Save user to Firestore after successful sign in
     * BUT only if profile doesn't already exist (preserves existing bio, photo, etc.)
     */
    fun saveUserToFirestore(user: FirebaseUser, context: Context) {
        viewModelScope.launch {
            // First check if profile already exists
            repository.getUserProfile(user.uid) { existingResult ->
                when (existingResult) {
                    is Result.Success -> {
                        // Profile exists - preserve user-edited profile data (especially custom profile photo).
                        val existingProfile = existingResult.data

                        // Only update display name from Firebase when changed.
                        val needsDisplayNameUpdate =
                            (user.displayName != null && user.displayName != existingProfile.displayName)

                        // Only seed photo from Firebase if app profile photo is currently empty.
                        val shouldSeedPhoto =
                            existingProfile.photoUrl.isBlank() && user.photoUrl != null

                        if (needsDisplayNameUpdate || shouldSeedPhoto) {
                            val updatedProfile = existingProfile.copy(
                                displayName = user.displayName ?: existingProfile.displayName,
                                photoUrl = if (shouldSeedPhoto) user.photoUrl.toString() else existingProfile.photoUrl
                            )
                            repository.saveUserProfile(user.uid, updatedProfile) { result ->
                                if (result is Result.Success) {
                                    userProfile = updatedProfile
                                } else {
                                    userProfile = existingProfile
                                }
                            }
                        } else {
                            // Use existing profile as-is
                            userProfile = existingProfile
                        }
                    }
                    is Result.Error -> {
                        // Only create a new profile when truly missing; don't overwrite on transient read errors.
                        val msg = existingResult.message.lowercase()
                        if (msg.contains("not found")) {
                            createNewProfile(user, context)
                        } else {
                            errorMessage = existingResult.message
                            loadUserProfile(user.uid)
                        }
                    }
                    is Result.Loading -> { }
                }
            }
        }
    }
    
    private fun createNewProfile(user: FirebaseUser, context: Context) {
        val phoneNumber = getDevicePhoneNumber(context)
        
        val profile = UserProfile(
            uid = user.uid,
            email = user.email ?: "",
            displayName = user.displayName ?: "",
            photoUrl = user.photoUrl?.toString() ?: "",
            phoneNumber = phoneNumber
        )
        repository.saveUserProfile(user.uid, profile) { result ->
            when (result) {
                is Result.Success -> {
                    userProfile = profile
                    // Track signup for analytics
                    Analytics.trackSignUp()
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
        }
    }

    /**
     * Update user profile photo URL
     */
    fun updateProfilePhoto(photoUrl: String) {
        viewModelScope.launch {
            userProfile?.let { profile ->
                val updatedProfile = profile.copy(photoUrl = photoUrl)
                repository.saveUserProfile(profile.uid, updatedProfile) { result ->
                    when (result) {
                        is Result.Success -> {
                            userProfile = updatedProfile
                        }
                        is Result.Error -> {
                            errorMessage = result.message
                        }
                        is Result.Loading -> { }
                    }
                }
            }
        }
    }

    /**
     * Update user bio
     */
    fun updateBio(newBio: String) {
        viewModelScope.launch {
            userProfile?.let { profile ->
                val updatedProfile = profile.copy(bio = newBio)
                repository.saveUserProfile(profile.uid, updatedProfile) { result ->
                    when (result) {
                        is Result.Success -> {
                            userProfile = updatedProfile
                        }
                        is Result.Error -> {
                            errorMessage = result.message
                        }
                        is Result.Loading -> { }
                    }
                }
            }
        }
    }

    /**
     * Update user notification preferences
     */
    fun updateNotificationPreferences(preferences: com.picflick.app.data.NotificationPreferences) {
        viewModelScope.launch {
            userProfile?.let { profile ->
                val updatedProfile = profile.copy(notificationPreferences = preferences)
                val result = repository.saveNotificationPreferences(profile.uid, preferences)
                when (result) {
                    is Result.Success -> {
                        userProfile = updatedProfile
                    }
                    is Result.Error -> {
                        errorMessage = result.message
                    }
                    is Result.Loading -> { }
                }
            }
        }
    }

    /**
     * Sign out the current user
     */
    fun signOut(context: Context? = null) {
        profileListener?.remove()
        profileListener = null

        // Clear credential manager session when available (Google sign-in state)
        context?.let { ctx ->
            viewModelScope.launch {
                runCatching {
                    CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
                }.onFailure {
                    android.util.Log.w("AuthViewModel", "CredentialManager clear state failed: ${it.message}")
                }
            }
        }

        auth.signOut()
        userProfile = null
        currentUser = null
    }

    /**
     * Delete user account and all associated data
     * This permanently removes the user from Firebase Auth and Firestore
     */
    fun deleteAccount(context: Context? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    onComplete(false, "No user signed in")
                    return@launch
                }

                // Avoid partial deletion when Firebase requires recent login.
                val lastSignInAt = user.metadata?.lastSignInTimestamp ?: 0L
                val isPotentiallyStaleSession = lastSignInAt > 0L &&
                    (System.currentTimeMillis() - lastSignInAt) > (10 * 60 * 1000)
                if (isPotentiallyStaleSession) {
                    onComplete(
                        false,
                        "For security, please sign out and sign in again, then delete your account."
                    )
                    return@launch
                }

                val userId = user.uid

                // 1) Delete user data from Firestore (best-effort cleanup in repository)
                when (val result = repository.deleteUserData(userId)) {
                    is Result.Success -> {
                        // 2) Delete Firebase Auth user
                        user.delete()
                            .addOnSuccessListener {
                                signOut(context)
                                onComplete(true, null)
                            }
                            .addOnFailureListener { e ->
                                // Data cleanup already happened; always sign out to avoid staying on a broken in-app state.
                                signOut(context)

                                val msg = e.message?.lowercase().orEmpty()
                                if (msg.contains("recent") || msg.contains("credential") || msg.contains("login")) {
                                    onComplete(
                                        false,
                                        "Account data was removed. Please sign in again once, then retry Delete Account to fully remove auth."
                                    )
                                } else {
                                    onComplete(
                                        false,
                                        "Account data was removed, but auth deletion failed. You were signed out safely."
                                    )
                                }
                            }
                    }
                    is Result.Error -> {
                        onComplete(false, "Failed to delete user data: ${result.message}")
                    }
                    is Result.Loading -> { }
                }
            } catch (e: Exception) {
                onComplete(false, "Unexpected error: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        profileListener?.remove()
        profileListener = null
        super.onCleared()
    }

    /**
     * Clear any error message
     */
    fun clearError() {
        errorMessage = null
    }
}
