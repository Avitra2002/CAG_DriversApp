package com.roaddefect.driverapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    secondary = AppColors.Secondary,
    tertiary = AppColors.Tertiary,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onPrimary = AppColors.Light,
    onSecondary = AppColors.Light,
    onTertiary = AppColors.Light,
    onBackground = AppColors.Light,
    onSurface = AppColors.Light,
    error = AppColors.Error,
    onError = AppColors.Light
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
