package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.icon.MageIcon

/**
 * True black and the Material You accent. The app is dark only.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun ThemeSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()
    ListItem(
        headlineContent = { Text("Use true black") },
        supportingContent = { Text("OLED-friendly pitch-black background") },
        leadingContent = {
            MageIcon("color-swatch-fill", contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.useTrueBlack,
                onCheckedChange = viewModel::setUseTrueBlack,
            )
        },
        modifier = Modifier.clickable { viewModel.setUseTrueBlack(!uiState.useTrueBlack) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Material You") },
        supportingContent = { Text("Use your device's dynamic color theme") },
        leadingContent = {
            Icon(
                    painter = painterResource(mageIconRes("color-swatch")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.materialYou,
                onCheckedChange = viewModel::setMaterialYou,
            )
        },
        modifier = Modifier.clickable { viewModel.setMaterialYou(!uiState.materialYou) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    // Material You normally yields to the colours taken from the playing
    // album; this makes it the other way round.
    if (uiState.materialYou) {
        ListItem(
            headlineContent = { Text("Override dynamic colors") },
            supportingContent = { Text("Use Material You everywhere, including the player sheet, instead of colors taken from album covers") },
            leadingContent = {
                Icon(
                    painter = painterResource(mageIconRes("color-swatch")), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = uiState.materialYouOverride,
                    onCheckedChange = viewModel::setMaterialYouOverride,
                )
            },
            modifier = Modifier.clickable { viewModel.setMaterialYouOverride(!uiState.materialYouOverride) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }

}
