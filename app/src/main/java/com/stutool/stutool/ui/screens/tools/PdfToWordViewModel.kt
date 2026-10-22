package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.domain.engine.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfToWordViewModel(application: Application) : AndroidViewModel(application) {
    init {
        PDFBoxResourceLoader.init(application)
    }

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

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow("Preparing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _outputUri = MutableStateFlow<Uri?>(null)
    val outputUri: StateFlow<Uri?> = _outputUri.asStateFlow()

    private val _outputFileSizeBytes = MutableStateFlow(0L)
    val outputFileSizeBytes: StateFlow<Long> = _outputFileSizeBytes.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private var processingJob: Job? = null

    fun setPdf(uri: Uri?) {
        _selectedUri.value = uri
        _outputUri.value = null
        _outputFileSizeBytes.value = 0L
        _errorMessage.value = null
        _processingError.value = null
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
                val pages = withContext(Dispatchers.IO) {
                    PdfEngine.countPages(getApplication(), uri)
                }
                _pageCount.value = pages
            } catch (e: SecurityException) {
                _errorMessage.value = "Password-protected PDFs are not supported."
                _selectedUri.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to read PDF pages: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isLoadingInfo.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearProcessingError() {
        _processingError.value = null
    }

    fun startConversion() {
        val uri = _selectedUri.value ?: return
        if (_isConverting.value) return

        processingJob = viewModelScope.launch {
            _isConverting.value = true
            _processingError.value = null
            _progress.value = 0f
            _statusMessage.value = "Starting extraction..."

            try {
                var lastProgressTime = 0L
                val outUri = PdfEngine.convertPdfToWord(
                    context = getApplication(),
                    sourceUri = uri,
                    onProgress = { current, total ->
                        val now = System.currentTimeMillis()
                        if (current == total || current == 1 || now - lastProgressTime >= 50L) {
                            lastProgressTime = now
                            val frac = if (total > 0) current.toFloat() / total.toFloat() else -1f
                            _progress.value = frac
                            _statusMessage.value = "Extracting text: page $current of $total..."
                        }
                    }
                )

                _outputUri.value = outUri
                _progress.value = 1f
                _statusMessage.value = "Successfully generated Word document."

                try {
                    outUri.path?.let { path ->
                        val f = File(path)
                        if (f.exists()) {
                            _outputFileSizeBytes.value = f.length()
                        }
                    }
                } catch (_: Exception) {}

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _processingError.value = e.message ?: "Failed to convert document."
            } catch (oom: OutOfMemoryError) {
                _processingError.value = "Document is too large to process. Out of memory."
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

    fun reset() {
        _selectedUri.value = null
        _fileName.value = null
        _fileSizeBytes.value = 0L
        _pageCount.value = 0
        _isLoadingInfo.value = false
        _outputUri.value = null
        _outputFileSizeBytes.value = 0L
        _errorMessage.value = null
        _processingError.value = null
        _progress.value = 0f
        _statusMessage.value = "Preparing..."
    }
}
