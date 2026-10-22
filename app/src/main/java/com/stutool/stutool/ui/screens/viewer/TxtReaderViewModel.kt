package com.stutool.stutool.ui.screens.viewer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class TxtReaderViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MIN_FONT_SIZE_SP = 11
        const val MAX_FONT_SIZE_SP = 28
        const val DEFAULT_FONT_SIZE_SP = 15
        private const val PERSISTENT_TXT_COPY_DIR = "txt_copies"
        private const val MAX_LOCAL_COPY_BYTES = 5L * 1024L * 1024L // 5 MB
    }

    private val recentTxtRepository = com.stutool.stutool.data.local.RecentTxtRepository(application)

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _documentName = MutableStateFlow("Text Document")
    val documentName: StateFlow<String> = _documentName.asStateFlow()

    private val _fileSizeBytes = MutableStateFlow(0L)
    val fileSizeBytes: StateFlow<Long> = _fileSizeBytes.asStateFlow()

    private val _detectedEncoding = MutableStateFlow("UTF-8")
    val detectedEncoding: StateFlow<String> = _detectedEncoding.asStateFlow()

    private val _lines = MutableStateFlow<List<TxtLine>>(emptyList())
    val lines: StateFlow<List<TxtLine>> = _lines.asStateFlow()

    private val _isEmptyFile = MutableStateFlow(false)
    val isEmptyFile: StateFlow<Boolean> = _isEmptyFile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hadMalformedBytes = MutableStateFlow(false)
    val hadMalformedBytes: StateFlow<Boolean> = _hadMalformedBytes.asStateFlow()

    private val _currentWindowIndex = MutableStateFlow(0)
    val currentWindowIndex: StateFlow<Int> = _currentWindowIndex.asStateFlow()

    private val _hasMoreAfter = MutableStateFlow(false)
    val hasMoreAfter: StateFlow<Boolean> = _hasMoreAfter.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(DEFAULT_FONT_SIZE_SP)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _wordWrap = MutableStateFlow(true)
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    private val _showLineNumbers = MutableStateFlow(true)
    val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

    private var loadingJob: Job? = null

    fun openFromEncodedUri(encodedUri: String) {
        if (encodedUri.isBlank()) {
            _errorMessage.value = "No file URI provided."
            return
        }
        val decodedUriString = try {
            String(Base64.decode(encodedUri, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            _errorMessage.value = "Invalid document link."
            return
        }
        val uri = Uri.parse(decodedUriString)
        if (_currentUri.value == uri && (_lines.value.isNotEmpty() || _isEmptyFile.value) && _errorMessage.value == null) {
            return
        }
        _currentUri.value = uri
        loadWindow(0)
    }

    fun retry() {
        loadWindow(_currentWindowIndex.value)
    }

    fun loadNextWindow() {
        if (_isLoading.value || !_hasMoreAfter.value) return
        loadWindow(_currentWindowIndex.value + 1)
    }

    fun loadPreviousWindow() {
        if (_isLoading.value || _currentWindowIndex.value <= 0) return
        loadWindow(_currentWindowIndex.value - 1)
    }

    fun loadWindow(targetWindowIndex: Int) {
        val uri = _currentUri.value ?: return
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val context = getApplication<Application>()
                resolveMetadata(uri, context)

                ensureActive()

                openInputStreamForUri(uri, context).use { stream ->
                    if (stream == null) {
                        throw IOException("Unable to open stream for the selected file.")
                    }
                    val window = TxtDocumentLoader.loadWindow(
                        inputStream = stream,
                        windowIndex = targetWindowIndex,
                        checkCancelled = { ensureActive() }
                    )

                    ensureActive()

                    _lines.value = window.lines
                    _detectedEncoding.value = window.detectedCharset
                    _isEmptyFile.value = window.isEmpty
                    _hasMoreAfter.value = window.hasMoreAfter
                    _currentWindowIndex.value = window.windowIndex
                    _hadMalformedBytes.value = window.hadMalformedBytes

                    if (targetWindowIndex == 0) {
                        persistRecentTxtEntry(uri, context)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: TxtLoadException) {
                _lines.value = emptyList()
                _errorMessage.value = e.message ?: "Unsupported or invalid text file."
            } catch (e: SecurityException) {
                _lines.value = emptyList()
                _errorMessage.value = "Permission denied to access this file. Please re-select the file."
            } catch (e: FileNotFoundException) {
                _lines.value = emptyList()
                _errorMessage.value = "File not found. It may have been moved or deleted."
            } catch (e: IOException) {
                _lines.value = emptyList()
                _errorMessage.value = "Failed to read the file: ${e.localizedMessage ?: "I/O error"}"
            } catch (e: Exception) {
                _lines.value = emptyList()
                _errorMessage.value = "Could not open text file: ${e.localizedMessage ?: "Unexpected error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun increaseFontSize() {
        _fontSizeSp.value = (_fontSizeSp.value + 2).coerceAtMost(MAX_FONT_SIZE_SP)
    }

    fun decreaseFontSize() {
        _fontSizeSp.value = (_fontSizeSp.value - 2).coerceAtLeast(MIN_FONT_SIZE_SP)
    }

    fun toggleWordWrap() {
        _wordWrap.value = !_wordWrap.value
    }

    fun toggleLineNumbers() {
        _showLineNumbers.value = !_showLineNumbers.value
    }

    fun cancelLoading() {
        loadingJob?.cancel()
        loadingJob = null
    }

    override fun onCleared() {
        cancelLoading()
        super.onCleared()
    }

    private fun resolveMetadata(uri: Uri, context: Context) {
        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file != null && file.exists()) {
                _documentName.value = file.name
                _fileSizeBytes.value = file.length()
                return
            }
        }

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            _documentName.value = name
                        }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        _fileSizeBytes.value = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
            // Keep fallback metadata if cursor query fails
        }
    }

    private fun openInputStreamForUri(uri: Uri, context: Context): InputStream? {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw FileNotFoundException("Missing file path.")
            val file = File(path)
            if (!file.exists()) throw FileNotFoundException("File does not exist.")
            return FileInputStream(file)
        }
        return context.contentResolver.openInputStream(uri)
    }

    private fun persistRecentTxtEntry(uri: Uri, context: Context) {
        try {
            val rawName = _documentName.value.ifBlank { "Text Document" }
            val displayName = rawName.substringBeforeLast(".").takeIf { it.isNotBlank() } ?: rawName
            val size = _fileSizeBytes.value

            val localCopyPath = if (uri.scheme == "content" && size in 0L..MAX_LOCAL_COPY_BYTES) {
                copyTxtUriToInternalStorage(uri, displayName, context)
            } else if (uri.scheme == "file") {
                uri.path
            } else {
                null
            }

            recentTxtRepository.saveRecentTxt(
                com.stutool.stutool.domain.model.RecentTxtFile(
                    uri = uri.toString(),
                    name = displayName,
                    size = size,
                    lastOpened = System.currentTimeMillis(),
                    localCopyPath = localCopyPath
                )
            )
        } catch (_: Exception) {
            // Non-critical if saving recent history fails
        }
    }

    private fun copyTxtUriToInternalStorage(uri: Uri, displayName: String, context: Context): String? {
        return try {
            val dir = File(context.filesDir, PERSISTENT_TXT_COPY_DIR).also { it.mkdirs() }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".txt"
            val destFile = File(dir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.BufferedOutputStream(java.io.FileOutputStream(destFile)).use { output ->
                    java.io.BufferedInputStream(input).copyTo(output)
                }
            }
            if (destFile.exists()) destFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }
}
