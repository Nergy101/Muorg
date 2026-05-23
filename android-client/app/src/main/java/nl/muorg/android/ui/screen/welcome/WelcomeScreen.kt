package nl.muorg.android.ui.screen.welcome

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onNavigateToLibrary: () -> Unit,
    onNavigateToRemoteSetup: () -> Unit,
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = showTitle,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 3 },
                ) {
                    Text(
                        text = "Muorg",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = showSubtitle,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 3 },
                ) {
                    Text(
                        text = "How do you want to listen?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(48.dp))

                AnimatedVisibility(
                    visible = showCard1,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 2 },
                ) {
                    ShimmerModeCard(
                        icon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(32.dp)) },
                        title = "Muorg Server",
                        subtitle = "Stream from your self-hosted server",
                        onClick = viewModel::chooseRemote,
                    )
                }

                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = showCard2,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 2 },
                ) {
                    ShimmerModeCard(
                        icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(32.dp)) },
                        title = "Local Library",
                        subtitle = "Play music from folders on this device",
                        onClick = viewModel::chooseLocal,
                    )
                }
            }
        }

        WelcomeStep.LOCAL_SETUP -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Local library") },
                        navigationIcon = {
                            TextButton(onClick = viewModel::goBackToModeSelect) {
                                Text("← Back")
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Select the folders where your music is stored. Muorg will scan them for FLAC, MP3, and other audio files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(24.dp))

                    if (uiState.localFolderUris.isEmpty()) {
                        Text(
                            text = "No folders added yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        uiState.localFolderUris.forEach { uriString ->
                            val label = Uri.parse(uriString).lastPathSegment
                                ?.substringAfterLast(':')
                                ?: uriString
                            ListItem(
                                headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                leadingContent = {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeFolder(uriString) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove folder",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add music folder")
                    }

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = viewModel::finishLocalSetup,
                        enabled = uiState.localFolderUris.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Start listening")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerModeCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
            Color.Transparent,
        ),
        start = Offset(shimmerX, 0f),
        end = Offset(shimmerX + 300f, 300f),
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(shimmerBrush, RoundedCornerShape(16.dp))
            )
        }
    }
}
