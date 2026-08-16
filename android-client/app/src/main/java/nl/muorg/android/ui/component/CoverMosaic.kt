package nl.muorg.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage

/**
 * The 2x2 cover collage used by playlist tiles, mix tiles and the mix header.
 *
 * FALLS BACK RATHER THAN LEAVING HOLES. A tile only shows the 2x2 when all
 * four covers actually decode; the moment one fails — a 404, a cover the
 * catalog claims exists but the server will not serve, a request dropped
 * because a screenful of tiles asked for twenty images at once — the tile
 * drops to a single full-bleed cover, exactly like an album card. That was the
 * black-square bug: a quarter that never loaded left bare background behind
 * it, and on a real device enough of them failed to make whole tiles look
 * empty.
 *
 * One shared implementation because it was previously copied into three
 * screens, and a fix in one of them fixed only that screen.
 */
@Composable
fun CoverMosaic(
    coverTrackIds: List<Int>,
    baseUrl: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    placeholder: @Composable BoxScope.() -> Unit = {},
) {
    var failed by remember(coverTrackIds) { mutableStateOf(emptySet<Int>()) }
    val usable = remember(coverTrackIds, failed) { coverTrackIds.filterNot { it in failed } }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when {
            usable.isEmpty() -> placeholder()

            usable.size >= 4 -> Column(Modifier.fillMaxSize()) {
                for (row in 0 until 2) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        for (col in 0 until 2) {
                            val id = usable[row * 2 + col]
                            AsyncImage(
                                model = "$baseUrl/api/tracks/$id/cover",
                                contentDescription = null,
                                imageLoader = imageLoader,
                                contentScale = ContentScale.Crop,
                                onError = { failed = failed + id },
                                // weight FIRST: fillMaxSize would resolve against the
                                // incoming max width, so a cell claims the whole row
                                // and its siblings measure to nothing.
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            else -> AsyncImage(
                model = "$baseUrl/api/tracks/${usable.first()}/cover",
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                onError = { failed = failed + usable.first() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
