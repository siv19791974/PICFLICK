package com.example.picflick.data

import androidx.compose.ui.graphics.Color

/**
 * Subscription tiers for PicFlick storage management
 * Ordered from lowest to highest: FREE → STANDARD → PLUS → PRO → ULTRA
 */
enum class SubscriptionTier {
    FREE,
    STANDARD,
    PLUS,
    PRO,
    ULTRA;

    companion object {
        fun fromString(value: String): SubscriptionTier {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                FREE
            }
        }
    }
}

/**
 * Tier colors for UI display - from low to high
 * Grey → White → Bronze → Silver → Gold
 */
object TierColors {
    // Grey - Free tier (basic)
    val Free = Color(0xFF9E9E9E)
    val FreeDark = Color(0xFF757575)
    val FreeLight = Color(0xFFBDBDBD)

    // White - Standard tier (clean)
    val Standard = Color(0xFFF5F5F5)
    val StandardDark = Color(0xFFD4D4D4)
    val StandardLight = Color(0xFFFFFFFF)

    // Bronze - Plus tier (warm, entry premium)
    val Plus = Color(0xFFCD7F32)
    val PlusDark = Color(0xFF8B4513)
    val PlusLight = Color(0xFFE8C39E)

    // Silver - Pro tier (premium)
    val Pro = Color(0xFFC0C0C0)
    val ProDark = Color(0xFF808080)
    val ProLight = Color(0xFFF0F0F0)

    // Gold - Ultra tier (elite)
    val Ultra = Color(0xFFFFD700)
    val UltraDark = Color(0xFFDAA520)
    val UltraLight = Color(0xFFFFF4A3)

    // Rainbow gradient colors for Founder status
    val FounderRainbow = listOf(
        Color(0xFFFF6B6B), // Red
        Color(0xFFFFD93D), // Yellow
        Color(0xFF6BCF7F), // Green
        Color(0xFF4D96FF), // Blue
        Color(0xFF9B59B6)  // Purple
    )
}

/**
 * Get the display color for a subscription tier
 */
fun SubscriptionTier.getColor(): Color {
    return when (this) {
        SubscriptionTier.FREE -> TierColors.Free
        SubscriptionTier.STANDARD -> TierColors.Standard
        SubscriptionTier.PLUS -> TierColors.Plus
        SubscriptionTier.PRO -> TierColors.Pro
        SubscriptionTier.ULTRA -> TierColors.Ultra
    }
}

/**
 * Get the display name for a subscription tier
 */
fun SubscriptionTier.getDisplayName(): String {
    return when (this) {
        SubscriptionTier.FREE -> "Free"
        SubscriptionTier.STANDARD -> "Standard"
        SubscriptionTier.PLUS -> "Plus"
        SubscriptionTier.PRO -> "Pro"
        SubscriptionTier.ULTRA -> "Ultra"
    }
}

/**
 * Get the dark shade of the tier color (for gradients)
 */
fun SubscriptionTier.getDarkColor(): Color {
    return when (this) {
        SubscriptionTier.FREE -> TierColors.FreeDark
        SubscriptionTier.STANDARD -> TierColors.StandardDark
        SubscriptionTier.PLUS -> TierColors.PlusDark
        SubscriptionTier.PRO -> TierColors.ProDark
        SubscriptionTier.ULTRA -> TierColors.UltraDark
    }
}

/**
 * Get the light shade of the tier color (for gradients)
 */
fun SubscriptionTier.getLightColor(): Color {
    return when (this) {
        SubscriptionTier.FREE -> TierColors.FreeLight
        SubscriptionTier.STANDARD -> TierColors.StandardLight
        SubscriptionTier.PLUS -> TierColors.PlusLight
        SubscriptionTier.PRO -> TierColors.ProLight
        SubscriptionTier.ULTRA -> TierColors.UltraLight
    }
}

/**
 * Get tier limits and pricing
 */
fun SubscriptionTier.getDailyUploadLimit(): Int {
    return when (this) {
        SubscriptionTier.FREE -> 10
        SubscriptionTier.STANDARD -> 25
        SubscriptionTier.PLUS -> 50
        SubscriptionTier.PRO -> 100
        SubscriptionTier.ULTRA -> Int.MAX_VALUE // Unlimited
    }
}

fun SubscriptionTier.getStorageLimitGB(): Int {
    return when (this) {
        SubscriptionTier.FREE -> 2
        SubscriptionTier.STANDARD -> 15
        SubscriptionTier.PLUS -> 50
        SubscriptionTier.PRO -> 100
        SubscriptionTier.ULTRA -> 200
    }
}

fun SubscriptionTier.getStorageLimitBytes(): Long {
    return getStorageLimitGB() * 1024L * 1024L * 1024L
}

fun SubscriptionTier.getMonthlyPrice(): Double {
    return when (this) {
        SubscriptionTier.FREE -> 0.0
        SubscriptionTier.STANDARD -> 2.99
        SubscriptionTier.PLUS -> 4.99
        SubscriptionTier.PRO -> 8.99
        SubscriptionTier.ULTRA -> 14.99
    }
}

fun SubscriptionTier.getYearlyPrice(): Double {
    // 2 months free when paying yearly
    return (getMonthlyPrice() * 10)
}

fun SubscriptionTier.getImageQuality(): Int {
    return when (this) {
        SubscriptionTier.FREE -> 80
        SubscriptionTier.STANDARD -> 85
        SubscriptionTier.PLUS -> 90
        SubscriptionTier.PRO -> 95
        SubscriptionTier.ULTRA -> 100
    }
}

fun SubscriptionTier.getQualityDescription(): String {
    return when (this) {
        SubscriptionTier.FREE -> "Standard"
        SubscriptionTier.STANDARD -> "Better"
        SubscriptionTier.PLUS -> "High"
        SubscriptionTier.PRO -> "Premium"
        SubscriptionTier.ULTRA -> "Maximum"
    }
}

fun SubscriptionTier.getNextTier(): SubscriptionTier? {
    return when (this) {
        SubscriptionTier.FREE -> SubscriptionTier.STANDARD
        SubscriptionTier.STANDARD -> SubscriptionTier.PLUS
        SubscriptionTier.PLUS -> SubscriptionTier.PRO
        SubscriptionTier.PRO -> SubscriptionTier.ULTRA
        SubscriptionTier.ULTRA -> null // Already highest
    }
}

fun SubscriptionTier.getPreviousTier(): SubscriptionTier? {
    return when (this) {
        SubscriptionTier.FREE -> null // Already lowest
        SubscriptionTier.STANDARD -> SubscriptionTier.FREE
        SubscriptionTier.PLUS -> SubscriptionTier.STANDARD
        SubscriptionTier.PRO -> SubscriptionTier.PLUS
        SubscriptionTier.ULTRA -> SubscriptionTier.PRO
    }
}
