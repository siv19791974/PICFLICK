package com.picflick.app.ui.theme

import androidx.compose.ui.graphics.Color

// Material3 Purple/Pink defaults (kept for reference)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ============================================
// PICFLICK LIGHT MODE COLORS
// ============================================

/** Main background - Light blue */
val PicFlickLightBackground = Color(0xFFD7ECFF)

/** Backward compatibility alias */
val PicFlickBackground = PicFlickLightBackground

/** Surface/card backgrounds - White */
val PicFlickLightSurface = Color.White

/** Top banner/bottom nav - Always black in both modes */
val PicFlickBannerBackground = Color.Black

/** Text colors for light mode */
val PicFlickLightOnBackground = Color.Black
val PicFlickLightOnSurface = Color(0xFF1C1B1F)

/** Accent color - Light blue */
val PicFlickAccent = Color(0xFF87CEEB)

/** Secondary text - Gray */
val PicFlickLightSecondaryText = Color.Gray

// ============================================
// PICFLICK DARK MODE COLORS
// ============================================

/** Main background - Black */
val PicFlickDarkBackground = Color.Black

/** Surface/card backgrounds - Dark gray */
val PicFlickDarkSurface = Color(0xFF1C1C1E)

/** Text colors for dark mode */
val PicFlickDarkOnBackground = Color.White
val PicFlickDarkOnSurface = Color(0xFFE0E0E0)

/** Secondary text - Light gray */
val PicFlickDarkSecondaryText = Color(0xFFB0B0B0)

// ============================================
// DYNAMIC THEME HELPERS
// ============================================

/**
 * Get background color based on theme
 */
fun isDarkModeBackground(isDark: Boolean): Color = 
    if (isDark) PicFlickDarkBackground else PicFlickLightBackground

/**
 * Get surface color based on theme
 */
fun isDarkModeSurface(isDark: Boolean): Color = 
    if (isDark) PicFlickDarkSurface else PicFlickLightSurface

/**
 * Get on-background text color based on theme
 */
fun isDarkModeOnBackground(isDark: Boolean): Color = 
    if (isDark) PicFlickDarkOnBackground else PicFlickLightOnBackground

/**
 * Get on-surface text color based on theme
 */
fun isDarkModeOnSurface(isDark: Boolean): Color = 
    if (isDark) PicFlickDarkOnSurface else PicFlickLightOnSurface

/**
 * Get secondary text color based on theme
 */
fun isDarkModeSecondaryText(isDark: Boolean): Color = 
    if (isDark) PicFlickDarkSecondaryText else PicFlickLightSecondaryText
