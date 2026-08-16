package nl.muorg.android.ui.screen.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.data.repository.Mix
import nl.muorg.android.ui.component.AlbumCard
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.MarqueeText
import androidx.compose.ui.graphics.RectangleShape
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.glass.scrimLabelStyle
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.theme.MuorgShapes

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
            Spacer(Modifier.statusBarsPadding().height(24.dp))
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
                baseUrl = baseUrl,
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
    baseUrl: String,
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
        Box(Modifier.fillMaxSize()) {
            CoverMosaic(mix.coverTrackIds, baseUrl, imageLoader)
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
@Composable
private fun CoverMosaic(
    coverTrackIds: List<Int>,
    baseUrl: String,
    imageLoader: ImageLoader,
) {
    val ids = coverTrackIds.take(4)
    if (ids.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            MageIcon(
                name = "color-swatch",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp),
            )
        }
        return
    }
    if (ids.size < 4) {
        AsyncImage(
            model = "$baseUrl/api/tracks/${ids.first()}/cover",
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 2) {
                    AsyncImage(
                        model = "$baseUrl/api/tracks/${ids[row * 2 + col]}/cover",
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().weight(1f),
                    )
                }
            }
        }
    }
}
