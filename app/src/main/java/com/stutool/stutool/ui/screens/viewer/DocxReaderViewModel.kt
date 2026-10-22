package com.stutool.stutool.ui.screens.viewer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.data.local.RecentDocxRepository
import com.stutool.stutool.domain.model.RecentDocxFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class DocxReaderViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MIN_FONT_SIZE_SP = 12
        const val MAX_FONT_SIZE_SP = 26
        const val DEFAULT_FONT_SIZE_SP = 16
        private const val PERSISTENT_DOCX_COPY_DIR = "docx_copies"
        private const val MAX_LOCAL_COPY_BYTES = 10L * 1024L * 1024L // 10 MB
    }

    private val recentDocxRepository = RecentDocxRepository(application)

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _documentName = MutableStateFlow("Word Document")
    val documentName: StateFlow<String> = _documentName.asStateFlow()

    private val _fileSizeBytes = MutableStateFlow(0L)
    val fileSizeBytes: StateFlow<Long> = _fileSizeBytes.asStateFlow()

    private val _blocks = MutableStateFlow<List<DocxBlock>>(emptyList())
    val blocks: StateFlow<List<DocxBlock>> = _blocks.asStateFlow()

    private val _isEmptyDocument = MutableStateFlow(false)
    val isEmptyDocument: StateFlow<Boolean> = _isEmptyDocument.asStateFlow()

    private val _isTruncated = MutableStateFlow(false)
    val isTruncated: StateFlow<Boolean> = _isTruncated.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(DEFAULT_FONT_SIZE_SP)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

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
        if (_currentUri.value == uri && (_blocks.value.isNotEmpty() || _isEmptyDocument.value) && _errorMessage.value == null) {
            return
        }
        _currentUri.value = uri
        loadDocument()
    }

    fun retry() {
        loadDocument()
    }

    fun loadDocument() {
        val uri = _currentUri.value ?: return
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val context = getApplication<Application>()
                resolveMetadata(uri, context)

                ensureActive()

                val resolvedName = _documentName.value
                if (resolvedName.endsWith(".doc", ignoreCase = true) &&
                    !resolvedName.endsWith(".docx", ignoreCase = true)
                ) {
                    throw DocxLoadException(
                        "Legacy binary Word (.doc) files are not supported by this viewer. " +
                            "Please open the document in Word or a compatible editor and save/export it as .docx."
                    )
                }

                openInputStreamForUri(uri, context).use { stream ->
                    if (stream == null) {
                        throw IOException("Unable to open stream for the selected file.")
                    }
                    val doc = DocxDocumentLoader.loadDocument(
                        inputStream = stream,
                        checkCancelled = { ensureActive() }
                    )

                    ensureActive()

                    _blocks.value = doc.blocks
                    _isEmptyDocument.value = doc.isEmpty
                    _isTruncated.value = doc.isTruncated

                    persistRecentDocxEntry(uri, context)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: DocxLoadException) {
                _blocks.value = emptyList()
                _errorMessage.value = e.message ?: "Unsupported or invalid .docx document."
            } catch (e: SecurityException) {
                _blocks.value = emptyList()
                _errorMessage.value = "Permission denied to access this file. Please re-select the file."
            } catch (e: FileNotFoundException) {
                _blocks.value = emptyList()
                _errorMessage.value = "File not found. It may have been moved or deleted."
            } catch (e: IOException) {
                _blocks.value = emptyList()
                _errorMessage.value = "Failed to read the file: ${e.localizedMessage ?: "I/O error"}"
            } catch (e: Exception) {
                _blocks.value = emptyList()
                _errorMessage.value = "Could not open Word document: ${e.localizedMessage ?: "Unexpected error"}"
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

    private fun persistRecentDocxEntry(uri: Uri, context: Context) {
        try {
            val rawName = _documentName.value.ifBlank { "Word Document" }
            val displayName = rawName.substringBeforeLast(".").takeIf { it.isNotBlank() } ?: rawName
            val size = _fileSizeBytes.value

            val localCopyPath = if (uri.scheme == "content" && size in 1L..MAX_LOCAL_COPY_BYTES) {
                copyDocxUriToInternalStorage(uri, displayName, context)
            } else if (uri.scheme == "file") {
                uri.path
            } else {
                null
            }

            recentDocxRepository.saveRecentDocx(
                RecentDocxFile(
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

    private fun copyDocxUriToInternalStorage(uri: Uri, displayName: String, context: Context): String? {
        return try {
            val dir = File(context.filesDir, PERSISTENT_DOCX_COPY_DIR).also { it.mkdirs() }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".docx"
            val destFile = File(dir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                    BufferedInputStream(input).copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }
}
