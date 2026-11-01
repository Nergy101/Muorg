package nl.muorg.android.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.muorg.android.ui.icon.mageIconRes

/**
 * A settings section with a tappable header, matching the web client's
 * SettingsSection: the title row toggles the body, with a chevron that
 * flips when open. Sections start collapsed unless [defaultOpen] is set
 * (the version/info section).
 */
@Composable
fun CollapsibleSection(
    title: String,
    icon: String? = null,
    defaultOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by remember(title) { mutableStateOf(defaultOpen) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(start = 16.dp, top = 20.dp, bottom = 4.dp, end = 16.dp),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(mageIconRes(icon)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (icon != null) 6.dp else 0.dp),
            )
            Icon(
                painter = painterResource(mageIconRes("chevron-down")),
                contentDescription = if (open) "Collapse section" else "Expand section",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (open) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}
