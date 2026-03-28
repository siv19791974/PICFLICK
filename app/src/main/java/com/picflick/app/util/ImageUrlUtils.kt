package com.picflick.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.picflick.app.data.Result
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.data.getColor
import com.picflick.app.repository.FlickRepository
import java.util.concurrent.TimeUnit

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
fun rememberChatImageModel(url: String?, messageTimestamp: Long): Any {
    val context = LocalContext.current
    val normalized = normalizePhotoUrl(url)

    if (normalized.isBlank()) return ""

    val twoDaysMs = TimeUnit.DAYS.toMillis(2)
    val now = System.currentTimeMillis()
    val isRecent = now - messageTimestamp <= twoDaysMs

    val requestData = if (isRecent) {
        normalized
    } else {
        val bucket = messageTimestamp / TimeUnit.DAYS.toMillis(1)
        withCacheBust(normalized, bucket)
    }

    return remember(normalized, messageTimestamp, requestData) {
        ImageRequest.Builder(context)
            .data(requestData)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }
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

@Composable
fun rememberLiveUserTierColor(userId: String): Color {
    var tierColor by remember(userId) {
        mutableStateOf(SubscriptionTier.FREE.getColor())
    }

    DisposableEffect(userId) {
        if (userId.isBlank()) {
            onDispose { }
        } else {
            val listener = FlickRepository.getInstance().listenToUserProfile(userId) { result ->
                if (result is Result.Success) {
                    tierColor = result.data.getEffectiveTier().getColor()
                }
            }
            onDispose { listener.remove() }
        }
    }

    return tierColor
}
