package nl.muorg.android.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import nl.muorg.android.R

/**
 * The Muorg Orc scenes, ported from the web client's `OrcIllustration.vue`.
 * The vector drawables are generated from that component's SVG output; only
 * the round stage behind the Orc follows the app theme.
 */
enum class OrcVariant(@DrawableRes val drawable: Int, val description: String) {
    NotFound(R.drawable.orc_not_found, "A small Orc scratching its head and holding an empty record sleeve"),
    ServerError(R.drawable.orc_server_error, "A small Orc panicking next to a guitar with a snapped string"),
    Forbidden(R.drawable.orc_forbidden, "A small Orc bouncer in sunglasses with crossed arms between two velvet ropes"),
    Maintenance(R.drawable.orc_maintenance, "A small Orc in a hard hat holding a tuning fork and a wrench"),
    Empty(R.drawable.orc_empty, "A small Orc shrugging while holding an empty crate"),
    ServerTinker(R.drawable.orc_server_tinker, "A small Orc in a hard hat tinkering inside a computer with a screwdriver"),
    RecordSnack(R.drawable.orc_record_snack, "A small Orc taking a bite out of a record above a crate full of records"),
}

@Composable
fun OrcIllustration(variant: OrcVariant, modifier: Modifier = Modifier) {
    val stage = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Box(modifier = modifier.aspectRatio(240f / 270f)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = stage,
                radius = size.width * 112f / 240f,
                center = Offset(size.width * 0.5f, size.height * 150f / 270f),
            )
        }
        Image(
            painter = painterResource(variant.drawable),
            contentDescription = variant.description,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
