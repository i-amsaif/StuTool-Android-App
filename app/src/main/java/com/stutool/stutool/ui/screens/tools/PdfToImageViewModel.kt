package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.domain.engine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfToImageViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _fileSizeBytes = MutableStateFlow(0L)
    val fileSizeBytes: StateFlow<Long> = _fileSizeBytes.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _isLoadingInfo = MutableStateFlow(false)
    val isLoadingInfo: StateFlow<Boolean> = _isLoadingInfo.asStateFlow()

    private val _format = MutableStateFlow("JPEG")
    val format: StateFlow<String> = _format.asStateFlow()

    private val _quality = MutableStateFlow("High")
    val quality: StateFlow<String> = _quality.asStateFlow()

    private val _pageRange = MutableStateFlow("")
    val pageRange: StateFlow<String> = _pageRange.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow("Preparing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _outputFiles = MutableStateFlow<List<File>?>(null)
    val outputFiles: StateFlow<List<File>?> = _outputFiles.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private var processingJob: Job? = null

    fun setPdf(uri: Uri?) {
        _selectedUri.value = uri
        _outputFiles.value = null
        _errorMessage.value = null
        _processingError.value = null
        _pageRange.value = ""
        _progress.value = 0f

        if (uri == null) {
            _fileName.value = null
            _fileSizeBytes.value = 0L
            _pageCount.value = 0
            _isLoadingInfo.value = false
            return
        }

        var extractedName = "document.pdf"
        var extractedSize = 0L
        try {
            if (uri.scheme == "content") {
                getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            extractedName = cursor.getString(nameIndex) ?: extractedName
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            extractedSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } else {
                uri.path?.let { path ->
                    val f = File(path)
                    extractedName = f.name
                    extractedSize = f.length()
                }
            }
        } catch (_: Exception) {}

        _fileName.value = extractedName
        _fileSizeBytes.value = extractedSize
        _pageCount.value = 0
        _isLoadingInfo.value = true

        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    PdfEngine.countPages(getApplication(), uri)
                }
                _pageCount.value = count
            } catch (e: SecurityException) {
                _errorMessage.value = "Encrypted PDF: Password-protected PDFs are not supported."
                _selectedUri.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to open PDF file: ${e.localizedMessage ?: "Unknown error"}"
                _selectedUri.value = null
            } finally {
                _isLoadingInfo.value = false
            }
        }
    }

    fun setFormat(fmt: String) { _format.value = fmt }
    fun setQuality(q: String) { _quality.value = q }
    fun setPageRange(range: String) { _pageRange.value = range }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearProcessingError() {
        _processingError.value = null
    }

    fun startConversion() {
        val uri = _selectedUri.value ?: return
        if (_isConverting.value) return

        val totalPages = _pageCount.value
        val targetPages = parsePageRange(_pageRange.value, totalPages)
        if (targetPages.isEmpty()) {
            _errorMessage.value = "Invalid page range."
            return
        }

        processingJob = viewModelScope.launch {
            _isConverting.value = true
            _processingError.value = null
            _progress.value = 0f
            _statusMessage.value = "Converting pages to images..."

            try {
                var lastProgressTime = 0L
                val files = PdfEngine.convertPdfToImages(
                    context = getApplication(),
                    sourceUri = uri,
                    format = _format.value,
                    qualityMode = _quality.value,
                    targetPages = targetPages,
                    onProgress = { current, total ->
                        val now = System.currentTimeMillis()
                        if (current == total || current == 1 || now - lastProgressTime >= 50L) {
                            lastProgressTime = now
                            val frac = if (total > 0) current.toFloat() / total.toFloat() else -1f
                            _progress.value = frac
                            _statusMessage.value = "Rendered $current of $total image(s)..."
                        }
                    }
                )
                _outputFiles.value = files
                _progress.value = 1f
                _statusMessage.value = "Successfully generated ${files.size} image(s)."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _processingError.value = e.message ?: "Failed to convert PDF."
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun cancelConversion() {
        processingJob?.cancel()
        _isConverting.value = false
        _progress.value = 0f
        _statusMessage.value = "Preparing..."
    }

    fun saveToGallery(onComplete: (String) -> Unit) {
        val files = _outputFiles.value ?: return
        viewModelScope.launch {
            _isConverting.value = true
            _statusMessage.value = "Saving images to Gallery..."
            try {
                val savedCount = withContext(Dispatchers.IO) {
                    PdfEngine.saveImagesToGallery(getApplication(), files, _format.value)
                }
                if (savedCount == files.size) {
                    onComplete("Successfully saved all $savedCount images to Gallery.")
                } else if (savedCount > 0) {
                    onComplete("Partially saved $savedCount of ${files.size} images to Gallery.")
                } else {
                    onComplete("Failed to save images to Gallery.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(e.message ?: "Failed to save to Gallery.")
            } finally {
                _isConverting.value = false
            }
        }
    }

    suspend fun createZipForSharing(): File? {
        val files = _outputFiles.value ?: return null
        return withContext(Dispatchers.IO) {
            PdfEngine.createZipFile(getApplication(), files, "PDF_Images_${System.currentTimeMillis()}.zip")
        }
    }

    fun reset() {
        _selectedUri.value = null
        _fileName.value = null
        _fileSizeBytes.value = 0L
        _pageCount.value = 0
        _isLoadingInfo.value = false
        _outputFiles.value = null
        _errorMessage.value = null
        _processingError.value = null
        _pageRange.value = ""
        _progress.value = 0f
        _statusMessage.value = "Preparing..."
    }

    private fun parsePageRange(range: String, totalPages: Int): List<Int> {
        if (range.isBlank()) return (1..totalPages).toList()

        val pages = mutableSetOf<Int>()
        try {
            val parts = range.split(",")
            for (part in parts) {
                val p = part.trim()
                if (p.contains("-")) {
                    val bounds = p.split("-")
                    if (bounds.size == 2) {
                        val start = bounds[0].trim().toInt()
                        val end = bounds[1].trim().toInt()
                        if (start in 1..end && end <= totalPages) {
                            pages.addAll(start..end)
                        } else {
                            return emptyList()
                        }
                    }
                } else {
                    val page = p.toInt()
                    if (page in 1..totalPages) {
                        pages.add(page)
                    } else {
                        return emptyList()
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return pages.sorted()
    }
}
