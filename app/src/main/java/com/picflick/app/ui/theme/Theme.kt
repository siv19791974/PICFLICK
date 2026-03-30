package com.picflick.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// PICFLICK COLOR SCHEMES
// ============================================

private val PicFlickLightColorScheme = lightColorScheme(
    primary = PicFlickAccent,
    secondary = PicFlickAccent,
    tertiary = Pink40,
    background = PicFlickLightBackground,
    surface = PicFlickLightSurface,
    onBackground = PicFlickLightOnBackground,
    onSurface = PicFlickLightOnSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black
)

private val PicFlickDarkColorScheme = darkColorScheme(
    primary = PicFlickAccent,
    secondary = PicFlickAccent,
    tertiary = Pink80,
    background = PicFlickDarkBackground,
    surface = PicFlickDarkSurface,
    onBackground = PicFlickDarkOnBackground,
    onSurface = PicFlickDarkOnSurface,
    onPrimary = Color.White,
    onSecondary = Color.White
)

/**
 * PicFlickTheme - Main theme composable
 * Respects user preference from ThemeManager, falls back to system default
 */
@Composable
fun PicFlickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    LaunchedEffect(darkTheme, ThemeManager.themeMode.value) {
        ThemeManager.syncWithSystem(darkTheme)
    }

    val isDarkModeEnabled = ThemeManager.isDarkMode.value

    val colorScheme = if (isDarkModeEnabled) {
        PicFlickDarkColorScheme
    } else {
        PicFlickLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PicFlickBannerBackground.toArgb()
            window.navigationBarColor = Color.Black.toArgb() // Black nav bar
            // Keep status bar icons light for strong visibility on dark top bar across devices
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
