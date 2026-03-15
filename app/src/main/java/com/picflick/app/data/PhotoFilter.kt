package com.picflick.app.data

/**
 * Enum representing available photo filters
 */
enum class PhotoFilter(val displayName: String, val icon: String) {
    ORIGINAL("Original", "📷"),
    BLACK_AND_WHITE("B&W", "⚫"),
    SEPIA("Sepia", "📜"),
    NEGATIVE("Negative", "🔄"),
    HIGH_CONTRAST("Contrast", "⚡"),
    WARM("Warm", "☀️"),
    COOL("Cool", "❄️"),
    VINTAGE("Vintage", "📻"),
    RETRO("Retro", "🎞️"),
    POLAROID("Polaroid", "📸"),
    NOIR("Noir", "🎬"),
    FADE("Fade", "🌫️"),
    VIVID("Vivid", "🌈")
}

