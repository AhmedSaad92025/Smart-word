package com.smartword.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1F5FBF)
private val BlueDark = Color(0xFF9DBDFF)

private val LightColors = lightColorScheme(
    primary = Blue,
    secondary = Color(0xFF2B7A78),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    secondary = Color(0xFF7FD1CE),
    background = Color(0xFF101319),
    surface = Color(0xFF181C24),
)

@Composable
fun SmartWordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
