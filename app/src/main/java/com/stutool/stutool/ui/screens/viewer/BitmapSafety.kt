package com.stutool.stutool.ui.screens.viewer

import android.util.Log
import kotlin.math.sqrt

object BitmapSafety {
    private const val TAG = "BitmapSafety"

    // Most Android devices support 4096 OpenGL texture sizes. Modern ones support 8192+.
    // We use 4096 as an ultra-safe dimension ceiling to prevent Hardware Canvas crashes 
    // across all possible device configurations.
    const val SAFE_MAX_DIMENSION = 4096

    // Adaptive pixel count based on device memory can be injected here, but a 
    // baseline 16M pixels (64MB) provides a very strong safety net against OOMs.
    const val MAX_PIXEL_COUNT = 16_000_000L

    data class SafeDimensions(
        val width: Int,
        val height: Int,
        val isClamped: Boolean,
        val clampReason: String? = null
    )

    /**
     * Calculates safely constrained bitmap dimensions to prevent OOM and Canvas crashes.
     * Handles overflow, invalid inputs, and applies dynamic clamping based on memory.
     *
     * @param targetWidth The desired pixel width
     * @param pageRatio The aspect ratio (width / height)
     * @param maxMemoryBytes Optional maximum memory threshold (can be dynamically fetched from Runtime)
     */
    fun calculateSafeDimensions(
        targetWidth: Int,
        pageRatio: Float,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()
    ): SafeDimensions {
        // 1. Validation & Overflow Protection
        if (targetWidth <= 0 || pageRatio <= 0f || pageRatio.isNaN() || pageRatio.isInfinite()) {
            Log.e(TAG, "Invalid dimensions: targetWidth=$targetWidth, pageRatio=$pageRatio")
            return SafeDimensions(1, 1, true, "Invalid inputs")
        }

        // 2. Base Dimensions
        var safeWidth = targetWidth
        var safeHeight = (targetWidth / pageRatio).toInt()
        var isClamped = false
        val clampReasons = mutableListOf<String>()

        // 3. Max Dimension Clamping (Hardware Texture Limits)
        if (safeWidth > SAFE_MAX_DIMENSION) {
            safeWidth = SAFE_MAX_DIMENSION
            safeHeight = (safeWidth / pageRatio).toInt()
            isClamped = true
            clampReasons.add("Exceeded MAX_DIMENSION ($SAFE_MAX_DIMENSION) horizontally")
        }
        
        if (safeHeight > SAFE_MAX_DIMENSION) {
            safeHeight = SAFE_MAX_DIMENSION
            safeWidth = (safeHeight * pageRatio).toInt()
            isClamped = true
            clampReasons.add("Exceeded MAX_DIMENSION ($SAFE_MAX_DIMENSION) vertically")
        }

        // 4. Memory / Pixel Count Clamping
        // We allow a single bitmap to use at most 1/4 of the total max heap, OR MAX_PIXEL_COUNT (whichever is smaller)
        // 1 pixel = 4 bytes (ARGB_8888)
        val memoryBasedPixelCap = (maxMemoryBytes / 4) / 4 // 1/4th of memory
        val activePixelCap = minOf(MAX_PIXEL_COUNT, memoryBasedPixelCap)

        val currentPixels = safeWidth.toLong() * safeHeight.toLong()
        
        if (currentPixels > activePixelCap) {
            val scaleDown = sqrt(activePixelCap.toDouble() / currentPixels.toDouble())
            safeWidth = (safeWidth * scaleDown).toInt()
            safeHeight = (safeHeight * scaleDown).toInt()
            isClamped = true
            clampReasons.add("Exceeded pixel cap ($activePixelCap pixels)")
        }

        // Final safety check (Zero protection)
        safeWidth = safeWidth.coerceAtLeast(1)
        safeHeight = safeHeight.coerceAtLeast(1)

        val finalReason = if (isClamped) clampReasons.joinToString("; ") else null
        
        if (isClamped) {
            Log.d(TAG, "Clamped bitmap dimensions. Requested Width: $targetWidth, Safe: ${safeWidth}x${safeHeight}, Scaling Factor: ${safeWidth.toFloat() / targetWidth}, Reason: $finalReason")
        }

        return SafeDimensions(safeWidth, safeHeight, isClamped, finalReason)
    }
}
