package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
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
import nl.muorg.android.ui.component.PickerSheet
import nl.muorg.android.ui.screen.library.SortMode

/**
 * Scanning, caching and what the library counts.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun LibrarySection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {

    Box {
        ListItem(
            headlineContent = { Text("Default sort order") },
            supportingContent = { Text(uiState.defaultSort.label) },
            leadingContent = {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::showSortDropdown),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }

    ListItem(
        headlineContent = { Text("Sort direction") },
        supportingContent = { Text(if (uiState.sortAscending) "Ascending" else "Descending") },
        leadingContent = {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.sortAscending,
                onCheckedChange = viewModel::setSortAscending,
            )
        },
        modifier = Modifier.clickable { viewModel.setSortAscending(!uiState.sortAscending) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Layout",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("grid", "list", "tracks").forEachIndexed { index, style ->
                SegmentedButton(
                    selected = uiState.albumViewStyle == style,
                    onClick = { viewModel.setAlbumViewStyle(style) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    icon = {},
                ) {
                    Text(style.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    ListItem(
        headlineContent = { Text("Reduce motion") },
        supportingContent = { Text("Keep navigation, skip the slide and fade travel") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("zap")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.reduceMotion,
                onCheckedChange = viewModel::setReduceMotion,
            )
        },
        modifier = Modifier.clickable { viewModel.setReduceMotion(!uiState.reduceMotion) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    ListItem(
        headlineContent = { Text("Open on last tab") },
        supportingContent = { Text("Restore the last visited tab after an app restart") },
        leadingContent = {
            Icon(
                painter = painterResource(mageIconRes("refresh")), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = uiState.openOnLastTab,
                onCheckedChange = viewModel::setOpenOnLastTab,
            )
        },
        modifier = Modifier.clickable { viewModel.setOpenOnLastTab(!uiState.openOnLastTab) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )

    if (uiState.showSortDropdown) {
        PickerSheet(
            title = "Default sort order",
            options = SortMode.entries.toList(),
            isSelected = { it == uiState.defaultSort },
            label = { it.label },
            onSelect = { viewModel.setDefaultSort(it) },
            onDismiss = viewModel::dismissSortDropdown,
        )
    }

}
