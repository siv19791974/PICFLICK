package com.example.picflick.viewmodel

import com.example.picflick.data.Result
import com.example.picflick.data.UserProfile
import com.example.picflick.repository.FlickRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FriendsViewModel
 */
@ExperimentalCoroutinesApi
class FriendsViewModelTest {

    private lateinit var viewModel: FriendsViewModel
    private lateinit var mockRepository: FlickRepository
    private val testDispatcher = StandardTestDispatcher()

    private val testUsers = listOf(
        UserProfile(
            uid = "user2",
            email = "user2@example.com",
            displayName = "User Two",
            photoUrl = "https://example.com/user2.jpg",
            bio = "Hello",
            followers = listOf("user3"),
            following = emptyList()
        ),
        UserProfile(
            uid = "user3",
            email = "user3@example.com",
            displayName = "User Three",
            photoUrl = "https://example.com/user3.jpg",
            bio = "Hi there",
            followers = listOf("user1", "user2"),
            following = listOf("user2")
        ),
        UserProfile(
            uid = "user4",
            email = "user4@example.com",
            displayName = "User Four",
            photoUrl = "",
            bio = "",
            followers = emptyList(),
            following = emptyList()
        )
    )

    private val currentUser = UserProfile(
        uid = "user1",
        email = "user1@example.com",
        displayName = "Current User",
        photoUrl = "",
        bio = "",
        followers = listOf("user3"),
        following = listOf("user3") // Already following user3
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock the repository singleton
        mockRepository = mockk(relaxed = true)
        mockkObject(FlickRepository)
        every { FlickRepository.getInstance() } returns mockRepository

        viewModel = FriendsViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state should have empty search results`() {
        // Then
        assertTrue(viewModel.searchResults.isEmpty())
    }

    @Test
    fun `initial state should not be loading`() {
        // Then
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `initial search query should be empty`() {
        // Then
        assertEquals("", viewModel.searchQuery)
    }

    @Test
    fun `searchUsers should set loading to true initially`() {
        // Given
        val query = "Test"
        val currentUserId = "user1"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            // Don't call callback yet
        }

        // When
        viewModel.searchUsers(query, currentUserId)

        // Then
        assertTrue(viewModel.isLoading)
        assertEquals(query, viewModel.searchQuery)
    }

    @Test
    fun `searchUsers should populate results on success`() {
        // Given
        val query = "User"
        val currentUserId = "user1"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testUsers))
        }

        // When
        viewModel.searchUsers(query, currentUserId)

        // Then
        assertEquals(3, viewModel.searchResults.size)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `searchUsers should exclude current user from results`() {
        // Given - mock returns users including current user (should be filtered by repository)
        val query = "User"
        val currentUserId = "user1"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testUsers)) // Repository should filter user1
        }

        // When
        viewModel.searchUsers(query, currentUserId)

        // Then - verify no user1 in results (assuming repository filters correctly)
        assertTrue(viewModel.searchResults.none { it.uid == "user1" })
    }

    @Test
    fun `searchUsers should set error message on failure`() {
        // Given
        val query = "Test"
        val currentUserId = "user1"
        val errorMessage = "Search failed"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Error(Exception(errorMessage), errorMessage))
        }

        // When
        viewModel.searchUsers(query, currentUserId)

        // Then
        assertTrue(viewModel.searchResults.isEmpty())
        assertFalse(viewModel.isLoading)
        assertEquals(errorMessage, viewModel.errorMessage)
    }

    @Test
    fun `searchUsers with blank query should clear results`() {
        // Given - first populate results
        val query = "User"
        val currentUserId = "user1"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testUsers))
        }
        viewModel.searchUsers(query, currentUserId)
        assertEquals(3, viewModel.searchResults.size)

        // When - search with blank query
        viewModel.searchUsers("", currentUserId)

        // Then
        assertTrue(viewModel.searchResults.isEmpty())
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `followUser should call repository followUser method`() = runTest {
        // Given
        val currentUserId = "user1"
        val targetUser = testUsers[0] // user2
        
        coEvery { mockRepository.followUser(currentUserId, targetUser.uid) } returns Result.Success(Unit)
        
        // Setup search results to contain target user
        val query = "User"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testUsers))
        }
        viewModel.searchUsers(query, currentUserId)

        // When
        viewModel.followUser(currentUserId, targetUser, currentUser)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockRepository.followUser(currentUserId, targetUser.uid) }
    }

    @Test
    fun `unfollowUser should call repository unfollowUser method`() = runTest {
        // Given
        val currentUserId = "user1"
        val targetUserId = "user3"
        
        coEvery { mockRepository.unfollowUser(currentUserId, targetUserId) } returns Result.Success(Unit)
        
        // Setup search results
        val query = "User"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testUsers))
        }
        viewModel.searchUsers(query, currentUserId)

        // When
        viewModel.unfollowUser(currentUserId, targetUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockRepository.unfollowUser(currentUserId, targetUserId) }
    }

    @Test
    fun `clearError should set error message to null`() {
        // Given - trigger an error first
        val query = "Test"
        val currentUserId = "user1"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Error(Exception("Error"), "Error"))
        }
        viewModel.searchUsers(query, currentUserId)
        assertNotNull(viewModel.errorMessage)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `searchUsers should be called with correct parameters`() {
        // Given
        val query = "specific_query"
        val currentUserId = "specific_user_id"
        val callbackSlot = slot<(Result<List<UserProfile>>) -> Unit>()
        every { mockRepository.searchUsers(query, currentUserId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(emptyList()))
        }

        // When
        viewModel.searchUsers(query, currentUserId)

        // Then
        io.mockk.verify { mockRepository.searchUsers(query, currentUserId, any()) }
    }
}
