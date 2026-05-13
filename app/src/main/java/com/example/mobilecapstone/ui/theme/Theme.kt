package com.example.mobilecapstone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF),
    onPrimaryContainer = Color(0xFF082C55),

    secondary = AppSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F8EC),
    onSecondaryContainer = Color(0xFF123A1E),

    tertiary = AppTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFF1FF),
    onTertiaryContainer = Color(0xFF202765),

    background = WhiteBackground,
    onBackground = Color(0xFF111827),
    surface = WhiteSurface,
    onSurface = Color(0xFF111827),
    surfaceVariant = WhiteSurfaceVariant,
    onSurfaceVariant = Color(0xFF5F6B7A),
    outline = WhiteOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark,
    onPrimary = Color(0xFF06203F),
    primaryContainer = Color(0xFF143B61),
    onPrimaryContainer = Color(0xFFE8F2FF),

    secondary = AppSecondaryDark,
    onSecondary = Color(0xFF0D2E18),
    secondaryContainer = Color(0xFF1F4C2B),
    onSecondaryContainer = Color(0xFFE7F8EC),

    tertiary = AppTertiaryDark,
    onTertiary = Color(0xFF1E2458),
    tertiaryContainer = Color(0xFF333B85),
    onTertiaryContainer = Color(0xFFEFF1FF),

    background = DarkBackground,
    onBackground = Color(0xFFE8EDF5),
    surface = DarkSurface,
    onSurface = Color(0xFFE8EDF5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB7C0CC),
    outline = DarkOutline
)

@Composable
fun MobileCapstoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
