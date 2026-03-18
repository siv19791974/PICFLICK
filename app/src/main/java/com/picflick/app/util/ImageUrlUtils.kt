package com.picflick.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.picflick.app.data.Result
import com.picflick.app.repository.FlickRepository

/**
 * Appends a lightweight cache-busting query param to force image refresh in Coil/Firebase URLs.
 */
fun withCacheBust(url: String, key: Any?): String {
    if (url.isBlank()) return url
    val k = key?.toString()?.ifBlank { "0" } ?: "0"
    return if (url.contains("?")) "$url&cb=$k" else "$url?cb=$k"
}

fun normalizePhotoUrl(url: String?): String {
    val cleaned = url?.trim().orEmpty()
    if (cleaned.isBlank()) return ""
    if (cleaned.equals("null", ignoreCase = true)) return ""
    if (cleaned.equals("undefined", ignoreCase = true)) return ""
    return cleaned
}

@Composable
fun rememberLiveUserPhotoUrl(userId: String, fallbackPhotoUrl: String?): String {
    var resolvedPhotoUrl by remember(userId, fallbackPhotoUrl) {
        mutableStateOf(normalizePhotoUrl(fallbackPhotoUrl))
    }

    DisposableEffect(userId) {
        if (userId.isBlank()) {
            onDispose { }
        } else {
            val listener = FlickRepository.getInstance().listenToUserProfile(userId) { result ->
                if (result is Result.Success) {
                    val liveUrl = normalizePhotoUrl(result.data.photoUrl)
                    if (liveUrl.isNotBlank()) {
                        resolvedPhotoUrl = liveUrl
                    }
                }
            }
            onDispose { listener.remove() }
        }
    }

    val finalUrl = if (resolvedPhotoUrl.isNotBlank()) resolvedPhotoUrl else normalizePhotoUrl(fallbackPhotoUrl)
    return withCacheBust(finalUrl, finalUrl)
}
