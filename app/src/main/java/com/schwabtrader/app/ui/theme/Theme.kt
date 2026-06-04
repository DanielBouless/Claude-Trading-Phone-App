package com.schwabtrader.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextPrimary,
    primaryContainer = AccentBlueDark,
    onPrimaryContainer = TextPrimary,
    secondary = AccentTeal,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003543),
    onSecondaryContainer = AccentTeal,
    tertiary = GainGreen,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF002204),
    onTertiaryContainer = GainGreen,
    error = LossRed,
    onError = TextPrimary,
    errorContainer = LossRedDark,
    onErrorContainer = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextHint,
    outlineVariant = DividerColor,
    scrim = Color(0xFF000000),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = AccentBlueDark,
    surfaceTint = AccentBlue
)

@Composable
fun SchwabTraderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
