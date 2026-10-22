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
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CompressionResult(
    val uri: Uri,
    val originalSizeBytes: Long,
    val newSizeBytes: Long
)

data class OcrResult(
    val uri: Uri,
    val pagesProcessed: Int,
    val pagesSkippedText: Int,
    val pagesSkippedRotation: Int
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

    suspend fun convertPdfToImages(
        context: Context,
        sourceUri: Uri,
        format: String,
        qualityMode: String,
        targetPages: List<Int>,
        onProgress: (Int, Int) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "pdf_images_${System.currentTimeMillis()}")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val files = mutableListOf<File>()
        context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            try {
                val totalPages = renderer.pageCount
                // Using targetPages if provided, otherwise all pages
                val pagesToConvert = if (targetPages.isNotEmpty()) {
                    targetPages.filter { it in 1..totalPages }.map { it - 1 }
                } else {
                    (0 until totalPages).toList()
                }
                
                for (i in pagesToConvert) {
                    ensureActive()
                    val page = renderer.openPage(i)
                    var bitmap: Bitmap? = null
                    try {
                        val highQuality = qualityMode == "High"
                        val scale = if (highQuality) 3f else 1.5f
                        var w = (page.width * scale).toInt()
                        var h = (page.height * scale).toInt()
                        
                        val maxPixels = 10_000_000
                        if (w * h > maxPixels) {
                            val ratio = Math.sqrt(maxPixels.toDouble() / (w * h)).toFloat()
                            w = (w * ratio).toInt()
                            h = (h * ratio).toInt()
                        }
                        
                        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        val ext = if (format == "PNG") ".png" else ".jpg"
                        val compressFormat = if (format == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        val file = File(cacheDir, "page_${i + 1}$ext")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(compressFormat, if (highQuality) 100 else 80, out)
                        }
                        files.add(file)
                        onProgress(i + 1, totalPages)
                    } finally {
                        bitmap?.recycle()
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        } ?: throw Exception("Failed to open PDF")
        files
    }

    suspend fun saveImagesToGallery(context: Context, files: List<File>, format: String): Int = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw Exception("Direct gallery saving requires Android 10+. Please use Share as ZIP instead.")
        }
        var savedCount = 0
        val mimeType = if (format == "PNG") "image/png" else "image/jpeg"
        val ext = if (format == "PNG") ".png" else ".jpg"
        
        for (file in files) {
            ensureActive()
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.nameWithoutExtension + ext)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StuTool")
            }
            var uri: Uri? = null
            try {
                uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        java.io.FileInputStream(file).use { input ->
                            input.copyTo(out)
                        }
                    }
                    savedCount++
                }
            } catch (e: Exception) {
                if (uri != null) context.contentResolver.delete(uri, null, null)
                throw e
            }
        }
        savedCount
    }

    suspend fun createZipFile(context: Context, files: List<File>, zipFileName: String): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "zip_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val zipFile = File(cacheDir, zipFileName)
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            for (file in files) {
                ensureActive()
                java.io.FileInputStream(file).use { fis ->
                    val zipEntry = java.util.zip.ZipEntry(file.name)
                    zos.putNextEntry(zipEntry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        zipFile
    }

    suspend fun makeSearchablePdf(
        context: Context,
        sourceUri: Uri,
        language: com.stutool.stutool.domain.ocr.OcrLanguage = com.stutool.stutool.domain.ocr.OcrLanguage.AUTO,
        mode: com.stutool.stutool.domain.ocr.OcrMode = com.stutool.stutool.domain.ocr.OcrMode.DEFAULT,
        onProgress: (Int, Int) -> Unit
    ): OcrResult = withContext(Dispatchers.IO) {
        val operationId = UUID.randomUUID().toString()
        val opDir = File(context.cacheDir, "ocr_cache_${System.currentTimeMillis()}_${operationId}")
        if (!opDir.exists()) opDir.mkdirs()
        
        var baseName = "searchable_document"
        var fileSizeBytes = getFileSizeBytes(context, sourceUri)
        context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val fullName = cursor.getString(idx)
                    baseName = fullName.substringBeforeLast(".")
                }
                if (fileSizeBytes <= 0L) {
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        fileSizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        val outputFile = File(opDir, "${baseName}_OCR.pdf")
        val tempPdfFile = File(opDir, "temp_source_${operationId}.pdf")
        
        var processed = 0
        var skippedText = 0
        var skippedRotation = 0

        var document: PDDocument? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var isSuccess = false
        
        try {
            // Stream sourceUri to temporary file to prevent buffering in Java heap
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open source file stream.")

            // Memory-bounded PDFBox loading using disk-backed temporary-file buffering
            val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(opDir)
            document = try {
                PDDocument.load(tempPdfFile, memorySetting)
            } catch (oom: OutOfMemoryError) {
                throw Exception("The document is too large or complex to load into memory. Please select a smaller PDF.", oom)
            }

            val totalPages = document.numberOfPages
            if (totalPages <= 0) {
                throw Exception("The document contains no pages.")
            }

            if (mode == com.stutool.stutool.domain.ocr.OcrMode.CLOUD_ONLY) {
                if (totalPages > 3 || fileSizeBytes > 1048576L) {
                    val sizeKb = fileSizeBytes / 1024
                    val reason = if (totalPages > 3 && fileSizeBytes > 1048576L) {
                        "Document has $totalPages pages and is $sizeKb KB, exceeding Cloud OCR limits (max 3 pages, 1 MB)."
                    } else if (totalPages > 3) {
                        "Document has $totalPages pages, exceeding the Cloud OCR limit of 3 pages."
                    } else {
                        "Document size ($sizeKb KB) exceeds the Cloud OCR limit of 1 MB."
                    }
                    throw com.stutool.stutool.domain.ocr.OcrLimitExceededException(
                        "$reason Please switch to Smart Auto or Offline OCR.",
                        isSizeLimit = fileSizeBytes > 1048576L,
                        isPageLimit = totalPages > 3
                    )
                }
            }

            var currentMode = if (mode.allowsCloud && (totalPages > 3 || fileSizeBytes > 1048576L)) {
                com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
            } else {
                mode
            }

            // Pre-load Unicode TrueType fonts
            val kannadaFont = try {
                context.assets.open("fonts/NotoSansKannada-Regular.ttf").use { fontStream ->
                    com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(document, fontStream, true)
                }
            } catch (e: Exception) {
                null
            }
            val latinFont = try {
                context.assets.open("fonts/NotoSans-Regular.ttf").use { fontStream ->
                    com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(document, fontStream, true)
                }
            } catch (e: Exception) {
                null
            }

            pfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()

            for (i in 0 until totalPages) {
                ensureActive()
                
                val pdPage = document.getPage(i)
                
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val existingText = stripper.getText(document)
                if (com.stutool.stutool.domain.ocr.SmartTextDetector.isAuthenticNativeText(existingText)) {
                    skippedText++
                    onProgress(i + 1, totalPages)
                    continue
                }
                
                val page = renderer.openPage(i)
                var bitmap: Bitmap? = null
                try {
                    var w = page.width * 2
                    var h = page.height * 2
                    val maxPixels = 10_000_000
                    if (w * h > maxPixels) {
                        val ratio = Math.sqrt(maxPixels.toDouble() / (w * h)).toFloat()
                        w = (w * ratio).toInt()
                        h = (h * ratio).toInt()
                    }
                    bitmap = try {
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    } catch (oom: OutOfMemoryError) {
                        throw Exception("Insufficient memory to render page ${i + 1} for OCR. Try selecting a smaller PDF.", oom)
                    }
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val ocrPageResult = com.stutool.stutool.domain.ocr.OcrEngineService.recognizeBitmap(
                        context = context,
                        bitmap = bitmap,
                        language = language,
                        mode = currentMode
                    )
                    if (ocrPageResult.engineSource != com.stutool.stutool.domain.ocr.OcrEngineSource.CLOUD_OCR_SPACE) {
                        currentMode = com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
                    }
                    
                    val cropBox = pdPage.cropBox ?: pdPage.mediaBox
                    val scaleX = cropBox.width / bitmap.width.toFloat()
                    val scaleY = cropBox.height / bitmap.height.toFloat()
                    
                    if (ocrPageResult.lines.isNotEmpty()) {
                        val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                            document,
                            pdPage,
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        )
                        contentStream.setRenderingMode(com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER)
                        
                        appendOcrTextToContentStream(
                            contentStream = contentStream,
                            lines = ocrPageResult.lines,
                            cropBox = cropBox,
                            scaleX = scaleX,
                            scaleY = scaleY,
                            kannadaFont = kannadaFont,
                            latinFont = latinFont
                        )
                        
                        contentStream.close()
                        processed++
                    }
                } finally {
                    bitmap?.recycle()
                    page.close()
                }
                onProgress(i + 1, totalPages)
            }
            
            FileOutputStream(outputFile).use { out ->
                document.save(out)
            }
            isSuccess = true
            
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (s: SecurityException) {
            throw Exception("Encrypted PDF: Password-protected PDFs are not supported.")
        } catch (oom: OutOfMemoryError) {
            throw Exception("Document processing ran out of memory. Try selecting a smaller PDF.", oom)
        } catch (e: Exception) {
            throw e
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { document?.close() } catch (_: Exception) {}
            try {
                if (tempPdfFile.exists()) tempPdfFile.delete()
                if (isSuccess) {
                    opDir.listFiles()?.forEach { file ->
                        if (file != outputFile && file.exists()) {
                            file.delete()
                        }
                    }
                }
            } catch (_: Exception) {}
            if (!isSuccess) {
                try { opDir.deleteRecursively() } catch (_: Exception) {}
            }
        }
        
        OcrResult(
            uri = Uri.fromFile(outputFile),
            pagesProcessed = processed,
            pagesSkippedText = skippedText,
            pagesSkippedRotation = skippedRotation
        )
    }

    suspend fun convertPdfToSearchablePdf(
        context: Context,
        sourceUri: Uri,
        language: com.stutool.stutool.domain.ocr.OcrLanguage = com.stutool.stutool.domain.ocr.OcrLanguage.AUTO,
        mode: com.stutool.stutool.domain.ocr.OcrMode = com.stutool.stutool.domain.ocr.OcrMode.DEFAULT,
        onProgress: (Int, Int) -> Unit
    ): OcrResult = makeSearchablePdf(context, sourceUri, language, mode, onProgress)

    private fun appendOcrTextToContentStream(
        contentStream: com.tom_roush.pdfbox.pdmodel.PDPageContentStream,
        lines: List<com.stutool.stutool.domain.ocr.OcrLine>,
        cropBox: com.tom_roush.pdfbox.pdmodel.common.PDRectangle,
        scaleX: Float,
        scaleY: Float,
        kannadaFont: com.tom_roush.pdfbox.pdmodel.font.PDType0Font?,
        latinFont: com.tom_roush.pdfbox.pdmodel.font.PDType0Font?
    ) {
        for (line in lines) {
            val box = line.boundingBox
            val pdfX = cropBox.lowerLeftX + (box.left * scaleX)
            val pdfY = cropBox.lowerLeftY + cropBox.height - (box.bottom * scaleY)
            val fontSize = ((box.bottom - box.top) * scaleY).coerceIn(4f, 48f)

            val tokens = line.text.split(" ").filter { it.isNotBlank() }
            if (tokens.isEmpty()) continue

            contentStream.beginText()
            contentStream.newLineAtOffset(pdfX, pdfY)

            for ((index, token) in tokens.withIndex()) {
                val (font, safeToken) = selectFontForToken(token, kannadaFont, latinFont)
                if (font != null && safeToken.isNotEmpty()) {
                    try {
                        contentStream.setFont(font, fontSize)
                        contentStream.showText(safeToken)
                        if (index < tokens.size - 1) {
                            contentStream.showText(" ")
                        }
                    } catch (_: Exception) {}
                }
            }
            contentStream.endText()
        }
    }

    private fun selectFontForToken(
        token: String,
        kannadaFont: com.tom_roush.pdfbox.pdmodel.font.PDType0Font?,
        latinFont: com.tom_roush.pdfbox.pdmodel.font.PDType0Font?
    ): Pair<com.tom_roush.pdfbox.pdmodel.font.PDType0Font?, String> {
        val hasKannada = token.any { it.code in 0x0C80..0x0CFF }
        if (hasKannada && kannadaFont != null) {
            val safe = token.filter { c ->
                try { kannadaFont.encode(c.toString()); true } catch (_: Exception) { false }
            }
            if (safe.isNotEmpty()) return Pair(kannadaFont, safe)
        }

        if (latinFont != null) {
            val safe = token.filter { c ->
                try { latinFont.encode(c.toString()); true } catch (_: Exception) { false }
            }
            if (safe.isNotEmpty()) return Pair(latinFont, safe)
        }

        if (kannadaFont != null) {
            val safe = token.filter { c ->
                try { kannadaFont.encode(c.toString()); true } catch (_: Exception) { false }
            }
            if (safe.isNotEmpty()) return Pair(kannadaFont, safe)
        }

        return Pair(null, "")
    }

    suspend fun convertPdfToWord(
        context: Context,
        sourceUri: Uri,
        language: com.stutool.stutool.domain.ocr.OcrLanguage = com.stutool.stutool.domain.ocr.OcrLanguage.AUTO,
        mode: com.stutool.stutool.domain.ocr.OcrMode = com.stutool.stutool.domain.ocr.OcrMode.DEFAULT,
        onProgress: (Int, Int) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        val operationId = UUID.randomUUID().toString()
        val opDir = File(context.cacheDir, "pdf_to_word_${System.currentTimeMillis()}_${operationId}")
        if (!opDir.exists()) opDir.mkdirs()

        val fileSizeBytes = getFileSizeBytes(context, sourceUri)
        var baseName = "converted_document"
        context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val fullName = cursor.getString(idx)
                    baseName = fullName.substringBeforeLast(".")
                }
            }
        }
        val outputFile = File(opDir, "${baseName}.docx")
        val tempPdfFile = File(opDir, "temp_source_${operationId}.pdf")

        var document: PDDocument? = null
        var pfd: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        var isSuccess = false

        try {
            // Safe streaming to cache file to prevent heap bloat
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open source file stream.")

            // Memory-bounded PDFBox loading using disk-backed temporary-file buffering
            val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(opDir)
            document = try {
                PDDocument.load(tempPdfFile, memorySetting)
            } catch (oom: OutOfMemoryError) {
                throw Exception("The document is too large or complex to load into memory. Please select a smaller PDF.", oom)
            }

            val totalPages = document.numberOfPages
            if (totalPages <= 0) {
                throw Exception("The document contains no pages.")
            }

            if (mode == com.stutool.stutool.domain.ocr.OcrMode.CLOUD_ONLY) {
                if (totalPages > 3 || fileSizeBytes > 1048576L) {
                    val sizeKb = fileSizeBytes / 1024
                    val reason = if (totalPages > 3 && fileSizeBytes > 1048576L) {
                        "Document has $totalPages pages and is $sizeKb KB, exceeding Cloud OCR limits (max 3 pages, 1 MB)."
                    } else if (totalPages > 3) {
                        "Document has $totalPages pages, exceeding the Cloud OCR limit of 3 pages."
                    } else {
                        "Document size ($sizeKb KB) exceeds the Cloud OCR limit of 1 MB."
                    }
                    throw com.stutool.stutool.domain.ocr.OcrLimitExceededException(
                        "$reason Please switch to Smart Auto or Offline OCR.",
                        isSizeLimit = fileSizeBytes > 1048576L,
                        isPageLimit = totalPages > 3
                    )
                }
            }

            val pageOcrMode = if (mode == com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO && (totalPages > 3 || fileSizeBytes > 1048576L)) {
                com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
            } else {
                mode
            }

            val stripper = PDFTextStripper()
            val extractedTextBuilder = java.lang.StringBuilder()

            // 1. Fast digital text extraction & analysis pass
            val pageTexts = mutableListOf<String>()
            var hasAnyNativeText = false

            for (i in 0 until totalPages) {
                ensureActive()
                onProgress(i + 1, totalPages)

                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val nativeText = stripper.getText(document) ?: ""
                if (com.stutool.stutool.domain.ocr.SmartTextDetector.isAuthenticNativeText(nativeText)) {
                    hasAnyNativeText = true
                }
                pageTexts.add(nativeText)
            }

            // 2. Handle image-only documents:
            // When a document has no authentic digital text and exceeds the small-document threshold (totalPages > 3),
            // do not silently run an unbounded OCR loop on hundreds of scanned pages inside PDF-to-Word.
            // Direct the user to the dedicated OCR PDF tool instead.
            if (!hasAnyNativeText && totalPages > 3) {
                throw Exception("This document contains scanned images or no extractable text. Please use StuTool's dedicated OCR PDF tool.")
            }

            // 3. Compile extracted text and safely apply OCR fallback only for small scanned documents (totalPages <= 3)
            for (i in 0 until totalPages) {
                ensureActive()
                val text = pageTexts[i]
                if (com.stutool.stutool.domain.ocr.SmartTextDetector.isAuthenticNativeText(text)) {
                    extractedTextBuilder.append(text.trim()).append("\n\n")
                } else if (totalPages <= 3) {
                    if (pdfRenderer == null) {
                        pfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        pdfRenderer = PdfRenderer(pfd)
                    }
                    val page = pdfRenderer.openPage(i)
                    try {
                        val maxPixels = 10_000_000
                        var width = page.width
                        var height = page.height

                        if (width * height > maxPixels) {
                            val scale = Math.sqrt(maxPixels.toDouble() / (width * height))
                            width = (width * scale).toInt()
                            height = (height * scale).toInt()
                        }

                        val bitmap = try {
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        } catch (oom: OutOfMemoryError) {
                            throw Exception("Insufficient memory to render scanned page ${i + 1}. Try using the OCR PDF tool.", oom)
                        }

                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val ocrResult = com.stutool.stutool.domain.ocr.OcrEngineService.recognizeBitmap(
                                context = context,
                                bitmap = bitmap,
                                language = language,
                                mode = pageOcrMode
                            )
                            if (ocrResult.text.isNotBlank()) {
                                extractedTextBuilder.append(ocrResult.text.trim()).append("\n\n")
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    } finally {
                        page.close()
                    }
                }
            }

            val finalRawText = extractedTextBuilder.toString().trim()
            if (finalRawText.isEmpty()) {
                throw Exception("This document contains no extractable text. Please use StuTool's dedicated OCR PDF tool.")
            }

            writeTextToDocxZip(outputFile, finalRawText)
            isSuccess = true
            return@withContext Uri.fromFile(outputFile)

        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (oom: OutOfMemoryError) {
            throw Exception("Document processing ran out of memory. Try selecting a smaller PDF.", oom)
        } catch (e: Exception) {
            throw e
        } finally {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { document?.close() } catch (_: Exception) {}
            try {
                if (tempPdfFile.exists()) tempPdfFile.delete()
                if (isSuccess) {
                    opDir.listFiles()?.forEach { file ->
                        if (file != outputFile && file.exists()) {
                            file.delete()
                        }
                    }
                }
            } catch (_: Exception) {}
            if (!isSuccess) {
                try { opDir.deleteRecursively() } catch (_: Exception) {}
            }
        }
    }

    fun writeTextToDocxZip(outputFile: File, rawText: String) {
        val finalRawText = rawText.trim()
        if (finalRawText.isEmpty()) {
            throw Exception("No text available to export.")
        }

        java.io.FileOutputStream(outputFile).use { fos ->
            java.util.zip.ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                        "    <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                        "    <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                        "    <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                        "</Types>"
                
                zos.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
                zos.write(contentTypes.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                
                // 2. _rels/.rels
                val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                        "    <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                        "</Relationships>"
                
                zos.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
                zos.write(rels.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                
                // 3. word/document.xml
                val docXmlStart = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
                        "    <w:body>\n"
                
                val docXmlEnd = "    </w:body>\n" +
                        "</w:document>"
                
                zos.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
                zos.write(docXmlStart.toByteArray(Charsets.UTF_8))
                
                val paragraphs = finalRawText.split("\n\n")
                for (p in paragraphs) {
                    val cleanP = escapeXml(p)
                    if (cleanP.isBlank()) continue
                    val lines = cleanP.split("\n")
                    zos.write("<w:p>".toByteArray(Charsets.UTF_8))
                    for ((index, line) in lines.withIndex()) {
                        zos.write("<w:r><w:t xml:space=\"preserve\">${line}</w:t></w:r>".toByteArray(Charsets.UTF_8))
                        if (index < lines.size - 1) {
                            zos.write("<w:r><w:br/></w:r>".toByteArray(Charsets.UTF_8))
                        }
                    }
                    zos.write("</w:p>\n".toByteArray(Charsets.UTF_8))
                }
                
                zos.write(docXmlEnd.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    suspend fun exportTextToDocx(
        context: Context,
        rawText: String,
        baseName: String = "extracted_text"
    ): Uri = withContext(Dispatchers.IO) {
        val opDir = File(context.cacheDir, "ocr_docx_${System.currentTimeMillis()}_${UUID.randomUUID()}")
        if (!opDir.exists()) opDir.mkdirs()
        val outputFile = File(opDir, "${baseName}.docx")
        writeTextToDocxZip(outputFile, rawText)
        Uri.fromFile(outputFile)
    }

    suspend fun exportTextToTxt(
        context: Context,
        rawText: String,
        baseName: String = "extracted_text"
    ): Uri = withContext(Dispatchers.IO) {
        val opDir = File(context.cacheDir, "ocr_txt_${System.currentTimeMillis()}_${UUID.randomUUID()}")
        if (!opDir.exists()) opDir.mkdirs()
        val outputFile = File(opDir, "${baseName}.txt")
        outputFile.writeText(rawText, Charsets.UTF_8)
        Uri.fromFile(outputFile)
    }

    fun getFileSizeBytes(context: Context, uri: Uri): Long {
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            val size = cursor.getLong(sizeIndex)
                            if (size > 0L) return size
                        }
                    }
                }
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val size = pfd.statSize
                    if (size > 0L) return size
                }
            } else if (uri.scheme == "file" || uri.path != null) {
                val f = File(uri.path ?: "")
                if (f.exists()) return f.length()
            }
        } catch (_: Exception) {}
        return 0L
    }

    fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var decoded = false
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                decoded = options.outWidth > 0 && options.outHeight > 0
            }
            if (!decoded) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun performImageOcr(
        context: Context,
        imageUri: Uri,
        language: com.stutool.stutool.domain.ocr.OcrLanguage = com.stutool.stutool.domain.ocr.OcrLanguage.AUTO,
        mode: com.stutool.stutool.domain.ocr.OcrMode = com.stutool.stutool.domain.ocr.OcrMode.DEFAULT
    ): com.stutool.stutool.domain.ocr.OcrPageResult = withContext(Dispatchers.IO) {
        ensureActive()

        val fileSizeBytes = getFileSizeBytes(context, imageUri)
        val effectiveMode = if (mode == com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO && fileSizeBytes > 1048576L) {
            com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
        } else if (mode == com.stutool.stutool.domain.ocr.OcrMode.CLOUD_ONLY && fileSizeBytes > 1048576L) {
            throw com.stutool.stutool.domain.ocr.OcrLimitExceededException(
                "Image size (${fileSizeBytes / 1024} KB) exceeds the Cloud OCR limit of 1 MB. Please switch to Smart Auto or Offline OCR.",
                isSizeLimit = true
            )
        } else {
            mode
        }

        val bitmap = decodeSampledBitmapFromUri(context, imageUri, maxDimension = 2400)
            ?: throw Exception("Failed to decode image from selected file.")
        try {
            com.stutool.stutool.domain.ocr.OcrEngineService.recognizeBitmap(
                context = context,
                bitmap = bitmap,
                language = language,
                mode = effectiveMode
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val tempFile = try {
            File.createTempFile("ocr_decode_", ".tmp", context.cacheDir)
        } catch (_: Exception) {
            null
        }

        try {
            var streamCopied = false
            if (tempFile != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        streamCopied = tempFile.length() > 0L
                    }
                } catch (_: Exception) {}

                if (!streamCopied) {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                                streamCopied = tempFile.length() > 0L
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (tempFile != null && streamCopied) {
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)
                val origW = boundsOptions.outWidth
                val origH = boundsOptions.outHeight
                if (origW > 0 && origH > 0) {
                    var inSampleSize = 1
                    val maxSide = maxOf(origW, origH)
                    while (maxSide / (inSampleSize * 2) >= maxDimension) {
                        inSampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    val decoded = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
                    if (decoded != null) {
                        return fixOrientation(tempFile.absolutePath, decoded)
                    }
                }
            }

            // Fallback: in-memory byte buffer
            val bytes = readBytesFromUri(context, uri) ?: return null
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) return null

            var inSampleSize = 1
            val maxSide = maxOf(origW, origH)
            while (maxSide / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
            return fixOrientationFromBytes(bytes, decoded)
        } finally {
            tempFile?.delete()
        }
    }

    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val byteStream = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    byteStream.write(buffer, 0, read)
                }
                val arr = byteStream.toByteArray()
                if (arr.isNotEmpty()) return arr
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { stream ->
                    val byteStream = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        byteStream.write(buffer, 0, read)
                    }
                    val arr = byteStream.toByteArray()
                    if (arr.isNotEmpty()) return arr
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun fixOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
            applyExifOrientation(orientation, bitmap)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun fixOrientationFromBytes(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
            applyExifOrientation(orientation, bitmap)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun applyExifOrientation(orientation: Int, bitmap: Bitmap): Bitmap {
        val degrees = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    suspend fun extractTextFromPdf(
        context: Context,
        sourceUri: Uri,
        language: com.stutool.stutool.domain.ocr.OcrLanguage = com.stutool.stutool.domain.ocr.OcrLanguage.AUTO,
        mode: com.stutool.stutool.domain.ocr.OcrMode = com.stutool.stutool.domain.ocr.OcrMode.DEFAULT,
        onProgress: (Int, Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val operationId = UUID.randomUUID().toString()
        val opDir = File(context.cacheDir, "extract_text_${System.currentTimeMillis()}_${operationId}")
        if (!opDir.exists()) opDir.mkdirs()
        val tempPdfFile = File(opDir, "temp_source_${operationId}.pdf")

        val fileSizeBytes = getFileSizeBytes(context, sourceUri)
        val builder = StringBuilder()

        var document: PDDocument? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            // Stream sourceUri to temporary file to prevent buffering in Java heap
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open source file stream.")

            val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(opDir)
            document = try {
                PDDocument.load(tempPdfFile, memorySetting)
            } catch (oom: OutOfMemoryError) {
                throw Exception("The document is too large or complex to load into memory. Please select a smaller PDF.", oom)
            }

            pfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            val totalPages = document.numberOfPages
            if (mode == com.stutool.stutool.domain.ocr.OcrMode.CLOUD_ONLY) {
                if (totalPages > 3 || fileSizeBytes > 1048576L) {
                    val sizeKb = fileSizeBytes / 1024
                    val reason = if (totalPages > 3 && fileSizeBytes > 1048576L) {
                        "Document has $totalPages pages and is $sizeKb KB, exceeding Cloud OCR limits (max 3 pages, 1 MB)."
                    } else if (totalPages > 3) {
                        "Document has $totalPages pages, exceeding the Cloud OCR limit of 3 pages."
                    } else {
                        "Document size ($sizeKb KB) exceeds the Cloud OCR limit of 1 MB."
                    }
                    throw com.stutool.stutool.domain.ocr.OcrLimitExceededException(
                        "$reason Please switch to Smart Auto or Offline OCR.",
                        isSizeLimit = fileSizeBytes > 1048576L,
                        isPageLimit = totalPages > 3
                    )
                }
            }

            var currentMode = if (mode.allowsCloud && (totalPages > 3 || fileSizeBytes > 1048576L)) {
                com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
            } else {
                mode
            }
            val stripper = PDFTextStripper()

            for (i in 0 until totalPages) {
                ensureActive()
                onProgress(i + 1, totalPages)

                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val nativeText = stripper.getText(document)

                if (com.stutool.stutool.domain.ocr.SmartTextDetector.isAuthenticNativeText(nativeText)) {
                    builder.append(nativeText.trim()).append("\n\n")
                } else {
                    val page = renderer.openPage(i)
                    var bitmap: Bitmap? = null
                    try {
                        val ratio = page.width.toFloat() / page.height.toFloat()
                        val safeDims = com.stutool.stutool.ui.screens.viewer.BitmapSafety.calculateSafeDimensions(1800, ratio)
                        bitmap = try {
                            Bitmap.createBitmap(safeDims.width, safeDims.height, Bitmap.Config.ARGB_8888)
                        } catch (oom: OutOfMemoryError) {
                            throw Exception("Insufficient memory to render page ${i + 1} for OCR. Try selecting a smaller PDF.", oom)
                        }
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val ocrResult = com.stutool.stutool.domain.ocr.OcrEngineService.recognizeBitmap(
                            context = context,
                            bitmap = bitmap,
                            language = language,
                            mode = currentMode
                        )
                        if (ocrResult.engineSource != com.stutool.stutool.domain.ocr.OcrEngineSource.CLOUD_OCR_SPACE) {
                            currentMode = com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
                        }
                        if (ocrResult.text.isNotBlank()) {
                            builder.append(ocrResult.text).append("\n\n")
                        }
                    } finally {
                        bitmap?.recycle()
                        page.close()
                    }
                }
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (oom: OutOfMemoryError) {
            throw Exception("Document processing ran out of memory. Try selecting a smaller PDF.", oom)
        } catch (e: Exception) {
            throw e
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { document?.close() } catch (_: Exception) {}
            try { if (tempPdfFile.exists()) tempPdfFile.delete() } catch (_: Exception) {}
            try { opDir.deleteRecursively() } catch (_: Exception) {}
        }
        builder.toString().trim()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "") // Remove invalid XML chars
    }
    
    suspend fun convertPdfToExcel(
        context: Context,
        sourceUri: Uri,
        onProgress: (Int, Int) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        val operationId = UUID.randomUUID().toString()
        val opDir = File(context.cacheDir, "pdf_to_excel_${System.currentTimeMillis()}_${operationId}")
        if (!opDir.exists()) opDir.mkdirs()

        var baseName = "extracted_spreadsheet"
        context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val fullName = cursor.getString(idx)
                    baseName = fullName.substringBeforeLast(".")
                }
            }
        }
        val outputFile = File(opDir, "${baseName}.xlsx")
        val tempPdfFile = File(opDir, "temp_source_${operationId}.pdf")

        var document: PDDocument? = null
        var isSuccess = false

        try {
            // Stream sourceUri to temporary file to prevent buffering in Java heap
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open source file stream.")

            // Memory-bounded PDFBox loading using disk-backed temporary-file buffering
            val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(opDir)
            document = try {
                PDDocument.load(tempPdfFile, memorySetting)
            } catch (oom: OutOfMemoryError) {
                throw Exception("The document is too large or complex to load into memory. Please select a smaller PDF.", oom)
            }

            val totalPages = document.numberOfPages
            if (totalPages <= 0) {
                throw Exception("The document contains no pages.")
            }

            val allRows = mutableListOf<List<String>>()

            class TextBlock(val text: String, val x: Float, val y: Float)

            val stripper = object : PDFTextStripper() {
                var currentRow = mutableListOf<TextBlock>()
                val pageRows = mutableListOf<List<TextBlock>>()
                var currentY = -1f

                init {
                    sortByPosition = true
                }

                override fun writeString(text: String?, textPositions: MutableList<com.tom_roush.pdfbox.text.TextPosition>?) {
                    if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
                    val first = textPositions!!.first()
                    val x = first.xDirAdj
                    val y = first.yDirAdj

                    val yThreshold = 5.0f
                    val nonNullText = text!!
                    if (currentY == -1f) {
                        currentY = y
                        currentRow.add(TextBlock(nonNullText, x, y))
                    } else if (Math.abs(y - currentY) < yThreshold) {
                        currentRow.add(TextBlock(nonNullText, x, y))
                    } else {
                        currentRow.sortBy { it.x }
                        pageRows.add(currentRow.toList())
                        currentRow.clear()
                        currentRow.add(TextBlock(nonNullText, x, y))
                        currentY = y
                    }
                }
            }

            for (i in 0 until totalPages) {
                ensureActive()
                onProgress(i + 1, totalPages)

                stripper.startPage = i + 1
                stripper.endPage = i + 1
                stripper.pageRows.clear()
                stripper.currentRow.clear()
                stripper.currentY = -1f
                stripper.getText(document)

                if (stripper.currentRow.isNotEmpty()) {
                    stripper.currentRow.sortBy { it.x }
                    stripper.pageRows.add(stripper.currentRow.toList())
                }

                allRows.addAll(stripper.pageRows.map { row -> row.map { it.text } })
            }

            // Close document before generating XLSX to free memory early
            try {
                document.close()
                document = null
            } catch (_: Exception) {}

            if (allRows.isEmpty()) {
                throw Exception("No selectable text found. Scanned PDFs are not supported in this version.")
            }

            // Generate XLSX ZIP
            FileOutputStream(outputFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    // 1. [Content_Types].xml
                    val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                            "    <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                            "    <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                            "    <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                            "    <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                            "</Types>"
                    zos.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
                    zos.write(contentTypes.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. _rels/.rels
                    val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                            "    <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                            "</Relationships>"
                    zos.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
                    zos.write(rels.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 3. xl/workbook.xml
                    val workbookXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                            "    <sheets>\n" +
                            "        <sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                            "    </sheets>\n" +
                            "</workbook>"
                    zos.putNextEntry(java.util.zip.ZipEntry("xl/workbook.xml"))
                    zos.write(workbookXml.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 4. xl/_rels/workbook.xml.rels
                    val wbRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                            "    <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                            "</Relationships>"
                    zos.putNextEntry(java.util.zip.ZipEntry("xl/_rels/workbook.xml.rels"))
                    zos.write(wbRels.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 5. xl/worksheets/sheet1.xml
                    zos.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"))
                    val sheetStart = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                            "    <sheetData>\n"
                    zos.write(sheetStart.toByteArray(Charsets.UTF_8))

                    for ((rowIndex, rowData) in allRows.withIndex()) {
                        val rowNum = rowIndex + 1
                        zos.write("        <row r=\"$rowNum\">\n".toByteArray(Charsets.UTF_8))
                        for ((colIndex, cellValue) in rowData.withIndex()) {
                            val colName = getColumnName(colIndex)
                            val cellRef = "$colName$rowNum"
                            val cleanValue = escapeXml(cellValue)
                            val cellXml = "            <c r=\"$cellRef\" t=\"inlineStr\"><is><t xml:space=\"preserve\">$cleanValue</t></is></c>\n"
                            zos.write(cellXml.toByteArray(Charsets.UTF_8))
                        }
                        zos.write("        </row>\n".toByteArray(Charsets.UTF_8))
                    }

                    val sheetEnd = "    </sheetData>\n</worksheet>"
                    zos.write(sheetEnd.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }

            isSuccess = true
            return@withContext Uri.fromFile(outputFile)

        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (oom: OutOfMemoryError) {
            throw Exception("Document processing ran out of memory. Try selecting a smaller PDF.", oom)
        } catch (e: Exception) {
            throw e
        } finally {
            try { document?.close() } catch (_: Exception) {}
            try {
                if (tempPdfFile.exists()) tempPdfFile.delete()
                if (isSuccess) {
                    opDir.listFiles()?.forEach { file ->
                        if (file != outputFile && file.exists()) {
                            file.delete()
                        }
                    }
                }
            } catch (_: Exception) {}
            if (!isSuccess) {
                try { opDir.deleteRecursively() } catch (_: Exception) {}
            }
        }
    }

    private fun getColumnName(index: Int): String {
        var colName = ""
        var num = index + 1
        while (num > 0) {
            val mod = (num - 1) % 26
            colName = (65 + mod).toChar() + colName
            num = (num - 1) / 26
        }
        return colName
    }
}
