package nl.muorg.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DominantColorState(
    val color: Color,
    val isBland: Boolean,
)

@Composable
fun rememberDominantColor(
    url: String?,
    imageLoader: ImageLoader,
    fallback: Color,
): DominantColorState {
    var state by remember(url) { mutableStateOf(DominantColorState(fallback, isBland = true)) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        if (url == null) {
            state = DominantColorState(fallback, isBland = true)
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(64)
            .build()
        val result = imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: run {
            state = DominantColorState(fallback, isBland = true)
            return@LaunchedEffect
        }
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        if (swatch == null) {
            state = DominantColorState(fallback, isBland = true)
            return@LaunchedEffect
        }
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        val isBland = hsv[1] < 0.25f || (hsv[1] < 0.45f && hsv[2] > 0.85f)
        state = DominantColorState(
            color = if (isBland) fallback else Color(swatch.rgb),
            isBland = isBland,
        )
    }

    return state
}
