package com.example.mobilecapstone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF21145A),

    secondary = LavenderSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1ECFF),
    onSecondaryContainer = Color(0xFF2E235F),

    tertiary = LavenderTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4EFFF),
    onTertiaryContainer = Color(0xFF331B62),

    background = WhiteBackground,
    onBackground = Color(0xFF1B1630),
    surface = WhiteSurface,
    onSurface = Color(0xFF1B1630),
    surfaceVariant = WhiteSurfaceVariant,
    onSurfaceVariant = Color(0xFF625B7B),
    outline = WhiteOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = LavenderPrimaryDark,
    onPrimary = Color(0xFF2C1E74),
    primaryContainer = Color(0xFF3E3190),
    onPrimaryContainer = Color(0xFFF1EDFF),

    secondary = LavenderSecondaryDark,
    onSecondary = Color(0xFF34266D),
    secondaryContainer = Color(0xFF4A3B87),
    onSecondaryContainer = Color(0xFFF1EDFF),

    tertiary = LavenderTertiaryDark,
    onTertiary = Color(0xFF39226B),
    tertiaryContainer = Color(0xFF51378B),
    onTertiaryContainer = Color(0xFFF3EEFF),

    background = DarkBackground,
    onBackground = Color(0xFFE8E2FF),
    surface = DarkSurface,
    onSurface = Color(0xFFE8E2FF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC3E3),
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
