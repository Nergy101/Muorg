package nl.muorg.android.ui.screen.welcome

import nl.muorg.android.ui.component.OrcLoading
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nl.muorg.android.ui.component.BobbingLogo
import nl.muorg.android.ui.component.ChoiceCard
import nl.muorg.android.ui.component.OnboardingBackButton
import nl.muorg.android.ui.component.OnboardingHeadline
import nl.muorg.android.ui.component.OnboardingPage
import nl.muorg.android.ui.component.OnboardingScreen
import nl.muorg.android.ui.component.OrcMotion
import nl.muorg.android.ui.component.OrcPortrait
import nl.muorg.android.ui.component.OrcStage
import nl.muorg.android.ui.component.OrcVariant
import nl.muorg.android.ui.component.PrimaryButton
import nl.muorg.android.ui.component.drawDashedOutline
import nl.muorg.android.ui.component.drawOnboardingRings
import nl.muorg.android.ui.component.entrance
import nl.muorg.android.ui.component.focusRing
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.theme.OnboardingBrand
import nl.muorg.android.ui.theme.OnboardingType

@Composable
fun WelcomeScreen(
    onNavigateToLibrary: () -> Unit,
    onNavigateToRemoteSetup: () -> Unit,
    onNavigateToScanning: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            is WelcomeDestination.Library -> {
                viewModel.consumeDestination()
                onNavigateToLibrary()
            }
            is WelcomeDestination.RemoteSetup -> {
                viewModel.consumeDestination()
                onNavigateToRemoteSetup()
            }
            is WelcomeDestination.Scanning -> {
                viewModel.consumeDestination()
                onNavigateToScanning()
            }
            null -> Unit
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.addFolder(it.toString())
        }
    }

    when (uiState.step) {
        WelcomeStep.CHECKING -> {
            OnboardingScreen {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OrcLoading()
                }
            }
        }

        WelcomeStep.MODE_SELECT -> {
            var showTitle by remember { mutableStateOf(false) }
            var showSubtitle by remember { mutableStateOf(false) }
            var showCard1 by remember { mutableStateOf(false) }
            var showCard2 by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(80)
                showTitle = true
                delay(140)
                showSubtitle = true
                delay(180)
                showCard1 = true
                delay(100)
                showCard2 = true
            }

            OnboardingScreen {
                OnboardingPage(
                    top = 60.dp,
                    decoration = { contentTop ->
                        drawOnboardingRings(Offset(size.width / 2f, contentTop + 100.dp.toPx()))
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().entrance(showTitle, durationMillis = 400, slideFraction = 1f / 3f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(124.dp)) {
                            BobbingLogo(Modifier.align(Alignment.TopCenter))
                            SpeechBubble()
                        }
                        Spacer(Modifier.height(20.dp))
                        OnboardingHeadline("Feed me some music.", OnboardingType.Headline34)
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Where does your music live?",
                        style = OnboardingType.Body17,
                        color = OnboardingBrand.Mist,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.entrance(showSubtitle, durationMillis = 400, slideFraction = 1f / 3f),
                    )

                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(24.dp))

                    ChoiceCard(
                        title = "Muorg Server",
                        subtitle = "Stream from your self-hosted server",
                        accent = OnboardingBrand.Spotlight,
                        stageColor = OnboardingBrand.ServerStage,
                        orc = OrcVariant.ServerTinker,
                        onClick = viewModel::chooseRemote,
                        modifier = Modifier.entrance(showCard1, durationMillis = 500, slideFraction = 0.5f),
                    )

                    Spacer(Modifier.height(16.dp))

                    ChoiceCard(
                        title = "Local Library",
                        subtitle = "Play music from folders on this device",
                        accent = OnboardingBrand.Moss,
                        stageColor = OnboardingBrand.LocalStage,
                        orc = OrcVariant.RecordSnack,
                        hopDelayMillis = 800,
                        onClick = viewModel::chooseLocal,
                        modifier = Modifier.entrance(showCard2, durationMillis = 500, slideFraction = 0.5f),
                    )

                    // 16dp column gap + 10dp margin in the canvas.
                    Spacer(Modifier.height(26.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MageIcon(
                            "lock",
                            Modifier.padding(top = 1.dp).size(16.dp),
                            tint = OnboardingBrand.Mist,
                        )
                        Text(
                            text = "Settings stay on this device and don’t sync through the server.",
                            style = OnboardingType.Body13,
                            color = OnboardingBrand.Mist,
                        )
                    }
                }
            }
        }

        WelcomeStep.LOCAL_SETUP -> {
            var pendingFolder by remember { mutableStateOf<String?>(null) }
            val folders = uiState.localFolderUris

            OnboardingScreen {
                OnboardingPage(top = 56.dp) {
                    OnboardingBackButton(
                        onClick = viewModel::goBackToModeSelect,
                        modifier = Modifier.align(Alignment.Start),
                    )

                    OrcStage(OnboardingBrand.LocalStage, Modifier.size(176.dp)) {
                        Crossfade(
                            targetState = folders.isNotEmpty(),
                            animationSpec = tween(250),
                            label = "localOrc",
                        ) { hasFolders ->
                            if (hasFolders) {
                                OrcPortrait(
                                    OrcVariant.RecordSnack,
                                    OrcVariant.RecordSnack.description,
                                    motion = OrcMotion.Hop(),
                                )
                            } else {
                                OrcPortrait(OrcVariant.Empty, OrcVariant.Empty.description)
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    OnboardingHeadline(
                        text = if (folders.isNotEmpty()) "Which folders should I raid?" else "My crate is empty…",
                        style = OnboardingType.Headline30,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Pick the folders on this device where your music lives.",
                        style = OnboardingType.Body15,
                        color = OnboardingBrand.Mist,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(22.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 218.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        folders.forEach { uriString ->
                            FolderRow(
                                display = folderDisplay(context, uriString),
                                onRemove = { pendingFolder = uriString },
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    AddFolderButton(onClick = { folderPickerLauncher.launch(null) })

                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(16.dp))

                    PrimaryButton(
                        text = when (folders.size) {
                            0 -> "Add a folder first"
                            1 -> "Let’s eat · 1 folder"
                            else -> "Let’s eat · ${folders.size} folders"
                        },
                        enabled = folders.isNotEmpty(),
                        onClick = viewModel::finishLocalSetup,
                    )
                }

                pendingFolder?.let { uriString ->
                    val label = Uri.parse(uriString).lastPathSegment
                        ?.substringAfterLast(':')
                        ?: uriString
                    AlertDialog(
                        onDismissRequest = { pendingFolder = null },
                        containerColor = OnboardingBrand.Hull,
                        titleContentColor = OnboardingBrand.Text,
                        textContentColor = OnboardingBrand.Mist,
                        shape = RoundedCornerShape(26.dp),
                        title = { Text("Remove folder?", style = OnboardingType.RowTitle) },
                        text = {
                            Text(
                                "Tracks scanned from \"$label\" and their playlist entries " +
                                    "will be deleted from this device."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.removeFolder(uriString)
                                    pendingFolder = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OnboardingBrand.Moss,
                                    contentColor = OnboardingBrand.Ink,
                                ),
                            ) { Text("Remove") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { pendingFolder = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = OnboardingBrand.Mist),
                            ) { Text("Cancel") }
                        },
                    )
                }
            }
        }
    }
}

private val BubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)

/**
 * "Hi, I'm Muorg!" tilted −5°, its left edge 12dp inside the logo's right edge.
 * Clamped to the screen so a large font scale pushes it back over the logo
 * instead of off the edge.
 */
@Composable
private fun SpeechBubble() {
    Box(
        Modifier.layout { measurable, constraints ->
            val bubble = measurable.measure(Constraints())
            val logoLeft = (constraints.maxWidth - 124.dp.roundToPx()) / 2
            val desired = logoLeft + 112.dp.roundToPx()
            // The page's 24dp side padding is usable; keep 12dp from the screen edge.
            val farthest = constraints.maxWidth + 12.dp.roundToPx() - bubble.width
            layout(constraints.maxWidth, bubble.height) {
                bubble.place(desired.coerceAtMost(farthest).coerceAtLeast(0), 0)
            }
        },
    ) {
        Text(
            text = "Hi, I’m Muorg!",
            style = OnboardingType.Bubble,
            color = OnboardingBrand.Ink,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .graphicsLayer { rotationZ = -5f }
                .background(OnboardingBrand.Fang, BubbleShape)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FolderRow(display: FolderDisplay, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnboardingBrand.Hull, shape)
            .border(1.5.dp, OnboardingBrand.Groove, shape)
            .padding(start = 12.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(OnboardingBrand.Moss, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MageIcon("folder-fill", Modifier.size(20.dp), tint = OnboardingBrand.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = display.name,
                style = OnboardingType.RowTitle,
                color = OnboardingBrand.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = display.path,
                style = OnboardingType.Body13,
                color = OnboardingBrand.Mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            MageIcon(
                "multiply",
                Modifier.size(20.dp),
                tint = OnboardingBrand.Mist,
                contentDescription = "Remove ${display.name}",
            )
        }
    }
}

@Composable
private fun AddFolderButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val outline = if (pressed || hovered) OnboardingBrand.Moss else OnboardingBrand.DashBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRing(source, shape)
            .drawBehind { drawDashedOutline(outline, 18.dp) }
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MageIcon("plus", Modifier.size(20.dp), tint = OnboardingBrand.Moss)
        Text("Add folder", style = OnboardingType.ActionLabel, color = OnboardingBrand.AddFolderLabel)
    }
}

/** What a picked folder looks like in a row: its own name, and where it lives. */
private data class FolderDisplay(val name: String, val path: String)

/**
 * Names a picked tree URI. External-storage trees (`primary:Download/Albums`)
 * read as "Albums" / "Internal storage › Download › Albums"; anything else
 * falls back to the display-name logic the screen always had.
 */
private fun folderDisplay(context: Context, uriString: String): FolderDisplay {
    val uri = Uri.parse(uriString)
    val label = uri.lastPathSegment?.substringAfterLast(':') ?: uriString
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    if (uri.authority != EXTERNAL_STORAGE_AUTHORITY || docId == null || ':' !in docId) {
        return FolderDisplay(name = label.ifBlank { uriString }, path = label.replace("/", " › ").ifBlank { uriString })
    }
    val volumeId = docId.substringBefore(':')
    val segments = docId.substringAfter(':').split('/').filter { it.isNotEmpty() }
    val volume = if (volumeId == "primary") {
        "Internal storage"
    } else {
        context.getSystemService(StorageManager::class.java)
            ?.storageVolumes
            ?.firstOrNull { it.uuid.equals(volumeId, ignoreCase = true) }
            ?.getDescription(context)
            ?: "External storage"
    }
    return FolderDisplay(
        name = segments.lastOrNull() ?: volume,
        path = (listOf(volume) + segments).joinToString(" › "),
    )
}

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
