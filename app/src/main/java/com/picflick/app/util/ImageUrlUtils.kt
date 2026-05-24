package com.picflick.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        // One-time fetch to avoid spawning a snapshot listener per composable instance
        FlickRepository.getInstance().getUserProfile(userId) { result ->
            if (result is Result.Success) {
                val fetchedUrl = normalizePhotoUrl(result.data.photoUrl)
                if (fetchedUrl.isNotBlank()) {
                    resolvedPhotoUrl = fetchedUrl
                }
            }
        }
    }

    val finalUrl = if (resolvedPhotoUrl.isNotBlank()) resolvedPhotoUrl else normalizePhotoUrl(fallbackPhotoUrl)
    return withCacheBust(finalUrl, finalUrl)
}

@Composable
fun rememberLiveUserDisplayName(
    userId: String,
    fallbackDisplayName: String?,
    showFallbackWhileLoading: Boolean = true
): String {
    var hasLoadedProfile by remember(userId) { mutableStateOf(false) }
    var resolvedDisplayName by remember(userId, fallbackDisplayName, showFallbackWhileLoading) {
        mutableStateOf(if (showFallbackWhileLoading) fallbackDisplayName.orEmpty() else "")
    }

    LaunchedEffect(userId) {
        hasLoadedProfile = false
        if (userId.isBlank()) {
            resolvedDisplayName = fallbackDisplayName.orEmpty()
            hasLoadedProfile = true
            return@LaunchedEffect
        }
        // One-time fetch keeps UI labels from showing stale upload-time names.
        FlickRepository.getInstance().getUserProfile(userId) { result ->
            if (result is Result.Success) {
                val fetchedName = result.data.displayName.trim()
                resolvedDisplayName = fetchedName.ifBlank { fallbackDisplayName.orEmpty() }
            } else if (showFallbackWhileLoading) {
                resolvedDisplayName = fallbackDisplayName.orEmpty()
            }
            hasLoadedProfile = true
        }
    }

    return when {
        resolvedDisplayName.isNotBlank() -> resolvedDisplayName
        showFallbackWhileLoading || hasLoadedProfile -> fallbackDisplayName.orEmpty()
        else -> ""
    }
}

@Composable
fun rememberLiveUserOnline(userId: String): Boolean {
    var isOnline by remember(userId) { mutableStateOf(false) }

    DisposableEffect(userId) {
        if (userId.isBlank()) {
            isOnline = false
            return@DisposableEffect onDispose { }
        }

        var registration: ListenerRegistration? = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, _ ->
                val online = snapshot?.getBoolean("isOnline") ?: false
                val lastSeenAt = when (val value = snapshot?.get("lastSeenAt")) {
                    is Long -> value
                    is Number -> value.toLong()
                    is Timestamp -> value.toDate().time
                    else -> 0L
                }
                val recentlySeen = lastSeenAt > 0L &&
                    System.currentTimeMillis() - lastSeenAt <= TimeUnit.MINUTES.toMillis(2)
                isOnline = online || recentlySeen
            }

        onDispose {
            registration?.remove()
            registration = null
        }
    }

    return isOnline
}

@Composable
fun rememberLiveUserTierColor(userId: String): Color {
    var tierColor by remember(userId) {
        mutableStateOf(SubscriptionTier.FREE.getColor())
    }

    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        // One-time fetch to avoid spawning a snapshot listener per composable instance
        FlickRepository.getInstance().getUserProfile(userId) { result ->
            if (result is Result.Success) {
                tierColor = result.data.getEffectiveTier().getColor()
            }
        }
    }

    return tierColor
}
