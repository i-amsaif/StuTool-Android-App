package com.stutool.stutool.ui.screens.tools

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
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

enum class PageSelectionMode { MANUAL, VISUAL }

class ExtractPagesViewModel(application: Application) : AndroidViewModel(application) {

    init {
        PDFBoxResourceLoader.init(application)
    }

    // ── Core State ──

    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri.asStateFlow()

    private val _originalFileName = MutableStateFlow<String?>(null)
    val originalFileName: StateFlow<String?> = _originalFileName.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _pageInput = MutableStateFlow("")
    val pageInput: StateFlow<String> = _pageInput.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _successUri = MutableStateFlow<Uri?>(null)
    val successUri: StateFlow<Uri?> = _successUri.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow("Preparing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private var processingJob: kotlinx.coroutines.Job? = null

    // ── Visual Selection State ──

    private val _selectionMode = MutableStateFlow(PageSelectionMode.MANUAL)
    val selectionMode: StateFlow<PageSelectionMode> = _selectionMode.asStateFlow()

    private val _selectedPages = MutableStateFlow<Set<Int>>(emptySet())
    val selectedPages: StateFlow<Set<Int>> = _selectedPages.asStateFlow()

    private val _inputError = MutableStateFlow<String?>(null)
    val inputError: StateFlow<String?> = _inputError.asStateFlow()

    // Thumbnail cache — sized by memory (bytes/1024), not count
    private val maxCacheSize = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt() // 1/8th of available memory
    private val thumbnailCache = object : LruCache<Int, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    // Tracks which pages are currently being rendered to prevent duplicate loads
    private val _loadingPages = MutableStateFlow<Set<Int>>(emptySet())
    val loadingPages: StateFlow<Set<Int>> = _loadingPages.asStateFlow()

    // Bumps to trigger recomposition when a new thumbnail is added to the LruCache
    private val _thumbnailVersion = MutableStateFlow(0L)
    val thumbnailVersion: StateFlow<Long> = _thumbnailVersion.asStateFlow()

    // Track rendering jobs for cancellation
    private val thumbnailJobs = mutableMapOf<Int, Job>()

    // Sync guard to prevent infinite loops
    private var isUpdatingFromGrid = false
    private var isUpdatingFromText = false

    // PdfRenderer for thumbnails (kept open while file is loaded)
    private var thumbnailRenderer: PdfRenderer? = null
    private var thumbnailPfd: ParcelFileDescriptor? = null

    // ── File Loading ──

    fun loadFile(uri: Uri, context: Context) {
        // Cleanup previous state
        closeThumbnailRenderer()
        cancelAllThumbnailJobs()
        thumbnailCache.evictAll()
        _thumbnailVersion.value = 0L

        _selectedFileUri.value = uri
        _successUri.value = null
        _errorMessage.value = null
        _inputError.value = null
        _pageInput.value = ""
        _selectedPages.value = emptySet()
        _loadingPages.value = emptySet()
        _originalFileName.value = getFileName(uri, context) ?: "document"

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val pagesCount = PdfEngine.countPages(context, uri)
                _totalPages.value = pagesCount

                // Open PdfRenderer for thumbnail generation
                withContext(Dispatchers.IO) {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        thumbnailPfd = pfd
                        thumbnailRenderer = PdfRenderer(pfd)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load PDF: ${e.localizedMessage}"
                _selectedFileUri.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Selection Mode ──

    fun setSelectionMode(mode: PageSelectionMode) {
        _selectionMode.value = mode
    }

    // ── Page Selection (from Grid) ──

    fun togglePageSelection(pageIndex: Int) {
        val current = _selectedPages.value
        val newSelection = if (pageIndex in current) {
            current - pageIndex
        } else {
            current + pageIndex
        }
        _selectedPages.value = newSelection
        _inputError.value = null

        // Sync to text input (guard against loop)
        isUpdatingFromGrid = true
        _pageInput.value = generateRangeString(newSelection)
        isUpdatingFromGrid = false
    }

    fun selectAllPages() {
        val total = _totalPages.value
        if (total <= 0) return
        val allPages = (1..total).toSet()
        _selectedPages.value = allPages
        _inputError.value = null

        isUpdatingFromGrid = true
        _pageInput.value = generateRangeString(allPages)
        isUpdatingFromGrid = false
    }

    fun clearPageSelection() {
        _selectedPages.value = emptySet()
        _inputError.value = null

        isUpdatingFromGrid = true
        _pageInput.value = ""
        isUpdatingFromGrid = false
    }

    // ── Manual Input (from Text) ──

    fun updatePageInput(input: String) {
        _pageInput.value = input
        _errorMessage.value = null

        // Don't re-parse if this update came from a grid click
        if (isUpdatingFromGrid) return

        val total = _totalPages.value
        if (input.isBlank()) {
            _selectedPages.value = emptySet()
            _inputError.value = null
            return
        }

        try {
            isUpdatingFromText = true
            val parsed = parsePageInput(input, total)
            _selectedPages.value = parsed.toSet()
            _inputError.value = null
            isUpdatingFromText = false
        } catch (e: Exception) {
            // Show inline error but DON'T update selection — keep last valid state
            _inputError.value = e.message
            isUpdatingFromText = false
        }
    }

    // ── Thumbnail Loading ──

    fun getThumbnail(pageIndex: Int): Bitmap? {
        return thumbnailCache.get(pageIndex)
    }

    fun requestThumbnailLoad(pageIndex: Int) {
        // Guard: already cached or already loading
        if (thumbnailCache.get(pageIndex) != null) return
        if (_loadingPages.value.contains(pageIndex)) return
        if (thumbnailRenderer == null) return

        _loadingPages.value = _loadingPages.value + pageIndex

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val renderer = thumbnailRenderer ?: return@launch
                val page = synchronized(renderer) { renderer.openPage(pageIndex - 1) } // 0-indexed

                // Aggressive downscale: max 200px on longest side
                val maxDim = 200
                val scale = maxDim.toFloat() / maxOf(page.width, page.height)
                val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                val bmpH = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                synchronized(renderer) {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                }

                thumbnailCache.put(pageIndex, bitmap)
                withContext(Dispatchers.Main) {
                    _thumbnailVersion.value++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    _loadingPages.value = _loadingPages.value - pageIndex
                    thumbnailJobs.remove(pageIndex)
                }
            }
        }
        thumbnailJobs[pageIndex] = job
    }

    fun cancelThumbnailLoad(pageIndex: Int) {
        thumbnailJobs[pageIndex]?.cancel()
        thumbnailJobs.remove(pageIndex)
        _loadingPages.value = _loadingPages.value - pageIndex
    }

    private fun cancelAllThumbnailJobs() {
        thumbnailJobs.values.forEach { it.cancel() }
        thumbnailJobs.clear()
        _loadingPages.value = emptySet()
    }

    // ── Extraction ──

    fun extractPages(context: Context) {
        val uri = _selectedFileUri.value
        if (uri == null) {
            _errorMessage.value = "No file selected."
            return
        }

        val total = _totalPages.value
        val pagesToExtract: List<Int>

        // Determine pages from the single source of truth
        val currentSelection = _selectedPages.value
        val input = _pageInput.value.trim()

        if (currentSelection.isNotEmpty()) {
            pagesToExtract = currentSelection.sorted()
        } else if (input.isNotEmpty()) {
            try {
                pagesToExtract = parsePageInput(input, total)
                if (pagesToExtract.isEmpty()) {
                    _errorMessage.value = "Invalid page range."
                    return
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Invalid page range format."
                return
            }
        } else {
            _errorMessage.value = "Please select pages to extract."
            return
        }

        _isLoading.value = true
        _isExtracting.value = true
        _processingError.value = null
        _errorMessage.value = null
        _successUri.value = null

        processingJob = viewModelScope.launch {
            try {
                _statusMessage.value = "Preparing document..."
                delay(200)
                _statusMessage.value = "Extracting selected pages..."
                
                val originalName = _originalFileName.value ?: "document"
                val resultUri = PdfEngine.extractPages(context, uri, originalName, pagesToExtract)
                
                _statusMessage.value = "Building document..."
                delay(300)
                _statusMessage.value = "Finalizing..."
                delay(400)
                _successUri.value = resultUri
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore, as it was cancelled intentionally
            } catch (e: Exception) {
                _processingError.value = "An error occurred while extracting the pages. Please try again."
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _isExtracting.value = false
            }
        }
    }

    // ── Helpers ──

    private fun generateRangeString(pages: Set<Int>): String {
        if (pages.isEmpty()) return ""
        val sorted = pages.sorted()
        val ranges = mutableListOf<String>()
        var rangeStart = sorted[0]
        var rangeEnd = sorted[0]

        for (i in 1 until sorted.size) {
            if (sorted[i] == rangeEnd + 1) {
                rangeEnd = sorted[i]
            } else {
                ranges.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")
                rangeStart = sorted[i]
                rangeEnd = sorted[i]
            }
        }
        ranges.add(if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd")
        return ranges.joinToString(", ")
    }

    private fun parsePageInput(input: String, totalPages: Int): List<Int> {
        val result = mutableSetOf<Int>()
        val parts = input.replace(" ", "").split(",")

        for (part in parts) {
            if (part.isEmpty()) continue

            if (part.contains("-")) {
                val rangeParts = part.split("-")
                if (rangeParts.size != 2) throw Exception("Invalid range format: $part")

                val start = rangeParts[0].toIntOrNull() ?: throw Exception("Invalid number in range: $part")
                val end = rangeParts[1].toIntOrNull() ?: throw Exception("Invalid number in range: $part")

                if (start > end) throw Exception("Range cannot be reversed: $part")
                if (start < 1 || end > totalPages) throw Exception("Pages out of bounds: $part (Max: $totalPages)")

                for (i in start..end) {
                    result.add(i)
                }
            } else {
                val page = part.toIntOrNull() ?: throw Exception("Invalid page number: $part")
                if (page < 1 || page > totalPages) throw Exception("Page $page out of bounds (Max: $totalPages)")
                result.add(page)
            }
        }

        return result.sorted()
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

    fun resetState() {
        closeThumbnailRenderer()
        cancelAllThumbnailJobs()
        thumbnailCache.evictAll()

        _selectedFileUri.value = null
        _originalFileName.value = null
        _totalPages.value = 0
        _pageInput.value = ""
        _selectedPages.value = emptySet()
        _loadingPages.value = emptySet()
        _thumbnailVersion.value = 0L
        _successUri.value = null
        _errorMessage.value = null
        _inputError.value = null
        _selectionMode.value = PageSelectionMode.MANUAL
        _statusMessage.value = "Preparing..."
        _processingError.value = null
        _isExtracting.value = false
    }

    private fun closeThumbnailRenderer() {
        try {
            thumbnailRenderer?.close()
            thumbnailPfd?.close()
        } catch (_: Exception) {}
        thumbnailRenderer = null
        thumbnailPfd = null
    }

    override fun onCleared() {
        super.onCleared()
        closeThumbnailRenderer()
        cancelAllThumbnailJobs()
        thumbnailCache.evictAll()
    }
}
