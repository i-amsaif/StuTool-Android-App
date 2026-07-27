package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
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
import com.stutool.stutool.ui.components.ReorderableItem

// ── Enums ──

enum class PagesPerSheetOption(val count: Int, val label: String) {
    TWO(2, "2 Pages"),
    FOUR(4, "4 Pages"),
    SIX(6, "6 Pages"),
    EIGHT(8, "8 Pages"),
    NINE(9, "9 Pages"),
    SIXTEEN(16, "16 Pages")
}

enum class SheetSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 (210 × 297 mm)", 595, 842),
    US_LETTER("US Letter (8.5 × 11 in)", 612, 792)
}

enum class SheetOrientation { PORTRAIT, LANDSCAPE }

enum class ReadingDirection(val label: String) {
    ROW_BY_ROW("Row by Row"),
    COLUMN_BY_COLUMN("Column by Column")
}

enum class InnerSpacingOption(val label: String, val pts: Int) {
    SMALL("Small", 4),
    MEDIUM("Medium", 10),
    LARGE("Large", 20)
}

data class OuterMargins(
    val top: Int = 20,
    val bottom: Int = 20,
    val left: Int = 20,
    val right: Int = 20
)

data class SelectedFile(
    val uri: Uri,
    override val id: String = UUID.randomUUID().toString()
) : ReorderableItem

class PagesPerSheetViewModel(application: Application) : AndroidViewModel(application) {

    // ── State ──

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _pagesPerSheet = MutableStateFlow(PagesPerSheetOption.FOUR)
    val pagesPerSheet: StateFlow<PagesPerSheetOption> = _pagesPerSheet.asStateFlow()

    private val _sheetSize = MutableStateFlow(SheetSize.A4)
    val sheetSize: StateFlow<SheetSize> = _sheetSize.asStateFlow()

    private val _orientation = MutableStateFlow(SheetOrientation.PORTRAIT)
    val orientation: StateFlow<SheetOrientation> = _orientation.asStateFlow()

    private val _readingDirection = MutableStateFlow(ReadingDirection.ROW_BY_ROW)
    val readingDirection: StateFlow<ReadingDirection> = _readingDirection.asStateFlow()

    private val _outerMargins = MutableStateFlow(OuterMargins())
    val outerMargins: StateFlow<OuterMargins> = _outerMargins.asStateFlow()

    private val _innerSpacing = MutableStateFlow(InnerSpacingOption.SMALL)
    val innerSpacing: StateFlow<InnerSpacingOption> = _innerSpacing.asStateFlow()

    private val _drawBorder = MutableStateFlow(true)
    val drawBorder: StateFlow<Boolean> = _drawBorder.asStateFlow()

    // ── Collapsible Section States ──

    private val _layoutExpanded = MutableStateFlow(true)
    val layoutExpanded: StateFlow<Boolean> = _layoutExpanded.asStateFlow()

    private val _spacingExpanded = MutableStateFlow(false)
    val spacingExpanded: StateFlow<Boolean> = _spacingExpanded.asStateFlow()

    private val _orderExpanded = MutableStateFlow(false)
    val orderExpanded: StateFlow<Boolean> = _orderExpanded.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow("Preparing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _resultUri = MutableStateFlow<Uri?>(null)
    val resultUri: StateFlow<Uri?> = _resultUri.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private var processingJob: kotlinx.coroutines.Job? = null

    // ── Actions ──

    fun addFiles(uris: List<Uri>) {
        val newFiles = uris.map { SelectedFile(it) }
        _selectedFiles.value = _selectedFiles.value + newFiles
        _resultUri.value = null
        _errorMessage.value = null
    }

    fun removeFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != id }
    }

    fun moveFile(fromId: String, toIndex: Int) {
        _selectedFiles.value = _selectedFiles.value.toMutableList().let { list ->
            val fromIndex = list.indexOfFirst { it.id == fromId }
            if (fromIndex != -1 && toIndex in list.indices && fromIndex != toIndex) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            list.toList()
        }
    }

    fun setPagesPerSheet(option: PagesPerSheetOption) { _pagesPerSheet.value = option }
    fun setSheetSize(size: SheetSize) { _sheetSize.value = size }
    fun setOrientation(o: SheetOrientation) { _orientation.value = o }
    fun setReadingDirection(d: ReadingDirection) { _readingDirection.value = d }
    fun setInnerSpacing(s: InnerSpacingOption) { _innerSpacing.value = s }
    fun setDrawBorder(enabled: Boolean) { _drawBorder.value = enabled }

    fun setOuterMarginTop(v: Int) { _outerMargins.value = _outerMargins.value.copy(top = v) }
    fun setOuterMarginBottom(v: Int) { _outerMargins.value = _outerMargins.value.copy(bottom = v) }
    fun setOuterMarginLeft(v: Int) { _outerMargins.value = _outerMargins.value.copy(left = v) }
    fun setOuterMarginRight(v: Int) { _outerMargins.value = _outerMargins.value.copy(right = v) }

    fun toggleLayoutExpanded() { _layoutExpanded.value = !_layoutExpanded.value }
    fun toggleSpacingExpanded() { _spacingExpanded.value = !_spacingExpanded.value }
    fun toggleOrderExpanded() { _orderExpanded.value = !_orderExpanded.value }

    fun generatePdf(context: Context) {
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            _errorMessage.value = "Please select at least one file."
            return
        }

        _isProcessing.value = true
        _errorMessage.value = null
        _processingError.value = null
        _statusMessage.value = "Preparing..."

        processingJob = viewModelScope.launch {
            try {
                // EXPLICIT ORDER GUARANTEE: Map URIs from the current ordered list
                val orderedUris = files.map { it.uri }
                val uri = performGeneration(context, orderedUris)
                delay(400)
                _resultUri.value = uri
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore, as it was cancelled intentionally
            } catch (e: Exception) {
                _processingError.value = "An error occurred while generating the PDF. Please try again."
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun performGeneration(context: Context, inputUris: List<Uri>): Uri =
        withContext(Dispatchers.IO) {
            val pps = _pagesPerSheet.value.count
            val size = _sheetSize.value
            val orient = _orientation.value
            val direction = _readingDirection.value
            val margins = _outerMargins.value
            val spacing = _innerSpacing.value.pts
            val border = _drawBorder.value

            // Resolve sheet dimensions
            val sheetW: Int
            val sheetH: Int
            if (orient == SheetOrientation.LANDSCAPE) {
                sheetW = maxOf(size.widthPt, size.heightPt)
                sheetH = minOf(size.widthPt, size.heightPt)
            } else {
                sheetW = minOf(size.widthPt, size.heightPt)
                sheetH = maxOf(size.widthPt, size.heightPt)
            }

            // Calculate grid dimensions (cols x rows)
            val (cols, rows) = calculateGrid(pps, orient)

            _statusMessage.value = "Preparing document..."
            // Collect all source page bitmaps from all input files
            val sourceBitmaps = mutableListOf<Bitmap>()
            try {
                for (uri in inputUris) {
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    if (mimeType == "application/pdf") {
                        // Open PDF and render each page as a bitmap
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            ?: continue
                        val renderer = PdfRenderer(pfd)
                        for (pageIndex in 0 until renderer.pageCount) {
                            val srcPage = renderer.openPage(pageIndex)
                            val renderScale = 2
                            val bmpW = srcPage.width * renderScale
                            val bmpH = srcPage.height * renderScale
                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            srcPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            srcPage.close()
                            sourceBitmaps.add(bitmap)
                        }
                        renderer.close()
                        pfd.close()
                    } else if (mimeType.startsWith("image/")) {
                        // Decode image as a single page
                        val bitmap = decodeSampledBitmap(context, uri, sheetW * 2, sheetH * 2)
                        if (bitmap != null) {
                            sourceBitmaps.add(bitmap)
                        }
                    }
                }

                _statusMessage.value = "Processing pages..."
                if (sourceBitmaps.isEmpty()) {
                    throw Exception("No pages could be extracted from the selected files.")
                }

                _statusMessage.value = "Building document..."
                val outputDoc = PdfDocument()
                var outputPageNum = 1
                var srcIndex = 0

                while (srcIndex < sourceBitmaps.size) {
                    val pageInfo = PdfDocument.PageInfo.Builder(sheetW, sheetH, outputPageNum).create()
                    val outputPage = outputDoc.startPage(pageInfo)
                    val canvas: Canvas = outputPage.canvas

                    // Fill white background
                    canvas.drawColor(Color.WHITE)

                    // Available area after outer margins
                    val availX = margins.left.toFloat()
                    val availY = margins.top.toFloat()
                    val availW = sheetW - margins.left - margins.right
                    val availH = sheetH - margins.top - margins.bottom

                    // Cell dimensions (accounting for inner spacing)
                    val totalSpacingX = spacing * (cols - 1)
                    val totalSpacingY = spacing * (rows - 1)
                    val cellW = (availW - totalSpacingX).toFloat() / cols
                    val cellH = (availH - totalSpacingY).toFloat() / rows

                    val borderPaint = if (border) {
                        Paint().apply {
                            color = Color.GRAY
                            style = Paint.Style.STROKE
                            strokeWidth = 0.5f
                        }
                    } else null

                    for (slot in 0 until pps) {
                        if (srcIndex >= sourceBitmaps.size) break

                        // Determine grid position based on reading direction
                        val col: Int
                        val row: Int
                        if (direction == ReadingDirection.ROW_BY_ROW) {
                            col = slot % cols
                            row = slot / cols
                        } else {
                            col = slot / rows
                            row = slot % rows
                        }

                        val cellX = availX + col * (cellW + spacing)
                        val cellY = availY + row * (cellH + spacing)

                        val bitmap = sourceBitmaps[srcIndex]

                        // Scale bitmap to fit cell, maintaining aspect ratio
                        val scaleX = cellW / bitmap.width
                        val scaleY = cellH / bitmap.height
                        val scale = minOf(scaleX, scaleY)
                        val drawW = bitmap.width * scale
                        val drawH = bitmap.height * scale

                        // Center within cell
                        val drawX = cellX + (cellW - drawW) / 2f
                        val drawY = cellY + (cellH - drawH) / 2f

                        val destRect = android.graphics.RectF(drawX, drawY, drawX + drawW, drawY + drawH)
                        canvas.drawBitmap(bitmap, null, destRect, null)

                        // Draw border around the drawn area
                        if (borderPaint != null) {
                            canvas.drawRect(destRect, borderPaint)
                        }

                        srcIndex++
                    }

                    outputDoc.finishPage(outputPage)
                    outputPageNum++
                }

                _statusMessage.value = "Finalizing..."
                // Save output
                val fileName = "pages_per_sheet_${System.currentTimeMillis()}.pdf"
                val outUri = savePdf(context, outputDoc, fileName)
                outputDoc.close()

                outUri
            } finally {
                // Recycle all bitmaps
                sourceBitmaps.forEach { it.recycle() }
            }
        }

    private fun calculateGrid(pagesPerSheet: Int, orientation: SheetOrientation): Pair<Int, Int> {
        // Returns (cols, rows)
        return when (pagesPerSheet) {
            2 -> if (orientation == SheetOrientation.PORTRAIT) Pair(1, 2) else Pair(2, 1)
            4 -> Pair(2, 2)
            6 -> if (orientation == SheetOrientation.PORTRAIT) Pair(2, 3) else Pair(3, 2)
            8 -> if (orientation == SheetOrientation.PORTRAIT) Pair(2, 4) else Pair(4, 2)
            9 -> Pair(3, 3)
            16 -> Pair(4, 4)
            else -> Pair(2, 2)
        }
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
        _selectedFiles.value = emptyList()
        _resultUri.value = null
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
