package com.whaleup.gameshub.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.random.Random

/**
 * Custom Drawable for the game card content overlay.
 *
 * Implements the Figma spec:
 *  - Fill: Linear gradient (angle ~180°) at 10% opacity
 *      stops: #336B85 0% → #0C191F 30% → #0C191F 62% → #336B85 100%
 *  - Background blur: provided by the game bg image and vignettes behind the card DIV
 *  - Noise: multi-density noise at 15% opacity (size 0.5×0.5)
 *  - Inner shadow 1: X-5, Y0, blur 4, spread 0, #00A8F3 at 10%
 *  - Inner shadow 2: X2, Y-2, blur 4, spread 0, #00A8F3 at 20%
 *  - Stroke: none
 */
class GameCardDrawable(private val cornerRadius: Float) : Drawable() {

    // ── Gradient fill (10% opacity) ──────────────────────────────────────────
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Noise overlay ────────────────────────────────────────────────────────
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var noiseBitmap: Bitmap? = null
    private var lastNoiseWidth = 0
    private var lastNoiseHeight = 0

    // ── Inner shadow 1: X-5, Y0, blur 4, #00A8F3 10% ────────────────────────
    private val innerShadow1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb((0.10f * 255).toInt(), 0x00, 0xA8, 0xF3)
        strokeWidth = 8f
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.INNER)
    }

    // ── Inner shadow 2: X2, Y-2, blur 4, #00A8F3 20% ────────────────────────
    private val innerShadow2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb((0.20f * 255).toInt(), 0x00, 0xA8, 0xF3)
        strokeWidth = 8f
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.INNER)
    }

    private val clipPath = Path()
    private val rect = RectF()
    private var overallAlpha = 255

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        rect.set(0f, 0f, w, h)
        clipPath.reset()
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

        // Save to a layer so compositing effects (SRC_ATOP) work within card bounds
        val saveCount = canvas.saveLayer(rect, null)

        // 1. Linear gradient fill at 10% opacity.
        val shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(
                applyOpacity(0x336B85, 0.10f),   // #336B85 at 10% -> stop 0%
                applyOpacity(0x0C191F, 0.10f),   // #0C191F at 10% -> stop 30%
                applyOpacity(0x0C191F, 0.10f),   // #0C191F at 10% -> stop 62%
                applyOpacity(0x336B85, 0.10f)    // #336B85 at 10% -> stop 100%
            ),
            floatArrayOf(0f, 0.30f, 0.62f, 1.00f),
            Shader.TileMode.CLAMP
        )
        gradientPaint.shader = shader
        gradientPaint.alpha = overallAlpha
        canvas.drawPath(clipPath, gradientPaint)

        // 2. Noise overlay at 100% density and 15% opacity.
        val noiseBmp = getOrCreateNoise(w.toInt(), h.toInt())
        val noiseShader = BitmapShader(noiseBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        // Scale the noise so each texel is 0.5 px → matches "Noise Size X0.5, Y0.5"
        val noiseMatrix = Matrix()
        noiseMatrix.setScale(0.5f, 0.5f)
        noiseShader.setLocalMatrix(noiseMatrix)
        noisePaint.shader = noiseShader
        noisePaint.alpha = ((0.15f * 255).toInt() * overallAlpha / 255f).toInt()
        canvas.drawPath(clipPath, noisePaint)

        // 3. Inner shadow 1: X-5, Y0, blur 4, spread 0, #00A8F3 at 10%.
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(-5f, 0f)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, innerShadow1Paint)
        canvas.restore()

        // 4. Inner shadow 2: X2, Y-2, blur 4, spread 0, #00A8F3 at 20%.
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(2f, -2f)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, innerShadow2Paint)
        canvas.restore()

        canvas.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        overallAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        gradientPaint.colorFilter = colorFilter
        noisePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a packed ARGB color from a 0xRRGGBB int and a [0..1] opacity fraction.
     */
    private fun applyOpacity(rgb: Int, opacity: Float): Int {
        val a = (opacity * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return Color.argb(a, r, g, b)
    }

    /**
     * Generates (or returns cached) a grayscale noise bitmap.
     * The bitmap is 128×128 px and tiled via the BitmapShader.
     */
    private fun getOrCreateNoise(viewW: Int, viewH: Int): Bitmap {
        val existing = noiseBitmap
        if (existing != null && lastNoiseWidth == viewW && lastNoiseHeight == viewH) {
            return existing
        }
        // Fixed-seed noise tile — deterministic so it doesn't flicker on redraw
        val tileSize = 128
        val bmp = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val rnd = Random(42L)
        val pixels = IntArray(tileSize * tileSize)
        for (i in pixels.indices) {
            val lum = rnd.nextInt(256)
            // Noise is multi-density (luminance only), at 100% density → pure B&W grain
            pixels[i] = Color.argb(255, lum, lum, lum)
        }
        bmp.setPixels(pixels, 0, tileSize, 0, 0, tileSize, tileSize)
        noiseBitmap = bmp
        lastNoiseWidth = viewW
        lastNoiseHeight = viewH
        return bmp
    }
}
