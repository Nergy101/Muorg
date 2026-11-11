package nl.muorg.android.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bitmap treatments for the Now Playing widget. RemoteViews cannot blur,
 * clip or dim an image, so the album art is prepared here and handed over
 * finished.
 */
internal object WidgetArt {

    /** Longest side of the heavily downscaled copy the blur runs on. */
    private const val BLUR_WORK_PX = 48
    private const val BLUR_RADIUS = 3
    private const val BLUR_PASSES = 3

    /** Corner radius of the cover as a fraction of its side: "slightly" rounded. */
    private const val COVER_CORNER_FRACTION = 0.08f

    /** 0 = black, 1 = untouched. */
    private const val BACKDROP_BRIGHTNESS = 0.45f

    /**
     * [src] centre-cropped to a square of [sizePx] with rounded corners. The
     * widget's ImageView scales it to whatever room the layout leaves.
     */
    fun roundedCover(src: Bitmap, sizePx: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
        return roundCorners(scaled, sizePx * COVER_CORNER_FRACTION)
    }

    /**
     * [src] centre-cropped to the widget's aspect ratio, blurred hard, dimmed
     * so white text stays readable, and rounded to match the widget's own
     * corners (launchers before Android 12 do not clip them).
     */
    fun blurredBackdrop(src: Bitmap, widthPx: Int, heightPx: Int, cornerPx: Float): Bitmap {
        val targetRatio = widthPx.toFloat() / heightPx
        val srcRatio = src.width.toFloat() / src.height
        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = src.height
            cropW = (src.height * targetRatio).roundToInt().coerceAtLeast(1)
        } else {
            cropW = src.width
            cropH = (src.width / targetRatio).roundToInt().coerceAtLeast(1)
        }
        val cropped = Bitmap.createBitmap(src, (src.width - cropW) / 2, (src.height - cropH) / 2, cropW, cropH)

        // Blur a tiny copy: a few box-blur passes over ~48px is both cheap and,
        // once scaled back up, far smoother than any radius on the full image.
        val workScale = BLUR_WORK_PX.toFloat() / max(cropW, cropH)
        val workW = (cropW * workScale).roundToInt().coerceAtLeast(2)
        val workH = (cropH * workScale).roundToInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(cropped, workW, workH, true)
        val pixels = IntArray(workW * workH)
        small.getPixels(pixels, 0, workW, 0, 0, workW, workH)
        repeat(BLUR_PASSES) {
            boxBlur(pixels, workW, workH, horizontal = true)
            boxBlur(pixels, workW, workH, horizontal = false)
        }
        dim(pixels)
        val blurred = Bitmap.createBitmap(pixels, workW, workH, Bitmap.Config.ARGB_8888)
        val full = Bitmap.createScaledBitmap(blurred, widthPx, heightPx, true)
        return roundCorners(full, cornerPx)
    }

    private fun roundCorners(src: Bitmap, radiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun dim(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16 and 0xFF) * BACKDROP_BRIGHTNESS).toInt()
            val g = ((p shr 8 and 0xFF) * BACKDROP_BRIGHTNESS).toInt()
            val b = ((p and 0xFF) * BACKDROP_BRIGHTNESS).toInt()
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** One in-place box-blur pass (edges clamp) along rows or columns. */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = BLUR_RADIUS * 2 + 1
        val line = IntArray(length)
        for (l in 0 until lines) {
            for (i in 0 until length) {
                line[i] = pixels[if (horizontal) l * w + i else i * w + l]
            }
            for (i in 0 until length) {
                var r = 0
                var g = 0
                var b = 0
                for (k in -BLUR_RADIUS..BLUR_RADIUS) {
                    val p = line[(i + k).coerceIn(0, length - 1)]
                    r += p shr 16 and 0xFF
                    g += p shr 8 and 0xFF
                    b += p and 0xFF
                }
                pixels[if (horizontal) l * w + i else i * w + l] =
                    (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            }
        }
    }
}
