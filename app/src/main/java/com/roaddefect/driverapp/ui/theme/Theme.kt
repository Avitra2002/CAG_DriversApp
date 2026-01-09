package com.roaddefect.driverapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF10B981), // Green
    secondary = Color(0xFF3B82F6), // Blue
    tertiary = Color(0xFFA855F7), // Purple
    background = Color(0xFF0F172A), // Slate 950
    surface = Color(0xFF1E293B), // Slate 900
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFDC2626), // Red
    onError = Color.White
)

@Composable
fun DriverAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
