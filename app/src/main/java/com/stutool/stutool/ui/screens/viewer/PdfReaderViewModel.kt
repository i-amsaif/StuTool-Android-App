package com.stutool.stutool.ui.screens.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.app.Application
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.data.local.RecentPdfRepository
import com.stutool.stutool.domain.model.RecentPdf
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

enum class BackgroundMode { Original, Paper, EyeComfort, Invert }
enum class ReadingDirection { Vertical, Horizontal }

data class PdfTextElement(
    val text: String,
    val x: Float,     // PDF Points (72 dpi)
    val y: Float,     // PDF Points (from top)
    val width: Float,
    val height: Float,
    val charPositions: List<Float>,  // Exact X of each character (PDF points, relative to cropBox)
    val charWidths: List<Float>      // Exact width of each character (PDF points)
)

data class SearchMatch(
    val pageIndex: Int,
    val matchText: String,
    val bounds: List<android.graphics.RectF>
)

data class PageTextData(
    val pageIndex: Int,
    val extractedText: String
)

data class PdfPageLayout(
    val elements: List<PdfTextElement>,
    val pageWidth: Float,
    val pageHeight: Float
)

class PdfReaderViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Internal-storage subdirectory used for persisted PDF copies. */
        private const val PERSISTENT_COPY_DIR = "pdf_copies"
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            PDFBoxResourceLoader.init(application)
            cleanupCacheSync()
        }
    }

    private val repository = RecentPdfRepository(application)

    private val _currentPdfUri = MutableStateFlow<Uri?>(null)
    val currentPdfUri: StateFlow<Uri?> = _currentPdfUri.asStateFlow()

    // The actual URI used for rendering (could be original or temp decrypted file)
    private val _displayUri = MutableStateFlow<Uri?>(null)
    val displayUri: StateFlow<Uri?> = _displayUri.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPasswordRequired = MutableStateFlow(false)
    val isPasswordRequired: StateFlow<Boolean> = _isPasswordRequired.asStateFlow()

    private val _isUnlocking = MutableStateFlow(false)
    val isUnlocking: StateFlow<Boolean> = _isUnlocking.asStateFlow()

    private val _passwordErrorMessage = MutableStateFlow<String?>(null)
    val passwordErrorMessage: StateFlow<String?> = _passwordErrorMessage.asStateFlow()

    private val _isLargeFile = MutableStateFlow(false)
    val isLargeFile: StateFlow<Boolean> = _isLargeFile.asStateFlow()

    private var loadingJob: Job? = null
    private var activeTempPdfFile: File? = null
    private val TEMP_PDF_PREFIX = "stutool_tmp_decrypted_"

    private val _pdfName = MutableStateFlow<String?>("Document")
    val pdfName: StateFlow<String?> = _pdfName.asStateFlow()

    private val progressRepository = com.stutool.stutool.data.local.PdfProgressRepository(application)

    private val _bookmarks = MutableStateFlow<List<Int>>(emptyList())
    val bookmarks: StateFlow<List<Int>> = _bookmarks.asStateFlow()

    private val _restoredPage = MutableStateFlow<Int?>(null)
    val restoredPage: StateFlow<Int?> = _restoredPage.asStateFlow()

    private var currentPdfId: String? = null
    private var saveProgressJob: Job? = null

    // --- UX States ---
    private val _backgroundMode = MutableStateFlow(BackgroundMode.Original)
    val backgroundMode: StateFlow<BackgroundMode> = _backgroundMode.asStateFlow()

    private val _readingDirection = MutableStateFlow(ReadingDirection.Vertical)
    val readingDirection: StateFlow<ReadingDirection> = _readingDirection.asStateFlow()

    private val _isReflowEnabled = MutableStateFlow(false)
    val isReflowEnabled: StateFlow<Boolean> = _isReflowEnabled.asStateFlow()

    private val _isPageByPageEnabled = MutableStateFlow(false)
    val isPageByPageEnabled: StateFlow<Boolean> = _isPageByPageEnabled.asStateFlow()

    private val _isKeepScreenOn = MutableStateFlow(false)
    val isKeepScreenOn: StateFlow<Boolean> = _isKeepScreenOn.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isTextMode = MutableStateFlow(false)
    val isTextMode: StateFlow<Boolean> = _isTextMode.asStateFlow()

    // ── Search State ──
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchMatch>>(emptyList())
    val searchResults: StateFlow<List<SearchMatch>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(0)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    private val _isSearchParsing = MutableStateFlow(false)
    val isSearchParsing: StateFlow<Boolean> = _isSearchParsing.asStateFlow()

    private val _searchNoTextFound = MutableStateFlow(false)
    val searchNoTextFound: StateFlow<Boolean> = _searchNoTextFound.asStateFlow()

    private var searchSessionCache: List<PageTextData>? = null
    private var searchJob: Job? = null

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    
    // Store the content URI for text extraction
    private var currentContentUri: Uri? = null

    // Cache to hold up to 4 recently rendered Bitmaps tied to specific screen widths/zoom levels.
    // Reduced from 8 to 4 to lower native memory pressure without causing Compose recycled bitmap crashes.
    private val bitmapCache = LruCache<String, Bitmap>(4)

    // ── Zoom-level bitmap cache (1x, 2x, 4x) ──
    // DO NOT manually recycle on eviction. Compose's RenderNode holds async references to bitmaps.
    // Manual recycling causes "Canvas: trying to use a recycled bitmap" crashes. Rely on GC.
    private val zoomBitmapCache = LruCache<String, Bitmap>(2)
    
    // Cache for extracted text per page (full strings)
    private val textCache = LruCache<Int, String>(10)
    
    // Cache for coordinate-aware layout elements
    private val layoutCache = LruCache<Int, PdfPageLayout>(10)
    
    // PdfRenderer is not thread safe
    private val rendererMutex = Mutex()
    
    // PDFBox document is not thread safe for extraction
    private var activePdfDocument: PDDocument? = null
    private val pdfDocumentMutex = Mutex()

    private fun computePdfId(uri: Uri, resolvedName: String, fileSize: Long): String {
        // 1. Persistent document Uri (e.g., from Storage Access Framework)
        if (uri.scheme == "content" && android.provider.DocumentsContract.isDocumentUri(getApplication(), uri)) {
            return uri.toString()
        }
        // 2. Canonical file path
        if (uri.scheme == "file") {
            return uri.path ?: uri.toString()
        }
        // 3. Fallback (fileIdentity)
        return "${resolvedName.lowercase().trim().removeSuffix(".pdf")}|$fileSize"
    }

    fun openPdf(uri: Uri, context: Context, name: String?) {
        if (uri.toString() == _currentPdfUri.value?.toString() && pdfRenderer != null) {
            return
        }
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            _isPasswordRequired.value = false
            _passwordErrorMessage.value = null
            
            try {
                rendererMutex.withLock {
                    closePdfInternal()
                    _currentPdfUri.value = uri
                    _pdfName.value = name ?: "Document"
                    
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        try {
                            // Try native open (The Probe)
                            pdfRenderer = PdfRenderer(pfd)
                            fileDescriptor = pfd
                            _displayUri.value = uri
                            currentContentUri = uri
                            _pageCount.value = pdfRenderer?.pageCount ?: 0
                            
                            // Initialize PDFBox concurrently without blocking the main renderer
                            launch(Dispatchers.IO) {
                                pdfDocumentMutex.withLock {
                                    try {
                                        context.contentResolver.openInputStream(uri)?.use { stream ->
                                            val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
                                            activePdfDocument = PDDocument.load(stream, memorySetting)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            
                            // ── Save to Recent Files ──
                            val resolvedName = resolveFileName(uri, context) ?: (name ?: "Document")
                            val fileSize = try {
                                pfd.statSize.takeIf { it > 0 } ?: 0L
                            } catch (_: Exception) { 0L }
                            _pdfName.value = resolvedName
                            
                            // ── Persist fallback copy for content:// URIs ──
                            // Must happen BEFORE saveRecentPdf so the localCopyPath
                            // is stored immediately. Without it, pruneInaccessibleEntries()
                            // deletes entries whose temp content:// permission has expired
                            // (e.g. PDFs shared from Chrome).
                            val localCopyPath = if (uri.scheme == "content") {
                                copyUriToInternalStorage(uri, resolvedName, context)
                            } else null
                            
                            repository.saveRecentPdf(
                                RecentPdf(
                                    uri = uri.toString(),
                                    name = resolvedName,
                                    size = fileSize,
                                    lastOpened = System.currentTimeMillis(),
                                    localCopyPath = localCopyPath
                                )
                            )

                            val pdfId = computePdfId(uri, resolvedName, fileSize)
                            currentPdfId = pdfId
                            
                            val savedPage = progressRepository.getProgress(pdfId)
                            val maxPage = pdfRenderer?.pageCount ?: 0
                            _restoredPage.value = if (maxPage > 0 && savedPage in 0 until maxPage) savedPage else 0
                            _bookmarks.value = progressRepository.getBookmarks(pdfId)
                            
                            // Check if favorite
                            val recentPdfs = repository.getRecentPdfs()
                            _isFavorite.value = recentPdfs.find { it.uri == uri.toString() }?.isFavorite ?: false
                            
                            // ── Pre-render page 0 eagerly ──
                            // Render at screen-density width so it's cached before
                            // the composable asks for it, eliminating first-page lag.
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            launch(Dispatchers.IO) {
                                try {
                                    getPageBitmap(0, screenWidth)
                                } catch (_: Exception) { /* non-critical */ }
                            }
                        } catch (e: Exception) {
                            // Native open failed. Validate if it's encryption or corruption.
                            pfd.close()
                            validateEncryption(uri, context)
                        }
                    } else {
                        _errorMessage.value = "Failed to access the selected file."
                    }
                }
            } catch (e: SecurityException) {
                _errorMessage.value = "Permission denied to open PDF."
                e.printStackTrace()
            } catch (e: IOException) {
                _errorMessage.value = "Failed to open the PDF file."
                e.printStackTrace()
            } catch (e: Exception) {
                _errorMessage.value = "An unexpected error occurred."
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Copies a content:// URI to a stable internal storage file so the PDF can be
     * reopened in future sessions without needing the original URI permission.
     * Returns the absolute file path on success, null otherwise.
     */
    private fun copyUriToInternalStorage(uri: Uri, displayName: String, context: Context): String? {
        return try {
            val dir = File(context.filesDir, PERSISTENT_COPY_DIR).also { it.mkdirs() }
            // Sanitise the name for file system safety
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".pdf"
            val destFile = File(dir, safeName)
            // Skip re-copy if an identical-named file already exists
            if (destFile.exists() && destFile.length() > 0L) return destFile.absolutePath
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                    BufferedInputStream(input).copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun validateEncryption(uri: Uri, context: Context) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                try {
                    // Try PDFBox without password
                    val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
                    PDDocument.load(stream, memorySetting).close()
                    // If it reaches here without exception, it's NOT encrypted but native failed. 
                    // This means it's likely a format error/unsupported feature.
                    _errorMessage.value = "This PDF format is not supported by the native renderer."
                } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                    _isPasswordRequired.value = true
                } catch (e: Exception) {
                    _errorMessage.value = "The PDF file is corrupt or invalid."
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to validate file security."
        }
    }

    fun unlockPdf(password: String, context: Context) {
        val uri = _currentPdfUri.value ?: return
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("PDF_DEBUG", "unlockPdf started")
            _isUnlocking.value = true
            _passwordErrorMessage.value = null
            
            try {
                // Large file safeguard (> 50MB)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (pfd.statSize > 50 * 1024 * 1024) {
                        _isLargeFile.value = true
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                inputStream?.use { stream ->
                    val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
                    val document = PDDocument.load(stream, password, memorySetting)
                    document.use { doc ->
                        doc.isAllSecurityToBeRemoved = true
                        
                        // Save to temporary cache
                        val tempFile = File(context.cacheDir, "$TEMP_PDF_PREFIX${System.currentTimeMillis()}.pdf")
                        FileOutputStream(tempFile).use { out ->
                            doc.save(out)
                        }
                        
                        // Now open the decrypted file
                        withContext(Dispatchers.IO) {
                            activeTempPdfFile = tempFile
                            openPdfInternal(Uri.fromFile(tempFile), context)
                            
                            // ── Persist Original Encrypted PDF ──
                            val originalName = _pdfName.value ?: "Document"
                            val resolvedName = resolveFileName(uri, context) ?: originalName
                            val fileSize = try {
                                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { s -> s > 0 } } ?: 0L
                            } catch (_: Exception) { 0L }
                            _pdfName.value = resolvedName
                            
                            val localCopyPath = if (uri.scheme == "content") {
                                copyUriToInternalStorage(uri, resolvedName, context)
                            } else null
                            
                            repository.saveRecentPdf(
                                RecentPdf(
                                    uri = uri.toString(),
                                    name = resolvedName,
                                    size = fileSize,
                                    lastOpened = System.currentTimeMillis(),
                                    localCopyPath = localCopyPath
                                )
                            )

                            val pdfId = computePdfId(uri, resolvedName, fileSize)
                            currentPdfId = pdfId
                            
                            val savedPage = progressRepository.getProgress(pdfId)
                            val maxPage = _pageCount.value
                            _restoredPage.value = if (maxPage > 0 && savedPage in 0 until maxPage) savedPage else 0
                            _bookmarks.value = progressRepository.getBookmarks(pdfId)
                            
                            val recentPdfs = repository.getRecentPdfs()
                            _isFavorite.value = recentPdfs.find { it.uri == uri.toString() }?.isFavorite ?: false
                            
                            android.util.Log.d("PDF_DEBUG", "unlock successful")
                            android.util.Log.d("PDF_DEBUG", "original URI: $uri")
                            android.util.Log.d("PDF_DEBUG", "temp decrypted URI: $tempFile")
                            android.util.Log.d("PDF_DEBUG", "currentPdfId: $currentPdfId")
                            android.util.Log.d("PDF_DEBUG", "_pdfName: ${_pdfName.value}")
                            android.util.Log.d("PDF_DEBUG", "_pageCount: ${_pageCount.value}")
                        }
                    }
                }
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                _passwordErrorMessage.value = "Incorrect password. Try again."
            } catch (e: Exception) {
                _passwordErrorMessage.value = "Failed to decrypt PDF: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                _isUnlocking.value = false
            }
        }
    }

    private suspend fun openPdfInternal(tempUri: Uri, context: Context) {
        android.util.Log.d("PDF_DEBUG", "openPdfInternal entered")
        rendererMutex.withLock {
            val pfd = context.contentResolver.openFileDescriptor(tempUri, "r")
            if (pfd != null) {
                try {
                    pdfRenderer = PdfRenderer(pfd)
                    fileDescriptor = pfd
                    _displayUri.value = tempUri
                    currentContentUri = tempUri
                    _pageCount.value = pdfRenderer?.pageCount ?: 0
                    _isPasswordRequired.value = false
                    
                    android.util.Log.d("PDF_DEBUG", "openPdfInternal displayUri: ${_displayUri.value}")
                    android.util.Log.d("PDF_DEBUG", "openPdfInternal currentContentUri: $currentContentUri")
                    android.util.Log.d("PDF_DEBUG", "openPdfInternal pageCount: ${_pageCount.value}")
                    android.util.Log.d("PDF_DEBUG", "openPdfInternal renderer created successfully")
                } catch (e: Exception) {
                    pfd.close()
                    _errorMessage.value = "Failed to load decrypted PDF."
                }
            } else {
                _errorMessage.value = "Failed to load decrypted PDF."
            }
        }
        
        pdfDocumentMutex.withLock {
            try {
                context.contentResolver.openInputStream(tempUri)?.use { stream ->
                    activePdfDocument = PDDocument.load(stream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }

    suspend fun getPageLayout(pageIndex: Int): PdfPageLayout? {
        return withContext(Dispatchers.IO) {
            layoutCache.get(pageIndex)?.let { return@withContext it }
            
            pdfDocumentMutex.withLock {
                coroutineContext.ensureActive()
                val doc = activePdfDocument ?: return@withContext null
                
                try {
                    val page = doc.getPage(pageIndex)
                    val cropBox = page.cropBox
                    val rotation = page.rotation
                    val isRotated = rotation == 90 || rotation == 270
                    val pageWidth = if (isRotated) cropBox.height else cropBox.width
                    val pageHeight = if (isRotated) cropBox.width else cropBox.height
                    
                    val elements = mutableListOf<PdfTextElement>()
                        val stripper = object : PDFTextStripper() {
                            private val linesMap = mutableMapOf<Float, MutableList<TextPosition>>()

                            override fun writeString(string: String?, textPositions: MutableList<TextPosition>?) {
                                if (string != null && textPositions != null) {
                                    textPositions.forEach { pos ->
                                        val y = (pos.yDirAdj * 10).toInt() / 10f 
                                        linesMap.getOrPut(y) { mutableListOf() }.add(pos)
                                    }
                                }
                            }

                            override fun writePage() {
                                super.writePage() 
                                linesMap.keys.sorted().forEach { y ->
                                    val lineChars = linesMap[y]?.sortedBy { it.xDirAdj } ?: return@forEach
                                    if (lineChars.isEmpty()) return@forEach
                                    
                                    val lineText = lineChars.joinToString("") { it.unicode ?: "" }
                                    val first = lineChars.first()
                                    val last = lineChars.last()
                                    
                                    // Build exact per-character geometry from PDFBox TextPosition
                                    val charPos = mutableListOf<Float>()
                                    val charWid = mutableListOf<Float>()
                                    for (tp in lineChars) {
                                        val unicode = tp.unicode ?: ""
                                        if (unicode.isEmpty()) continue
                                        if (unicode.length == 1) {
                                            charPos.add(tp.xDirAdj - cropBox.lowerLeftX)
                                            charWid.add(tp.widthDirAdj)
                                        } else {
                                            // Multi-character glyph (ligature): distribute width evenly
                                            val perCharWidth = tp.widthDirAdj / unicode.length
                                            for (i in unicode.indices) {
                                                charPos.add(tp.xDirAdj - cropBox.lowerLeftX + i * perCharWidth)
                                                charWid.add(perCharWidth)
                                            }
                                        }
                                    }
                                    
                                    // Adjust for cropBox offset if any
                                    val elementX = first.xDirAdj - cropBox.lowerLeftX
                                    
                                    // PDFBox yDirAdj is from the top of the mediaBox. 
                                    // We need to adjust it to be relative to the top of the cropBox.
                                    val relativeY = first.yDirAdj - (page.mediaBox.upperRightY - cropBox.upperRightY)

                                    var ascent = -1f
                                    try {
                                        val font = first.font
                                        val descriptor = font?.fontDescriptor
                                        if (descriptor != null && descriptor.ascent > 0) {
                                            val asc = descriptor.ascent
                                            val desc = descriptor.descent
                                            val total = asc - desc
                                            if (total > 0) {
                                                ascent = first.heightDir * (asc / total)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Ignore and fallback
                                    }
                                    
                                    if (ascent <= 0f) {
                                        // Fallback heuristic for fonts without metrics
                                        ascent = first.heightDir * 0.8f
                                    }

                                    elements.add(PdfTextElement(
                                        text = lineText,
                                        x = elementX,
                                        y = relativeY - ascent,
                                        width = (last.xDirAdj + last.widthDirAdj) - first.xDirAdj,
                                        height = first.heightDir * 1.1f,
                                        charPositions = charPos,
                                        charWidths = charWid
                                    ))
                                }
                            }
                        }
                        stripper.sortByPosition = true
                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        stripper.getText(doc)
                        val result = PdfPageLayout(elements, pageWidth, pageHeight)
                        layoutCache.put(pageIndex, result)
                        result
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    // Toggles for Bottom Sheet view modes
    fun setBackgroundMode(mode: BackgroundMode) { _backgroundMode.value = mode }
    fun setReadingDirection(direction: ReadingDirection) { _readingDirection.value = direction }
    fun toggleReflow() { _isReflowEnabled.value = !_isReflowEnabled.value }
    fun togglePageByPage() { _isPageByPageEnabled.value = !_isPageByPageEnabled.value }
    fun toggleKeepScreenOn() { _isKeepScreenOn.value = !_isKeepScreenOn.value }
    fun toggleTextMode() { _isTextMode.value = !_isTextMode.value }

    suspend fun getPageText(pageIndex: Int): String {
        return withContext(Dispatchers.IO) {
            textCache.get(pageIndex)?.let { return@withContext it }
            
            pdfDocumentMutex.withLock {
                coroutineContext.ensureActive()
                val doc = activePdfDocument ?: return@withContext "Could not load text."
                
                try {
                    val stripper = PDFTextStripper()
                    stripper.startPage = pageIndex + 1  // 1-indexed
                    stripper.endPage = pageIndex + 1
                    val text = stripper.getText(doc)
                    val result = text.ifBlank { "(No selectable text found on this page. The content may be an image or scanned document.)" }
                    textCache.put(pageIndex, result)
                    result
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Failed to extract text: ${e.localizedMessage}"
                }
            }
        }
    }

    fun toggleFavorite() {
        val uri = _currentPdfUri.value?.toString() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(uri)
            _isFavorite.value = !_isFavorite.value
        }
    }

    fun renamePdf(newName: String) {
        val uri = _currentPdfUri.value?.toString() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.renamePdf(uri, newName)
            _pdfName.value = newName
        }
    }

    fun printPdf(context: Context) {
        val uri = _currentPdfUri.value ?: return
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "${_pdfName.value ?: "Document"}.pdf"
        
        try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return
            val printAdapter = object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(jobName)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(fileDescriptor)
                    val output = android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination)
                    
                    try {
                        input.copyTo(output)
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        // Don't close input here as it's from the shared fileDescriptor
                    }
                }
            }
            printManager.print(jobName, printAdapter, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getPageBitmap(pageIndex: Int, targetWidth: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            // Check LRU cache first using a composite key to prevent stretching on device rotation
            val cacheKey = "$pageIndex-$targetWidth"
            bitmapCache.get(cacheKey)?.let { return@withContext it }

            rendererMutex.withLock {
                val renderer = pdfRenderer ?: return@withContext null
                
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                    return@withContext null
                }
                
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    
                    val pageRatio = page.width.toFloat() / page.height.toFloat()
                    val safeDims = BitmapSafety.calculateSafeDimensions(targetWidth, pageRatio)
                    
                    val bitmap = Bitmap.createBitmap(safeDims.width, safeDims.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    bitmapCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext null
                } finally {
                    try {
                        page?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun getPageRegionBitmap(
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
        matrix: android.graphics.Matrix
    ): Bitmap? {
        if (targetWidth <= 0 || targetHeight <= 0) return null
        return withContext(Dispatchers.IO) {
            rendererMutex.withLock {
                val renderer = pdfRenderer ?: return@withContext null
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    
                    // Render with the transformation matrix (scale + translate) 
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return@withContext bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } finally {
                    try {
                        page?.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }

    suspend fun getPageSize(pageIndex: Int): Pair<Int, Int>? {
        return withContext(Dispatchers.IO) {
            rendererMutex.withLock {
                val renderer = pdfRenderer ?: return@withContext null
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    return@withContext page.width to page.height
                } catch (e: Exception) {
                    null
                } finally {
                    try {
                        page?.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }

    // ── Snap a continuous scale to the nearest cached zoom tier ──
    fun snapToZoomLevel(rawScale: Float): Float {
        return when {
            rawScale <= 1.5f  -> 1.5f
            rawScale <= 2.5f  -> 2.5f
            rawScale <= 4.0f  -> 4.0f
            rawScale <= 6.0f  -> 6.0f
            else              -> 8.0f
        }
    }

    /**
     * Returns a bitmap rendered at the given zoom level. Results are cached
     * in [zoomBitmapCache]; on a miss the page is rendered on [Dispatchers.IO]
     * at `viewerWidth * zoomLevel` resolution, then cached for reuse.
     */
    suspend fun getZoomLevelBitmap(
        pageIndex: Int,
        zoomLevel: Float,
        viewerWidth: Int
    ): Bitmap? {
        val cacheKey = "$pageIndex-$zoomLevel"
        zoomBitmapCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            rendererMutex.withLock {
                // Double-check after acquiring the lock
                zoomBitmapCache.get(cacheKey)?.let { return@withContext it }

                val renderer = pdfRenderer ?: return@withContext null
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val pageRatio = page.width.toFloat() / page.height.toFloat()
                    val targetWidth = (viewerWidth * zoomLevel).toInt()
                    val safeDims = BitmapSafety.calculateSafeDimensions(targetWidth, pageRatio)

                    val bitmap = Bitmap.createBitmap(
                        safeDims.width, safeDims.height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(
                        bitmap, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    zoomBitmapCache.put(cacheKey, bitmap)
                    bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } finally {
                    try { page?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun cleanupCacheSync() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(TEMP_PDF_PREFIX)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun closePdfInternal() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            activeTempPdfFile?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfRenderer = null
            fileDescriptor = null
            activeTempPdfFile = null
            bitmapCache.evictAll()
            zoomBitmapCache.evictAll()
            textCache.evictAll()
            layoutCache.evictAll()
            _pageCount.value = 0
            _currentPdfUri.value = null
            _displayUri.value = null
            _isPasswordRequired.value = false
            _isUnlocking.value = false
            _passwordErrorMessage.value = null
            searchSessionCache = null
            searchJob?.cancel()
            _isSearchActive.value = false
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _isLargeFile.value = false
            currentContentUri = null
        }
        
        try {
            activePdfDocument?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activePdfDocument = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        closePdfInternal()
    }

    fun saveReadingProgress(pageIndex: Int) {
        val pdfId = currentPdfId ?: return
        saveProgressJob?.cancel()
        saveProgressJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(400) // Debounce
            progressRepository.saveProgress(pdfId, pageIndex)
        }
    }

    fun toggleBookmark(pageIndex: Int) {
        android.util.Log.d("PDF_DEBUG", "bookmark button pressed")
        val pdfId = currentPdfId ?: return
        android.util.Log.d("PDF_DEBUG", "toggleBookmark entered for page $pageIndex with pdfId $pdfId")
        viewModelScope.launch(Dispatchers.IO) {
            val current = _bookmarks.value.toMutableSet()
            if (current.contains(pageIndex)) {
                progressRepository.removeBookmark(pdfId, pageIndex)
            } else {
                progressRepository.addBookmark(pdfId, pageIndex)
            }
            _bookmarks.value = progressRepository.getBookmarks(pdfId)
            android.util.Log.d("PDF_DEBUG", "toggleBookmark completed, current bookmarks: ${_bookmarks.value}")
        }
    }

    fun removeBookmark(pageIndex: Int) {
        val pdfId = currentPdfId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            progressRepository.removeBookmark(pdfId, pageIndex)
            _bookmarks.value = progressRepository.getBookmarks(pdfId)
        }
    }
    
    fun consumeRestoredPage() {
        _restoredPage.value = null
    }

    /**
     * Resolve a human-readable file name from a URI.
     * Supports content:// URIs (external apps like WhatsApp, file managers)
     * and file:// URIs.
     */
    private fun resolveFileName(uri: Uri, context: Context): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = it.getString(index)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        if (name == null) {
            name = uri.path?.let { File(it).name }
        }
        // Return without extension, fallback to non-null
        return name?.substringBeforeLast(".")?.takeIf { it.isNotBlank() }
    }
    // ── Search Functions ──
    fun openSearch() {
        _isSearchActive.value = true
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentMatchIndex.value = 0
        _searchNoTextFound.value = false
        
        // Lazy initialize search cache
        if (searchSessionCache == null && !_isLoading.value && activePdfDocument != null) {
            buildSearchCache()
        }
    }

    fun closeSearch() {
        _isSearchActive.value = false
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            searchJob?.cancel()
            _searchResults.value = emptyList()
            _currentMatchIndex.value = 0
            _searchNoTextFound.value = false
            return
        }
        executeSearch(query)
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isNotEmpty()) {
            _currentMatchIndex.value = (_currentMatchIndex.value + 1) % results.size
        }
    }

    fun previousSearchResult() {
        val results = _searchResults.value
        if (results.isNotEmpty()) {
            val nextIdx = _currentMatchIndex.value - 1
            _currentMatchIndex.value = if (nextIdx < 0) results.size - 1 else nextIdx
        }
    }

    private fun buildSearchCache() {
        viewModelScope.launch(Dispatchers.Default) {
            _isSearchParsing.value = true
            val cache = mutableListOf<PageTextData>()
            pdfDocumentMutex.withLock {
                val doc = activePdfDocument ?: return@withLock
                try {
                    val pageCount = doc.numberOfPages
                    val stripper = PDFTextStripper()
                    for (i in 0 until pageCount) {
                        ensureActive()
                        stripper.startPage = i + 1
                        stripper.endPage = i + 1
                        val text = stripper.getText(doc)
                        cache.add(PageTextData(i, text))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            searchSessionCache = cache
            _isSearchParsing.value = false
            
            // If user typed while parsing, execute it now
            val query = _searchQuery.value
            if (query.isNotBlank()) {
                executeSearch(query)
            }
        }
    }

    private fun executeSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val cache = searchSessionCache
            if (cache == null) return@launch // Still parsing, will execute after

            val lowerQuery = query.trim().lowercase()
            val matches = mutableListOf<SearchMatch>()
            
            // 1. Find pages with matches using lightweight text cache
            val pagesWithMatches = cache.filter { it.extractedText.lowercase().contains(lowerQuery) }
            
            if (pagesWithMatches.isEmpty()) {
                _searchResults.value = emptyList()
                _searchNoTextFound.value = cache.all { it.extractedText.isBlank() }
                return@launch
            }
            
            _searchNoTextFound.value = false
            
            // 2. Dynamically extract geometry ONLY for matching pages
            for (pageData in pagesWithMatches) {
                ensureActive()
                val layout = getPageLayout(pageData.pageIndex) ?: continue
                
                for (element in layout.elements) {
                    ensureActive()
                    val lowerElementText = element.text.lowercase()
                    var startIndex = lowerElementText.indexOf(lowerQuery)
                    while (startIndex >= 0) {
                        val endIndex = startIndex + lowerQuery.length - 1
                        
                        // Use exact per-character geometry from PDFBox
                        val left = if (startIndex < element.charPositions.size) {
                            element.charPositions[startIndex]
                        } else {
                            element.x
                        }
                        val right = if (endIndex < element.charPositions.size) {
                            element.charPositions[endIndex] + element.charWidths[endIndex]
                        } else {
                            element.x + element.width
                        }
                        
                        matches.add(SearchMatch(
                            pageIndex = pageData.pageIndex,
                            matchText = element.text.substring(startIndex, startIndex + lowerQuery.length),
                            bounds = listOf(android.graphics.RectF(left, element.y, right, element.y + element.height))
                        ))
                        
                        startIndex = lowerElementText.indexOf(lowerQuery, startIndex + 1)
                    }
                }
            }
            
            _searchResults.value = matches
            _currentMatchIndex.value = 0
        }
    }
}
