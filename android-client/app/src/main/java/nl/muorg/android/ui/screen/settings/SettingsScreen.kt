package nl.muorg.android.ui.screen.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.muorg.android.ui.component.LocalBottomInset

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMetadataScanSheet by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.addFolder(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Before verticalScroll, so the inset shrinks the viewport instead
            // of scrolling away — there is no app bar here to sit under the
            // status bar, so the first section would collide with the clock.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalBottomInset.current),
    ) {
        InfoSection(uiState)

        PlaybackSection(uiState, viewModel)

        LibrarySection(uiState, viewModel)

        ThemeSection(uiState, viewModel)

        MusicSourceSection(uiState, viewModel)

        // The two sources configure completely different things, so each owns
        // its own section rather than one branching on the mode throughout.
        if (uiState.sourceMode == SourceMode.ONLINE_SERVER) {
            ServerSection(uiState, viewModel)
        } else {
            LocalFoldersSection(
                uiState = uiState,
                viewModel = viewModel,
                onAddFolder = { folderPickerLauncher.launch(null) },
            )
        }

        LibraryToolsSection(onMetadataScan = { showMetadataScanSheet = true })

    }

    if (showMetadataScanSheet) {
        MetadataScanSheet(onDismiss = { showMetadataScanSheet = false })
    }

    if (uiState.showSwitchConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSwitchDialog,
            title = { Text("Switch music source?") },
            text = { Text("Switching wipes the current library cache. Continue?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSwitchSourceMode(onLoggedOut) }) {
                    Text("Switch", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSwitchDialog) { Text("Cancel") }
            },
        )
    }

    if (uiState.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLogoutDialog,
            title = { Text("Log out?") },
            text = { Text("This will clear your server URL and API key. You'll need to reconnect.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.logout(onLoggedOut) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogoutDialog) {
                    Text("Cancel")
                }
            },
        )
    }
}


