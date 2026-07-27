package com.stutool.stutool.domain.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class CompressionResult(
    val uri: Uri,
    val originalSizeBytes: Long,
    val newSizeBytes: Long
)

object PdfEngine {

    suspend fun countPages(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                renderer.pageCount
            }
        } ?: throw Exception("Could not open file descriptor")
    }

    suspend fun mergePdfs(context: Context, uris: List<Uri>): Uri = withContext(Dispatchers.IO) {
        val merger = PDFMergerUtility()
        val fileName = "merged_${System.currentTimeMillis()}.pdf"
        var outputStream: java.io.OutputStream? = null
        var finalUri: Uri? = null

        val inputStreams = mutableListOf<java.io.InputStream>()

        try {
            val (uri, stream) = createSafOutputStream(context, fileName)
            finalUri = uri
            outputStream = stream

            uris.forEach { u ->
                val inputStream = context.contentResolver.openInputStream(u)
                if (inputStream != null) {
                    inputStreams.add(inputStream)
                    merger.addSource(inputStream)
                }
            }

            merger.destinationStream = outputStream
            // Use hybrid memory: up to 50MB in RAM, then spill to temp files to prevent OutOfMemoryError
            val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
            merger.mergeDocuments(memorySetting)
        } finally {
            inputStreams.forEach { it.close() }
            outputStream?.close()
        }

        finalUri ?: throw Exception("Failed to generate final URI")
    }

    suspend fun extractPages(context: Context, sourceUri: Uri, originalFileName: String, targetPages: List<Int>): Uri = withContext(Dispatchers.IO) {
        val fileName = "${originalFileName}_extracted.pdf"
        var outputStream: java.io.OutputStream? = null
        var finalUri: Uri? = null

        try {
            val (uri, stream) = createSafOutputStream(context, fileName)
            finalUri = uri
            outputStream = stream

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                PDDocument.load(inputStream, memorySetting).use { sourceDoc ->
                    PDDocument().use { targetDoc ->
                        // PdfBox pages are 0-indexed, but our input is 1-indexed
                        for (pageNumber in targetPages) {
                            val zeroBasedIndex = pageNumber - 1
                            if (zeroBasedIndex < sourceDoc.numberOfPages) {
                                val page = sourceDoc.getPage(zeroBasedIndex)
                                targetDoc.addPage(page)
                            }
                        }
                        
                        targetDoc.save(outputStream)
                    }
                }
            } ?: throw Exception("Could not open source file stream")
        } finally {
            outputStream?.close()
        }

        finalUri ?: throw Exception("Failed to generate final URI")
    }

    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        originalFileName: String,
        qualityLevel: Int,
        scaleFactor: Float
    ): CompressionResult = withContext(Dispatchers.IO) {
        val fileName = "${originalFileName}_compressed.pdf"
        var outputStream: java.io.OutputStream? = null
        var finalUri: Uri? = null
        
        var originalSize = 0L

        // Get original size via content resolver
        context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { fd ->
            originalSize = fd.statSize
        }

        try {
            val (uri, stream) = createSafOutputStream(context, fileName)
            finalUri = uri
            outputStream = stream

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                PDDocument.load(inputStream, memorySetting).use { document ->
                    for (page in document.pages) {
                        val resources: PDResources = page.resources ?: continue
                        val xObjectNames = resources.xObjectNames

                        for (name in xObjectNames) {
                            if (resources.isImageXObject(name)) {
                                val imageXObject = resources.getXObject(name) as PDImageXObject
                                
                                // Convert PDImageXObject to Android Bitmap safely
                                val originalBitmap: Bitmap? = imageXObject.image
                                
                                if (originalBitmap != null) {
                                    // Scale down bitmap based on requested compression factor
                                    val scaledBitmap = if (scaleFactor < 1f) {
                                        val newWidth = (originalBitmap.width * scaleFactor).toInt().coerceAtLeast(1)
                                        val newHeight = (originalBitmap.height * scaleFactor).toInt().coerceAtLeast(1)
                                        Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                                    } else {
                                        originalBitmap
                                    }

                                    // Compress bitmap utilizing Android JPEG engine
                                    val baos = ByteArrayOutputStream()
                                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, qualityLevel, baos)
                                    val compressedBytes = baos.toByteArray()

                                    // Create new compressed PDImage wrapper and replace into standard generic dictionary
                                    val compressedImage = JPEGFactory.createFromByteArray(document, compressedBytes)
                                    resources.put(name, compressedImage)
                                    
                                    // Recycle explicitly considering we handle multiple PDFs dynamically
                                    if (scaledBitmap != originalBitmap) {
                                        scaledBitmap.recycle()
                                    }
                                }
                            }
                        }
                    }
                    
                    document.save(outputStream)
                }
            } ?: throw Exception("Could not open source file stream")

        } finally {
            outputStream?.close()
        }
        
        val readyUri = finalUri ?: throw Exception("Failed to generate final URI")

        // Retrieve new size
        var newSize = 0L
        context.contentResolver.openFileDescriptor(readyUri, "r")?.use { fd ->
            newSize = fd.statSize
        }
        
        // Failsafe in extremely rare cases:
        if (newSize == 0L) {
             context.contentResolver.query(readyUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (index != -1) newSize = cursor.getLong(index)
                }
            }
        }

        CompressionResult(
            uri = readyUri,
            originalSizeBytes = originalSize,
            newSizeBytes = newSize
        )
    }

    private fun createSafOutputStream(context: Context, fileName: String): Pair<Uri, java.io.OutputStream> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/StuTool")
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: throw Exception("Failed to create MediaStore record")
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw Exception("Failed to open output stream")
            return Pair(uri, outputStream)
        } else {
            val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val outputFile = File(outputDir, fileName)
            val uri = Uri.fromFile(outputFile)
            val outputStream = FileOutputStream(outputFile)
            return Pair(uri, outputStream)
        }
    }
}
