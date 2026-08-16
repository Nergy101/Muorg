package nl.muorg.android.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import nl.muorg.android.player.PlayerState
import nl.muorg.android.ui.glass.GlassMaterial
import nl.muorg.android.ui.glass.GlassSurface
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.theme.MuorgMotion
import nl.muorg.android.ui.theme.MuorgShapes

/**
 * The bottom island — the web client's `App.vue` shell, ported.
 *
 * On the web this is ONE `.glass-deep` panel with 24px top corners, pinned to
 * the bottom and overlaying the routed view, holding the mini player, the
 * library's search/filter chrome and the tab row. Content scrolls *behind* it
 * and shows through the blur, which is where the warm bloom under the bar
 * comes from. It is deliberately not a `Scaffold` bottom bar: a bottom bar
 * would consume layout height and the artwork would never pass underneath.
 */

/**
 * Height of the island, measured at runtime. Every scroller ends with this as
 * bottom padding — the web does the same thing with `--bottom-inset`, and a
 * hardcoded value drifts the moment the mini player appears or the library
 * chrome collapses.
 */
val LocalBottomInset = compositionLocalOf { 0.dp }

/**
 * The artwork-derived accent the island paints itself with, published so the
 * surfaces sharing its glass — the library chrome hosted inside it — tint to
 * the same colour instead of sitting there theme-green next to it.
 */
val LocalIslandAccent = compositionLocalOf { Color.Unspecified }

/** Slot the library screen fills; the island renders it as its second row. */
@Stable
class LibraryChromeHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalLibraryChromeHost = staticCompositionLocalOf<LibraryChromeHost?> { null }

/**
 * Publishes the caller's chrome into the island for as long as it is composed.
 * Renders nothing in place.
 */
@Composable
fun ProvideLibraryChrome(content: @Composable () -> Unit) {
    val host = LocalLibraryChromeHost.current ?: return
    val latest by rememberUpdatedState(content)
    DisposableEffect(host) {
        host.content = { latest() }
        onDispose { host.content = null }
    }
}

@Immutable
data class IslandTab(
    val icon: String,
    val iconActive: String,
    val label: String,
)

@Composable
fun BottomIsland(
    bloom: Color,
    tabs: List<IslandTab>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    playerState: PlayerState,
    baseUrl: String,
    imageLoader: ImageLoader,
    onMiniPlayerClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onTrackMenu: () -> Unit,
    /** Lights the queue glyph while the queue screen is the one on screen. */
    queueActive: Boolean,
    showTabs: Boolean,
    chrome: (@Composable () -> Unit)?,
    chromeExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    val accent = LocalIslandAccent.current
    GlassSurface(
        material = GlassMaterial.Deep,
        shape = MuorgShapes.island,
        modifier = modifier.fillMaxWidth(),
        bloom = bloom,
    ) {
    // The whole stack animates its height, so collapsing the chrome and the
    // tabs slides the island down to just the mini player rather than popping.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(MuorgMotion.COLLAPSE_MS, easing = MuorgMotion.easing)),
    ) {
        if (playerState.currentTrack != null) {
            MiniPlayerRow(
                playerState = playerState,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onClick = onMiniPlayerClick,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onOpenQueue = onOpenQueue,
                onMenu = onTrackMenu,
                queueActive = queueActive,
                accent = accent,
            )
        }

        if (chrome != null && chromeExpanded) {
            HorizontalDivider(thickness = 1.dp, color = outline.copy(alpha = 0.10f))
            chrome()
        }

        if (showTabs) {
            IslandTabs(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = onSelectTab,
                accent = accent,
            )
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    }
}

/**
 * The web's mini player: artwork flush into the island's left edge at full row
 * height, then title over artist, then the action cluster — and a hairline
 * progress line riding the bottom edge of the row, running under the artwork.
 */
@Composable
private fun MiniPlayerRow(
    playerState: PlayerState,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onMenu: () -> Unit,
    queueActive: Boolean,
    accent: Color,
) {
    val track = playerState.currentTrack ?: return
    // Glyphs are pulled a little toward the artwork colour rather than set to
    // it: full accent on every control turns the row into a colour swatch and
    // costs contrast against the fill.
    val onSurface = lerp(MaterialTheme.colorScheme.onSurface, accent, 0.30f)

    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverModel: Any? = when {
                track.localCoverPath != null -> java.io.File(track.localCoverPath)
                track.hasCover -> "$baseUrl/api/tracks/${track.id}/cover"
                else -> null
            }
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    MageIcon("music", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurface,
                )
                Text(
                    text = track.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                MageIcon(
                    name = "stack",
                    tint = if (queueActive) accent else onSurface,
                    contentDescription = if (queueActive) "Close queue" else "Queue",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(32.dp)) {
                MageIcon("dots", tint = onSurface, contentDescription = "Track actions", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                MageIcon(
                    if (playerState.isPlaying) "pause" else "play",
                    tint = onSurface,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                MageIcon("next", tint = onSurface, contentDescription = "Next", modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(playerState.progress.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(accent),
        )
    }
}

/**
 * Four icon-only tabs with a sliding pill behind the active one — `BottomNav.vue`.
 * No labels: the web shows glyphs alone, and the active glyph swaps to its
 * filled Mage variant rather than only changing colour.
 */
@Composable
private fun IslandTabs(
    tabs: List<IslandTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
) {
    val inactive = lerp(MaterialTheme.colorScheme.onSurfaceVariant, accent, 0.25f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
    ) {
        val slot = maxWidth / tabs.size
        val pillWidth = 64.dp
        val pillOffset by animateDpAsState(
            targetValue = slot * selectedIndex + (slot - pillWidth) / 2,
            animationSpec = tween(MuorgMotion.NAV_DURATION_MS, easing = MuorgMotion.easing),
            label = "tabPill",
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = pillOffset)
                .size(pillWidth, 36.dp)
                .background(accent.copy(alpha = 0.20f), MuorgShapes.pill),
        )

        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = tween(MuorgMotion.NAV_DURATION_MS, easing = MuorgMotion.easing),
                    label = "tabScale$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MageIcon(
                        name = if (selected) tab.iconActive else tab.icon,
                        tint = if (selected) accent else inactive,
                        contentDescription = tab.label,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
            }
        }
    }
}

/** Padding helper so screens never hardcode the island's height. */
@Composable
fun bottomInset(): Dp = LocalBottomInset.current
