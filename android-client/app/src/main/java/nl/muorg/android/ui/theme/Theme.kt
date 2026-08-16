package nl.muorg.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

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

private val MuorgLightColorScheme = lightColorScheme(
    primary = MuorgGreenOnLight,
    onPrimary = Color.White,
    primaryContainer = MuorgGreenContainerLight,
    onPrimaryContainer = MuorgGreenOnContainer,
    secondary = MuorgGreenOnLight,
    onSecondary = Stone50,
    secondaryContainer = Stone300,
    onSecondaryContainer = Stone900,
    tertiary = Stone600,
    onTertiary = Color.White,
    background = Stone50,
    onBackground = Stone900,
    surface = Stone150,
    onSurface = Stone900,
    surfaceVariant = Stone300,
    onSurfaceVariant = Stone700,
    surfaceContainer = Stone150,
    outline = Stone400,
    error = ErrorRedLight,
    onError = Color.White,
    errorContainer = ErrorContainerL,
    onErrorContainer = OnErrorContainerL,
)

@Composable
fun MuorgTheme(
    themeMode: String = "dark",
    useTrueBlack: Boolean = false,
    useMaterialYou: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "light" -> false
        "dark"  -> true
        else    -> systemInDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        !isDark      -> MuorgLightColorScheme
        useTrueBlack -> MuorgTrueBlackColorScheme
        else         -> MuorgDarkColorScheme
    }

    val window = (context as? Activity)?.window
    if (window != null) {
        SideEffect {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MuorgTypography,
        shapes = MuorgMaterialShapes,
        content = content,
    )
}
