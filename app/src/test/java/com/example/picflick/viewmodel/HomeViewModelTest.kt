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
 * Unit tests for HomeViewModel
 */
@ExperimentalCoroutinesApi
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var mockRepository: FlickRepository
    private val testDispatcher = StandardTestDispatcher()

    private val testFlicks = listOf(
        Flick(
            id = "1",
            userId = "user1",
            userName = "User One",
            imageUrl = "https://example.com/1.jpg",
            description = "Test flick 1",
            timestamp = 1234567890,
            likes = listOf("user2")
        ),
        Flick(
            id = "2",
            userId = "user2",
            userName = "User Two",
            imageUrl = "https://example.com/2.jpg",
            description = "Test flick 2",
            timestamp = 1234567891,
            likes = emptyList()
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock the repository singleton
        mockRepository = mockk(relaxed = true)
        mockkObject(FlickRepository)
        every { FlickRepository.getInstance() } returns mockRepository

        viewModel = HomeViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state should have empty flicks list`() {
        // Then
        assertTrue(viewModel.flicks.isEmpty())
    }

    @Test
    fun `initial state should not be loading`() {
        // Then
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `loadFlicks should set loading to true initially`() {
        // Given
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getFlicks(capture(callbackSlot)) } answers {
            // Don't call callback yet, just verify loading state
        }

        // When
        viewModel.loadFlicks()

        // Then
        assertTrue(viewModel.isLoading)
    }

    @Test
    fun `loadFlicks should populate flicks on success`() {
        // Given
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getFlicks(capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(testFlicks))
        }

        // When
        viewModel.loadFlicks()

        // Then
        assertEquals(2, viewModel.flicks.size)
        assertEquals("1", viewModel.flicks[0].id)
        assertEquals("2", viewModel.flicks[1].id)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `loadFlicks should set error message on failure`() {
        // Given
        val errorMessage = "Network error"
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getFlicks(capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Error(Exception(errorMessage), errorMessage))
        }

        // When
        viewModel.loadFlicks()

        // Then
        assertTrue(viewModel.flicks.isEmpty())
        assertFalse(viewModel.isLoading)
        assertEquals(errorMessage, viewModel.errorMessage)
    }

    @Test
    fun `checkDailyUploads should update todayUploadCount on success`() {
        // Given
        val userId = "test_user"
        val uploadCount = 5
        val callbackSlot = slot<(Result<Int>) -> Unit>()
        every { mockRepository.getDailyUploadCount(userId, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(uploadCount))
        }

        // When
        viewModel.checkDailyUploads(userId)

        // Then
        assertEquals(uploadCount, viewModel.todayUploadCount)
        assertEquals(userId, viewModel.currentUserId)
    }

    @Test
    fun `toggleLike should call repository with correct parameters`() {
        // Given
        val flick = testFlicks[0] // Has 1 like from user2
        val userId = "user3"
        val callbackSlot = slot<(Result<Unit>) -> Unit>()
        every { mockRepository.toggleLike(flick.id, userId, false, capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Success(Unit))
        }

        // When
        viewModel.toggleLike(flick, userId)

        // Then
        verify { mockRepository.toggleLike(flick.id, userId, false, any()) }
    }

    @Test
    fun `clearError should set error message to null`() {
        // Given - trigger an error first
        val callbackSlot = slot<(Result<List<Flick>>) -> Unit>()
        every { mockRepository.getFlicks(capture(callbackSlot)) } answers {
            callbackSlot.captured(Result.Error(Exception("Error"), "Error"))
        }
        viewModel.loadFlicks()
        assertNotNull(viewModel.errorMessage)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `todayUploadCount should be 0 initially`() {
        // Then
        assertEquals(0, viewModel.todayUploadCount)
    }
}
