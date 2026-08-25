package com.firmlyplanted.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF2E5339)
private val ForestLight = Color(0xFF4C7A5C)
private val Gold = Color(0xFFC9A24B)

private val LightColors = lightColorScheme(
    primary = Forest,
    secondary = Gold,
    tertiary = ForestLight,
)

private val DarkColors = darkColorScheme(
    primary = ForestLight,
    secondary = Gold,
    tertiary = Forest,
)

@Composable
fun FirmlyPlantedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
