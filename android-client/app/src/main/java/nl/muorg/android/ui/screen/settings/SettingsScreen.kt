package nl.muorg.android.ui.screen.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.icon.mageIconRes

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMetadataScanSheet by remember { mutableStateOf(false) }

    // Re-read the offline counts every time the screen resumes, so downloads
    // made elsewhere show up without reopening settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshOfflineStats()
        }
    }

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
            // Opaque: the route is transparent otherwise, and while a back
            // gesture is in flight the home screen shows through it.
            .background(MaterialTheme.colorScheme.background)
            // Before verticalScroll, so the inset shrinks the viewport instead
            // of scrolling away — there is no app bar here to sit under the
            // status bar, so the first section would collide with the clock.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalBottomInset.current),
    ) {
        // Image, not Icon: the logo carries its own colours and Icon would
        // tint it to a single-colour silhouette.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_muorg_logo),
                contentDescription = "Muorg",
                modifier = Modifier.size(56.dp),
            )
        }

        // Reports and statistics sit above the collapsible sections, like on
        // the web client's settings page.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onOpenReports, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.AutoMirrored.Filled.FactCheck,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Reports")
            }
            Button(onClick = onOpenStats, modifier = Modifier.weight(1f)) {
                Icon(
                    painter = painterResource(mageIconRes("chart-up")),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Statistics")
            }
        }

        // Sections collapse like the web client's settings page; only the
        // version/info section starts expanded.
        CollapsibleSection("Muorg Info", defaultOpen = true) {
            InfoSection(uiState, viewModel)
        }

        CollapsibleSection("Playback", icon = "play") {
            PlaybackSection(uiState, viewModel)
        }

        CollapsibleSection("Library", icon = "music") {
            LibrarySection(uiState, viewModel)
        }

        CollapsibleSection("Metadata writing", icon = "edit") {
            WriteSection(uiState, viewModel)
        }

        CollapsibleSection("Offline", icon = "download") {
            OfflineSection(uiState, viewModel)
        }

        CollapsibleSection("Theme", icon = "color-swatch") {
            ThemeSection(uiState, viewModel)
        }

        CollapsibleSection("Music source", icon = "server") {
            MusicSourceSection(uiState, viewModel)
        }

        // The two sources configure completely different things, so each owns
        // its own section rather than one branching on the mode throughout.
        if (uiState.sourceMode == SourceMode.ONLINE_SERVER) {
            CollapsibleSection("Server", icon = "server") {
                ServerSection(uiState, viewModel)
            }
        } else {
            CollapsibleSection("Local folders", icon = "library-music") {
                LocalFoldersSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onAddFolder = { folderPickerLauncher.launch(null) },
                )
            }
        }

        CollapsibleSection("Library tools", icon = "wrench") {
            LibraryToolsSection(
                onMetadataScan = { showMetadataScanSheet = true },
            )
        }

        CollapsibleSection("Development", icon = "wrench") {
            DevelopmentSection(uiState, viewModel)
        }

    }

    if (showMetadataScanSheet) {
        MetadataScanSheet(onDismiss = { showMetadataScanSheet = false })
    }

    if (uiState.showClearOfflineDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearOffline,
            title = { Text("Clear offline cache?") },
            text = { Text("Every downloaded file is deleted, including manual playlist and album downloads.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearOfflineCache() }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearOffline) { Text("Cancel") }
            },
        )
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


