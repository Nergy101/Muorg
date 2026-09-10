package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The small primary-coloured caption above each block of settings. */
@Composable
fun SectionHeader(title: String) {
Text(
    text = title.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp, end = 16.dp),
)
}

/** "1h 20m" for a library's total running time. */
fun formatDuration(secs: Long): String {
val hours = secs / 3600
val minutes = (secs % 3600) / 60
return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
