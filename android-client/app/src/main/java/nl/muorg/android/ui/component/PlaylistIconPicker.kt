package nl.muorg.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.icon.LUCIDE_ICON_NAMES
import nl.muorg.android.ui.icon.LucideIcon
import nl.muorg.android.ui.icon.PlaylistIconDefaults
import nl.muorg.android.ui.icon.formatPlaylistIcon
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.icon.resolvePlaylistIcon

// Cap the unfiltered grid: rendering all ~2.4k icons at once is janky, and a
// search narrows the list anyway.
private const val MAX_VISIBLE = 400

/**
 * Searchable full-lucide icon grid with a color swatch row underneath.
 * `value`/`onChange` carry the stored `"<lucide-name>:#RRGGBB"` string.
 */
@Composable
fun PlaylistIconPicker(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val current = resolvePlaylistIcon(value)

    val filtered = remember(query) {
        val q = query.trim().lowercase()
        val matches = if (q.isEmpty()) {
            LUCIDE_ICON_NAMES
        } else {
            LUCIDE_ICON_NAMES.filter { it.contains(q) }
        }
        if (matches.size > MAX_VISIBLE) matches.take(MAX_VISIBLE) else matches
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search icons") },
            leadingIcon = {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(mageIconRes("search")),
                    contentDescription = null,
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(filtered, key = { it }) { name ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (name == current.name)
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onChange(formatPlaylistIcon(name, current.colorHex)) },
                    contentAlignment = Alignment.Center,
                ) {
                    LucideIcon(name = name, tint = current.color, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (filtered.isEmpty()) {
            Text(
                text = "No icons match \"$query\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (colorHex in PlaylistIconDefaults.COLORS) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(colorHex)))
                        .clickable { onChange(formatPlaylistIcon(current.name, colorHex)) },
                )
            }
        }
    }
}
