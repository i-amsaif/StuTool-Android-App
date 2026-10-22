package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.domain.engine.OcrResult
import com.stutool.stutool.domain.engine.PdfEngine
import com.stutool.stutool.domain.ocr.OcrLanguage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OcrPdfViewModel(application: Application) : AndroidViewModel(application) {
    init {
        PDFBoxResourceLoader.init(application)
    }

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _fileSizeBytes = MutableStateFlow(0L)
    val fileSizeBytes: StateFlow<Long> = _fileSizeBytes.asStateFlow()

    private val _isImage = MutableStateFlow(false)
    val isImage: StateFlow<Boolean> = _isImage.asStateFlow()

    private val _imageDimensions = MutableStateFlow<Pair<Int, Int>?>(null)
    val imageDimensions: StateFlow<Pair<Int, Int>?> = _imageDimensions.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val ocrPreferences = com.stutool.stutool.data.local.OcrPreferences(application)

    private val _selectedLanguage = MutableStateFlow(OcrLanguage.AUTO)
    val selectedLanguage: StateFlow<OcrLanguage> = _selectedLanguage.asStateFlow()

    private val _selectedMode = MutableStateFlow(ocrPreferences.getOcrMode())
    val selectedMode: StateFlow<com.stutool.stutool.domain.ocr.OcrMode> = _selectedMode.asStateFlow()

    private val _forceOfflineOnly = MutableStateFlow(false)
    val forceOfflineOnly: StateFlow<Boolean> = _forceOfflineOnly.asStateFlow()

    private val _showConsentDialog = MutableStateFlow(false)
    val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

    private val _showCloudOptInDialog = MutableStateFlow<com.stutool.stutool.domain.ocr.OcrMode?>(null)
    val showCloudOptInDialog: StateFlow<com.stutool.stutool.domain.ocr.OcrMode?> = _showCloudOptInDialog.asStateFlow()

    private var pendingRetryAfterConsent = false

    private val _engineUsed = MutableStateFlow<String?>(null)
    val engineUsed: StateFlow<String?> = _engineUsed.asStateFlow()

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

    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText.asStateFlow()

    private val _outputTxtUri = MutableStateFlow<Uri?>(null)
    val outputTxtUri: StateFlow<Uri?> = _outputTxtUri.asStateFlow()

    private val _outputDocxUri = MutableStateFlow<Uri?>(null)
    val outputDocxUri: StateFlow<Uri?> = _outputDocxUri.asStateFlow()

    private val _ocrResult = MutableStateFlow<OcrResult?>(null)
    val ocrResult: StateFlow<OcrResult?> = _ocrResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private var processingJob: Job? = null

    fun setLanguage(language: OcrLanguage) {
        _selectedLanguage.value = language
    }

    fun setForceOfflineOnly(force: Boolean) {
        _forceOfflineOnly.value = force
    }

    fun setMode(mode: com.stutool.stutool.domain.ocr.OcrMode) {
        if (mode.allowsCloud && !ocrPreferences.isCloudOptInAcknowledged()) {
            _showCloudOptInDialog.value = mode
            _showConsentDialog.value = true
        } else {
            _selectedMode.value = mode
            ocrPreferences.setOcrMode(mode)
        }
    }

    fun requestCloudRetry() {
        if (!ocrPreferences.isCloudOptInAcknowledged()) {
            pendingRetryAfterConsent = true
            _showCloudOptInDialog.value = com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO
            _showConsentDialog.value = true
        } else {
            _selectedMode.value = com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO
            ocrPreferences.setOcrMode(com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO)
            startConversion()
        }
    }

    fun onConsentProceedOnline() {
        ocrPreferences.setCloudOptInAcknowledged(true)
        val targetMode = _showCloudOptInDialog.value ?: com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO
        ocrPreferences.setOcrMode(targetMode)
        _selectedMode.value = targetMode
        _showConsentDialog.value = false
        _showCloudOptInDialog.value = null
        startConversion(overrideMode = com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO)
    }

    fun onConsentProcessOffline() {
        _showConsentDialog.value = false
        _showCloudOptInDialog.value = null
        _selectedMode.value = com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
        pendingRetryAfterConsent = false
        startConversion(overrideMode = com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY)
    }

    fun onConsentDismissed() {
        onConsentProcessOffline()
    }

    fun onCloudOptInConfirmed() {
        onConsentProceedOnline()
    }

    fun onCloudOptInDismissed() {
        _showCloudOptInDialog.value = null
        _showConsentDialog.value = false
        _selectedMode.value = com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
        pendingRetryAfterConsent = false
    }

    fun setPdf(uri: Uri?) = setInput(uri)

    fun setInput(uri: Uri?) {
        _selectedUri.value = uri
        _outputUri.value = null
        _outputTxtUri.value = null
        _outputDocxUri.value = null
        _extractedText.value = ""
        _outputFileSizeBytes.value = 0L
        _ocrResult.value = null
        _errorMessage.value = null
        _processingError.value = null
        _progress.value = 0f
        _imageDimensions.value = null

        if (uri == null) {
            _fileName.value = null
            _fileSizeBytes.value = 0L
            _pageCount.value = 0
            _isImage.value = false
            _isLoadingInfo.value = false
            return
        }

        try {
            if (uri.scheme == "content") {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (_: Exception) {}

        var extractedName = "document"
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

        if (extractedSize <= 0L) {
            extractedSize = PdfEngine.getFileSizeBytes(getApplication(), uri)
        }

        _fileName.value = extractedName
        _fileSizeBytes.value = extractedSize

        val mimeType = try {
            getApplication<Application>().contentResolver.getType(uri)
        } catch (_: Exception) { null }

        val isImg = mimeType?.startsWith("image/") == true ||
                extractedName.endsWith(".png", ignoreCase = true) ||
                extractedName.endsWith(".jpg", ignoreCase = true) ||
                extractedName.endsWith(".jpeg", ignoreCase = true) ||
                extractedName.endsWith(".webp", ignoreCase = true) ||
                extractedName.endsWith(".bmp", ignoreCase = true)

        _isImage.value = isImg
        _pageCount.value = if (isImg) 1 else 0
        _isLoadingInfo.value = true

        viewModelScope.launch {
            try {
                if (isImg) {
                    val dims = withContext(Dispatchers.IO) {
                        PdfEngine.getImageDimensions(getApplication(), uri)
                    }
                    _imageDimensions.value = dims
                } else {
                    val pages = withContext(Dispatchers.IO) {
                        PdfEngine.countPages(getApplication(), uri)
                    }
                    _pageCount.value = pages
                }
            } catch (e: SecurityException) {
                _errorMessage.value = "Password-protected PDFs are not supported."
                _selectedUri.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to inspect file: ${e.localizedMessage ?: "Unknown error"}"
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

    fun startConversion(overrideMode: com.stutool.stutool.domain.ocr.OcrMode? = null) {
        val uri = _selectedUri.value ?: return
        if (_isConverting.value) return

        val effectiveMode = if (overrideMode != null) {
            overrideMode
        } else {
            val decision = com.stutool.stutool.domain.ocr.OcrRouter.evaluate(
                context = getApplication(),
                fileSizeBytes = _fileSizeBytes.value,
                pageCount = if (_isImage.value) 1 else _pageCount.value,
                hasExtractableText = false,
                userForceOffline = _forceOfflineOnly.value,
                ocrPreferences = ocrPreferences
            )
            when (decision) {
                is com.stutool.stutool.domain.ocr.OcrRouter.RouteDecision.NeedsConsent -> {
                    _showConsentDialog.value = true
                    return
                }
                is com.stutool.stutool.domain.ocr.OcrRouter.RouteDecision.UseCloud -> {
                    com.stutool.stutool.domain.ocr.OcrMode.SMART_AUTO
                }
                else -> {
                    com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY
                }
            }
        }
        _selectedMode.value = effectiveMode

        processingJob = viewModelScope.launch {
            _isConverting.value = true
            _processingError.value = null
            _progress.value = 0f
            _statusMessage.value = "Preparing OCR..."

            val baseName = _fileName.value?.substringBeforeLast(".") ?: "ocr_output"

            try {
                val mode = effectiveMode
                if (_isImage.value) {
                    _statusMessage.value = "Running OCR on image..."
                    val ocrPageResult = withContext(Dispatchers.IO) {
                        PdfEngine.performImageOcr(
                            context = getApplication(),
                            imageUri = uri,
                            language = _selectedLanguage.value,
                            mode = mode
                        )
                    }

                    _engineUsed.value = ocrPageResult.engineSource.displayName
                    val text = ocrPageResult.text.trim()
                    _extractedText.value = text
                    _progress.value = 1f
                    _statusMessage.value = "Text successfully extracted from image."

                    if (text.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            val txtUri = PdfEngine.exportTextToTxt(getApplication(), text, baseName)
                            val docxUri = PdfEngine.exportTextToDocx(getApplication(), text, baseName)
                            _outputTxtUri.value = txtUri
                            _outputDocxUri.value = docxUri
                            _outputUri.value = txtUri
                            try {
                                txtUri.path?.let { p ->
                                    val f = File(p)
                                    if (f.exists()) _outputFileSizeBytes.value = f.length()
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    _ocrResult.value = OcrResult(
                        uri = _outputTxtUri.value ?: uri,
                        pagesProcessed = 1,
                        pagesSkippedText = 0,
                        pagesSkippedRotation = 0
                    )
                } else {
                    _statusMessage.value = "Analyzing PDF..."
                    var lastProgressTime = 0L
                    val outResult = PdfEngine.makeSearchablePdf(
                        context = getApplication(),
                        sourceUri = uri,
                        language = _selectedLanguage.value,
                        mode = mode,
                        onProgress = { current, total ->
                            val now = System.currentTimeMillis()
                            if (current == total || current == 1 || now - lastProgressTime >= 50L) {
                                lastProgressTime = now
                                val frac = if (total > 0) current.toFloat() / total.toFloat() else -1f
                                _progress.value = frac
                                _statusMessage.value = "OCRing page $current of $total..."
                            }
                        }
                    )

                    _outputUri.value = outResult.uri
                    _ocrResult.value = outResult

                    val actualEngineUsed = if (mode == com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY || _forceOfflineOnly.value || _pageCount.value > 3 || _fileSizeBytes.value > 1048576L) {
                        com.stutool.stutool.domain.ocr.OcrEngineSource.LOCAL_TESSERACT.displayName
                    } else if (mode == com.stutool.stutool.domain.ocr.OcrMode.CLOUD_ONLY) {
                        com.stutool.stutool.domain.ocr.OcrEngineSource.CLOUD_OCR_SPACE.displayName
                    } else {
                        "OCR.space Cloud / Tesseract 5 Hybrid"
                    }
                    _engineUsed.value = actualEngineUsed

                    // Also extract readable text from PDF for selectable text view and TXT/DOCX exports
                    val extracted = withContext(Dispatchers.IO) {
                        PdfEngine.extractTextFromPdf(
                            context = getApplication(),
                            sourceUri = uri,
                            language = _selectedLanguage.value,
                            mode = mode,
                            onProgress = { _, _ -> }
                        )
                    }

                    _extractedText.value = extracted
                    if (extracted.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            _outputTxtUri.value = PdfEngine.exportTextToTxt(getApplication(), extracted, baseName)
                            _outputDocxUri.value = PdfEngine.exportTextToDocx(getApplication(), extracted, baseName)
                        }
                    }

                    _progress.value = 1f
                    var successMsg = "Successfully processed ${outResult.pagesProcessed} page(s)."
                    if (outResult.pagesSkippedText > 0) {
                        successMsg += " Kept ${outResult.pagesSkippedText} pages with native text."
                    }
                    _statusMessage.value = successMsg

                    try {
                        outResult.uri.path?.let { path ->
                            val f = File(path)
                            if (f.exists()) {
                                _outputFileSizeBytes.value = f.length()
                            }
                        }
                    } catch (_: Exception) {}
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _processingError.value = e.message ?: "Could not complete text recognition for this document. Please retry or choose another file."
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun exportToTxt(onSuccess: (Uri) -> Unit) {
        val text = _extractedText.value
        if (text.isBlank()) return
        val baseName = _fileName.value?.substringBeforeLast(".") ?: "ocr_output"
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    PdfEngine.exportTextToTxt(getApplication(), text, baseName)
                }
                _outputTxtUri.value = uri
                onSuccess(uri)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to export TXT: ${e.message}"
            }
        }
    }

    fun exportToDocx(onSuccess: (Uri) -> Unit) {
        val text = _extractedText.value
        if (text.isBlank()) return
        val baseName = _fileName.value?.substringBeforeLast(".") ?: "ocr_output"
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    PdfEngine.exportTextToDocx(getApplication(), text, baseName)
                }
                _outputDocxUri.value = uri
                onSuccess(uri)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to export DOCX: ${e.message}"
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
        _isImage.value = false
        _imageDimensions.value = null
        _isLoadingInfo.value = false
        _outputUri.value = null
        _outputTxtUri.value = null
        _outputDocxUri.value = null
        _extractedText.value = ""
        _outputFileSizeBytes.value = 0L
        _ocrResult.value = null
        _errorMessage.value = null
        _processingError.value = null
        _progress.value = 0f
        _engineUsed.value = null
        _statusMessage.value = "Preparing..."
    }
}
