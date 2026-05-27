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
    url: Any?,
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
            .size(128)
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
    val w = bitmap.width
    val h = bitmap.height
    val size = minOf(w, h)
    val hsv = FloatArray(3)

    val pixels = IntArray(size * size)
    bitmap.getPixels(pixels, 0, w, 0, 0, size, size)

    // Single pass: average saturation + dominant hue bucket.
    // 36 buckets of 10° each — only count pixels that are meaningfully colored.
    val hueBucketCount = IntArray(36)
    val hueBucketBestArgb = IntArray(36) { -1 }
    val hueBucketBestSat = FloatArray(36)
    var totalSaturation = 0f

    for (pixel in pixels) {
        android.graphics.Color.colorToHSV(pixel, hsv)
        totalSaturation += hsv[1]
        if (hsv[1] >= 0.15f && hsv[2] >= 0.15f) {
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

    // If the dominant color only covers a small fraction of pixels the image is
    // multi-coloured (e.g. rainbow gradient) — no single accent makes sense.
    val dominantBucketFraction = if (dominantBucket != null)
        hueBucketCount[dominantBucket].toFloat() / pixels.size else 0f
    val isTooMulticoloured = dominantBucketFraction < 0.15f

    // ── Edge uniformity check ─────────────────────────────────────────────────
    // Sample the outer ring of the bitmap (4 sides × 9 points, 1px inset).
    // If all sampled pixels are roughly the same color (small RGB range), the
    // cover has a uniform border — treat it like bland and use the blurred image.
    val inset = 1
    val numPerSide = 9
    val edgeArgbs = buildList {
        repeat(numPerSide) { i ->
            val t = (i + 1).toFloat() / (numPerSide + 1)
            val pos = (inset + t * (size - 2 * inset)).toInt()
            add(pixels[pos * size + inset])                      // left
            add(pixels[pos * size + (size - 1 - inset)])         // right
            add(pixels[inset * size + pos])                      // top
            add(pixels[(size - 1 - inset) * size + pos])         // bottom
        }
    }
    val rRange = edgeArgbs.maxOf { android.graphics.Color.red(it) }   - edgeArgbs.minOf { android.graphics.Color.red(it) }
    val gRange = edgeArgbs.maxOf { android.graphics.Color.green(it) } - edgeArgbs.minOf { android.graphics.Color.green(it) }
    val bRange = edgeArgbs.maxOf { android.graphics.Color.blue(it) }  - edgeArgbs.minOf { android.graphics.Color.blue(it) }
    val hasUniformEdge = rRange < 40 && gRange < 40 && bRange < 40

    // If edges are uniform, check whether that edge color is itself vibrant.
    // Vibrant uniform edge → use it as the accent (e.g. bright-pink border).
    // Bland uniform edge (white/black/gray/brown border) → blurred image fallback.
    var uniformEdgeAccentArgb: Int? = null
    if (hasUniformEdge) {
        val avgR = edgeArgbs.sumOf { android.graphics.Color.red(it) }   / edgeArgbs.size
        val avgG = edgeArgbs.sumOf { android.graphics.Color.green(it) } / edgeArgbs.size
        val avgB = edgeArgbs.sumOf { android.graphics.Color.blue(it) }  / edgeArgbs.size
        val avgArgb = android.graphics.Color.rgb(avgR, avgG, avgB)
        android.graphics.Color.colorToHSV(avgArgb, hsv)
        if (hsv[1] > 0.28f && hsv[2] > 0.25f) uniformEdgeAccentArgb = avgArgb
    }

    // ── Final accent + bland decision ─────────────────────────────────────────
    val finalAccentArgb: Int?
    val isFinalBland: Boolean

    // Shared center-based bland check — used when there's no vibrant uniform edge
    val isDominantColorMuted = if (accentArgb != null) {
        android.graphics.Color.colorToHSV(accentArgb, hsv)
        val sat = hsv[1]; val value = hsv[2]; val hue = hsv[0]
        sat < 0.30f || (hue < 50f && sat < 0.70f && value < 0.70f)
    } else false
    val centerIsBland = isDominantColorMuted || isTooMulticoloured || (isAverageColorBland && accentArgb == null)

    if (hasUniformEdge && uniformEdgeAccentArgb != null) {
        // Vibrant uniform edge (e.g. bright-pink border) → use it as accent
        finalAccentArgb = uniformEdgeAccentArgb
        isFinalBland = false
    } else {
        // No vibrant uniform edge: black/white/dark border or no uniform edge at all
        // → fall through to center analysis in both cases
        finalAccentArgb = accentArgb
        isFinalBland = centerIsBland
    }

    return DominantColorState(
        color = if (finalAccentArgb != null) Color(finalAccentArgb) else fallback,
        isBland = isFinalBland,
    )
}
