package com.example.picflick.viewmodel

import com.example.picflick.data.Flick
import com.example.picflick.data.Result
import com.example.picflick.repository.FlickRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
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
 * Unit tests for ProfileViewModel
 */
@ExperimentalCoroutinesApi
class ProfileViewModelTest {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var mockRepository: FlickRepository
    private val testDispatcher = StandardTestDispatcher()

    private val testPhotos = listOf(
        Flick(
            id = "1",
            userId = "user1",
            userName = "User One",
            imageUrl = "https://example.com/photo1.jpg",
            description = "My photo 1",
            timestamp = 1234567890,
            reactions = mapOf("user2" to "LIKE", "user3" to "LIKE")
        ),
        Flick(
            id = "2",
            userId = "user1",
            userName = "User One",
            imageUrl = "https://example.com/photo2.jpg",
            description = "My photo 2",
            timestamp = 1234567891,
            reactions = emptyMap()
        ),
        Flick(
            id = "3",
            userId = "user1",
            userName = "User One",
            imageUrl = "https://example.com/photo3.jpg",
            description = "My photo 3",
            timestamp = 1234567892,
            reactions = mapOf("user2" to "LIKE")
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock the repository singleton
        mockRepository = mockk(relaxed = true)
        mockkObject(FlickRepository)
        every { FlickRepository.getInstance() } returns mockRepository

        viewModel = ProfileViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state should have empty photos list`() {
        // Then
        assertTrue(viewModel.photos.isEmpty())
    }

    @Test
    fun `initial state should not be loading`() {
        // Then
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `initial photo count should be 0`() {
        // Then
        assertEquals(0, viewModel.photoCount)
    }

    @Test
    fun `loadUserPhotos should set loading to true initially`() {
        // Given
        val userId = "user1"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getUserFlicks(userId, capture(callbackSlot)) } answers {
            // Don't call callback yet
        }

        // When
        viewModel.loadUserPhotos(userId)

        // Then
        assertTrue(viewModel.isLoading)
    }

    @Test
    fun `loadUserPhotos should populate photos on success`() {
        // Given
        val userId = "user1"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getUserFlicks(userId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testPhotos))
        }

        // When
        viewModel.loadUserPhotos(userId)

        // Then
        assertEquals(3, viewModel.photos.size)
        assertEquals(3, viewModel.photoCount)
        assertEquals("1", viewModel.photos[0].id)
        assertEquals("2", viewModel.photos[1].id)
        assertEquals("3", viewModel.photos[2].id)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `loadUserPhotos should update photoCount correctly`() {
        // Given
        val userId = "user1"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getUserFlicks(userId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testPhotos))
        }

        // When
        viewModel.loadUserPhotos(userId)

        // Then
        assertEquals(testPhotos.size, viewModel.photoCount)
    }

    @Test
    fun `loadUserPhotos should set error message on failure`() {
        // Given
        val userId = "user1"
        val errorMessage = "Failed to load photos"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getUserFlicks(userId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Error(Exception(errorMessage), errorMessage))
        }

        // When
        viewModel.loadUserPhotos(userId)

        // Then
        assertTrue(viewModel.photos.isEmpty())
        assertEquals(0, viewModel.photoCount)
        assertFalse(viewModel.isLoading)
        assertEquals(errorMessage, viewModel.errorMessage)
    }

    @Test
    fun `loadUserPhotos should handle empty result`() {
        // Given
        val userId = "user1"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getUserFlicks(userId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(emptyList()))
        }

        // When
        viewModel.loadUserPhotos(userId)

        // Then
        assertTrue(viewModel.photos.isEmpty())
        assertEquals(0, viewModel.photoCount)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `clearError should set error message to null`() {
        // Given - trigger an error first
        val userId = "user1"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getUserFlicks(userId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Error(Exception("Error"), "Error"))
        }
        viewModel.loadUserPhotos(userId)
        assertNotNull(viewModel.errorMessage)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `loadUserPhotos should be called with correct userId`() {
        // Given
        val userId = "specific_user_id"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getUserFlicks(userId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(emptyList()))
        }

        // When
        viewModel.loadUserPhotos(userId)

        // Then
        verify { mockRepository.getUserFlicks(userId, any()) }
    }
}
