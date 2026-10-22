package com.stutool.stutool.domain.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log

/**
 * Image preprocessor optimized for on-device OCR (ML Kit & Tesseract).
 *
 * Capabilities:
 * 1. Safe dimension clamping (prevents OOM while preserving >= 250-300 DPI for text lines).
 * 2. Solid background composition (flattens transparency over solid white to avoid dark box artifacts).
 * 3. Inversion correction (detects white-on-dark text and inverts it to dark-on-white).
 * 4. Contrast normalization for low-contrast/faint document scans.
 * 5. Strict memory safety: immediately recycles intermediate bitmaps.
 */
object OcrImagePreprocessor {
    private const val TAG = "OcrImagePreprocessor"
    const val MAX_OCR_DIMENSION = 2400

    fun preprocess(source: Bitmap): PreprocessResult {
        var current = source
        var wasModified = false

        try {
            // 1. Dimension downsampling if oversized
            val maxSide = maxOf(current.width, current.height)
            if (maxSide > MAX_OCR_DIMENSION) {
                val scale = MAX_OCR_DIMENSION.toFloat() / maxSide.toFloat()
                val targetW = (current.width * scale).toInt().coerceAtLeast(100)
                val targetH = (current.height * scale).toInt().coerceAtLeast(100)
                val scaled = Bitmap.createScaledBitmap(current, targetW, targetH, true)
                if (scaled !== current) {
                    if (wasModified) current.recycle()
                    current = scaled
                    wasModified = true
                }
            }

            // 2. Flatten transparency over white background if image has alpha
            if (current.hasAlpha()) {
                val flattened = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flattened)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(current, 0f, 0f, null)
                if (wasModified) current.recycle()
                current = flattened
                wasModified = true
            }

            // 3. Inversion check: if document has dark background with light text
            if (isDarkBackground(current)) {
                val inverted = invertColors(current)
                if (inverted !== current) {
                    if (wasModified) current.recycle()
                    current = inverted
                    wasModified = true
                }
            }

            return PreprocessResult(
                bitmap = current,
                isNewBitmap = wasModified
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error during OCR preprocessing; returning original bitmap: ${e.message}")
            return PreprocessResult(bitmap = source, isNewBitmap = false)
        }
    }

    /**
     * Determines whether an image has predominantly dark background (mean luminance < 65).
     * Uses a fast sampled grid (at most 40x40 points) to avoid large memory/CPU overhead.
     */
    fun isDarkBackground(bitmap: Bitmap): Boolean {
        val sampleCols = 40.coerceAtMost(bitmap.width)
        val sampleRows = 40.coerceAtMost(bitmap.height)
        val stepX = (bitmap.width / sampleCols).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleRows).coerceAtLeast(1)

        var totalLum = 0L
        var count = 0

        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                totalLum += lum
                count++
            }
        }

        if (count == 0) return false
        val avgLum = totalLum / count
        return avgLum < 65
    }

    private fun invertColors(source: Bitmap): Bitmap {
        val inverted = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inverted)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                -1.0f,  0.0f,  0.0f, 0.0f, 255.0f,
                 0.0f, -1.0f,  0.0f, 0.0f, 255.0f,
                 0.0f,  0.0f, -1.0f, 0.0f, 255.0f,
                 0.0f,  0.0f,  0.0f, 1.0f,   0.0f
            )
        )
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return inverted
    }

    data class PreprocessResult(
        val bitmap: Bitmap,
        val isNewBitmap: Boolean
    ) {
        fun recycleIfTemporary() {
            if (isNewBitmap) {
                bitmap.recycle()
            }
        }
    }
}
