package com.picflick.app.data

/**
 * Sealed class representing different states of UI operations
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Unknown error") : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

