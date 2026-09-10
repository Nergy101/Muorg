package nl.muorg.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Local files or a remote server — the choice everything else follows from.
 *
 * One section of [SettingsScreen], which was a single 640-line composable.
 */
@Composable
fun MusicSourceSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    HorizontalDivider()

    Text(
        "MUSIC SOURCE",
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val onlineSelected = uiState.sourceMode == SourceMode.ONLINE_SERVER
        val localSelected = uiState.sourceMode == SourceMode.LOCAL_LIBRARY

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (onlineSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else Color.Transparent
                )
                .then(
                    if (onlineSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
                .clickable { viewModel.requestSwitchSourceMode(SourceMode.ONLINE_SERVER) }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (onlineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Online server",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (onlineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "muorg-server",
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (onlineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.85f),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (localSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else Color.Transparent
                )
                .then(
                    if (localSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
                .clickable { viewModel.requestSwitchSourceMode(SourceMode.LOCAL_LIBRARY) }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (localSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Local folders",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (localSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "on this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (localSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.85f),
                )
            }
        }
    }

    Text(
        "Pick one. Local playlists are always preserved.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

}
