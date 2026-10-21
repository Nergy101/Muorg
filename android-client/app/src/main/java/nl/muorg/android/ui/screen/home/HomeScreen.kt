package nl.muorg.android.ui.screen.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.R
import nl.muorg.android.data.repository.Mix
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.CoverMosaic
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.MarqueeText
import nl.muorg.android.ui.component.coverModelFor
import androidx.compose.ui.graphics.RectangleShape
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.glass.scrimLabelStyle
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes

/** "Shuffle all" seeds the queue with this many random songs from the catalog. */
private const val SHUFFLE_ALL_COUNT = 100

/**
 * The web's Home tab (`HomeView.vue`): shelves, not a library. "Recommended"
 * is four albums re-rolled on demand; "Mixes" are eight ephemeral, genre-themed
 * playlists that live only for the session.
 */
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    imageLoader: ImageLoader,
    baseUrl: String,
    onOpenAlbum: (String) -> Unit,
    onOpenMix: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Local mode's automatic first scan: while it runs, a full-screen loader
    // with the scanner bar replaces the page. It hides itself as soon as the
    // scan completes, revealing the filled shelves underneath.
    if (state.isScanning) {
        ScanningPlaceholder(
            progress = state.scanProgress,
            total = state.scanTotal,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = LocalBottomInset.current + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            HomeHeader(
                shuffleEnabled = !state.isOfflineMode,
                onShuffleAll = {
                    playerViewModel.startShuffleAll(
                        viewModel.allTracks().shuffled().take(SHUFFLE_ALL_COUNT)
                    )
                },
            )
        }

        // Offline mode is a hard divide: every shelf is a server call, so
        // instead of four empty sections the page says so once, in the middle.
        if (state.isOfflineMode) {
            item(span = { GridItemSpan(2) }) {
                OfflineModePlaceholder()
            }
            return@LazyVerticalGrid
        }

        item(span = { GridItemSpan(2) }) {
            ShelfHeader(
                icon = "heart",
                title = "Recommended",
                busy = state.loading,
                onRefresh = viewModel::refreshRecommended,
            )
        }

        if (state.error != null) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Couldn't load recommended.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        items(state.recommended, key = { it.albumName + it.artist }) { album ->
            AlbumCard(
                album = album,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onClick = { onOpenAlbum(album.albumName) },
            )
        }

        item(span = { GridItemSpan(2) }) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ShelfHeader(
                    icon = "color-swatch",
                    title = "Mixes",
                    busy = state.loading,
                    onRefresh = viewModel::refreshMixes,
                )
            }
        }

        items(state.mixes, key = { it.name }) { mix ->
            MixCard(
                mix = mix,
                // Resolved once per lineup: local art files in local mode,
                // server cover URLs otherwise.
                covers = remember(mix.coverTrackIds, baseUrl) {
                    mix.coverTrackIds.map {
                        coverModelFor(viewModel.trackFor(it)?.localCoverPath, it, baseUrl)
                    }
                },
                imageLoader = imageLoader,
                onClick = { onOpenMix(mix.id) },
            )
        }

        item(span = { GridItemSpan(2) }) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ShelfHeader(
                    icon = "chart-up",
                    title = "Most Played",
                    busy = state.loading,
                    onRefresh = viewModel::refreshMostPlayed,
                )
            }
        }

        items(state.mostPlayed, key = { "top-" + it.albumName + it.artist }) { album ->
            AlbumCard(
                album = album,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onClick = { onOpenAlbum(album.albumName) },
            )
        }

        item(span = { GridItemSpan(2) }) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ShelfHeader(
                    icon = "clock",
                    title = "Recently Played",
                    busy = state.loading,
                    onRefresh = viewModel::refreshRecentlyPlayed,
                )
            }
        }

        items(state.recentlyPlayed, key = { "recent-" + it.albumName + it.artist }) { album ->
            AlbumCard(
                album = album,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onClick = { onOpenAlbum(album.albumName) },
            )
        }
    }
}

/**
 * The brand row: the Muorg mark and name on the left (as in the web's
 * sidebar), "Shuffle all" as a glass pill on the right. Always shown, even
 * in offline mode — there the button is dimmed and inert, since the catalog
 * is not loaded.
 */
@Composable
private fun HomeHeader(
    shuffleEnabled: Boolean,
    onShuffleAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Image, not Icon: Icon tints to a single colour and flattens the mark
        // to a silhouette; the logo carries its own colours.
        Image(
            painter = painterResource(R.drawable.ic_muorg_logo),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Muorg",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        GlassSurface(
            material = GlassMaterial.Glass,
            shape = MuorgShapes.pill,
            modifier = Modifier
                .height(36.dp)
                .clickable(
                    enabled = shuffleEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onShuffleAll,
                ),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MageIcon(
                    name = "exchange",
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Shuffle all",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (shuffleEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Full-screen loader for the local library's automatic first scan: the
 * scanner bar with its live progress, in place of the still-empty page.
 */
@Composable
private fun ScanningPlaceholder(
    progress: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MageIcon(
            name = "music",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Scanning library…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { progress.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$progress / $total files scanned",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // The scan's first phase only counts the files, so the total —
            // and with it the bar's fraction — is not known yet.
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Counting files…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shown in place of every shelf while offline mode is on. */
@Composable
private fun OfflineModePlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MageIcon(
            name = "download",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "You are in offline mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Only downloaded music is available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shelf title with its Mage glyph and the circular refresh control on the
 * right. The bottom spacing belongs to the row, not the heading, so the button
 * centres on the heading's own centre line.
 */
@Composable
private fun ShelfHeader(
    icon: String,
    title: String,
    busy: Boolean,
    onRefresh: () -> Unit,
) {
    val spin = rememberInfiniteTransition(label = "refreshSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "refreshAngle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MageIcon(
            name = icon,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        GlassSurface(
            material = GlassMaterial.Glass,
            shape = MuorgShapes.pill,
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRefresh,
                ),
        ) {
            MageIcon(
                name = "refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = "New $title",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (busy) angle else 0f },
            )
        }
    }
}

/** A mix tile: a 2x2 cover mosaic under the same frosted caption as an album. */
@Composable
private fun MixCard(
    mix: Mix,
    covers: List<Any>,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = MuorgShapes.card,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(MuorgShapes.card)
            .background(MaterialTheme.colorScheme.surface)
            .aspectRatio(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        CoverMosaic(
            covers = covers,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
        ) {
            MageIcon(
                name = "color-swatch",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        GlassSurface(
            material = GlassMaterial.Scrim,
            shape = RectangleShape,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarqueeText(
                text = mix.name,
                style = scrimLabelStyle(MaterialTheme.typography.titleSmall),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            MageIcon(
                name = "music",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "${mix.trackIds.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
    }
}

/** One cover fills the square; four tile it. Matches `MixCard.vue`. */
