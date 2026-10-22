package com.example.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

/**
 * Universal dynamic color engine for PulsePlay.
 * Provides Material You harmonious dynamic tonal palette generation across ALL Android devices
 * (API 21 through API 36+), automatically extracting wallpaper colors when available or synthesizing
 * an adaptive dynamic tonal palette.
 */
fun extractWallpaperSeedColor(context: Context): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        runCatching {
            val wm = context.getSystemService(WallpaperManager::class.java)
            val colors = wm?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val primary = colors?.primaryColor
            if (primary != null) {
                return Color(primary.toArgb())
            }
        }
    }
    // High-vibrancy adaptive default dynamic seed (Electric Studio Indigo/Violet)
    return Color(0xFF7C3AED)
}

/**
 * Dynamically synthesizes a full Material 3 ColorScheme from any seed color
 * with accessible contrast ratios, tonal elevations, and harmonic accents.
 */
fun generateDynamicTonalColorScheme(seed: Color, isDark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    val argb = seed.toArgb()
    android.graphics.Color.colorToHSV(argb, hsv)
    val hue = (hsv[0] % 360f + 360f) % 360f
    val sat = hsv[1].coerceIn(0.40f, 0.95f)

    fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val normH = (h % 360f + 360f) % 360f
        val rgb = android.graphics.Color.HSVToColor(
            floatArrayOf(normH, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        )
        return Color(rgb)
    }

    val secHue = (hue + 25f) % 360f
    val tertHue = (hue + 60f) % 360f
    val bgHue = (hue + 12f) % 360f

    return if (isDark) {
        darkColorScheme(
            primary = hsvToColor(hue, sat * 0.70f, 0.92f),
            onPrimary = hsvToColor(hue, 0.95f, 0.12f),
            primaryContainer = hsvToColor(hue, sat * 0.85f, 0.32f),
            onPrimaryContainer = hsvToColor(hue, 0.20f, 0.98f),
            inversePrimary = hsvToColor(hue, sat * 0.90f, 0.42f),

            secondary = hsvToColor(secHue, sat * 0.60f, 0.86f),
            onSecondary = hsvToColor(secHue, 0.90f, 0.12f),
            secondaryContainer = hsvToColor(secHue, sat * 0.75f, 0.28f),
            onSecondaryContainer = hsvToColor(secHue, 0.20f, 0.96f),

            tertiary = hsvToColor(tertHue, sat * 0.65f, 0.88f),
            onTertiary = hsvToColor(tertHue, 0.90f, 0.12f),
            tertiaryContainer = hsvToColor(tertHue, sat * 0.70f, 0.26f),
            onTertiaryContainer = hsvToColor(tertHue, 0.20f, 0.96f),

            background = hsvToColor(bgHue, 0.30f, 0.08f),
            onBackground = Color(0xFFF1F5F9),
            surface = hsvToColor(bgHue, 0.24f, 0.12f),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = hsvToColor(bgHue, 0.22f, 0.18f),
            onSurfaceVariant = Color(0xFFCBD5E1),
            surfaceTint = hsvToColor(hue, sat * 0.70f, 0.92f),
            inverseSurface = Color(0xFFF1F5F9),
            inverseOnSurface = Color(0xFF0F172A),

            outline = hsvToColor(bgHue, 0.20f, 0.35f),
            outlineVariant = hsvToColor(bgHue, 0.18f, 0.22f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    } else {
        lightColorScheme(
            primary = hsvToColor(hue, sat * 0.95f, 0.52f),
            onPrimary = Color.White,
            primaryContainer = hsvToColor(hue, 0.25f, 0.95f),
            onPrimaryContainer = hsvToColor(hue, 0.95f, 0.18f),
            inversePrimary = hsvToColor(hue, sat * 0.70f, 0.85f),

            secondary = hsvToColor(secHue, sat * 0.80f, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = hsvToColor(secHue, 0.22f, 0.94f),
            onSecondaryContainer = hsvToColor(secHue, 0.90f, 0.18f),

            tertiary = hsvToColor(tertHue, sat * 0.85f, 0.48f),
            onTertiary = Color.White,
            tertiaryContainer = hsvToColor(tertHue, 0.20f, 0.95f),
            onTertiaryContainer = hsvToColor(tertHue, 0.90f, 0.18f),

            background = hsvToColor(bgHue, 0.05f, 0.98f),
            onBackground = Color(0xFF0F172A),
            surface = Color.White,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = hsvToColor(bgHue, 0.10f, 0.92f),
            onSurfaceVariant = Color(0xFF475569),
            surfaceTint = hsvToColor(hue, sat * 0.95f, 0.52f),
            inverseSurface = Color(0xFF1E293B),
            inverseOnSurface = Color(0xFFF8FAFC),

            outline = Color(0xFF94A3B8),
            outlineVariant = Color(0xFFE2E8F0),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002)
        )
    }
}

/**
 * Returns a complete dynamic ColorScheme on any device.
 * Uses Android 12+ system dynamic color extraction if available,
 * otherwise falls back seamlessly to the universal algorithmic tonal generator.
 */
fun getUniversalDynamicColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val systemDynamic = runCatching {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrNull()
        if (systemDynamic != null) {
            return systemDynamic
        }
    }
    val wallpaperSeed = extractWallpaperSeedColor(context)
    return generateDynamicTonalColorScheme(seed = wallpaperSeed, isDark = darkTheme)
}

@Composable
fun PulsePlayTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = if (dynamicColor) {
        getUniversalDynamicColorScheme(context, darkTheme)
    } else {
        generateDynamicTonalColorScheme(seed = Color(0xFF7C3AED), isDark = darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    PulsePlayTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        content = content
    )
}
