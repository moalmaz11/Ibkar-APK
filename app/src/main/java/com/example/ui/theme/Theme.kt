package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BlueAccentLight,
    secondary = BlueAccentLight,
    tertiary = SophisticatedGradientStart,
    background = SophisticatedDarkBg,
    surface = SophisticatedDarkSurface,
    onPrimary = BlueAccentDarkText,
    onSecondary = BlueAccentDarkText,
    onTertiary = OffWhiteText,
    onBackground = OffWhiteText,
    onSurface = OffWhiteText,
    surfaceVariant = SophisticatedDarkSurfaceVariant,
    onSurfaceVariant = OffWhiteText
)

private val LightColorScheme = lightColorScheme(
    primary = SophisticatedGradientStart,
    secondary = SophisticatedGradientStart,
    tertiary = BlueAccentLight,
    background = SophisticatedLightBg,
    surface = SophisticatedLightSurface,
    onPrimary = SophisticatedLightSurface,
    onSecondary = SophisticatedLightSurface,
    onTertiary = SophisticatedLightText,
    onBackground = SophisticatedLightText,
    onSurface = SophisticatedLightText,
    surfaceVariant = SophisticatedLightSurfaceVariant,
    onSurfaceVariant = SophisticatedLightText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color customizable
    dynamicColor: Boolean = false, // Set to false to enforce our elegant spiritual colors by default!
    content: @Composable () -> Unit,
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
