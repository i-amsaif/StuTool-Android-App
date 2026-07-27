package com.stutool.stutool.ui.screens.scanner.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageProcessing {

    fun determineSmartFilter(bitmap: Bitmap): ScanFilter {
        val w = bitmap.width
        val h = bitmap.height
        val targetSize = 100
        val scale = targetSize.toFloat() / Math.max(w, h).toFloat()
        val small = Bitmap.createScaledBitmap(
            bitmap,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true
        )

        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        small.recycle()

        var totalLuminance = 0L
        var minLum = 255
        var maxLum = 0
        var totalSaturation = 0L

        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            totalLuminance += lum
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum

            // Simple saturation estimate
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            totalSaturation += (maxC - minC)
        }

        val avgLuminance = totalLuminance / pixels.size.coerceAtLeast(1)
        val contrast = maxLum - minLum
        val avgSaturation = totalSaturation / pixels.size.coerceAtLeast(1)

        // High contrast + bright background + low color -> text document -> B&W
        if (contrast > 120 && avgLuminance > 130 && avgSaturation < 30) {
            return ScanFilter.BW_1
        }

        return ScanFilter.ENHANCED
    }

    fun applyFilter(bitmap: Bitmap, filter: ScanFilter, strength: Float = 1.0f, isActive: () -> Boolean = { true }): Bitmap {
        if (strength <= 0f) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val filtered = applyFilterInternal(bitmap, filter, isActive)
        if (!isActive()) {
            if (filtered !== bitmap) filtered.recycle()
            return bitmap
        }
        if (strength >= 1f || filtered === bitmap) return filtered
        // Blend: result = original*(1-strength) + filtered*strength
        val w = bitmap.width; val h = bitmap.height
        val origPixels = IntArray(w * h)
        val filtPixels = IntArray(w * h)
        bitmap.getPixels(origPixels, 0, w, 0, 0, w, h)
        filtered.getPixels(filtPixels, 0, w, 0, 0, w, h)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inv = 1f - strength
        for (i in origPixels.indices) {
            if (i % 10000 == 0 && !isActive()) {
                result.recycle()
                if (filtered !== bitmap) filtered.recycle()
                return bitmap
            }
            val oR = (origPixels[i] shr 16) and 0xFF; val fR = (filtPixels[i] shr 16) and 0xFF
            val oG = (origPixels[i] shr 8) and 0xFF; val fG = (filtPixels[i] shr 8) and 0xFF
            val oB = origPixels[i] and 0xFF; val fB = filtPixels[i] and 0xFF
            val r = (oR * inv + fR * strength).toInt().coerceIn(0, 255)
            val g = (oG * inv + fG * strength).toInt().coerceIn(0, 255)
            val b = (oB * inv + fB * strength).toInt().coerceIn(0, 255)
            filtPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        result.setPixels(filtPixels, 0, w, 0, 0, w, h)
        if (filtered !== bitmap) filtered.recycle()
        return result
    }

    private fun applyFilterInternal(bitmap: Bitmap, filter: ScanFilter, isActive: () -> Boolean): Bitmap {
        return when (filter) {
            ScanFilter.ORIGINAL -> bitmap
            ScanFilter.ENHANCED -> {
                // Professional Enhancement: Denoise -> Whitening -> Unsharp Mask -> Contrast
                val denoised = medianFilter3x3(bitmap, isActive)
                if (!isActive()) return denoised
                val flattened = adaptiveIlluminationCorrection(denoised, windowDivisor = 15, targetMean = 240f, isActive = isActive)
                if (!isActive()) return flattened
                val sharpened = unsharpMask(flattened, amount = 1.2f, isActive = isActive)
                if (!isActive()) return sharpened
                val contrasted = applySigmoidContrast(sharpened, 1.2f, isActive)
                
                if (denoised !== bitmap) denoised.recycle()
                if (flattened !== denoised && flattened !== bitmap) flattened.recycle()
                if (sharpened !== flattened && sharpened !== bitmap) sharpened.recycle()
                contrasted
            }
            ScanFilter.BW_1 -> {
                // Aggressive text extraction (Printed text)
                val denoised = medianFilter3x3(bitmap, isActive)
                if (!isActive()) return denoised
                val flattened = adaptiveIlluminationCorrection(denoised, windowDivisor = 25, targetMean = 240f, isActive = isActive)
                if (!isActive()) return flattened
                val thresholded = applyLocalAdaptiveThreshold(flattened, windowDivisor = 20, offsetSub = 15, isActive = isActive)
                
                if (denoised !== bitmap) denoised.recycle()
                if (flattened !== denoised && flattened !== bitmap) flattened.recycle()
                thresholded
            }
            ScanFilter.BW_2 -> {
                // Softer B&W (Preserves handwriting better)
                val denoised = medianFilter3x3(bitmap, isActive)
                if (!isActive()) return denoised
                val flattened = adaptiveIlluminationCorrection(denoised, windowDivisor = 15, targetMean = 230f, isActive = isActive)
                if (!isActive()) return flattened
                val thresholded = applyLocalAdaptiveThreshold(flattened, windowDivisor = 10, offsetSub = 5, isActive = isActive)
                
                if (denoised !== bitmap) denoised.recycle()
                if (flattened !== denoised && flattened !== bitmap) flattened.recycle()
                thresholded
            }
            ScanFilter.GRAYSCALE -> {
                // Grayscale for diagrams/sketches
                val gray = toGrayscale(bitmap)
                if (!isActive()) return gray
                val denoised = medianFilter3x3(gray, isActive)
                if (!isActive()) return denoised
                val flattened = adaptiveIlluminationCorrection(denoised, windowDivisor = 15, targetMean = 230f, isActive = isActive)
                if (!isActive()) return flattened
                val contrasted = applySigmoidContrast(flattened, 1.1f, isActive)
                
                if (gray !== bitmap) gray.recycle()
                if (denoised !== gray && denoised !== bitmap) denoised.recycle()
                if (flattened !== denoised && flattened !== bitmap) flattened.recycle()
                contrasted
            }
        }
    }

    // ── Adaptive Illumination Correction (Paper Whitening) ───────────────

    private fun adaptiveIlluminationCorrection(bitmap: Bitmap, windowDivisor: Int = 20, targetMean: Float = 240f, isActive: () -> Boolean = { true }): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Resolution-aware window size (larger for larger images)
        val windowSize = (Math.max(width, height) / windowDivisor).coerceIn(15, 201)
        
        // Build integral image of luminance for fast background estimation
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                rowSum += lum
                integral[y * width + x] = rowSum + if (y > 0) integral[(y - 1) * width + x] else 0L
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)
        val halfW = windowSize / 2

        for (y in 0 until height) {
            if (!isActive()) {
                result.recycle()
                return bitmap
            }
            for (x in 0 until width) {
                val x1 = (x - halfW - 1).coerceAtLeast(-1)
                val y1 = (y - halfW - 1).coerceAtLeast(-1)
                val x2 = (x + halfW).coerceAtMost(width - 1)
                val y2 = (y + halfW).coerceAtMost(height - 1)

                val count = (x2 - x1) * (y2 - y1)
                var sum = integral[y2 * width + x2]
                if (x1 >= 0) sum -= integral[y2 * width + x1]
                if (y1 >= 0) sum -= integral[y1 * width + x2]
                if (x1 >= 0 && y1 >= 0) sum += integral[y1 * width + x1]

                val localMean = (sum / count).toInt().coerceAtLeast(1)
                
                val p = pixels[y * width + x]
                val origR = (p shr 16) and 0xFF
                val origG = (p shr 8) and 0xFF
                val origB = p and 0xFF
                
                // Adaptive whitening: scale pixel by (targetMean / localMean)
                // Extends dynamic range, lifting shadows while protecting dark ink
                val scale = (targetMean / localMean).coerceIn(0.5f, 3.0f)
                
                val r = (origR * scale).toInt().coerceIn(0, 255)
                val g = (origG * scale).toInt().coerceIn(0, 255)
                val b = (origB * scale).toInt().coerceIn(0, 255)

                outPixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    // ── Sigmoid Contrast (Enhance filter) ───────────────────────────────

    private fun applySigmoidContrast(bitmap: Bitmap, strength: Float, isActive: () -> Boolean = { true }): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)
        
        // Precompute sigmoid LUT
        val lut = IntArray(256)
        val k = strength * 5f // steepness
        for (i in 0..255) {
            val normalized = i / 255f
            val transformed = 1f / (1f + Math.exp((-k * (normalized - 0.5f)).toDouble())).toFloat()
            lut[i] = (transformed * 255f).toInt().coerceIn(0, 255)
        }

        for (i in pixels.indices) {
            if (i % 10000 == 0 && !isActive()) {
                result.recycle()
                return bitmap
            }
            val p = pixels[i]
            val r = lut[(p shr 16) and 0xFF]
            val g = lut[(p shr 8) and 0xFF]
            val b = lut[p and 0xFF]
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    // ── Local Adaptive Threshold (window-based, scanner-grade B&W) ──────

    private fun applyLocalAdaptiveThreshold(bitmap: Bitmap, windowDivisor: Int, offsetSub: Int, isActive: () -> Boolean = { true }): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Convert to grayscale luminance array
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Build integral image for fast local mean computation
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += gray[y * width + x]
                integral[y * width + x] = rowSum + if (y > 0) integral[(y - 1) * width + x] else 0L
            }
        }

        // Local adaptive threshold with resolution-aware window
        val windowSize = (Math.max(width, height) / windowDivisor).coerceIn(15, 81)
        val halfW = windowSize / 2
        // Dynamic offset based on window size to preserve text edges safely
        val offset = -(Math.max(width, height) / 300).coerceIn(2, 20) - offsetSub

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)

        for (y in 0 until height) {
            if (!isActive()) {
                result.recycle()
                return bitmap
            }
            for (x in 0 until width) {
                val x1 = (x - halfW - 1).coerceAtLeast(-1)
                val y1 = (y - halfW - 1).coerceAtLeast(-1)
                val x2 = (x + halfW).coerceAtMost(width - 1)
                val y2 = (y + halfW).coerceAtMost(height - 1)

                val count = (x2 - x1) * (y2 - y1)

                var sum = integral[y2 * width + x2]
                if (x1 >= 0) sum -= integral[y2 * width + x1]
                if (y1 >= 0) sum -= integral[y1 * width + x2]
                if (x1 >= 0 && y1 >= 0) sum += integral[y1 * width + x1]

                val localMean = (sum / count).toInt()
                val threshold = localMean + offset

                outPixels[y * width + x] = if (gray[y * width + x] > threshold) {
                    0xFFFFFFFF.toInt() // White
                } else {
                    0xFF000000.toInt() // Black
                }
            }
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    // ── 3×3 Median Filter (noise reduction for COLOR filter) ────────────

    private fun medianFilter3x3(bitmap: Bitmap, isActive: () -> Boolean = { true }): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)

        val rArr = IntArray(9)
        val gArr = IntArray(9)
        val bArr = IntArray(9)

        for (y in 1 until height - 1) {
            if (!isActive()) {
                result.recycle()
                return bitmap
            }
            for (x in 1 until width - 1) {
                var idx = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val p = pixels[(y + dy) * width + (x + dx)]
                        rArr[idx] = (p shr 16) and 0xFF
                        gArr[idx] = (p shr 8) and 0xFF
                        bArr[idx] = p and 0xFF
                        idx++
                    }
                }
                rArr.sort()
                gArr.sort()
                bArr.sort()
                outPixels[y * width + x] = (0xFF shl 24) or (rArr[4] shl 16) or (gArr[4] shl 8) or bArr[4]
            }
        }

        // Copy border pixels
        for (x in 0 until width) {
            outPixels[x] = pixels[x]
            outPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + width - 1] = pixels[y * width + width - 1]
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    // ── Brightness normalization ────────────────────────────────────────

    private fun normalizeBrightness(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalLuminance = 0L
        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
        }
        val avgLuminance = totalLuminance / pixels.size.coerceAtLeast(1)

        val diff = 200 - avgLuminance
        if (diff <= 0) return bitmap

        return increaseBrightness(bitmap, diff.toFloat().coerceAtMost(60f))
    }

    private fun increaseBrightness(bitmap: Bitmap, value: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, value,
                0f, 1f, 0f, 0f, value,
                0f, 0f, 1f, 0f, value,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun increaseContrast(bitmap: Bitmap, scale: Float): Bitmap {
        // Center contrast around 128 for natural results
        val translate = 128f * (1f - scale)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun unsharpMask(bitmap: Bitmap, amount: Float = 1.0f, isActive: () -> Boolean = { true }): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)

        // 3x3 Gaussian-like blur kernel
        val kernel = intArrayOf(
            1, 2, 1,
            2, 4, 2,
            1, 2, 1
        )
        val kernelSum = 16

        for (y in 1 until height - 1) {
            if (!isActive()) {
                result.recycle()
                return bitmap
            }
            for (x in 1 until width - 1) {
                var rBlur = 0
                var gBlur = 0
                var bBlur = 0

                var kIndex = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * width + (x + kx)]
                        val weight = kernel[kIndex++]

                        rBlur += ((pixel shr 16) and 0xFF) * weight
                        gBlur += ((pixel shr 8) and 0xFF) * weight
                        bBlur += (pixel and 0xFF) * weight
                    }
                }

                rBlur /= kernelSum
                gBlur /= kernelSum
                bBlur /= kernelSum

                val origP = pixels[y * width + x]
                val origR = (origP shr 16) and 0xFF
                val origG = (origP shr 8) and 0xFF
                val origB = origP and 0xFF

                // Unsharp mask: out = orig + amount * (orig - blur)
                val r = (origR + amount * (origR - rBlur)).toInt().coerceIn(0, 255)
                val g = (origG + amount * (origG - gBlur)).toInt().coerceIn(0, 255)
                val b = (origB + amount * (origB - bBlur)).toInt().coerceIn(0, 255)

                outPixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Copy border pixels
        for (x in 0 until width) {
            outPixels[x] = pixels[x]
            outPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + width - 1] = pixels[y * width + width - 1]
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }
}

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCED("Enhance"),
    BW_1("B&W 1"),
    BW_2("B&W 2"),
    GRAYSCALE("Gray")
}
