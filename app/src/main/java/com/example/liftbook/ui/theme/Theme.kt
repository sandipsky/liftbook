package com.example.liftbook.ui.theme

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

// Secondary and tertiary are deliberately neutral: the palette has exactly one accent.
// Selection states (chips, radio buttons) use ink, not the accent — see ChoiceChip.

private val LightColors = lightColorScheme(
    primary = Ember700,
    onPrimary = Graphite0,
    primaryContainer = Ember100,
    onPrimaryContainer = Ember950,
    inversePrimary = Ember300,
    secondary = Graphite600,
    onSecondary = Graphite0,
    secondaryContainer = Graphite150,
    onSecondaryContainer = Graphite900,
    tertiary = Graphite600,
    onTertiary = Graphite0,
    tertiaryContainer = Graphite150,
    onTertiaryContainer = Graphite900,
    background = Graphite25,
    onBackground = Graphite900,
    surface = Graphite25,
    onSurface = Graphite900,
    surfaceVariant = Graphite150,
    onSurfaceVariant = Graphite600,
    surfaceTint = Graphite600,
    inverseSurface = Graphite800,
    inverseOnSurface = Graphite50,
    error = Red600,
    onError = Graphite0,
    errorContainer = Red100,
    onErrorContainer = Red900,
    outline = Graphite500,
    outlineVariant = Graphite300,
    scrim = Color.Black,
    surfaceBright = Graphite25,
    surfaceDim = Graphite250,
    surfaceContainerLowest = Graphite0,
    surfaceContainerLow = Graphite75,
    surfaceContainer = Graphite100,
    surfaceContainerHigh = Graphite150,
    surfaceContainerHighest = Graphite200,
)

private val DarkColors = darkColorScheme(
    primary = Ember400,
    onPrimary = Ember950,
    primaryContainer = Ember800,
    onPrimaryContainer = Ember100,
    inversePrimary = Ember700,
    secondary = Graphite400,
    onSecondary = Graphite800,
    secondaryContainer = Graphite750,
    onSecondaryContainer = Graphite150,
    tertiary = Graphite400,
    onTertiary = Graphite800,
    tertiaryContainer = Graphite750,
    onTertiaryContainer = Graphite150,
    background = Graphite950,
    onBackground = Graphite150,
    surface = Graphite950,
    onSurface = Graphite150,
    surfaceVariant = Graphite750,
    onSurfaceVariant = Graphite400,
    surfaceTint = Graphite400,
    inverseSurface = Graphite150,
    inverseOnSurface = Graphite850,
    error = Red300,
    onError = Red800,
    errorContainer = Red700,
    onErrorContainer = Red200,
    outline = Graphite500,
    outlineVariant = Graphite700,
    scrim = Color.Black,
    surfaceBright = Graphite700,
    surfaceDim = Graphite950,
    surfaceContainerLowest = Graphite1000,
    surfaceContainerLow = Graphite900,
    surfaceContainer = Graphite850,
    surfaceContainerHigh = Graphite800,
    surfaceContainerHighest = Graphite750,
)

/**
 * LiftBook's theme. Dynamic colour is off by default so the app keeps its own identity;
 * light and dark are both first-class (NFR-5).
 */
@Composable
fun LiftBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
