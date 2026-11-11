package nl.muorg.android.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.R
import nl.muorg.android.ui.OrcMood
import nl.muorg.android.ui.theme.BricolageFamily
import nl.muorg.android.ui.theme.OnboardingBrand

private val SnackbarContainer = Color(0xFF1F3139)
private val SnackbarLip = Color(0xFF070D10)

/** [SnackbarVisuals] that carries the Orc mood through to the host. */
class MuorgSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val mood: OrcMood?,
) : SnackbarVisuals

/**
 * The app's snackbar: dark container with a 4dp lip underneath, an optional
 * Orc head (happy/worried) on the left, the message, and the action as a
 * cyan Bricolage text button.
 */
@Composable
fun MuorgSnackbar(data: SnackbarData) {
    val mood = (data.visuals as? MuorgSnackbarVisuals)?.mood
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SnackbarContainer, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mood != null) {
                Image(
                    painter = painterResource(
                        if (mood == OrcMood.Happy) R.drawable.orc_head_happy
                        else R.drawable.orc_head_worried
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .width(44.dp)
                        .aspectRatio(232f / 164f),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = BricolageFamily,
                        color = OnboardingBrand.Spotlight,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 12.dp)
                .background(SnackbarLip),
        )
    }
}
