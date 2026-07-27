package com.stutool.stutool.ui.screens.scanner.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.util.Log
import kotlin.math.roundToInt

/**
 * Perspective transform utility.
 * Uses Android's Matrix.setPolyToPoly() to warp a quadrilateral region
 * of the source image into a flat rectangle.
 *
 * All public methods are crash-safe: they catch exceptions and return null
 * on failure, never throwing to the caller.
 */
object PerspectiveTransform {

    private const val TAG = "PerspectiveTransform"

    // Maximum output dimension to prevent OOM on very large captures
    private const val MAX_OUTPUT_DIM = 3000

    /**
     * Warps the source bitmap so the [quad] region becomes a flat rectangle.
     *
     * @param source The full-resolution source bitmap (must not be recycled)
     * @param quad   The 4 corners of the document in source bitmap coordinates
     * @return A new bitmap containing the perspective-corrected document, or null on failure
     */
    fun warpBitmap(source: Bitmap, quad: Quad): Bitmap? {
        try {
            // Validate source bitmap
            if (source.isRecycled || source.width <= 0 || source.height <= 0) {
                Log.e(TAG, "warpBitmap: Invalid source bitmap (recycled=${source.isRecycled}, " +
                        "w=${source.width}, h=${source.height})")
                return null
            }

            // Validate and sanitize quad
            val safeQuad = quad.sanitize(source.width, source.height)
            Log.d(TAG, "warpBitmap: source=${source.width}x${source.height}, " +
                    "quad=[TL(${safeQuad.topLeft.x},${safeQuad.topLeft.y}), " +
                    "TR(${safeQuad.topRight.x},${safeQuad.topRight.y}), " +
                    "BR(${safeQuad.bottomRight.x},${safeQuad.bottomRight.y}), " +
                    "BL(${safeQuad.bottomLeft.x},${safeQuad.bottomLeft.y})]")

            // Calculate output dimensions from the quad edges
            var outW = safeQuad.estimatedWidth().roundToInt()
            var outH = safeQuad.estimatedHeight().roundToInt()

            // Clamp to prevent OOM
            if (outW > MAX_OUTPUT_DIM || outH > MAX_OUTPUT_DIM) {
                val scale = MAX_OUTPUT_DIM.toFloat() / maxOf(outW, outH)
                outW = (outW * scale).roundToInt()
                outH = (outH * scale).roundToInt()
            }

            // Ensure minimum size
            outW = outW.coerceIn(100, MAX_OUTPUT_DIM)
            outH = outH.coerceIn(100, MAX_OUTPUT_DIM)

            Log.d(TAG, "warpBitmap: output=${outW}x${outH}")

            // Source quad corners (in source bitmap space)
            val src = floatArrayOf(
                safeQuad.topLeft.x, safeQuad.topLeft.y,
                safeQuad.topRight.x, safeQuad.topRight.y,
                safeQuad.bottomRight.x, safeQuad.bottomRight.y,
                safeQuad.bottomLeft.x, safeQuad.bottomLeft.y
            )

            // Destination rectangle corners (in output bitmap space)
            val dst = floatArrayOf(
                0f, 0f,
                outW.toFloat(), 0f,
                outW.toFloat(), outH.toFloat(),
                0f, outH.toFloat()
            )

            // Compute the perspective transform matrix
            val matrix = Matrix()
            val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)
            if (!success) {
                Log.e(TAG, "warpBitmap: setPolyToPoly returned false (degenerate quad)")
                return null
            }

            // Create output bitmap and draw the transformed source
            val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE) // White background

            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true // Bilinear filtering for smooth output
                isDither = true // Add dithering to prevent banding on gradients
            }

            canvas.drawBitmap(source, matrix, paint)

            // Apply a lightweight post-crop sharpening pass to counteract bilinear softening
            val sharpenedOutput = applyLightSharpen(output)
            if (sharpenedOutput !== output) {
                output.recycle()
            }

            Log.d(TAG, "warpBitmap: SUCCESS output=${sharpenedOutput.width}x${sharpenedOutput.height}")
            return sharpenedOutput
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "warpBitmap: OOM during transform", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "warpBitmap: Transform failed", e)
            return null
        }
    }

    /**
     * Memory-safe version: loads the source with inSampleSize if it's very large,
     * applies the transform, then returns the result bitmap.
     *
     * @return The warped bitmap, or null on any failure.
     */
    fun warpFromFile(filePath: String, quad: Quad): Bitmap? {
        try {
            Log.d(TAG, "warpFromFile: path=$filePath")

            // Check dimensions first
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, opts)

            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                Log.e(TAG, "warpFromFile: Could not read image dimensions from $filePath")
                return null
            }

            Log.d(TAG, "warpFromFile: original=${opts.outWidth}x${opts.outHeight}")

            // Calculate sample size to keep memory reasonable
            val maxSourceDim = 4000
            opts.inSampleSize = 1
            while (opts.outWidth / opts.inSampleSize > maxSourceDim ||
                opts.outHeight / opts.inSampleSize > maxSourceDim
            ) {
                opts.inSampleSize *= 2
            }

            opts.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeFile(filePath, opts)
            if (bitmap == null) {
                Log.e(TAG, "warpFromFile: BitmapFactory.decodeFile returned null")
                return null
            }

            Log.d(TAG, "warpFromFile: loaded=${bitmap.width}x${bitmap.height}, inSampleSize=${opts.inSampleSize}")

            // Scale quad coordinates if we downsampled
            val scaledQuad = if (opts.inSampleSize > 1) {
                val s = 1f / opts.inSampleSize
                Quad(
                    topLeft = android.graphics.PointF(quad.topLeft.x * s, quad.topLeft.y * s),
                    topRight = android.graphics.PointF(quad.topRight.x * s, quad.topRight.y * s),
                    bottomRight = android.graphics.PointF(quad.bottomRight.x * s, quad.bottomRight.y * s),
                    bottomLeft = android.graphics.PointF(quad.bottomLeft.x * s, quad.bottomLeft.y * s)
                )
            } else {
                quad
            }

            val result = warpBitmap(bitmap, scaledQuad)
            bitmap.recycle()

            if (result == null) {
                Log.e(TAG, "warpFromFile: warpBitmap returned null")
            }
            return result
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "warpFromFile: OOM", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "warpFromFile: Failed", e)
            return null
        }
    }

    /**
     * Counteracts the slight blurring introduced by bilinear interpolation
     * during the perspective matrix warp. Uses a mild laplacian cross kernel.
     */
    private fun applyLightSharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)

        // Gentle cross kernel: [ 0 -0.5 0 ; -0.5 3 -0.5 ; 0 -0.5 0 ]
        val centerWeight = 3f
        val edgeWeight = -0.5f

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f
                var g = 0f
                var b = 0f

                // Center
                val pc = pixels[y * width + x]
                r += ((pc shr 16) and 0xFF) * centerWeight
                g += ((pc shr 8) and 0xFF) * centerWeight
                b += (pc and 0xFF) * centerWeight

                // Top
                val pt = pixels[(y - 1) * width + x]
                r += ((pt shr 16) and 0xFF) * edgeWeight
                g += ((pt shr 8) and 0xFF) * edgeWeight
                b += (pt and 0xFF) * edgeWeight

                // Bottom
                val pb = pixels[(y + 1) * width + x]
                r += ((pb shr 16) and 0xFF) * edgeWeight
                g += ((pb shr 8) and 0xFF) * edgeWeight
                b += (pb and 0xFF) * edgeWeight

                // Left
                val pl = pixels[y * width + (x - 1)]
                r += ((pl shr 16) and 0xFF) * edgeWeight
                g += ((pl shr 8) and 0xFF) * edgeWeight
                b += (pl and 0xFF) * edgeWeight

                // Right
                val pr = pixels[y * width + (x + 1)]
                r += ((pr shr 16) and 0xFF) * edgeWeight
                g += ((pr shr 8) and 0xFF) * edgeWeight
                b += (pr and 0xFF) * edgeWeight

                outPixels[y * width + x] = (0xFF shl 24) or
                        (r.toInt().coerceIn(0, 255) shl 16) or
                        (g.toInt().coerceIn(0, 255) shl 8) or
                        b.toInt().coerceIn(0, 255)
            }
        }

        // Copy borders
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
