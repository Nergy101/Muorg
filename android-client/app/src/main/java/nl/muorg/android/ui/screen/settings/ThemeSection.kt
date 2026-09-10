package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.icon.mageIconRes

/**
 * Light/dark mode and the accent colour.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun ThemeSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()
    SectionHeader("Theme")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val themeModes = listOf("dark", "light", "auto")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            themeModes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                    icon = {},
                ) {
                    Text(mode.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    ListItem(
        headlineContent = { Text("Use true black") },
        supportingContent = { Text("OLED-friendly pitch-black background") },
        leadingContent = {
            Icon(Icons.Filled.Contrast, contentDescription = null,
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

}
