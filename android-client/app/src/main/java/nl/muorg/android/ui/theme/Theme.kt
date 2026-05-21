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

@Composable
fun MuorgTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MuorgDarkColorScheme,
        typography = MuorgTypography,
        content = content
    )
}
