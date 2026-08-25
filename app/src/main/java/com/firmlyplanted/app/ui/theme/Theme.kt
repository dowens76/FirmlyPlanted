package com.firmlyplanted.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Role assignments below are restricted to pairs that measured >=4.5:1 WCAG contrast (the AA
 * threshold for body text) using the brand palette in Color.kt. Two colors in the palette —
 * Leaf Green and River Blue — don't reach 4.5:1 against any other palette color light or dark
 * enough to read as body text on them, so their "on*" pairs use plain black, which does clear
 * 4.5:1 against both (7.36:1 and 4.70:1 respectively). Every other on-color below is a palette
 * color. error/onError intentionally use Material3's default red — the brand palette has no
 * warning/danger color, and danger=red is too strong a convention to override with an
 * off-palette substitute.
 */

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Cream,
    primaryContainer = Sage,
    onPrimaryContainer = ForestGreen,
    secondary = LeafGreen,
    onSecondary = Color.Black,
    secondaryContainer = Sand,
    onSecondaryContainer = ForestGreen,
    tertiary = RiverBlue,
    onTertiary = Color.Black,
    tertiaryContainer = SkyBlue,
    onTertiaryContainer = ForestGreen,
    background = Cream,
    onBackground = ForestGreen,
    surface = Cream,
    onSurface = ForestGreen,
    surfaceVariant = Sand,
    onSurfaceVariant = ForestGreen,
    outline = EarthBrown,
)

private val DarkColors = darkColorScheme(
    primary = Sage,
    onPrimary = ForestGreen,
    primaryContainer = ForestGreen,
    onPrimaryContainer = Cream,
    secondary = SkyBlue,
    onSecondary = ForestGreen,
    secondaryContainer = RiverBlue,
    onSecondaryContainer = Color.Black,
    tertiary = Sand,
    onTertiary = ForestGreen,
    tertiaryContainer = EarthBrown,
    onTertiaryContainer = Cream,
    background = ForestGreen,
    onBackground = Cream,
    surface = ForestGreen,
    onSurface = Cream,
    surfaceVariant = EarthBrown,
    onSurfaceVariant = Cream,
    outline = Sand,
)

@Composable
fun FirmlyPlantedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
