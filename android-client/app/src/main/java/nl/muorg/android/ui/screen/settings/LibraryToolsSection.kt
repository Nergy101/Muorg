package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One-off jobs that act on the whole library.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun LibraryToolsSection(
    onMetadataScan: () -> Unit,
) {
    HorizontalDivider()
    SectionHeader("Library tools")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Button(onClick = { onMetadataScan() }) {
            Icon(Icons.Filled.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Metadata scan")
        }
    }

}
