package nl.muorg.android.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Rotating one-liners shown under the spinner on full-screen waits. */
val OrcLoadingLines = listOf(
    "Digging through the crates…",
    "Dusting off your vinyl…",
    "Untangling the headphone cables…",
    "Asking the server nicely…",
    "Sniffing out album art…",
    "Counting tracks. Losing count. Recounting…",
)

/**
 * Full-screen loading state: a 112dp [OrcSpinner] with one cycling line of
 * text 16dp below it. The line changes every 2.5s with a 250ms crossfade
 * and starts at a random index. Under reduced motion the first line shows
 * and nothing cycles. Only for full-screen or large-area waits — never on
 * errors or empty states.
 */
@Composable
fun OrcLoading(modifier: Modifier = Modifier, lines: List<String> = OrcLoadingLines) {
    val reduced = rememberReducedMotion()
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(lines, reduced) {
        if (reduced || lines.size < 2) return@LaunchedEffect
        index = lines.indices.random()
        while (true) {
            delay(2500)
            index = (index + 1) % lines.size
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OrcSpinner(size = 112.dp)
        Spacer(Modifier.height(16.dp))
        Crossfade(
            targetState = lines.getOrElse(index) { "" },
            animationSpec = tween(250),
            label = "orcLoadingLine",
        ) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
