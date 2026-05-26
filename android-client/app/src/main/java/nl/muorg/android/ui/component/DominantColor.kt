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
    var state by remember(url) {
        mutableStateOf(DominantColorState(fallback, isBland = true))
    }
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

        val newState = withContext(Dispatchers.Default) {
            analyzeBitmap(bitmap, fallback)
        }
        state = newState
    }

    return state
}

private fun analyzeBitmap(
    bitmap: android.graphics.Bitmap,
    fallback: Color,
): DominantColorState {
    val size = bitmap.width  // square 64×64
    val hsv = FloatArray(3)

    val pixels = IntArray(size * size)
    bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

    // Single pass: average saturation + dominant hue bucket.
    // 36 buckets of 10° each — only count pixels that are meaningfully colored.
    val hueBucketCount = IntArray(36)
    val hueBucketBestArgb = IntArray(36) { -1 }
    val hueBucketBestSat = FloatArray(36)
    var totalSaturation = 0f

    for (pixel in pixels) {
        android.graphics.Color.colorToHSV(pixel, hsv)
        totalSaturation += hsv[1]
        if (hsv[1] >= 0.25f && hsv[2] >= 0.20f) {
            val bucket = (hsv[0] / 10f).toInt().coerceIn(0, 35)
            hueBucketCount[bucket]++
            if (hsv[1] > hueBucketBestSat[bucket]) {
                hueBucketBestSat[bucket] = hsv[1]
                hueBucketBestArgb[bucket] = pixel
            }
        }
    }

    val isAverageColorBland = (totalSaturation / pixels.size) < 0.20f

    val dominantBucket = hueBucketCount.indices.maxByOrNull { hueBucketCount[it] }
    val accentArgb = dominantBucket
        ?.takeIf { hueBucketCount[it] > 0 }
        ?.let { hueBucketBestArgb[it].takeIf { v -> v != -1 } }

    return DominantColorState(
        color = if (accentArgb != null) Color(accentArgb) else fallback,
        isBland = isAverageColorBland,
    )
}
