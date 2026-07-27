package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.stutool.stutool.domain.engine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class SelectedPdf(
    val uri: Uri,
    val id: String = UUID.randomUUID().toString()
)

class MergePdfViewModel(application: Application) : AndroidViewModel(application) {
    
    init {
        PDFBoxResourceLoader.init(application)
    }

    private val _selectedFiles = MutableStateFlow<List<SelectedPdf>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedPdf>> = _selectedFiles.asStateFlow()

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging.asStateFlow()

    private val _mergeSuccess = MutableStateFlow<Uri?>(null)
    val mergeSuccess: StateFlow<Uri?> = _mergeSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow("Preparing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private var processingJob: Job? = null

    fun addFiles(uris: List<Uri>) {
        val newFiles = uris.map { SelectedPdf(it) }
        _selectedFiles.value = _selectedFiles.value + newFiles
        _mergeSuccess.value = null
        _errorMessage.value = null
        _processingError.value = null
    }

    fun removeFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != id }
    }

    fun moveFile(fromId: String, toIndex: Int) {
        val currentList = _selectedFiles.value.toMutableList()
        val fromIndex = currentList.indexOfFirst { it.id == fromId }
        
        if (fromIndex != -1 && toIndex in currentList.indices && fromIndex != toIndex) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _selectedFiles.value = currentList
        }
    }

    fun mergePdfs(context: Context) {
        if (_selectedFiles.value.size < 2) {
            _errorMessage.value = "Please select at least 2 PDF files."
            return
        }

        _isMerging.value = true
        _errorMessage.value = null
        _processingError.value = null

        processingJob = viewModelScope.launch {
            try {
                _statusMessage.value = "Preparing documents..."
                val uris = _selectedFiles.value.map { it.uri }
                
                // Keep the UI lively during preparation
                delay(300)
                _statusMessage.value = "Processing pages..."
                delay(300)
                
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        try {
                            // Check for 0 bytes or unreadable file descriptor
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                                if (fd.statSize <= 0) {
                                    throw Exception("Empty file")
                                }
                            } ?: throw Exception("Unreadable file")
                            
                            // Check if it's a valid PDF that PDFBox can open
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                                    if (doc.numberOfPages <= 0) {
                                        throw Exception("Invalid PDF")
                                    }
                                }
                            } ?: throw Exception("Unreadable stream")
                        } catch (e: Exception) {
                            throw IllegalArgumentException("One or more selected PDFs are invalid or empty.")
                        }
                    }
                }

                _statusMessage.value = "Building document..."
                val resultUri = PdfEngine.mergePdfs(context, uris)
                
                _statusMessage.value = "Finalizing..."
                delay(400)
                _mergeSuccess.value = resultUri
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore, as it was cancelled intentionally
            } catch (e: IllegalArgumentException) {
                _processingError.value = e.message
            } catch (e: Exception) {
                _processingError.value = "An error occurred while merging the PDFs. Please try again."
                e.printStackTrace()
            } finally {
                _isMerging.value = false
            }
        }
    }

    fun resetState() {
        _selectedFiles.value = emptyList()
        _mergeSuccess.value = null
        _errorMessage.value = null
        _statusMessage.value = "Preparing..."
        _processingError.value = null
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        resetState()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearProcessingError() {
        _processingError.value = null
    }
}
