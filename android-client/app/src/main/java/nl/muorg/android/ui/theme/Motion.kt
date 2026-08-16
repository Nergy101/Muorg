package nl.muorg.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * The web client's navigation timings (`--nav-duration`, `--nav-ease`,
 * `--sheet-duration`, `--nav-parallax`, `--nav-scrim` in `style.css`).
 *
 * The easing is the iOS-style "out" curve the web uses for every push and pop:
 * it leaves fast and settles slowly, which is what makes the two clients feel
 * the same in motion rather than only in a still screenshot.
 */
object MuorgMotion {
    const val NAV_DURATION_MS = 320
    const val SHEET_DURATION_MS = 260

    /** `cubic-bezier(0.32, 0.72, 0, 1)`. */
    val easing: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** The covered view slides this fraction of its width in the travel direction. */
    const val PARALLAX = 0.30f

    /** Peak opacity of the black scrim drawn over the covered view. */
    const val SCRIM_ALPHA = 0.28f

    /** Library chrome collapse (`transition-[max-height] duration-300 ease-out`). */
    const val COLLAPSE_MS = 300
}
