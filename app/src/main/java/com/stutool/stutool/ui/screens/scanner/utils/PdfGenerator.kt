package com.stutool.stutool.ui.screens.scanner.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {
    // A4 at ~150 DPI (standard document quality, memory efficient)
    private const val A4_WIDTH = 1240
    private const val A4_HEIGHT = 1754
    private const val LETTER_WIDTH = 1275
    private const val LETTER_HEIGHT = 1650

    /**
     * Generates a PDF from a list of page URIs.
     * @param context Android context
     * @param pages List of image URIs to include
     * @param customFileName Optional custom file name (without .pdf extension)
     * @param onProgress Callback with (currentPage, totalPages) for progress UI
     * @return The content:// URI of the saved PDF, or null on failure
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<Uri>,
        customFileName: String? = null,
        pageSizeStr: String = "A4",
        isGrayscale: Boolean = false,
        addMargin: Boolean = false,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Uri? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()
        
        val pageWidth = if (pageSizeStr == "Letter") LETTER_WIDTH else A4_WIDTH
        val pageHeight = if (pageSizeStr == "Letter") LETTER_HEIGHT else A4_HEIGHT

        for ((index, uri) in pages.withIndex()) {
            onProgress(index + 1, pages.size)

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index).create()
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            canvas.drawColor(Color.WHITE) // White background, never transparent

            val bitmap = loadAndScaleBitmap(context, uri, pageWidth, pageHeight)
            if (bitmap != null) {
                // Calculate centered rectangle maintaining aspect ratio
                val marginOffset = if (addMargin) (Math.min(pageWidth, pageHeight) * 0.05f).toInt() else 0
                val usableWidth = pageWidth - (marginOffset * 2)
                val usableHeight = pageHeight - (marginOffset * 2)
                
                // Scale bitmap to fit usable area
                val scale = Math.min(
                    usableWidth.toFloat() / bitmap.width,
                    usableHeight.toFloat() / bitmap.height
                )
                
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()
                
                val left = marginOffset + (usableWidth - scaledWidth) / 2
                val top = marginOffset + (usableHeight - scaledHeight) / 2
                val destRect = Rect(left, top, left + scaledWidth, top + scaledHeight)

                val paint = android.graphics.Paint()
                if (isGrayscale) {
                    val colorMatrix = android.graphics.ColorMatrix()
                    colorMatrix.setSaturation(0f)
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                }

                canvas.drawBitmap(bitmap, null, destRect, paint)
                bitmap.recycle() // Critical memory safety
            }

            pdfDocument.finishPage(page)
        }

        // Build file name
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val baseName = if (!customFileName.isNullOrBlank()) {
            // Sanitize user input for filesystem safety
            customFileName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
        } else {
            "StuTool_Scan"
        }
        val fileName = "${baseName}_${timestamp}.pdf"

        // Save to MediaStore (public Documents folder)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/StuTool")
        }

        try {
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            if (uri == null) {
                android.util.Log.e("PdfGenerator", "MediaStore insert returned null")
                pdfDocument.close()
                return@withContext null
            }

            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    pdfDocument.writeTo(out)
                    out.flush()
                } ?: run {
                    android.util.Log.e("PdfGenerator", "Failed to open output stream for $uri")
                    // Cleanup corrupt entry
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    pdfDocument.close()
                    return@withContext null
                }
            } catch (writeEx: Exception) {
                android.util.Log.e("PdfGenerator", "writeTo failed, cleaning up corrupt URI", writeEx)
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                pdfDocument.close()
                return@withContext null
            }

            pdfDocument.close()

            // Validate: confirm the file was actually written and has content
            val fileDescriptor = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) { null }

            if (fileDescriptor != null) {
                val size = fileDescriptor.statSize
                fileDescriptor.close()
                if (size <= 100) { // PDF header alone is ~100 bytes
                    android.util.Log.e("PdfGenerator", "PDF file too small after write (${size} bytes)")
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    return@withContext null
                }
                android.util.Log.d("PdfGenerator", "PDF export validated: ${size} bytes")
            } else {
                android.util.Log.e("PdfGenerator", "Cannot read exported PDF for validation")
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                return@withContext null
            }

            return@withContext uri
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "PDF export failed", e)
            pdfDocument.close()
            return@withContext null
        }
    }

    /**
     * Loads a bitmap from URI, downscales it to fit given dimensions,
     * and applies adaptive compression based on image content.
     */
    private fun loadAndScaleBitmap(context: Context, uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        try {
            // Step 1: Read bounds only (no memory allocation)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Step 2: Calculate inSampleSize to prevent OOM
            options.inSampleSize = calculateInSampleSize(options, targetW, targetH)
            options.inJustDecodeBounds = false

            // Step 3: Decode with sample size
            var bitmap: Bitmap? = null
            context.contentResolver.openInputStream(uri)?.use {
                bitmap = BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            // Step 4: Scale to fit target while maintaining aspect ratio
            val bmp = bitmap ?: return null
            val ratio = (targetW.toFloat() / bmp.width).coerceAtMost(targetH.toFloat() / bmp.height)
            
            val scaledWidth = (bmp.width * ratio).toInt()
            val scaledHeight = (bmp.height * ratio).toInt()
            val scaledBmp = Bitmap.createScaledBitmap(bmp, scaledWidth, scaledHeight, true)
            
            // Step 5: Adaptive compression
            // Analyze average luminance to determine if image is mostly text (high contrast)
            val quality = getAdaptiveQuality(scaledBmp)
            
            val stream = ByteArrayOutputStream()
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val compressedArray = stream.toByteArray()
            val compressedBmp = BitmapFactory.decodeByteArray(compressedArray, 0, compressedArray.size)
            
            // Aggressive cleanup
            if (scaledBmp !== bmp) bmp.recycle()
            if (compressedBmp !== scaledBmp) scaledBmp.recycle()
            
            return compressedBmp
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Determines compression quality based on image content analysis.
     * Text-heavy images (high contrast, bimodal luminance) get higher quality.
     * Photo-heavy images get standard compression.
     */
    private fun getAdaptiveQuality(bitmap: Bitmap): Int {
        // Sample a grid of pixels to determine content type
        val sampleSize = 20
        val stepX = (bitmap.width / sampleSize).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleSize).coerceAtLeast(1)

        var totalLuminance = 0.0
        var darkPixels = 0
        var lightPixels = 0
        var count = 0

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val luminance = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114)
                totalLuminance += luminance
                count++

                if (luminance < 80) darkPixels++
                else if (luminance > 180) lightPixels++
            }
        }

        if (count == 0) return 85

        val avgLuminance = totalLuminance / count
        val contrastRatio = (darkPixels + lightPixels).toFloat() / count

        // High contrast (text documents): mostly dark text on light background
        return if (contrastRatio > 0.7 && avgLuminance > 140) {
            95 // Text-heavy: preserve sharpness
        } else {
            85 // Photo/mixed: standard compression
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
