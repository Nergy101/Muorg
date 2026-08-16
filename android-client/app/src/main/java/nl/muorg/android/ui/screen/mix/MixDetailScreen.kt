package nl.muorg.android.ui.screen.mix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.ui.component.EqualizerBars
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes

/**
 * A mix opened as a track list. The web routes mixes through its playlist
 * detail view; Android has no ephemeral-playlist screen, so this is the same
 * anatomy as album detail — compact header, filled primary play, track rows —
 * over a mix that only exists for the session.
 */
@Composable
fun MixDetailScreen(
    mixId: Int,
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onBack: () -> Unit,
    viewModel: MixDetailViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val mix = remember(mixId) { viewModel.mix(mixId) }
    val tracks = remember(mixId) { viewModel.tracks(mixId) }
    val currentId = playerState.currentTrack?.id

    if (mix == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "This mix has expired.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(mageIconRes("chevron-left")),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
                Box(
                    modifier = Modifier.size(112.dp).clip(MuorgShapes.art),
                ) {
                    MixMosaic(mix.coverTrackIds, mix.emoji, baseUrl, imageLoader)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = mix.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Mix · ${tracks.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    tracks.firstOrNull()?.let { playerViewModel.playTrack(it, tracks) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(mageIconRes("play")),
                                contentDescription = "Play mix",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(
                            onClick = { playerViewModel.startShuffleAll(tracks) },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                painter = painterResource(mageIconRes("exchange")),
                                contentDescription = "Shuffle mix",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            val isPlaying = track.id == currentId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { playerViewModel.playTrack(track, tracks) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                    if (isPlaying) {
                        EqualizerBars(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(width = 20.dp, height = 14.dp),
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = track.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    MarqueeText(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = track.format.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                            MuorgShapes.chip,
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = track.formattedDuration(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MixMosaic(
    coverTrackIds: List<Int>,
    emoji: String,
    baseUrl: String,
    imageLoader: ImageLoader,
) {
    if (coverTrackIds.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
        }
        return
    }
    if (coverTrackIds.size < 4) {
        AsyncImage(
            model = "$baseUrl/api/tracks/${coverTrackIds.first()}/cover",
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 2) {
                    AsyncImage(
                        model = "$baseUrl/api/tracks/${coverTrackIds[row * 2 + col]}/cover",
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        // weight FIRST: fillMaxSize resolves against the incoming
                        // max width, so each cell claimed the whole row and its
                        // siblings measured to nothing — a black tile.
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}
