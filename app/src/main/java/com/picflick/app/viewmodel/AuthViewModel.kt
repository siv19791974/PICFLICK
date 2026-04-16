package com.picflick.app.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.Result
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import com.picflick.app.utils.Analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen and authentication state
 */
class AuthViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val developerUid = "LpSqE40IZGeAGMknTAEzysqp5l33"
    private val developerDisplayName = "PicFlick Developer"

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
                    val profile = enforceDeveloperDisplayName(result.data)
                    userProfile = profile
                    if (profile.displayName != result.data.displayName) {
                        repository.saveUserProfile(uid, profile) { }
                    }
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
                    val profile = enforceDeveloperDisplayName(result.data)
                    userProfile = profile
                    if (profile.displayName != result.data.displayName) {
                        repository.saveUserProfile(uid, profile) { }
                    }
                    isLoading = false
                    errorMessage = null
                    android.util.Log.d("AuthViewModel", "Profile loaded successfully: ${profile.displayName}")
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
     * Phone number is user-provided only (policy-safe).
     */
    private fun getDevicePhoneNumber(@Suppress("UNUSED_PARAMETER") context: Context): String {
        return ""
    }

    private fun enforceDeveloperDisplayName(profile: UserProfile): UserProfile {
        if (profile.uid != developerUid) return profile
        if (profile.displayName == developerDisplayName) return profile
        return profile.copy(
            displayName = developerDisplayName,
            displayNameLower = developerDisplayName.lowercase()
        )
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

    fun signInWithEmail(
        email: String,
        password: String,
        context: Context,
        onResult: (Boolean, String?) -> Unit
    ) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            onResult(false, "Email and password are required")
            return
        }

        auth.signInWithEmailAndPassword(normalizedEmail, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.let { user ->
                        saveUserToFirestore(user, context)
                        onResult(true, null)
                    } ?: onResult(false, "Signed in but user not available")
                } else {
                    val message = (task.exception as? FirebaseAuthException)?.localizedMessage
                        ?: task.exception?.message
                        ?: "Email sign in failed"
                    onResult(false, message)
                }
            }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        context: Context,
        onResult: (Boolean, String?) -> Unit
    ) {
        val normalizedEmail = email.trim()
        val normalizedName = displayName.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            onResult(false, "Email and password are required")
            return
        }
        if (password.length < 6) {
            onResult(false, "Password must be at least 6 characters")
            return
        }

        auth.createUserWithEmailAndPassword(normalizedEmail, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user == null) {
                        onResult(false, "Account created but user not available")
                        return@addOnCompleteListener
                    }

                    if (normalizedName.isNotBlank()) {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(normalizedName)
                            .build()
                        user.updateProfile(profileUpdates)
                            .addOnCompleteListener {
                                saveUserToFirestore(user, context)
                                onResult(true, null)
                            }
                    } else {
                        saveUserToFirestore(user, context)
                        onResult(true, null)
                    }
                } else {
                    val message = (task.exception as? FirebaseAuthException)?.localizedMessage
                        ?: task.exception?.message
                        ?: "Email sign up failed"
                    onResult(false, message)
                }
            }
    }

    fun resetPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) {
            onResult(false, "Enter your email first")
            return
        }

        auth.sendPasswordResetEmail(normalizedEmail)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    val message = (task.exception as? FirebaseAuthException)?.localizedMessage
                        ?: task.exception?.message
                        ?: "Failed to send reset email"
                    onResult(false, message)
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
                                // Data cleanup already happened; always sign out and treat as complete from app UX perspective.
                                android.util.Log.w("AuthViewModel", "Auth delete failed after data cleanup: ${e.message}")
                                signOut(context)
                                onComplete(true, null)
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
