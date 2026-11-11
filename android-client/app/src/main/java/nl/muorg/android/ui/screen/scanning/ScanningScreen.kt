package nl.muorg.android.ui.screen.scanning

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.muorg.android.ui.component.OnboardingHeadline
import nl.muorg.android.ui.component.OnboardingPage
import nl.muorg.android.ui.component.OnboardingScreen
import nl.muorg.android.ui.component.OrcMotion
import nl.muorg.android.ui.component.OrcPortrait
import nl.muorg.android.ui.component.OrcStage
import nl.muorg.android.ui.component.OrcVariant
import nl.muorg.android.ui.component.drawOnboardingRings
import nl.muorg.android.ui.theme.OnboardingBrand
import nl.muorg.android.ui.theme.OnboardingType
import java.text.NumberFormat

/**
 * Shown once, right after the user finishes setting up a music source: scans
 * the chosen folders (local) or waits for the server to finish its own startup
 * scan (remote), then moves on by itself. [ScanningViewModel] owns the timing.
 */
@Composable
fun ScanningScreen(
    onFinished: () -> Unit,
    viewModel: ScanningViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    OnboardingScreen {
        // Nothing to show until the saved music source has been read; the
        // brand background alone is steadier than a guess that flips.
        val kind = state.kind ?: return@OnboardingScreen
        val local = kind == ScanKind.LOCAL
        val copy = copyFor(state)
        val accent = if (local) OnboardingBrand.Moss else OnboardingBrand.Spotlight

        OnboardingPage(top = 60.dp, bottom = 24.dp) {
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.drawBehind {
                    drawOnboardingRings(Offset(size.width / 2f, size.height / 2f))
                },
            ) {
                OrcStage(
                    color = if (local) OnboardingBrand.LocalStage else OnboardingBrand.ServerStage,
                    modifier = Modifier.size(176.dp),
                ) {
                    val empty = state.phase == ScanPhase.FAILED ||
                        (local && state.phase == ScanPhase.DONE && state.trackCount == 0)
                    Crossfade(targetState = empty to state.phase, animationSpec = tween(250), label = "scanOrc") { (isEmpty, phase) ->
                        when {
                            isEmpty -> OrcPortrait(OrcVariant.Empty, OrcVariant.Empty.description)
                            local -> OrcPortrait(
                                OrcVariant.RecordSnack,
                                OrcVariant.RecordSnack.description,
                                motion = OrcMotion.Hop(),
                            )
                            else -> OrcPortrait(
                                OrcVariant.ServerTinker,
                                OrcVariant.ServerTinker.description,
                                motion = if (phase == ScanPhase.WORKING) OrcMotion.Wiggle else OrcMotion.Hop(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // The headline is the live region: it changes only when the state
            // does, whereas the subtitle ticks with every scanned file.
            OnboardingHeadline(
                text = copy.headline,
                style = OnboardingType.Headline30,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = copy.subtitle,
                style = OnboardingType.Body16,
                color = OnboardingBrand.Mist,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            ScanBar(progress = barProgress(state), color = accent)

            Spacer(Modifier.weight(1.2f))

            // Holds its height once the scan is over so nothing shifts.
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                if (state.phase == ScanPhase.WORKING) {
                    TextButton(onClick = viewModel::skip) {
                        Text("Skip", style = OnboardingType.ActionLabel, color = OnboardingBrand.Mist)
                    }
                }
            }
        }
    }
}

private data class ScanCopy(val headline: String, val subtitle: String)

private fun copyFor(state: ScanUiState): ScanCopy = when (state.kind) {
    ScanKind.LOCAL -> when (state.phase) {
        ScanPhase.WORKING -> ScanCopy(
            headline = "Munching through your music…",
            subtitle = if (state.total == 0) "Counting files…"
            else "${count(state.scanned)} / ${count(state.total)} files",
        )
        ScanPhase.DONE ->
            if (state.trackCount == 0) {
                ScanCopy("Nothing to munch…", "I couldn’t find any audio files in those folders.")
            } else {
                ScanCopy(
                    "Full belly!",
                    if (state.trackCount == 1) "1 track ready." else "${count(state.trackCount)} tracks ready.",
                )
            }
        ScanPhase.FAILED -> ScanCopy("Hit a snag.", "The scan didn’t finish. You can rescan from Settings.")
    }
    else -> when (state.phase) {
        ScanPhase.DONE -> ScanCopy("Server’s ready!", "Your library is all set.")
        // The server never "fails" here: it is polled until it answers.
        else -> ScanCopy(
            headline = "Waking up your server…",
            subtitle = when {
                state.serverUnreachable -> "Can’t reach it right now. Still trying…"
                state.serverBusy -> "It’s still scanning its library. Hang tight."
                else -> "Checking in…"
            },
        )
    }
}

/** Null = indeterminate: the local file count, or the server's progress, is not known. */
private fun barProgress(state: ScanUiState): Float? = when (state.phase) {
    ScanPhase.DONE -> 1f
    ScanPhase.FAILED -> if (state.total > 0) state.scanned.toFloat() / state.total else 0f
    ScanPhase.WORKING ->
        if (state.kind == ScanKind.LOCAL && state.total > 0) state.scanned.toFloat() / state.total else null
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)

@Composable
private fun ScanBar(progress: Float?, color: Color) {
    val shape = RoundedCornerShape(percent = 50)
    val modifier = Modifier
        .fillMaxWidth()
        .height(12.dp)
        .border(1.5.dp, OnboardingBrand.Groove, shape)
        .clip(shape)
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = OnboardingBrand.Hull,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    } else {
        val animated by animateFloatAsState(progress, tween(300), label = "scanProgress")
        LinearProgressIndicator(
            progress = { animated },
            modifier = modifier,
            color = color,
            trackColor = OnboardingBrand.Hull,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
