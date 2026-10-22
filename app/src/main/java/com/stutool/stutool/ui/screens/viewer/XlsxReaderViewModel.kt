package com.stutool.stutool.ui.screens.viewer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.data.local.RecentXlsxRepository
import com.stutool.stutool.domain.model.RecentXlsxFile
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

class XlsxReaderViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MIN_FONT_SIZE_SP = 11
        const val MAX_FONT_SIZE_SP = 22
        const val DEFAULT_FONT_SIZE_SP = 13
        private const val PERSISTENT_XLSX_COPY_DIR = "xlsx_copies"
        private const val MAX_LOCAL_COPY_BYTES = 10L * 1024L * 1024L // 10 MB
    }

    private val recentXlsxRepository = RecentXlsxRepository(application)

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _workbookName = MutableStateFlow("Excel Spreadsheet")
    val workbookName: StateFlow<String> = _workbookName.asStateFlow()

    private val _fileSizeBytes = MutableStateFlow(0L)
    val fileSizeBytes: StateFlow<Long> = _fileSizeBytes.asStateFlow()

    private val _sheets = MutableStateFlow<List<XlsxSheet>>(emptyList())
    val sheets: StateFlow<List<XlsxSheet>> = _sheets.asStateFlow()

    private val _selectedSheetIndex = MutableStateFlow(0)
    val selectedSheetIndex: StateFlow<Int> = _selectedSheetIndex.asStateFlow()

    private val _isEmptyWorkbook = MutableStateFlow(false)
    val isEmptyWorkbook: StateFlow<Boolean> = _isEmptyWorkbook.asStateFlow()

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
            _errorMessage.value = "Invalid spreadsheet link."
            return
        }
        val uri = Uri.parse(decodedUriString)
        if (_currentUri.value == uri && (_sheets.value.isNotEmpty() || _isEmptyWorkbook.value) && _errorMessage.value == null) {
            return
        }
        _currentUri.value = uri
        loadWorkbook()
    }

    fun retry() {
        loadWorkbook()
    }

    fun selectSheet(index: Int) {
        val currentSheets = _sheets.value
        if (index in currentSheets.indices) {
            _selectedSheetIndex.value = index
        }
    }

    fun loadWorkbook() {
        val uri = _currentUri.value ?: return
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val context = getApplication<Application>()
                resolveMetadata(uri, context)

                ensureActive()

                val resolvedName = _workbookName.value
                if (resolvedName.endsWith(".xls", ignoreCase = true) &&
                    !resolvedName.endsWith(".xlsx", ignoreCase = true)
                ) {
                    throw XlsxLoadException(
                        "Legacy binary Excel (.xls) files are not supported by this viewer. " +
                            "Please open the spreadsheet in Excel or a compatible editor and save/export it as .xlsx."
                    )
                }

                openInputStreamForUri(uri, context).use { stream ->
                    if (stream == null) {
                        throw IOException("Unable to open stream for the selected file.")
                    }
                    val workbook = XlsxDocumentLoader.loadWorkbook(
                        inputStream = stream,
                        checkCancelled = { ensureActive() }
                    )

                    ensureActive()

                    _sheets.value = workbook.sheets
                    _isEmptyWorkbook.value = workbook.isEmpty
                    _isTruncated.value = workbook.isTruncated

                    // Default to the first non-empty worksheet if available, otherwise index 0
                    val firstNonEmptyIdx = workbook.sheets.indexOfFirst { !it.isEmpty }
                    _selectedSheetIndex.value = if (firstNonEmptyIdx >= 0) firstNonEmptyIdx else 0

                    persistRecentXlsxEntry(uri, context)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: XlsxLoadException) {
                _sheets.value = emptyList()
                _errorMessage.value = e.message ?: "Unsupported or invalid .xlsx workbook."
            } catch (e: SecurityException) {
                _sheets.value = emptyList()
                _errorMessage.value = "Permission denied to access this file. Please re-select the file."
            } catch (e: FileNotFoundException) {
                _sheets.value = emptyList()
                _errorMessage.value = "File not found. It may have been moved or deleted."
            } catch (e: IOException) {
                _sheets.value = emptyList()
                _errorMessage.value = "Failed to read the file: ${e.localizedMessage ?: "I/O error"}"
            } catch (e: Exception) {
                _sheets.value = emptyList()
                _errorMessage.value = "Could not open Excel workbook: ${e.localizedMessage ?: "Unexpected error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun increaseFontSize() {
        _fontSizeSp.value = (_fontSizeSp.value + 1).coerceAtMost(MAX_FONT_SIZE_SP)
    }

    fun decreaseFontSize() {
        _fontSizeSp.value = (_fontSizeSp.value - 1).coerceAtLeast(MIN_FONT_SIZE_SP)
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
                _workbookName.value = file.name
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
                            _workbookName.value = name
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

    private fun persistRecentXlsxEntry(uri: Uri, context: Context) {
        try {
            val rawName = _workbookName.value.ifBlank { "Excel Spreadsheet" }
            val displayName = rawName.substringBeforeLast(".").takeIf { it.isNotBlank() } ?: rawName
            val size = _fileSizeBytes.value

            val localCopyPath = if (uri.scheme == "content" && size in 1L..MAX_LOCAL_COPY_BYTES) {
                copyXlsxUriToInternalStorage(uri, displayName, context)
            } else if (uri.scheme == "file") {
                uri.path
            } else {
                null
            }

            recentXlsxRepository.saveRecentXlsx(
                RecentXlsxFile(
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

    private fun copyXlsxUriToInternalStorage(uri: Uri, displayName: String, context: Context): String? {
        return try {
            val dir = File(context.filesDir, PERSISTENT_XLSX_COPY_DIR).also { it.mkdirs() }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".xlsx"
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
