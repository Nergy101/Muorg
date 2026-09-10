package nl.muorg.android.ui.screen.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.api.SmartRule
import nl.muorg.android.ui.icon.mageIconRes

/**
 * The rule editor for a smart playlist.
 *
 * Split out of [PlaylistsScreen], which held six composables in one file.
 */

/**
 * Rule editor for a smart playlist, mirroring `SmartPlaylistDialog.vue`: a
 * name plus a list of `field / operator / value` rows serialised straight into
 * the server's `rules_json`.
 */
@Composable
internal fun SmartPlaylistDialog(
    name: String,
    rules: List<SmartRule>,
    saving: Boolean,
    onNameChange: (String) -> Unit,
    onRuleChange: (Int, SmartRule) -> Unit,
    onAddRule: () -> Unit,
    onRemoveRule: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(mageIconRes("zap")),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Smart Playlist")
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Tracks matching every rule are included, and the playlist re-evaluates itself as the library changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                rules.forEachIndexed { index, rule ->
                    SmartRuleRow(
                        rule = rule,
                        canRemove = rules.size > 1,
                        onChange = { onRuleChange(index, it) },
                        onRemove = { onRemoveRule(index) },
                    )
                }
                TextButton(onClick = onAddRule) {
                    Icon(
                        painter = painterResource(mageIconRes("plus")),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add rule")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = saving.not() && name.isNotBlank() && rules.any { it.value.isNotBlank() },
            ) {
                Text(if (saving) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun SmartRuleRow(
    rule: SmartRule,
    canRemove: Boolean,
    onChange: (SmartRule) -> Unit,
    onRemove: () -> Unit,
) {
    var fieldOpen by remember { mutableStateOf(false) }
    var opOpen by remember { mutableStateOf(false) }
    val ops = opsForField(rule.field)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { fieldOpen = true }) {
                Text(SMART_FIELDS.first { it.first == rule.field }.second)
                Icon(
                    painter = painterResource(mageIconRes("chevron-down")),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(expanded = fieldOpen, onDismissRequest = { fieldOpen = false }) {
                SMART_FIELDS.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            fieldOpen = false
                            // Operators differ per field type, so reset to a valid one.
                            onChange(rule.copy(field = value, op = opsForField(value).first().first))
                        },
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { opOpen = true }) {
                Text(ops.first { it.first == rule.op }.second)
            }
            DropdownMenu(expanded = opOpen, onDismissRequest = { opOpen = false }) {
                ops.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { opOpen = false; onChange(rule.copy(op = value)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = rule.value,
            onValueChange = { onChange(rule.copy(value = it)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(mageIconRes("multiply")),
                    contentDescription = "Remove rule",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
