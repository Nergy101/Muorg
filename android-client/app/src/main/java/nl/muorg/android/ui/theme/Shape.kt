package nl.muorg.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The web client's radii, verbatim. Tailwind's `rounded-2xl` is 16px and
 * `rounded-t-3xl` is 24px; both land on cards and the bottom island
 * respectively, and every other surface in the app is one of those two or a
 * full pill.
 */
object MuorgShapes {
    /** Album, playlist and mix cards — `rounded-2xl`. */
    val card = RoundedCornerShape(16.dp)

    /** Cover art inside a card, and the player's artwork. */
    val art = RoundedCornerShape(16.dp)

    /** Small cover art in rows (queue, track lists). */
    val artSmall = RoundedCornerShape(8.dp)

    /** The bottom island: `rounded-t-3xl`, flush to the bottom edge. */
    val island = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** Bottom sheets. */
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** Search field, segmented control, nav indicator, floating control pills. */
    val pill = RoundedCornerShape(999.dp)

    /** Format chips on track rows. */
    val chip = RoundedCornerShape(8.dp)
}

/** Material's shape slots, pointed at the web's radii. */
val MuorgMaterialShapes = Shapes(
    extraSmall = MuorgShapes.chip,
    small = MuorgShapes.artSmall,
    medium = MuorgShapes.card,
    large = MuorgShapes.card,
    extraLarge = MuorgShapes.sheet,
)
