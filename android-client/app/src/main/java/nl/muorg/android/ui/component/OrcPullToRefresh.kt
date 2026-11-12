package nl.muorg.android.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.muorg.android.R

/**
 * Pull-to-refresh wrapper with the worried Orc head peeking from above the
 * top edge. The head (rotated 180°) slides down from −100% to −18dp as
 * `state.distanceFraction` grows (clamped at 1); while refreshing it bobs
 * 0 → −6dp on a 2.4s loop and "Shaking the crates…" appears under the chin.
 * The Orc only appears once the user actually pulls down: a programmatic
 * refresh (e.g. the initial load) drives the fraction to 1 too, but without
 * a gesture the head stays hidden. The indicator is decorative — the
 * refreshing state comes from the box.
 * Apply only where a refresh action already exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = rememberReducedMotion()
    val state = rememberPullToRefreshState()
    // True while the current pull distance comes from a user gesture. A
    // refresh that starts on its own (initial load) also animates the
    // fraction up, so the fraction alone cannot tell the two apart.
    var pulled by remember { mutableStateOf(false) }
    val fraction = state.distanceFraction.coerceIn(0f, 1f)
    LaunchedEffect(fraction, isRefreshing) {
        if (!isRefreshing && fraction > 0f) pulled = true
        if (fraction == 0f) pulled = false
    }
    val bobTransition = rememberInfiniteTransition(label = "orcP2rBob")
    val bob by bobTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orcP2rBobDp",
    )
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            if (pulled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            val h = size.height
                            val rest = -18.dp.toPx()
                            translationY = -h + (h + rest) * fraction +
                                if (isRefreshing && !reduced) bob.dp.toPx() else 0f
                        },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.orc_head_worried),
                            contentDescription = null,
                            modifier = Modifier
                                .width(150.dp)
                                .aspectRatio(232f / 164f)
                                .graphicsLayer { rotationZ = 180f },
                        )
                        AnimatedVisibility(visible = isRefreshing) {
                            Text(
                                text = "Shaking the crates…",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        content = content,
    )
}
