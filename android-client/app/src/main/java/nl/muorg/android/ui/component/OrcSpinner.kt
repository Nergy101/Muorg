package nl.muorg.android.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.muorg.android.R

/**
 * The Muorg Orc record spinner: the app logo as a record. Three stacked
 * layers — base, grooves, face — share the 168×162 viewport so they line
 * up exactly. Only the grooves rotate (pivot 0.5, 0.531), so the highlights
 * pass under the label and face, which never move.
 *
 * Sizes: 28dp inline and inside buttons, 56dp in lists and cards, 112dp
 * full-screen. Announces itself as an indeterminate progress indicator
 * ("Loading") unless the parent already announces loading.
 */
@Composable
fun OrcSpinner(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "orcSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
        ),
        label = "orcSpinnerAngle",
    )
    Box(
        modifier = modifier
            .size(width = size, height = size * 162f / 168f)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                contentDescription = "Loading"
            },
    ) {
        Image(
            painter = painterResource(R.drawable.orc_spinner_base),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.orc_spinner_grooves),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = if (reduced) 0f else angle
                    transformOrigin = TransformOrigin(0.5f, 0.531f)
                },
        )
        Image(
            painter = painterResource(R.drawable.orc_spinner_face),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
