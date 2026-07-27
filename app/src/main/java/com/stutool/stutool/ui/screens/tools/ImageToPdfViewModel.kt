package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SelectedImage(
    val uri: Uri,
    val id: String = UUID.randomUUID().toString()
)

// ── Enums ──

enum class PageOrientation { PORTRAIT, LANDSCAPE }

enum class PageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 (210 × 297 mm)", 595, 842),
    US_LETTER("US Letter (8.5 × 11 in)", 612, 792)
}

enum class MarginOption(val label: String, val pts: Int) {
    NONE("None", 0),
    SMALL("Small", 20),
    LARGE("Large", 40)
}

data class ConversionResult(
    val uris: List<Uri>,
    val isMerged: Boolean
)

class ImageToPdfViewModel(application: Application) : AndroidViewModel(application) {

    // ── State ──

    private val _selectedImages = MutableStateFlow<List<SelectedImage>>(emptyList())
    val selectedImages: StateFlow<List<SelectedImage>> = _selectedImages.asStateFlow()

    private val _orientation = MutableStateFlow(PageOrientation.PORTRAIT)
    val orientation: StateFlow<PageOrientation> = _orientation.asStateFlow()

    private val _pageSize = MutableStateFlow(PageSize.A4)
    val pageSize: StateFlow<PageSize> = _pageSize.asStateFlow()

    private val _margin = MutableStateFlow(MarginOption.NONE)
    val margin: StateFlow<MarginOption> = _margin.asStateFlow()

    private val _mergeEnabled = MutableStateFlow(true)
    val mergeEnabled: StateFlow<Boolean> = _mergeEnabled.asStateFlow()

    private val _isSettingsExpanded = MutableStateFlow(true)
    val isSettingsExpanded: StateFlow<Boolean> = _isSettingsExpanded.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _conversionResult = MutableStateFlow<ConversionResult?>(null)
    val conversionResult: StateFlow<ConversionResult?> = _conversionResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow("Preparing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private var processingJob: kotlinx.coroutines.Job? = null

    // ── Actions ──

    fun addImages(uris: List<Uri>) {
        val newImages = uris.map { SelectedImage(it) }
        _selectedImages.value = _selectedImages.value + newImages
        _conversionResult.value = null
        _errorMessage.value = null
    }

    fun removeImage(id: String) {
        _selectedImages.value = _selectedImages.value.filter { it.id != id }
    }

    fun moveImage(fromId: String, toIndex: Int) {
        val currentList = _selectedImages.value.toMutableList()
        val fromIndex = currentList.indexOfFirst { it.id == fromId }
        
        if (fromIndex != -1 && toIndex in currentList.indices && fromIndex != toIndex) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _selectedImages.value = currentList
        }
    }

    fun setOrientation(o: PageOrientation) { _orientation.value = o }
    fun setPageSize(s: PageSize) { _pageSize.value = s }
    fun setMargin(m: MarginOption) { _margin.value = m }
    fun setMergeEnabled(enabled: Boolean) { _mergeEnabled.value = enabled }
    fun toggleSettingsExpanded() { _isSettingsExpanded.value = !_isSettingsExpanded.value }

    fun convertToPdf(context: Context) {
        val images = _selectedImages.value
        if (images.isEmpty()) {
            _errorMessage.value = "Please select at least one image."
            return
        }

        _isConverting.value = true
        _errorMessage.value = null
        _processingError.value = null
        _statusMessage.value = "Preparing images..."

        processingJob = viewModelScope.launch {
            try {
                // EXPLICIT ORDER GUARANTEE: Map from the current ordered list of SelectedImage
                val imageUris = images.map { it.uri }
                val result = performConversion(context, imageUris)
                delay(400)
                _conversionResult.value = result
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore, as it was cancelled intentionally
            } catch (e: Exception) {
                _processingError.value = "An error occurred while converting the images to PDF. Please try again."
                e.printStackTrace()
            } finally {
                _isConverting.value = false
            }
        }
    }

    private suspend fun performConversion(
        context: Context,
        imageUris: List<Uri>
    ): ConversionResult = withContext(Dispatchers.IO) {

        val orient = _orientation.value
        val size = _pageSize.value
        val marginPts = _margin.value.pts
        val merge = _mergeEnabled.value

        // Resolve page dimensions based on orientation
        val pageW: Int
        val pageH: Int
        if (orient == PageOrientation.LANDSCAPE) {
            pageW = maxOf(size.widthPt, size.heightPt)
            pageH = minOf(size.widthPt, size.heightPt)
        } else {
            pageW = minOf(size.widthPt, size.heightPt)
            pageH = maxOf(size.widthPt, size.heightPt)
        }

        val resultUris = mutableListOf<Uri>()

        if (merge) {
            // All images → one PDF
            val pdfDoc = PdfDocument()
            imageUris.forEachIndexed { index, uri ->
                _statusMessage.value = "Creating pages... (${index + 1}/${imageUris.size})"
                val bitmap = decodeSampledBitmap(context, uri, pageW * 2, pageH * 2) ?: return@forEachIndexed
                drawImagePage(pdfDoc, bitmap, index + 1, pageW, pageH, marginPts)
                bitmap.recycle()
            }
            _statusMessage.value = "Building document..."
            val timestamp = System.currentTimeMillis()
            val fileName = "image_to_pdf_$timestamp.pdf"
            
            _statusMessage.value = "Finalizing..."
            val outUri = savePdf(context, pdfDoc, fileName)
            pdfDoc.close()
            resultUris.add(outUri)
        } else {
            // Each image → separate PDF
            imageUris.forEachIndexed { index, uri ->
                _statusMessage.value = "Creating pages... (${index + 1}/${imageUris.size})"
                val bitmap = decodeSampledBitmap(context, uri, pageW * 2, pageH * 2)
                if (bitmap != null) {
                    _statusMessage.value = "Building document..."
                    val pdfDoc = PdfDocument()
                    drawImagePage(pdfDoc, bitmap, 1, pageW, pageH, marginPts)
                    bitmap.recycle()
                    
                    val fileName = "image_${index + 1}.pdf"
                    _statusMessage.value = "Finalizing..."
                    val outUri = savePdf(context, pdfDoc, fileName)
                    pdfDoc.close()
                    resultUris.add(outUri)
                }
            }
        }

        if (resultUris.isEmpty()) throw Exception("No images could be processed.")

        ConversionResult(uris = resultUris, isMerged = merge)
    }

    private fun drawImagePage(
        pdfDoc: PdfDocument,
        bitmap: Bitmap,
        pageNumber: Int,
        pageW: Int,
        pageH: Int,
        marginPts: Int
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val availW = pageW - 2 * marginPts
        val availH = pageH - 2 * marginPts

        // Scale image to fit within available area, maintaining aspect ratio
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()
        val scale = minOf(availW / imgW, availH / imgH)
        val drawW = imgW * scale
        val drawH = imgH * scale

        // Center the image
        val left = marginPts + (availW - drawW) / 2f
        val top = marginPts + (availH - drawH) / 2f

        val destRect = android.graphics.RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(bitmap, null, destRect, null)

        pdfDoc.finishPage(page)
    }

    private fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            // First pass: get dimensions only
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            // Second pass: decode with inSampleSize
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun savePdf(context: Context, pdfDoc: PdfDocument, fileName: String): Uri {
        var outputStream: java.io.OutputStream? = null
        var finalUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/StuTool"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                ) ?: throw Exception("Failed to create MediaStore record")
                finalUri = uri
                outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw Exception("Failed to open output stream")
            } else {
                val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                val outputFile = File(outputDir, fileName)
                finalUri = Uri.fromFile(outputFile)
                outputStream = FileOutputStream(outputFile)
            }

            pdfDoc.writeTo(outputStream!!)
        } finally {
            outputStream?.close()
        }

        return finalUri ?: throw Exception("Failed to generate final URI")
    }

    fun resetState() {
        _selectedImages.value = emptyList()
        _conversionResult.value = null
        _errorMessage.value = null
        _processingError.value = null
        _statusMessage.value = "Preparing..."
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearProcessingError() {
        _processingError.value = null
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        resetState()
    }
}
