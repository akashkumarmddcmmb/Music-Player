package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PulseDarkColorScheme = darkColorScheme(
    primary = StudioAmber,
    onPrimary = Color(0xFF1A1100),
    primaryContainer = StudioAmberContainer,
    onPrimaryContainer = OnStudioAmberContainer,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF002024),
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = OnElectricCyanContainer,
    tertiary = CoralRose,
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF334155)
)

private val PulseLightColorScheme = lightColorScheme(
    primary = StudioAmberDark,
    onPrimary = Color.White,
    primaryContainer = StudioAmberLightContainer,
    onPrimaryContainer = OnStudioAmberLightContainer,
    secondary = DeepCyan,
    onSecondary = Color.White,
    secondaryContainer = DeepCyanContainer,
    onSecondaryContainer = OnDeepCyanContainer,
    tertiary = CoralRose,
    background = IvoryBackground,
    onBackground = TextPrimaryLight,
    surface = IvorySurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD5E1)
)

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PulseDarkColorScheme
        else -> PulseLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
