package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.domain.engine.CompressionResult
import com.stutool.stutool.domain.engine.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File

enum class CompressionLevel(val title: String, val quality: Int, val scaleFactor: Float) {
    LOW("Low", 80, 1.0f),
    MEDIUM("Medium", 60, 0.8f),
    HIGH("High", 40, 0.5f)
}

class CompressPdfViewModel(application: Application) : AndroidViewModel(application) {

    init {
        PDFBoxResourceLoader.init(application)
    }

    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri.asStateFlow()

    private val _originalFileName = MutableStateFlow<String?>(null)
    val originalFileName: StateFlow<String?> = _originalFileName.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _compressionLevel = MutableStateFlow(CompressionLevel.MEDIUM)
    val compressionLevel: StateFlow<CompressionLevel> = _compressionLevel.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()

    private val _statusMessage = MutableStateFlow("Preparing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private val _compressionResult = MutableStateFlow<CompressionResult?>(null)
    val compressionResult: StateFlow<CompressionResult?> = _compressionResult.asStateFlow()

    private var processingJob: kotlinx.coroutines.Job? = null

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadFile(uri: Uri, context: Context) {
        _selectedFileUri.value = uri
        _compressionResult.value = null
        _errorMessage.value = null
        _originalFileName.value = getFileName(uri, context) ?: "document"

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val pagesCount = PdfEngine.countPages(context, uri)
                _totalPages.value = pagesCount
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load PDF: ${e.localizedMessage}"
                _selectedFileUri.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getFileName(uri: Uri, context: Context): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { File(it).name }
        }
        return name?.substringBeforeLast(".")
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _compressionLevel.value = level
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearProcessingError() {
        _processingError.value = null
    }

    fun resetState() {
        _selectedFileUri.value = null
        _originalFileName.value = null
        _totalPages.value = 0
        _compressionLevel.value = CompressionLevel.MEDIUM
        _compressionResult.value = null
        _errorMessage.value = null
        _isCompressing.value = false
        _statusMessage.value = "Preparing..."
        _processingError.value = null
    }

    fun compressPdf(context: Context) {
        val uri = _selectedFileUri.value
        val originalName = _originalFileName.value ?: "document"

        if (uri == null) {
            _errorMessage.value = "No file selected."
            return
        }

        _isCompressing.value = true
        _processingError.value = null
        _compressionResult.value = null
        _statusMessage.value = "Preparing document..."

        processingJob = viewModelScope.launch {
            val messageJob = launch {
                val messages = listOf("Preparing document...", "Optimizing document...", "Reducing file size...", "Finalizing...")
                for (msg in messages) {
                    _statusMessage.value = msg
                    kotlinx.coroutines.delay(1200)
                }
            }
            try {
                val level = _compressionLevel.value
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PdfEngine.compressPdf(
                        context = context,
                        sourceUri = uri,
                        originalFileName = originalName,
                        qualityLevel = level.quality,
                        scaleFactor = level.scaleFactor
                    )
                }
                _compressionResult.value = result
                delay(400)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore, as it was cancelled intentionally
            } catch (e: Exception) {
                _processingError.value = "An error occurred while compressing the PDF. Please try again."
                e.printStackTrace()
            } finally {
                messageJob.cancel()
                _isCompressing.value = false
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        resetState()
    }
}
