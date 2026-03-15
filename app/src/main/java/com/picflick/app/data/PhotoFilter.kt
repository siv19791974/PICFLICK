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
    LOMO("Lomo", "📷"),
    _1977("1977", "📅"),
    NOIR("Noir", "🎬"),
    FADE("Fade", "🌫️"),
    VIVID("Vivid", "🌈"),
    // Android 12+ RenderEffect filters
    BLUR_LIGHT("Blur Light", "💫"),
    BLUR_MEDIUM("Blur Medium", "🔮"),
    BLUR_HEAVY("Blur Heavy", "🌫️"),
    COLOR_INVERT("Color Invert", "🎨")
}

