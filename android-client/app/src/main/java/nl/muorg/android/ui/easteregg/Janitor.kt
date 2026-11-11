package nl.muorg.android.ui.easteregg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.muorg.android.R
import nl.muorg.android.ui.component.rememberReducedMotion
import nl.muorg.android.ui.theme.BricolageFamily
import kotlin.math.sin

/**
 * The Muorg logo as a secret tap target: no ripple, no hint, 48dp minimum
 * touch target, and a tiny scale bounce (1 → 0.9 → 1, 120ms) per tap.
 */
@Composable
fun TappableLogo(
    size: Dp,
    contentDescription: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Image(
        painter = painterResource(R.drawable.ic_muorg_logo),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Muorg",
            ) {
                onTap()
                scope.launch {
                    scale.animateTo(0.9f, animationSpec = tween(60))
                    scale.animateTo(1f, animationSpec = tween(60))
                }
            }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
}

private val BubblePaper = Color(0xFFF4F1E6)
private val BubbleInk = Color(0xFF0B1418)
private val BubbleLip = Color(0xFF070D10)
private val BubbleHeader = Color(0xFF3E6A1C)
private val BubbleQuip = Color(0xFF3B4A50)
private val PillLip = Color(0xFF3E6A1C)

/**
 * The Janitor figure: 96×104dp, four stacked layers sharing the 240×270
 * viewport. The broom sweeps (rotationZ −7° ↔ +6°, pivot 0.5/0.704), the
 * dust puffs drift up-left, and the note floats. All loops stop while the
 * dust report bubble is open (the note keeps floating) and under reduced
 * motion. The whole figure is one tap target.
 */
@Composable
fun JanitorFigure(
    bubbleOpen: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val sweeping = !bubbleOpen && !reduced

    val broomTransition = rememberInfiniteTransition(label = "janitorBroom")
    val broomAngle by broomTransition.animateFloat(
        initialValue = -7f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "janitorBroomAngle",
    )
    val dustTransition = rememberInfiniteTransition(label = "janitorDust")
    val dustT by dustTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700, easing = LinearEasing)),
        label = "janitorDustT",
    )
    val noteTransition = rememberInfiniteTransition(label = "janitorNote")
    val noteT by noteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2200, easing = LinearEasing)),
        label = "janitorNoteT",
    )

    Box(
        modifier = modifier
            .size(width = 96.dp, height = 104.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .semantics { contentDescription = "Muorg is sweeping. Tap for library stats" },
    ) {
        Image(
            painter = painterResource(R.drawable.orc_janitor_dust),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                if (sweeping) {
                    val k = size.width / 240f
                    translationX = -14.dp.toPx() * k * dustT
                    translationY = -10.dp.toPx() * k * dustT
                    alpha = 0.9f * (1f - dustT)
                } else {
                    alpha = 0f
                }
            },
        )
        Image(
            painter = painterResource(R.drawable.orc_janitor_body),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.orc_janitor_broom),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                rotationZ = if (sweeping) broomAngle else 0f
                transformOrigin = TransformOrigin(0.5f, 0.704f)
            },
        )
        Image(
            painter = painterResource(R.drawable.orc_janitor_note),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                if (sweeping || bubbleOpen) {
                    val k = size.width / 240f
                    translationY = (4.dp.toPx() + (-18.dp.toPx()) * noteT) * k
                    alpha = sin(Math.PI * noteT).toFloat()
                } else {
                    alpha = 0f
                }
            },
        )
    }
}

/**
 * The dust report bubble: 262dp wide paper card with a lip, anchored above
 * the Janitor. Announced as a polite live region so new stats are read out.
 */
@Composable
fun DustReportBubble(
    stats: List<DustStat>,
    index: Int,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stat = stats.getOrElse(index) { stats.first() }
    Column(
        modifier = modifier
            .width(262.dp)
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier
                .background(BubblePaper, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MUORG'S DUST REPORT · ${index + 1}/${stats.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = BubbleHeader,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Close" },
                ) {
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.titleMedium,
                        color = BubbleInk,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stat.stat,
                fontFamily = BricolageFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = BubbleInk,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stat.quip,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = BubbleQuip,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(PillLip, RoundedCornerShape(50))
                    .padding(bottom = 3.dp)
                    .background(BubbleHeader, RoundedCornerShape(50))
                    .clickable(onClick = onNext)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Another one",
                    fontFamily = BricolageFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BubblePaper,
                )
            }
        }
        Box(
            modifier = Modifier
                .width(262.dp)
                .height(5.dp)
                .padding(horizontal = 12.dp)
                .background(BubbleLip),
        )
    }
}
