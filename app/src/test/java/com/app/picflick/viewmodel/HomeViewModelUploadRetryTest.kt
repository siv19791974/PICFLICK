package com.picflick.app.viewmodel

import com.picflick.app.data.Result
import com.picflick.app.repository.FlickRepository
import com.picflick.app.utils.Analytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelUploadRetryTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var mockRepository: FlickRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockRepository = mockk(relaxed = true)
        mockkObject(FlickRepository)
        mockkObject(Analytics)
        mockkStatic("android.util.Log")

        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { FlickRepository.getInstance() } returns mockRepository

        viewModel = HomeViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `runUploadWithRetry retries transient errors then succeeds`() = runTest(testDispatcher) {
        var calls = 0
        val resultDeferred = async {
            viewModel.runUploadWithRetry(maxRetries = 3, baseDelayMs = 500L) {
                calls++
                when (calls) {
                    1 -> Result.Error(Exception("network timeout"), "network timeout")
                    2 -> Result.Error(Exception("service unavailable"), "service unavailable")
                    else -> Result.Success("https://ok")
                }
            }
        }

        advanceTimeBy(500L)
        advanceTimeBy(1000L)
        advanceUntilIdle()

        val result = resultDeferred.await()
        assertTrue(result is Result.Success<*>)
        assertTrue(calls == 3)
    }

    @Test
    fun `runUploadWithRetry stops on non-transient error`() = runTest(testDispatcher) {
        var calls = 0
        val result = viewModel.runUploadWithRetry(maxRetries = 3, baseDelayMs = 500L) {
            calls++
            Result.Error(Exception("permission denied"), "permission denied")
        }

        assertTrue(result is Result.Error)
        assertTrue(calls == 1)
    }
}
