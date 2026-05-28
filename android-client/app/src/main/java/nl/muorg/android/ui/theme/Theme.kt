package nl.muorg.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MuorgDarkColorScheme = darkColorScheme(
    primary = MuorgGreen,
    onPrimary = Stone100,
    primaryContainer = MuorgGreenDark,
    onPrimaryContainer = MuorgGreenLight,
    secondary = MuorgGreenLight,
    onSecondary = Stone900,
    secondaryContainer = Stone700,
    onSecondaryContainer = Stone200,
    tertiary = Stone400,
    onTertiary = Stone900,
    background = Stone900,
    onBackground = Stone200,
    surface = Stone800,
    onSurface = Stone200,
    surfaceVariant = Stone700,
    onSurfaceVariant = Stone400,
    outline = Stone600,
    error = ErrorRed,
    onError = OnErrorDark,
)

private val MuorgTrueBlackColorScheme = darkColorScheme(
    primary = MuorgGreen,
    onPrimary = Stone100,
    primaryContainer = MuorgGreenDark,
    onPrimaryContainer = MuorgGreenLight,
    secondary = MuorgGreenLight,
    onSecondary = Stone900,
    secondaryContainer = TrueBlackSurfaceVariant,
    onSecondaryContainer = Stone200,
    tertiary = Stone400,
    onTertiary = Stone900,
    background = TrueBlack,
    onBackground = Stone200,
    surface = TrueBlackSurface,
    onSurface = Stone200,
    surfaceVariant = TrueBlackSurfaceVariant,
    onSurfaceVariant = Stone400,
    surfaceContainer = TrueBlackSurfaceContainer,
    outline = Stone600,
    error = ErrorRed,
    onError = OnErrorDark,
)

@Composable
fun MuorgTheme(
    useTrueBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useTrueBlack) MuorgTrueBlackColorScheme else MuorgDarkColorScheme,
        typography = MuorgTypography,
        content = content
    )
}
