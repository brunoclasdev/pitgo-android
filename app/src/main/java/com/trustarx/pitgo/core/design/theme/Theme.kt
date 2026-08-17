package com.trustarx.pitgo.core.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = White,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = DarkPurple,
    secondary = DarkPurple,
    onSecondary = White,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = PurpleContainerLight,
    onSurfaceVariant = TextSecondary,
    outline = OutlineLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurple,
    onPrimary = White,
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = DarkTextPrimary,
    secondary = BrandPurple,
    onSecondary = White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = OutlineDark,
)

@Composable
fun PitGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = PitGoTypography,
        shapes = PitGoShapes,
        content = content,
    )
}
