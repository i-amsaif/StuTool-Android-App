package com.stutool.stutool.ui.screens.scanner.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports scanned pages as individual JPG or PNG image files.
 * Multi-page exports create a subfolder: Pictures/StuTool/<DocumentName>/
 */
object ImageExporter {

    enum class ImageFormat(val extension: String, val mimeType: String) {
        JPG("jpg", "image/jpeg"),
        PNG("png", "image/png")
    }

    enum class ExportQuality(val label: String, val jpgQuality: Int) {
        HIGH("High", 95),
        BALANCED("Balanced", 85),
        COMPRESSED("Compressed", 70)
    }

    /**
     * Exports pages as individual image files.
     * @return List of saved URIs, or empty list on failure
     */
    suspend fun exportImages(
        context: Context,
        pages: List<Uri>,
        format: ImageFormat,
        quality: ExportQuality = ExportQuality.BALANCED,
        customName: String? = null,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Uri> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext emptyList()

        android.util.Log.d("ImageExporter", "exportImages: format=${format.extension}, mime=${format.mimeType}, pages=${pages.size}, quality=${quality.label}")

        val baseName = if (!customName.isNullOrBlank()) {
            customName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
        } else {
            "StuTool_Scan"
        }

        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
            .format(java.util.Date())

        // For multi-page: create a subfolder
        val relativePath = if (pages.size > 1) {
            Environment.DIRECTORY_PICTURES + "/StuTool/${baseName}_$timestamp"
        } else {
            Environment.DIRECTORY_PICTURES + "/StuTool"
        }

        val savedUris = mutableListOf<Uri>()

        for ((index, pageUri) in pages.withIndex()) {
            onProgress(index + 1, pages.size)

            try {
                val bitmap = loadBitmap(context, pageUri) ?: continue

                val fileName = if (pages.size > 1) {
                    "${baseName}_page${index + 1}.${format.extension}"
                } else {
                    "${baseName}_$timestamp.${format.extension}"
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                if (uri != null) {
                    android.util.Log.d("ImageExporter", "MediaStore URI created: $uri for page ${index + 1}")
                    var writeSuccess = false
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            val compressFormat = when (format) {
                                ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
                                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                            }
                            val q = if (format == ImageFormat.PNG) 100 else quality.jpgQuality
                            bitmap.compress(compressFormat, q, out)
                            out.flush()
                        }

                        // Validate written file
                        val fd = try {
                            context.contentResolver.openFileDescriptor(uri, "r")
                        } catch (_: Exception) { null }

                        if (fd != null && fd.statSize > 0) {
                            fd.close()
                            savedUris.add(uri)
                            writeSuccess = true
                            android.util.Log.d("ImageExporter", "Export validated: page ${index + 1}, size=${fd.statSize} bytes, mime=${format.mimeType}")
                        } else {
                            fd?.close()
                            android.util.Log.e("ImageExporter", "Export validation failed for page ${index + 1}")
                        }
                    } catch (writeEx: Exception) {
                        android.util.Log.e("ImageExporter", "Write failed for page ${index + 1}", writeEx)
                    }

                    if (!writeSuccess) {
                        // Cleanup corrupt/empty MediaStore entry
                        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                }

                bitmap.recycle()
            } catch (e: Exception) {
                android.util.Log.e("ImageExporter", "Failed to export page ${index + 1}", e)
            }
        }

        android.util.Log.d("ImageExporter", "Export complete: ${savedUris.size}/${pages.size} pages saved successfully")
        return@withContext savedUris
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageExporter", "Failed to load bitmap", e)
            null
        }
    }
}
