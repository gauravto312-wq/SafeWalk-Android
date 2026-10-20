package com.example.safewalk.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = SafeWalkRedLight,
    onPrimary = Color.Black,
    primaryContainer = SafeWalkRedDark,
    onPrimaryContainer = Color.White,
    secondary = SafeWalkBlueLight,
    onSecondary = Color.Black,
    tertiary = SafeWalkAmberLight,
    background = SafeWalkBackgroundDark,
    surface = SafeWalkSurfaceDark,
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1),
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = SafeWalkRedPrimary,
    onPrimary = SafeWalkOnPrimaryLight,
    primaryContainer = Color(0xFFFFEBEE),
    onPrimaryContainer = SafeWalkRedDark,
    secondary = SafeWalkBlueSecondary,
    onSecondary = Color.White,
    tertiary = SafeWalkAmberAlert,
    background = SafeWalkBackgroundLight,
    surface = SafeWalkSurfaceLight,
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    error = Color(0xFFB00020)
)

@Composable
fun SafewalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep emergency branding consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}