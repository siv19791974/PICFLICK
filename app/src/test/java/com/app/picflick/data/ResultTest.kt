package com.app.picflick.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Result sealed class
 */
class ResultTest {

    @Test
    fun `Success should contain correct data`() {
        // Given
        val testData = "Test Data"

        // When
        val result = Result.Success(testData)

        // Then
        assertEquals(testData, result.data)
    }

    @Test
    fun `Error should contain correct exception and message`() {
        // Given
        val exception = Exception("Test error")
        val message = "Custom error message"

        // When
        val result = Result.Error(exception, message)

        // Then
        assertEquals(exception, result.exception)
        assertEquals(message, result.message)
    }

    @Test
    fun `Error should use exception message as default`() {
        // Given
        val exception = Exception("Default exception message")

        // When
        val result = Result.Error(exception)

        // Then
        assertEquals(exception, result.exception)
        assertEquals("Default exception message", result.message)
    }

    @Test
    fun `Loading should be singleton`() {
        // When
        val loading1 = Result.Loading
        val loading2 = Result.Loading

        // Then
        assertSame(loading1, loading2)
    }

    @Test
    fun `Result types should be distinguishable`() {
        // Given
        val success: Result<String> = Result.Success("data")
        val error: Result<String> = Result.Error(Exception("error"), "error")
        val loading: Result<String> = Result.Loading

        // Then
        assertTrue(success is Result.Success)
        assertTrue(error is Result.Error)
        assertTrue(loading is Result.Loading)
    }

    @Test
    fun `Success data can be of any type`() {
        // Given
        val stringResult: Result.Success<String> = Result.Success("test")
        val intResult: Result.Success<Int> = Result.Success(42)
        val listResult: Result.Success<List<String>> = Result.Success(listOf("a", "b", "c"))

        // Then
        assertEquals("test", stringResult.data)
        assertEquals(42, intResult.data)
        assertEquals(listOf("a", "b", "c"), listResult.data)
    }
}
