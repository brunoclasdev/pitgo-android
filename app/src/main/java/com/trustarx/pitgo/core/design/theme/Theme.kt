package com.trustarx.pitgo.core.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = White,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = Black,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    onSurfaceVariant = DarkGray,
    outline = OutlineLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Black,
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = White,
    background = Black,
    onBackground = White,
    surface = NearBlack,
    onSurface = White,
    onSurfaceVariant = LightGray,
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
