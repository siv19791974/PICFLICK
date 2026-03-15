package com.picflick.app.util

/**
 * Appends a lightweight cache-busting query param to force image refresh in Coil/Firebase URLs.
 */
fun withCacheBust(url: String, key: Any?): String {
    if (url.isBlank()) return url
    val k = key?.toString()?.ifBlank { "0" } ?: "0"
    return if (url.contains("?")) "$url&cb=$k" else "$url?cb=$k"
}
