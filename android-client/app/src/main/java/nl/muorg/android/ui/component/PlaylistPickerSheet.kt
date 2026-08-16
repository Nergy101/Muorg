package nl.muorg.android.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import nl.muorg.android.ui.icon.mageIconRes
import androidx.compose.ui.unit.dp
import nl.muorg.android.data.api.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    playlists: List<Playlist>,
    membershipIds: Set<Int>,
    partialMembershipIds: Set<Int> = emptySet(),
    onAdd: (Playlist) -> Unit,
    onRemove: (Playlist) -> Unit,
    onCreatePlaylist: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localMembershipIds by remember(membershipIds) { mutableStateOf(membershipIds) }
    var localPartialIds by remember(partialMembershipIds) { mutableStateOf(partialMembershipIds) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Add to playlist",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        )

        if (playlists.isEmpty() && onCreatePlaylist == null) {
            Text(
                text = "No playlists yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    val isFull = playlist.id in localMembershipIds
                    val isPartial = !isFull && playlist.id in localPartialIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isFull) {
                                    localMembershipIds = localMembershipIds - playlist.id
                                    onRemove(playlist)
                                } else {
                                    localMembershipIds = localMembershipIds + playlist.id
                                    localPartialIds = localPartialIds - playlist.id
                                    onAdd(playlist)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${playlist.icon ?: "🎵"}  ${playlist.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            color = when {
                                isFull -> MaterialTheme.colorScheme.primary
                                isPartial -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        when {
                            isFull -> Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "In playlist — tap to remove",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            isPartial -> Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Partially added — tap to complete",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp),
                            )
                            else -> Icon(
                                imageVector = Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "Not in playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        if (onCreatePlaylist != null) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            if (!showCreate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreate = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(mageIconRes("plus")),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "New playlist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showCreate = false; newName = "" }) { Text("Cancel") }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onCreatePlaylist(newName.trim())
                                    showCreate = false
                                    newName = ""
                                }
                            },
                            enabled = newName.isNotBlank(),
                        ) { Text("Create") }
                    }
                }
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}
