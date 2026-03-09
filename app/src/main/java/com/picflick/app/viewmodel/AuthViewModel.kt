package com.picflick.app.viewmodel

import android.content.Context
import android.telephony.TelephonyManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.Result
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

    init {
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            // Only load profile if we don't already have one (prevents wiping on token refresh)
            if (userProfile == null) {
                currentUser?.let { loadUserProfile(it.uid) }
            }
        }
    }

    /**
     * Restore cached profile immediately (call this when app resumes)
     */
    fun restoreCachedProfile() {
        // If we have a user but no profile, reload it
        if (currentUser != null && userProfile == null) {
            loadUserProfile(currentUser!!.uid)
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
        repository.getUserProfile(uid) { result ->
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
     */
    fun saveUserToFirestore(user: FirebaseUser, context: Context) {
        viewModelScope.launch {
            // Auto-detect phone number from device
            val phoneNumber = getDevicePhoneNumber(context)

            val profile = UserProfile(
                uid = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                photoUrl = user.photoUrl?.toString() ?: "",
                phoneNumber = phoneNumber // Auto-detected!
            )
            repository.saveUserProfile(user.uid, profile) { result ->
                when (result) {
                    is Result.Success -> {
                        userProfile = profile
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
    fun signOut() {
        auth.signOut()
        userProfile = null
        currentUser = null
    }

    /**
     * Delete user account and all associated data
     * This permanently removes the user from Firebase Auth and Firestore
     */
    fun deleteAccount(onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    onComplete(false, "No user signed in")
                    return@launch
                }

                val userId = user.uid

                // 1. Delete user data from Firestore
                when (val result = repository.deleteUserData(userId)) {
                    is Result.Success -> {
                        // 2. Delete Firebase Auth user
                        user.delete()
                            .addOnSuccessListener {
                                // Clear local state
                                userProfile = null
                                currentUser = null
                                onComplete(true, null)
                            }
                            .addOnFailureListener { e ->
                                onComplete(false, "Failed to delete account: ${e.message}")
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

    /**
     * Clear any error message
     */
    fun clearError() {
        errorMessage = null
    }
}
