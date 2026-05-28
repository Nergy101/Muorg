package nl.muorg.android.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = 1200,
            repeatDelayMillis = 1200,
            spacing = MarqueeSpacing.fractionOfContainer(0.1f),
            velocity = 40.dp,
        ),
    )
}
