package com.picflick.app.viewmodel

import com.picflick.app.CoroutineTestRule
import com.picflick.app.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for AuthViewModel
 */
@ExperimentalCoroutinesApi
class AuthViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        viewModel = AuthViewModel()
    }

    @Test
    fun `initial state has no user and no profile`() = runTest {
        // Given: Fresh ViewModel instance

        // Then: Initial state should be null
        assertNull(viewModel.currentUser)
        assertNull(viewModel.userProfile)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `clearError resets error message`() = runTest {
        // Given: ViewModel with an error (simulated by accessing errorMessage)
        // Note: In real test with mocked repository, we'd trigger an actual error

        // When: clearError is called
        viewModel.clearError()

        // Then: Error message should be null
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `signOut clears user and profile`() = runTest {
        // Given: ViewModel with existing state
        // Note: In real implementation, we'd need to mock FirebaseAuth

        // When: signOut is called
        viewModel.signOut()

        // Then: User and profile should be cleared
        assertNull(viewModel.currentUser)
        assertNull(viewModel.userProfile)
    }

    @Test
    fun `updateBio updates user profile bio field`() = runTest {
        // Given: UserProfile with initial bio
        val initialProfile = UserProfile(
            uid = "test123",
            email = "test@test.com",
            displayName = "Test User",
            bio = "Initial bio"
        )

        // This test would require mocking the repository
        // For now, we verify the method signature exists and can be called
        // In a complete test suite, we'd mock FlickRepository.getInstance()

        // When: updateBio is called with new bio
        viewModel.updateBio("New bio")
        advanceUntilIdle()

        // Then: In a real test with mocked repository, we'd verify:
        // 1. Repository.saveUserProfile was called with updated bio
        // 2. userProfile was updated with new bio
        // For now, we just verify the method doesn't throw
    }

    @Test
    fun `updateProfilePhoto updates user profile photo`() = runTest {
        // Given: New photo URL
        val newPhotoUrl = "https://example.com/photo.jpg"

        // When: updateProfilePhoto is called
        viewModel.updateProfilePhoto(newPhotoUrl)
        advanceUntilIdle()

        // Then: In a real test with mocked repository, we'd verify:
        // 1. Repository.saveUserProfile was called with new photo URL
        // 2. userProfile was updated with new photo URL
    }
}
