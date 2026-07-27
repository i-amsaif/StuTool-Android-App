package com.stutool.stutool.ui.screens.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.ui.screens.scanner.models.*
import com.stutool.stutool.ui.screens.scanner.utils.ImageProcessing
import com.stutool.stutool.ui.screens.scanner.utils.PerspectiveTransform
import com.stutool.stutool.ui.screens.scanner.utils.Quad
import com.stutool.stutool.ui.screens.scanner.utils.ScanFilter
import com.stutool.stutool.ui.screens.scanner.utils.PdfGenerator
import com.stutool.stutool.ui.screens.scanner.utils.ImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

enum class ScanRatio(val displayAspect: Float, val label: String) {
    RATIO_4_3(3f / 4f, "4:3"),
    RATIO_1_1(1f, "1:1");
}

enum class ScanMode { SINGLE, BATCH }

class ScannerViewModel : ViewModel() {

    private lateinit var sessionRepository: ScannerSessionRepository

    // Global session state
    private val _scannedPages = MutableStateFlow<List<ScanPage>>(emptyList())
    val scannedPages: StateFlow<List<ScanPage>> = _scannedPages.asStateFlow()

    var documentTitle = MutableStateFlow("")

    var sessionDirty = false
    var recoveryPromptConsumed = false

    private val _hasRecoveredSession = MutableStateFlow(false)
    val hasRecoveredSession: StateFlow<Boolean> = _hasRecoveredSession.asStateFlow()

    // Export State
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    // Transient editing state for active capture/edit
    private val _currentImageUri = MutableStateFlow<Uri?>(null)
    val currentImageUri: StateFlow<Uri?> = _currentImageUri.asStateFlow()

    private val _activeQuad = MutableStateFlow<Quad?>(null)
    val activeQuad: StateFlow<Quad?> = _activeQuad.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ScanFilter.ENHANCED)
    val selectedFilter: StateFlow<ScanFilter> = _selectedFilter.asStateFlow()

    private val _filterStrength = MutableStateFlow(1.0f)
    val filterStrength: StateFlow<Float> = _filterStrength.asStateFlow()

    private val _pendingScansCount = MutableStateFlow(0)
    val pendingScansCount: StateFlow<Int> = _pendingScansCount.asStateFlow()

    private val _tempEditPreviewUri = MutableStateFlow<Uri?>(null)
    val tempEditPreviewUri: StateFlow<Uri?> = _tempEditPreviewUri.asStateFlow()

    private val _imageReady = MutableStateFlow(false)
    val imageReady: StateFlow<Boolean> = _imageReady.asStateFlow()

    private val _editingPageIndex = MutableStateFlow(-1)
    val editingPageIndex: StateFlow<Int> = _editingPageIndex.asStateFlow()

    private val _editingPageId = MutableStateFlow("")
    val editingPageId: StateFlow<String> = _editingPageId.asStateFlow()

    var captureRatio: ScanRatio = ScanRatio.RATIO_4_3

    private val _scanMode = MutableStateFlow(ScanMode.SINGLE)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    // G4.1: Navigation Safety
    private val _showExitDialog = MutableStateFlow(false)
    val showExitDialog: StateFlow<Boolean> = _showExitDialog.asStateFlow()

    private val _isIntentionalScannerExit = MutableStateFlow(false)
    val isIntentionalScannerExit: StateFlow<Boolean> = _isIntentionalScannerExit.asStateFlow()

    // G4.2: Transition stabilization
    private val _isTransitioningBetweenScannerStages = MutableStateFlow(false)
    val isTransitioningBetweenScannerStages: StateFlow<Boolean> = _isTransitioningBetweenScannerStages.asStateFlow()

    fun setTransitioning(isTransitioning: Boolean) {
        if (_isTransitioningBetweenScannerStages.value != isTransitioning) {
            Log.d("ScannerVM", "[G4.2 Transition] Transitioning: $isTransitioning")
            _isTransitioningBetweenScannerStages.value = isTransitioning
        }
    }

    // Processing Queues
    private val processingJobs = mutableMapOf<String, Job>()
    
    // Concurrency limit for low-RAM devices (max 2 concurrent heavy ops)
    private val processingSemaphore = Semaphore(2)
    
    // Global export lock to prevent double exports
    private val exportMutex = Mutex()
    
    // G2: Queue transaction lock — prevents double Next taps from advancing twice
    private val queueAdvanceLock = AtomicBoolean(false)
    
    // G2: Active image dimensions for building CropTransform
    private val _activeImageWidth = MutableStateFlow(0)
    val activeImageWidth: StateFlow<Int> = _activeImageWidth.asStateFlow()
    private val _activeImageHeight = MutableStateFlow(0)
    val activeImageHeight: StateFlow<Int> = _activeImageHeight.asStateFlow()
    
    // G2: One-time recovery Toast flag — prevents repeated "Recovered partially" spam
    private var recoveryToastShown = false
    
    // G2: Cache regeneration guard — track pages that already attempted regen (once-only)
    private val thumbnailRegenAttempts = ConcurrentHashMap<String, Boolean>()
    
    // G2: Strict timeout constants
    private companion object {
        const val BITMAP_DECODE_TIMEOUT_MS = 8000L
        const val THUMBNAIL_GEN_TIMEOUT_MS = 10000L
        const val FILTER_PREVIEW_TIMEOUT_MS = 8000L
        const val CACHE_GEN_TIMEOUT_MS = 15000L
    }
    
    // UI Caching version tracker for the currently editing page
    private var activeProcessingVersion: Long = 0
    private var hasManuallySelectedFilter = false

    // ── Batch Filter Pager State ─────────────────────────────────────────────
    // The index of the page currently focused in the BatchFilterPagerScreen.
    // Persisted through process death so the user returns to the same page.
    private val _currentFilterPageIndex = MutableStateFlow(0)
    val currentFilterPageIndex: StateFlow<Int> = _currentFilterPageIndex.asStateFlow()

    // Live low-res preview URI for the currently focused pager page.
    // Only used by the pager main view — NOT the filmstrip.
    private val _livePreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val livePreviewBitmap: StateFlow<Bitmap?> = _livePreviewBitmap.asStateFlow()

    private val _isPreviewProcessing = MutableStateFlow(false)
    val isPreviewProcessing: StateFlow<Boolean> = _isPreviewProcessing.asStateFlow()

    // Per-page loading indicator for filmstrip thumbnails (optimistic UI)
    private val _thumbnailLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val thumbnailLoadingIds: StateFlow<Set<String>> = _thumbnailLoadingIds.asStateFlow()

    // Per-page preview job tracking for aggressive cancellation
    private val previewJobs = ConcurrentHashMap<String, Job>()

    // ── Batch Processing Queue (G3 Isolated) ──────────────────────────────────
    private val _activeBatch = MutableStateFlow<ProcessingBatch?>(null)
    val activeBatch: StateFlow<ProcessingBatch?> = _activeBatch.asStateFlow()

    private val _batchPages = MutableStateFlow<List<ScanPage>>(emptyList())
    val batchPages: StateFlow<List<ScanPage>> = _batchPages.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _applyFilterToAll = MutableStateFlow(true)
    val applyFilterToAll: StateFlow<Boolean> = _applyFilterToAll.asStateFlow()

    private val lastPreviewRequests = mutableMapOf<String, String>()

    private val _cropMode = MutableStateFlow(CropMode.PERSPECTIVE)
    val cropMode: StateFlow<CropMode> = _cropMode.asStateFlow()

    val isQueueActive: Boolean get() = _activeBatch.value != null && _currentQueueIndex.value >= 0

    private val _activeRotation = MutableStateFlow(0)
    val activeRotation: StateFlow<Int> = _activeRotation.asStateFlow()

    // --- Iteration 3: Auto Crop Decision UI State ---
    private val _showCropDecisionSheet = MutableStateFlow(false)
    val showCropDecisionSheet: StateFlow<Boolean> = _showCropDecisionSheet.asStateFlow()

    private val _isAutoCropping = MutableStateFlow(false)
    val isAutoCropping: StateFlow<Boolean> = _isAutoCropping.asStateFlow()

    private val _isGalleryImporting = MutableStateFlow(false)
    val isGalleryImporting: StateFlow<Boolean> = _isGalleryImporting.asStateFlow()

    private var pendingBatchSource: BatchSource = BatchSource.UNKNOWN
    private var pendingCropCallback: (() -> Unit)? = null
    private var pendingCropReviewCallback: (() -> Unit)? = null
    private var pendingFilterCallback: (() -> Unit)? = null

    // --- Iteration 4: Auto Crop Review State ---
    private val _cropReviewQuads = MutableStateFlow<Map<String, Quad>>(emptyMap())
    val cropReviewQuads: StateFlow<Map<String, Quad>> = _cropReviewQuads.asStateFlow()

    private val _cropReviewRotations = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cropReviewRotations: StateFlow<Map<String, Int>> = _cropReviewRotations.asStateFlow()

    fun updateCropReviewQuad(pageId: String, quad: Quad) {
        _cropReviewQuads.value = _cropReviewQuads.value + (pageId to quad)
    }

    fun updateCropReviewRotation(pageId: String, clockwise: Boolean) {
        val current = _cropReviewRotations.value[pageId] ?: 0
        val newRotation = if (clockwise) (current + 90) % 360 else (current - 90 + 360) % 360
        _cropReviewRotations.value = _cropReviewRotations.value + (pageId to newRotation)
    }

    fun dismissCropDecisionSheet(context: Context) {
        handleCropDecision(context, isAuto = false)
    }

    fun handleCropDecision(context: Context, isAuto: Boolean) {
        _showCropDecisionSheet.value = false
        val rawPages = _scannedPages.value.filter { it.state == ScanPageState.IDLE }
        if (rawPages.isEmpty()) {
            clearPendingCallbacks()
            return
        }

        if (isAuto) {
            _isAutoCropping.value = true
            viewModelScope.launch {
                val batchId = java.util.UUID.randomUUID().toString()
                _activeBatch.value = ProcessingBatch(batchId, rawPages.map { it.id }, QueueStage.CROP, pendingBatchSource)
                _batchPages.value = rawPages
                _scannedPages.value = _scannedPages.value.filter { it.state != ScanPageState.IDLE }
                
                bulkApplyAutoEdges(context, rawPages)
                
                // Initialize Crop Review state with defaults
                val initialRotations = rawPages.associate { it.id to it.transform.effectiveRotation() }
                _cropReviewRotations.value = initialRotations
                
                _currentQueueIndex.value = 0
                saveSession()
                
                withContext(Dispatchers.Main) {
                    _isAutoCropping.value = false
                    pendingCropReviewCallback?.invoke()
                    clearPendingCallbacks()
                }
            }
        } else {
            commitBatchAndProceedToCrop(context, rawPages, pendingBatchSource)
            pendingCropCallback?.invoke()
            clearPendingCallbacks()
        }
    }

    private fun clearPendingCallbacks() {
        pendingCropCallback = null
        pendingCropReviewCallback = null
        pendingFilterCallback = null
        pendingBatchSource = BatchSource.UNKNOWN
    }

    // --- Iteration 4: Commit Crop Review Edits ---
    fun commitBatchCropReviewEdits(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            val batch = _activeBatch.value ?: return@launch
            val editedQuads = _cropReviewQuads.value
            val editedRotations = _cropReviewRotations.value
            
            val updatedPages = _batchPages.value.map { page ->
                val quad = editedQuads[page.id]
                val rotation = editedRotations[page.id]
                
                var isModified = false
                var newTransform = page.transform
                
                if (quad != null && quad != page.transform.effectiveCropQuad()) {
                    val cropTrans = CropTransform(quad, Quad.defaultForImage(1000,1000).bottomRight.x.toInt(), Quad.defaultForImage(1000,1000).bottomRight.y.toInt(), rotation ?: page.transform.effectiveRotation()) // The image dims will be resolved in processCrop
                    newTransform = newTransform.copy(cropTransform = cropTrans, cropQuad = quad, autoEdgeApplied = false)
                    isModified = true
                }
                
                if (rotation != null && rotation != page.transform.effectiveRotation()) {
                    newTransform = newTransform.copy(rotation = rotation)
                    isModified = true
                }
                
                if (isModified) {
                    page.copy(transform = newTransform, isEdited = true)
                } else {
                    page
                }
            }
            
            _batchPages.value = updatedPages
            
            // Re-crop modified pages
            val pagesToRecrop = updatedPages.filter { it.isEdited }
            if (pagesToRecrop.isNotEmpty()) {
                // To keep it simple, we use the unified crop processing via generateCachesSync
                // Wait, processCrop is what actually saves croppedCacheUri. 
                // In bulkApplyAutoEdges, it calls processCrop(context, page, quad, page.transform.effectiveRotation())
                for (page in pagesToRecrop) {
                    generateCroppedBitmapCache(context, page.id, batch.batchId)
                    generateCachesSync(context, page.id, batch.batchId)
                }
            }
            
            // Advance stage
            _activeBatch.value = batch.copy(stage = QueueStage.FILTER)
            saveSession()
            
            withContext(Dispatchers.Main) {
                // Clear state
                _cropReviewQuads.value = emptyMap()
                _cropReviewRotations.value = emptyMap()
                onComplete()
            }
        }
    }

    // --- Iteration 5: Single Page Crop Update ---
    fun updateSinglePageCrop(context: Context, pageId: String, newQuad: Quad, newRotation: Int? = null, onComplete: () -> Unit) {
        android.util.Log.e("CropTrace_DONE", "updateSinglePageCrop ACTUALLY ENTERED for pageId: $pageId")
        viewModelScope.launch {
            val batch = _activeBatch.value
            val isBatchPage = _batchPages.value.any { it.id == pageId }
            val listFlow = if (isBatchPage) _batchPages else _scannedPages
            
            val list = listFlow.value.toMutableList()
            val idx = list.indexOfFirst { it.id == pageId }
            
            if (idx != -1) {
                val page = list[idx]
                var newTransform = page.transform
                var isModified = false
                
                val effectiveRotation = newRotation ?: page.transform.effectiveRotation()
                
                val effectiveQuad = page.transform.effectiveCropQuad()
                android.util.Log.e("CropTrace", "Before isModified check:")
                android.util.Log.e("CropTrace", "effectiveCropQuad: $effectiveQuad")
                android.util.Log.e("CropTrace", "newQuad: $newQuad")
                android.util.Log.e("CropTrace", "newQuad == effectiveCropQuad: ${newQuad == effectiveQuad}")
                
                if (newQuad != effectiveQuad) {
                    val cropTrans = CropTransform(newQuad, Quad.defaultForImage(1000,1000).bottomRight.x.toInt(), Quad.defaultForImage(1000,1000).bottomRight.y.toInt(), effectiveRotation)
                    newTransform = newTransform.copy(cropTransform = cropTrans, cropQuad = newQuad, autoEdgeApplied = false)
                    isModified = true
                }
                
                if (effectiveRotation != page.transform.effectiveRotation()) {
                    newTransform = newTransform.copy(rotation = effectiveRotation)
                    isModified = true
                }
                
                android.util.Log.e("CropTrace", "isModified: $isModified")
                
                if (isModified) {
                    android.util.Log.e("CropTrace", "Entered if (isModified) block")
                    val updatedPage = page.copy(transform = newTransform, isEdited = true)
                    android.util.Log.e("CropTrace", "updatedPage.transform.cropQuad: ${updatedPage.transform.cropQuad}")
                    
                    list[idx] = updatedPage
                    android.util.Log.e("CropTrace", "list[idx] assigned")
                    
                    listFlow.value = list
                    android.util.Log.e("CropTrace", "listFlow.value assigned")
                    
                    val rereadPage = listFlow.value.find { it.id == pageId }
                    android.util.Log.e("CropTrace", "stored cropQuad after assignment: ${rereadPage?.transform?.cropQuad}")
                    
                    android.util.Log.e("CropTrace_SAVE", "cropQuad before save (pageId=$pageId): ${updatedPage.transform.cropQuad}")
                    // Re-crop using the shared generation pipeline
                    generateCroppedBitmapCache(context, updatedPage.id, batch?.batchId)
                    generateCachesSync(context, updatedPage.id, batch?.batchId)
                    
                    saveSession()
                    android.util.Log.e("CropTrace_SAVE", "cropQuad after save (pageId=$pageId): ${_scannedPages.value.find { it.id == pageId }?.transform?.cropQuad}")
                } else {
                    android.util.Log.e("CropTrace", "Skipped directly to onComplete() (isModified was false)")
                }
            }
            
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // ── Helper Logging ────────────────────────────────────────────────────────
    
    private fun logG3State(event: String) {
        val batch = _activeBatch.value
        val pages = _batchPages.value
        val finalized = _scannedPages.value
        Log.d("ScannerVM", "[G3] $event | batchId=${batch?.batchId ?: "null"} | source=${batch?.source?.name ?: "null"} | batchPageCount=${pages.size} | finalizedCount=${finalized.size} | currentIndex=${_currentQueueIndex.value}")
    }

    // ── Initialization ──────────────────────────────────────────────────────

    fun initRepository(context: Context) {
        if (!::sessionRepository.isInitialized) {
            sessionRepository = ScannerSessionRepository(context)
        }
    }

    // ── Session Management ──────────────────────────────────────────────────

    fun restoreSession(context: Context) {
        if (recoveryPromptConsumed) return // Never re-trigger in same lifecycle

        initRepository(context)
        viewModelScope.launch {
            val sessionData = sessionRepository.loadSession()
            if (sessionData.pages.isNotEmpty() || sessionData.activeBatchPages.isNotEmpty()) {
                _scannedPages.value = sessionData.pages
                _hasRecoveredSession.value = true
                sessionDirty = true

                // G3.2: One-time recovery notification (never repeated)
                if (sessionData.hadCorruption && !recoveryToastShown) {
                    recoveryToastShown = true
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Could not fully restore session. Recover available pages?", android.widget.Toast.LENGTH_LONG).show()
                    }
                    Log.w("ScannerVM", "[G3.2 RESTORE] Session had corruption, one-time recovery Toast shown (crash recovery path)")
                }

                // Restore batch state if interrupted mid-batch
                sessionData.activeBatch?.let { batch ->
                    // G3.2: Clean transient state for restored active batches
                    val validIds = batch.pageIds.filter { id -> sessionData.activeBatchPages.any { it.id == id } }
                    
                    if (validIds.isNotEmpty()) {
                        _activeBatch.value = batch.copy(pageIds = validIds)
                        _batchPages.value = sessionData.activeBatchPages.filter { it.id in validIds }
                        val resumeIndex = if (batch.stage == QueueStage.CROP) {
                            validIds.indexOfFirst { id -> 
                                sessionData.activeBatchPages.find { it.id == id }?.state == ScanPageState.IDLE 
                            }.coerceAtLeast(0)
                        } else 0
                        _currentQueueIndex.value = resumeIndex // G3.2.3 RESTORE: resume at exact unfinished queue index
                        prepareCurrentQueuePage(context) // G3.2: reconstruct queue state safely
                        Log.d("ScannerVM", "[G3.2 RESTORE] Queue reconstruction: Active batch restored: ${validIds.size} pages, stage=${batch.stage.name}")
                    } else {
                        Log.w("ScannerVM", "[G3.2 RESTORE] Queue reconstruction failed: Discarded invalid batch restore — no valid page IDs remain")
                    }
                }
                
                // G2: Clean up any orphaned cache files on disk (from dropped corrupted pages)
                cleanupUnusedTempFiles(context, forceAll = false)

                // G3.2.3 ORPHAN + G3.2.5 RESTORE_SYNC: Detect orphans and register restore deferred jobs
                // Must be called in-line (not in a child launch) so restoreJobs is populated BEFORE awaitRestoreCompletion()
                Log.d("ScannerVM", "[G3.2.5 RESTORE_SYNC] Restore started — scanning for orphaned pages")
                detectOrphanedProcessingJobs(context)
                Log.d("ScannerVM", "[G3.2.5 RESTORE_SYNC] Restore scan complete — ${restoreJobs.size} jobs queued")
            } else {
                // G2: No session exists, clean up everything just in case
                cleanupUnusedTempFiles(context, forceAll = true)
            }
            logG3State("restoreSession complete")
        }
    }
    
    private val restoreJobs = mutableListOf<Deferred<Boolean>>()

    /**
     * G3.2.5 FILE_COMMIT: Validate a file is truly usable before marking any page as ready.
     * A file must exist, have size > 0, and be decode-able (lightweight bounds-only check).
     */
    private fun validateCommittedFile(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) {
            Log.w("ScannerVM", "[G3.2.5 FILE_COMMIT] Validation FAILED: file missing or empty: ${file.name}")
            return false
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val valid = opts.outWidth > 0 && opts.outHeight > 0
        if (!valid) Log.w("ScannerVM", "[G3.2.5 FILE_COMMIT] Validation FAILED: file not decodable: ${file.name}")
        else Log.d("ScannerVM", "[G3.2.5 FILE_COMMIT] Validation passed: ${file.name} (${opts.outWidth}x${opts.outHeight})")
        return valid
    }

    private suspend fun detectOrphanedProcessingJobs(context: Context) {
        val allPages = _scannedPages.value + _batchPages.value
        val orphanedPages = mutableListOf<ScanPage>()
        
        withContext(Dispatchers.Main) {
            val scannedMut = _scannedPages.value.toMutableList()
            val batchMut = _batchPages.value.toMutableList()
            var stateChanged = false

            for (page in allPages) {
                if (page.assetState == AssetState.RESTORING) {
                    var needsHydration = true
                    
                    // G3.2.6: Strict file validation before marking READY
                    if (page.previewCacheUri != null && page.thumbnailCacheUri != null) {
                        val previewFile = page.previewCacheUri.path?.let { File(it) }
                        val thumbFile = page.thumbnailCacheUri.path?.let { File(it) }
                        
                        if (previewFile != null && thumbFile != null &&
                            validateCommittedFile(previewFile) && validateCommittedFile(thumbFile)) {
                            needsHydration = false
                        }
                    }

                    if (page.state == ScanPageState.IDLE || page.state == ScanPageState.CROPPED) {
                        needsHydration = true
                    }

                    val hasJob = processingJobs.containsKey(page.id) || previewJobs.containsKey(page.id)
                    if (hasJob) needsHydration = false

                    // Update state
                    val targetList = if (_batchPages.value.any { it.id == page.id }) batchMut else scannedMut
                    val idx = targetList.indexOfFirst { it.id == page.id }
                    if (idx != -1) {
                        if (needsHydration) {
                            targetList[idx] = page.copy(assetState = AssetState.HYDRATING)
                            orphanedPages.add(page)
                        } else {
                            targetList[idx] = page.copy(assetState = AssetState.READY)
                        }
                        stateChanged = true
                    }
                }
            }
            
            if (stateChanged) {
                _scannedPages.value = scannedMut
                _batchPages.value = batchMut
            }
        }

        if (orphanedPages.isEmpty()) {
            Log.d("ScannerVM", "[G3.2.6 HYDRATION] No orphaned jobs detected — restore complete immediately")
            return
        }

        Log.w("ScannerVM", "[G3.2.6 HYDRATION] Detected ${orphanedPages.size} pages needing hydration.")

        orphanedPages.forEach { page ->
            val batchId = if (_batchPages.value.any { it.id == page.id }) _activeBatch.value?.batchId else null
            val deferred = viewModelScope.async(Dispatchers.IO) {
                try {
                    Log.d("ScannerVM", "[G3.2.6 HYDRATION] Regeneration started for page ${page.id}")
                    generateCachesSync(context, page.id, batchId)
                    true
                } catch (e: Exception) {
                    Log.e("ScannerVM", "[G3.2.6 HYDRATION] Regeneration FAILED for page ${page.id}", e)
                    false
                } finally {
                    processingJobs.remove(page.id)
                }
            }
            processingJobs[page.id] = deferred
            restoreJobs.add(deferred)
        }
    }

    suspend fun awaitRestoreCompletion() {
        if (restoreJobs.isEmpty()) {
            Log.d("ScannerVM", "[G3.2.5 RESTORE_SYNC] No restore jobs pending — navigation unlocked immediately")
            return
        }
        Log.d("ScannerVM", "[G3.2.5 RESTORE_SYNC] awaitAll entered — blocking navigation until ${restoreJobs.size} jobs complete")
        // G3.2.5: timeout guard — never block indefinitely (max 30s)
        withTimeoutOrNull(30_000L) {
            restoreJobs.awaitAll()
        } ?: Log.w("ScannerVM", "[G3.2.5 RESTORE_SYNC] awaitAll timed out after 30s — unlocking navigation with partial restore")
        restoreJobs.clear()
        Log.d("ScannerVM", "[G3.2.5 RESTORE_SYNC] awaitAll completed — restore finalized, navigation unlocked")
    }

    fun dismissRecoveredSessionPrompt(context: Context) {
        _hasRecoveredSession.value = false
        recoveryPromptConsumed = true
        clearSession(context)
    }

    fun acceptRecoveredSession() {
        _hasRecoveredSession.value = false
        recoveryPromptConsumed = true
    }

    fun getRestoreDestination(): String {
        val batch = _activeBatch.value
        val dest = if (batch != null) {
            when (batch.stage) {
                QueueStage.CROP -> "edge_crop"
                QueueStage.FILTER -> "batch_filter_pager"
                else -> "document_review"
            }
        } else {
            "document_review"
        }
        Log.d("ScannerVM", "[G3.2.1 CHECKPOINT] Restore stage selected: $dest")
        return dest
    }

    fun getRecoveredPageCount(): Int {
        val finalizedCount = _scannedPages.value.filter { !it.isNew }.size
        val batchCount = _batchPages.value.size
        return finalizedCount + batchCount
    }

    fun saveSession() {
        if (!::sessionRepository.isInitialized) return
        viewModelScope.launch {
            val batch = _activeBatch.value
            val batchPages = _batchPages.value
            sessionRepository.saveSession(_scannedPages.value, batch, batchPages)
        }
    }

    fun clearSession(context: Context) {
        initRepository(context)
        viewModelScope.launch {
            Log.d("ScannerVM", "[G4.1 Cleanup] Clearing active session state")
            sessionRepository.clearSession()
            _scannedPages.value = emptyList()
            sessionDirty = false
            clearQueue()
            resetTransientState()
            cleanupUnusedTempFiles(context, true)
            processingJobs.values.forEach { it.cancel() }
            processingJobs.clear()
            previewJobs.values.forEach { it.cancel() }
            previewJobs.clear()
        }
    }

    // ── G4.1: Navigation Safety ──────────────────────────────────────────────

    fun requestScannerExit(onEmpty: () -> Unit) {
        val totalPages = _scannedPages.value.size + _batchPages.value.size
        if (totalPages == 0) {
            Log.d("ScannerVM", "[G4.1 Exit] Scanner state empty, auto-exiting")
            onEmpty()
        } else {
            Log.d("ScannerVM", "[G4.1 Exit] Requesting exit, showing dialog (Pages: $totalPages)")
            _showExitDialog.value = true
        }
    }

    fun dismissExitDialog() {
        Log.d("ScannerVM", "[G4.1 Exit] Exit dialog dismissed")
        _showExitDialog.value = false
    }

    fun confirmScannerExit(context: Context, onExit: () -> Unit) {
        Log.d("ScannerVM", "[G4.1 IntentionalExit] Exit confirmed, marking intentional exit active")
        _isIntentionalScannerExit.value = true

        viewModelScope.launch {
            Log.d("ScannerVM", "[G4.1 Cleanup] Canceling jobs and tearing down temporary session")
            processingJobs.values.forEach { it.cancel() }
            processingJobs.clear()
            previewJobs.values.forEach { it.cancel() }
            previewJobs.clear()
            
            clearSession(context)

            withContext(Dispatchers.Main) {
                Log.d("ScannerVM", "[G4.1 NavigationComplete] Navigating home and clearing stack")
                onExit()
                _showExitDialog.value = false
                _isIntentionalScannerExit.value = false
            }
        }
    }

    fun clearPages() {
        _scannedPages.value = emptyList()
        sessionDirty = false
        clearQueue()
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
    }

    fun setEditingPageIndex(index: Int) {
        _editingPageIndex.value = index
    }

    fun clearEditingPageId() {
        _editingPageId.value = ""
    }

    private fun updatePageState(pageId: String, newState: ScanPageState, inBatch: Boolean = false) {
        val listFlow = if (inBatch) _batchPages else _scannedPages
        val list = listFlow.value.toMutableList()
        val idx = list.indexOfFirst { it.id == pageId }
        if (idx != -1) {
            list[idx] = list[idx].copy(state = newState, lastModified = System.currentTimeMillis())
            listFlow.value = list
        }
    }

    // ── Page Management ─────────────────────────────────────────────────────

    fun addPage(context: Context) {
        val original = _currentImageUri.value ?: return
        val filter = _selectedFilter.value
        val rotation = _activeRotation.value

        val newPage = ScanPage(
            id = UUID.randomUUID().toString(),
            originalUri = original,
            transform = PageTransform(_activeQuad.value, filter, rotation, _filterStrength.value, _cropMode.value, false, 0L),
            previewCacheUri = null,
            thumbnailCacheUri = null,
            state = ScanPageState.FILTERED,  // Single-page mode: directly filtered
            isEdited = true,
            processingVersion = activeProcessingVersion,
            lastModified = System.currentTimeMillis(),
            isNew = true
        )

        val list = _scannedPages.value.toMutableList()
        list.add(newPage)
        _scannedPages.value = list

        sessionDirty = true
        saveSession()

        viewModelScope.launch {
            generateCachesSync(context, newPage.id)
        }
        resetTransientState()
    }

    fun replacePage(context: Context, index: Int) {
        val original = _currentImageUri.value ?: return
        val quad = _activeQuad.value
        val filter = _selectedFilter.value

        val list = _scannedPages.value.toMutableList()
        if (index in list.indices) {
            val oldPage = list[index]
            val rotation = _activeRotation.value

            oldPage.previewCacheUri?.path?.let { File(it).delete() }
            oldPage.thumbnailCacheUri?.path?.let { File(it).delete() }

            val newPage = oldPage.copy(
                transform = PageTransform(quad, filter, rotation, _filterStrength.value, _cropMode.value),
                previewCacheUri = null,
                thumbnailCacheUri = null,
                state = ScanPageState.FILTERED,  // Re-edit from review = stays FILTERED
                isEdited = true,
                processingVersion = activeProcessingVersion,
                lastModified = System.currentTimeMillis()
            )
            list[index] = newPage
            _scannedPages.value = list

            sessionDirty = true
            saveSession()

            viewModelScope.launch {
                generateCachesSync(context, newPage.id)
            }
            resetTransientState()
        }
    }

    fun removePage(context: Context, index: Int) {
        val list = _scannedPages.value.toMutableList()
        if (index in list.indices) {
            val pageId = list[index].id
            processingJobs[pageId]?.cancel()
            processingJobs.remove(pageId)
            
            list.removeAt(index)
            _scannedPages.value = list
            
            _activeBatch.value?.let { batch ->
                if (batch.pageIds.contains(pageId)) {
                    val newIds = batch.pageIds.filter { it != pageId }
                    _activeBatch.value = if (newIds.isEmpty()) null else batch.copy(pageIds = newIds)
                }
            }
            _batchPages.value = _batchPages.value.filter { it.id != pageId }
            
            sessionDirty = true
            saveSession()
            cleanupUnusedTempFiles(context)
        }
    }

    fun reorderPages(from: Int, to: Int) {
        val list = _scannedPages.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _scannedPages.value = list
            sessionDirty = true
            saveSession()
        }
    }

    // ── Editing Flow ────────────────────────────────────────────────────────

    fun prepareForEdit(index: Int) {
        val list = _scannedPages.value
        if (index in list.indices) {
            val page = list[index]
            processingJobs[page.id]?.cancel()
            
            _currentImageUri.value = page.originalUri
            _activeQuad.value = page.transform.cropQuad
            
            android.util.Log.e("CropTrace_LOAD", "[B] ScannerViewModel.prepareForEdit")
            android.util.Log.e("CropTrace_LOAD", "incoming pageId: ${page.id} (index: $index)")
            android.util.Log.e("CropTrace_LOAD", "cropQuad loaded: ${page.transform.cropQuad}")
            android.util.Log.e("CropTrace_LOAD", "activeQuad assigned: ${_activeQuad.value}")
            android.util.Log.e("CropTrace_LOAD", "rotation assigned: ${page.transform.rotation}")
            
            _selectedFilter.value = page.transform.filter
            _filterStrength.value = page.transform.filterStrength
            _activeRotation.value = page.transform.rotation
            _editingPageIndex.value = index
            _editingPageId.value = page.id
            activeProcessingVersion = page.processingVersion
            _imageReady.value = true
            Log.d("ScannerVM", "prepareForEdit: page $index (${page.id}), quad=${page.transform.cropQuad != null}, rotation=${page.transform.rotation}")
        }
    }

    fun prepareForFilterEdit(context: Context, index: Int) {
        val list = _scannedPages.value
        if (index in list.indices) {
            val page = list[index]
            _currentImageUri.value = page.originalUri
            _activeQuad.value = page.transform.cropQuad
            _selectedFilter.value = page.transform.filter
            _filterStrength.value = page.transform.filterStrength
            _activeRotation.value = page.transform.rotation
            _editingPageIndex.value = index
            _editingPageId.value = page.id
            activeProcessingVersion = page.processingVersion
            _imageReady.value = true
            Log.d("ScannerVM", "prepareForFilterEdit: page $index (${page.id}), rotation=${page.transform.rotation}")
            // Kick off preview generation immediately
            generateTempPreview(context)
        }
    }

    fun resetTransientState() {
        _currentImageUri.value = null
        _activeQuad.value = null
        _selectedFilter.value = ScanFilter.ENHANCED
        _filterStrength.value = 1.0f
        _activeRotation.value = 0
        _editingPageIndex.value = -1
        _editingPageId.value = ""
        _imageReady.value = false
        _tempEditPreviewUri.value = null
        activeProcessingVersion = System.currentTimeMillis()
    }

    // ── Batch Processing Queue ──────────────────────────────────────────────

    fun enqueueGalleryImages(
        context: Context, 
        uris: List<Uri>, 
        onProceedToCrop: (() -> Unit)? = null,
        onProceedToCropReview: (() -> Unit)? = null,
        onProceedToFilter: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isGalleryImporting.value = true
            try {
                val newPageIds = mutableListOf<String>()
                var failedCount = 0
                val batchStartTime = System.currentTimeMillis()
                
            withContext(Dispatchers.IO) {
                for ((uriIndex, uri) in uris.withIndex()) {
                    // G2: Fail-isolated — each image gets its own try-catch
                    try {
                        val imageStartTime = System.currentTimeMillis()
                        val tempFile = createCaptureOutputFile(context)
                        
                        // ── MIME-aware decode ─────────────────────────────────
                        val mimeType = context.contentResolver.getType(uri)
                        Log.d("ScannerVM", "[BatchImportTrace] Import start ${uriIndex + 1}/${uris.size}: thread=${Thread.currentThread().name}")
                        Log.d("ScannerVM", "[G2 Queue] Import ${uriIndex + 1}/${uris.size}: uri=$uri, mime=$mimeType")
                    
                    // Reject unsupported MIME types early
                    val supportedMimes = setOf(
                        "image/jpeg", "image/jpg", "image/png", "image/webp",
                        "image/heic", "image/heif", "image/gif",
                        "image/bmp", "image/x-ms-bmp"
                    )
                    if (mimeType != null && !supportedMimes.contains(mimeType.lowercase())) {
                        Log.e("ScannerVM", "[G2 Queue] Unsupported MIME type: $mimeType for $uri")
                        failedCount++
                        tempFile.delete()
                        continue
                    }
                    
                    // ── Step 1: Decode bounds to calculate safe sample size ──
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, boundsOpts)
                    }
                    
                    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                        Log.e("ScannerVM", "[G2 Queue] Image ${uriIndex + 1}/${uris.size}: could not decode bounds, skipping: $uri")
                        failedCount++
                        tempFile.delete()
                        continue
                    }
                    
                    // Memory-safe: downsample if image is massive (>16 megapixels)
                    val megapixels = (boundsOpts.outWidth.toLong() * boundsOpts.outHeight.toLong()) / 1_000_000
                    val sampleSize = when {
                        megapixels > 32 -> 4
                        megapixels > 16 -> 2
                        else -> 1
                    }
                    
                    // ── Step 2: Decode actual bitmap with safe config ─────
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    
                    val decodedBitmapRaw: Bitmap? = withTimeoutOrNull(BITMAP_DECODE_TIMEOUT_MS) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input, null, decodeOpts)
                        }
                    }
                    
                    if (decodedBitmapRaw == null) {
                        Log.e("ScannerVM", "[G2 Queue] Image ${uriIndex + 1}/${uris.size}: bitmap decode failed or timed out: $uri")
                        failedCount++
                        tempFile.delete()
                        continue
                    }
                    
                    // From here, bitmap is guaranteed non-null
                    var decodedBitmap: Bitmap = decodedBitmapRaw
                    
                    // ── Step 3: Flatten alpha onto white background ───────
                    // PNG/WEBP/GIF can have transparency — JPEG pipeline requires opaque bitmaps
                    if (decodedBitmap.hasAlpha()) {
                        Log.d("ScannerVM", "[G2 Queue] Image has alpha channel, flattening onto white background")
                        val opaqueBitmap = Bitmap.createBitmap(
                            decodedBitmap.width, decodedBitmap.height, Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(opaqueBitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        canvas.drawBitmap(decodedBitmap, 0f, 0f, null)
                        decodedBitmap.recycle()
                        decodedBitmap = opaqueBitmap
                    }
                    
                    // ── Step 4: Handle EXIF rotation from source ──────────
                    try {
                        context.contentResolver.openInputStream(uri)?.use { exifInput ->
                            val exif = androidx.exifinterface.media.ExifInterface(exifInput)
                            val orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
                            val rotationDegrees = when (orientation) {
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }
                            if (rotationDegrees != 0f && !decodedBitmap.isRecycled) {
                                val matrix = android.graphics.Matrix()
                                matrix.postRotate(rotationDegrees)
                                val rotated = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
                                if (rotated !== decodedBitmap) decodedBitmap.recycle()
                                decodedBitmap = rotated
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("ScannerVM", "[G2 Queue] EXIF read failed (non-critical), continuing: ${e.message}")
                    }
                    
                    // ── Step 5: Compress as JPEG to temp file ─────────────
                    val compressResult = withTimeoutOrNull(BITMAP_DECODE_TIMEOUT_MS) {
                        java.io.FileOutputStream(tempFile).use { out ->
                            decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            out.flush()
                            out.fd.sync()
                        }
                    }
                    // Run edge detection so manual crop doesn't freeze
                    val edgeResult = com.stutool.stutool.ui.screens.scanner.utils.EdgeDetection.detectWithConfidence(decodedBitmap)
                    val detectionConfidence = edgeResult.confidence
                    val quad = if (detectionConfidence > 0.8f) edgeResult.quad else com.stutool.stutool.ui.screens.scanner.utils.Quad.defaultForImage(decodedBitmap.width, decodedBitmap.height)
                    
                    decodedBitmap.recycle()
                    
                    // ── Step 6: Validate output file ─────────────────────
                    if (compressResult == null || !tempFile.exists() || tempFile.length() < 100) {
                        Log.e("ScannerVM", "[G2 Queue] Image ${uriIndex + 1}/${uris.size} JPEG write failed or produced empty file: $uri")
                        failedCount++
                        tempFile.delete()
                        continue
                    }
                    
                    // Validate the JPEG is decodable
                    val validateOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(tempFile.absolutePath, validateOpts)
                    if (validateOpts.outWidth <= 0 || validateOpts.outHeight <= 0) {
                        Log.e("ScannerVM", "[G2 Queue] Image ${uriIndex + 1}/${uris.size} output JPEG validation failed: $uri")
                        failedCount++
                        tempFile.delete()
                        continue
                    }

                    val pageId = java.util.UUID.randomUUID().toString()
                    val newPage = ScanPage(
                        id = pageId,
                        originalUri = android.net.Uri.fromFile(tempFile),
                        transform = PageTransform(quad, com.stutool.stutool.ui.screens.scanner.utils.ScanFilter.ORIGINAL, 0, detectionConfidence),
                        previewCacheUri = null,
                        thumbnailCacheUri = null,
                        state = com.stutool.stutool.ui.screens.scanner.models.ScanPageState.IDLE,
                        isEdited = false,
                        processingVersion = System.currentTimeMillis(),
                        lastModified = System.currentTimeMillis()
                    )
                    
                    withContext(Dispatchers.Main) {
                        _scannedPages.value = _scannedPages.value + newPage
                        sessionDirty = true
                        newPageIds.add(pageId)
                    }
                    val imageDuration = System.currentTimeMillis() - imageStartTime
                    Log.d("ScannerVM", "[BatchImportTrace] Import completion ${uriIndex + 1}/${uris.size}: duration=${imageDuration}ms")
                } catch (e: Exception) {
                    Log.e("ScannerVM", "[G2 Queue] Gallery import FAILED: $uri", e)
                    failedCount++
                }
            }
            }
            
            val totalDuration = System.currentTimeMillis() - batchStartTime
            Log.d("ScannerVM", "[BatchImportTrace] Batch completion: total duration=${totalDuration}ms for ${uris.size} images")
            
            withContext(Dispatchers.Main) {
                if (newPageIds.isNotEmpty()) {
                    if (isQueueActive) {
                        val currentBatch = _activeBatch.value!!
                        _activeBatch.value = currentBatch.copy(pageIds = currentBatch.pageIds + newPageIds)
                        val idlePages = _scannedPages.value.filter { it.id in newPageIds }
                        _batchPages.value = _batchPages.value + idlePages
                        _scannedPages.value = _scannedPages.value.filterNot { it.id in newPageIds }
                        saveSession()
                        Log.d("ScannerVM", "[G3 Batch] Appended ${newPageIds.size} pages to active batch.")
                        
                        // Force navigation to the active queue screen if they appended from camera
                        if (currentBatch.stage == QueueStage.CROP) {
                            onProceedToCrop?.invoke()
                        } else if (currentBatch.stage == QueueStage.FILTER) {
                            onProceedToFilter?.invoke()
                        }
                    } else {
                        startBatchCropQueue(context, BatchSource.GALLERY, onProceedToCrop, onProceedToCropReview, onProceedToFilter)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Could not import images. Supported formats: JPG, PNG, WEBP, HEIC.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } finally {
            _isGalleryImporting.value = false
        }
    }
}

    fun startBatchCropQueue(
        context: Context, 
        source: BatchSource = BatchSource.UNKNOWN,
        onProceedToCrop: (() -> Unit)? = null,
        onProceedToCropReview: (() -> Unit)? = null,
        onProceedToFilter: (() -> Unit)? = null
    ) {
        val rawPages = _scannedPages.value.filter { it.state == ScanPageState.IDLE }
        val count = rawPages.size
        
        if (count == 0) return
        
        if (count < 5) {
            commitBatchAndProceedToCrop(context, rawPages, source)
            onProceedToCrop?.invoke()
        } else {
            pendingBatchSource = source
            pendingCropCallback = onProceedToCrop
            pendingCropReviewCallback = onProceedToCropReview
            pendingFilterCallback = onProceedToFilter
            _showCropDecisionSheet.value = true
        }
    }

    private fun commitBatchAndProceedToCrop(context: Context, rawPages: List<ScanPage>, source: BatchSource) {
        val rawPageIds = rawPages.map { it.id }
        
        val batchId = java.util.UUID.randomUUID().toString()
        _activeBatch.value = ProcessingBatch(batchId, rawPageIds, QueueStage.CROP, source)
        _batchPages.value = rawPages
        
        // Isolate: remove from finalized pool
        _scannedPages.value = _scannedPages.value.filter { it.state != ScanPageState.IDLE }
        
        _currentQueueIndex.value = 0
        prepareCurrentQueuePage(context)
        saveSession()
        Log.d("ScannerVM", "[G3 Batch] Created isolated batch $batchId with ${rawPageIds.size} pages")
    }

    /**
     * Delete the currently displayed page from the crop queue.
     *
     * Safety contract:
     * - Cancels all preview/thumbnail/filter jobs for the deleted pageId
     * - Removes page from batchPages AND activeBatch.pageIds
     * - Recomputes currentQueueIndex safely (clamp to valid range)
     * - Does NOT touch remaining pages' crop/filter/preview state
     * - Does NOT trigger session restoration or preview invalidation storms
     * - If last page deleted: clears batch, resets transient state, invokes onEmpty
     * - Otherwise: prepares next page and invokes onNavigate
     */
    fun deleteCurrentCropPage(context: Context, onNavigate: () -> Unit, onEmpty: () -> Unit) {
        val batch = _activeBatch.value ?: return
        val ids = batch.pageIds
        val idx = _currentQueueIndex.value
        if (idx < 0 || idx >= ids.size) return

        val pageId = ids[idx]
        Log.d("ScannerVM", "[CropDelete] Deleting page $pageId at index $idx/${ids.size}")

        // 1. Cancel ALL jobs tied to this pageId
        processingJobs[pageId]?.cancel()
        processingJobs.remove(pageId)
        previewJobs[pageId]?.cancel()
        previewJobs.remove(pageId)

        // 2. Remove from batchPages list, cleaning up cache files
        val batchList = _batchPages.value.toMutableList()
        val batchIdx = batchList.indexOfFirst { it.id == pageId }
        if (batchIdx != -1) {
            val page = batchList[batchIdx]
            page.croppedCacheUri?.path?.let { File(it).delete() }
            page.previewCacheUri?.path?.let { File(it).delete() }
            page.thumbnailCacheUri?.path?.let { File(it).delete() }
            batchList.removeAt(batchIdx)
            _batchPages.value = batchList
        }

        // 3. Remove from activeBatch.pageIds
        val newIds = ids.toMutableList()
        newIds.removeAt(idx)

        if (newIds.isEmpty()) {
            // Last page deleted — tear down batch entirely
            Log.d("ScannerVM", "[CropDelete] Last page deleted, clearing batch")
            _activeBatch.value = null
            _batchPages.value = emptyList()
            _currentQueueIndex.value = 0
            resetTransientState()
            sessionDirty = true
            saveSession()
            cleanupUnusedTempFiles(context)
            onEmpty()
            return
        }

        // 4. Update batch with reduced page list
        _activeBatch.value = batch.copy(pageIds = newIds)

        // 5. Recompute safe queue index: stay at same position, or clamp to last if we deleted the tail
        val newIdx = idx.coerceIn(0, newIds.size - 1)
        _currentQueueIndex.value = newIdx

        // 6. Persist immediately
        sessionDirty = true
        saveSession()

        // 7. Load the page now at the new index
        prepareCurrentQueuePage(context)

        Log.d("ScannerVM", "[CropDelete] Deletion complete. Now at index $newIdx/${newIds.size}")
        onNavigate()
    }

    /**
     * Load only the currently active queue page's data into transient state.
     * LAZY: Decodes only this one page. No bulk preloading.
     */
    fun prepareCurrentQueuePage(context: Context) {
        val batch = _activeBatch.value ?: return
        val ids = batch.pageIds
        val idx = _currentQueueIndex.value
        if (idx < 0 || idx >= ids.size) return
        
        val pageId = ids[idx]
        val pageIndex = _batchPages.value.indexOfFirst { it.id == pageId }
        if (pageIndex == -1) {
            Log.e("ScannerVM", "[G3 Batch] prepareCurrentQueuePage: page $pageId not found in batch, skipping")
            advanceQueue(context) { /* Handle navigation externally or ignore if recovering */ }
            return
        }
        
        val page = _batchPages.value[pageIndex]
        _currentImageUri.value = page.originalUri
        _activeQuad.value = page.transform.cropQuad
        _selectedFilter.value = page.transform.filter
        _filterStrength.value = page.transform.filterStrength
        _activeRotation.value = page.transform.rotation
        _cropMode.value = page.transform.cropMode
        _editingPageIndex.value = pageIndex
        activeProcessingVersion = page.processingVersion
        _imageReady.value = false
        _tempEditPreviewUri.value = null
        
        // Trigger lazy image loading (EXIF correction etc.) without resetting transient state
        loadOriginalImageForQueue(context, page.originalUri)
        
        Log.d("ScannerVM", "[G3 Batch] prepareQueuePage: index=$idx/${ids.size}, pageId=$pageId, stage=${batch.stage}")
    }

    private fun loadOriginalImageForQueue(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val filePath = getFilePathFromUri(context, uri)
            if (filePath != null) {
                // EXIF rotation correction — only relevant for camera captures.
                // Gallery imports are pre-normalized to JPEG with correct orientation
                // in enqueueGalleryImages(), so this is a safety net only.
                try {
                    val file = File(filePath)
                    if (file.exists() && file.length() > 0) {
                        val exif = ExifInterface(filePath)
                        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                            val bitmap = withTimeoutOrNull(BITMAP_DECODE_TIMEOUT_MS) {
                                BitmapFactory.decodeFile(filePath)
                            }
                            val matrix = Matrix()
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                            if (bitmap != null) {
                                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                FileOutputStream(filePath).use { out ->
                                    rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                }
                                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                                exif.saveAttributes()
                                bitmap.recycle()
                                if (rotated !== bitmap) rotated.recycle()
                            } else {
                                Log.e("ScannerVM", "[G2 Crop] EXIF bitmap decode timed out for $uri")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // EXIF correction is non-critical — if it fails, we still proceed
                    Log.w("ScannerVM", "[G2 Crop] EXIF correction skipped for $uri: ${e.message}")
                }
                
                // G2: Populate active image dimensions for CropTransform
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(filePath, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        withContext(Dispatchers.Main) {
                            _activeImageWidth.value = opts.outWidth
                            _activeImageHeight.value = opts.outHeight
                        }
                        Log.d("ScannerVM", "[G2 Crop] Image dimensions: ${opts.outWidth}x${opts.outHeight}")
                    } else {
                        Log.e("ScannerVM", "[G2 Crop] Failed to read image dimensions from $filePath")
                    }
                } catch (e: Exception) {
                    Log.e("ScannerVM", "[G2 Crop] Dimension read failed for $filePath", e)
                }
            } else {
                Log.e("ScannerVM", "[G2 Crop] Could not resolve file path from URI: $uri")
            }
            withContext(Dispatchers.Main) {
                _imageReady.value = true
            }
        }
    }

    /**
     * G1: Strict sequential queue advancement.
     *
     * CROP stage:
     *   1. Save crop edits → mark page CROPPED immediately
     *   2. Generate caches (thumbnail/preview)
     *   3. Advance index or transition to FILTER stage
     *
     * FILTER stage:
     *   1. Snapshot filter + strength immutably
     *   2. If applyFilterToAll: propagate snapshot to remaining NON-LOCKED pages
     *   3. Generate caches
     *   4. ONLY after caches succeed: mark page FILTERED
     *   5. Advance index or navigate to document_review
     *
     * Navigation to document_review only fires after the FINAL filter page succeeds.
     */
    fun advanceQueue(context: Context, onNavigate: (String) -> Unit) {
        if (!queueAdvanceLock.compareAndSet(false, true)) {
            Log.w("ScannerVM", "[G3 Queue] advanceQueue blocked — transaction already in progress")
            return
        }
        
        val batch = _activeBatch.value
        if (batch == null) {
            queueAdvanceLock.set(false)
            return
        }
        
        val ids = batch.pageIds
        val idx = _currentQueueIndex.value
        val stage = batch.stage

        processingJobs.values.forEach { it.cancel() }
        processingJobs.clear()

        if (idx !in ids.indices) {
            Log.e("ScannerVM", "[G3 Queue] advanceQueue: idx=$idx out of bounds (size=${ids.size}), aborting")
            queueAdvanceLock.set(false)
            return
        }
        val pageId = ids[idx]

        if (stage == QueueStage.CROP) {
            // ── CROP STAGE ─────────────────────────────────────────────────
            try {
                saveCurrentQueuePageEdits()
                updatePageState(pageId, ScanPageState.CROPPED, inBatch = true)
                // G3.2.1 CHECKPOINT: Persist crop transform immediately before slow cache generation
                Log.d("ScannerVM", "[G3.2.1 CHECKPOINT] Crop persisted for page $pageId")
                saveSession()
                Log.d("ScannerVM", "[G3 Queue] Crop committed: page=$pageId (${idx + 1}/${ids.size})")
                logG3State("advanceQueue crop complete")
            } catch (e: Exception) {
                Log.e("ScannerVM", "[G3 Queue] Failed to save crop edits for page $pageId, skipping", e)
                // Don't freeze — skip this page and advance
            }

            viewModelScope.launch {
                try {
                    // G3: Bake crop cache before generating thumbnails
                    generateCroppedBitmapCache(context, pageId, batch.batchId)
                    generateCachesSync(context, pageId, batch.batchId)
                } catch (e: Exception) {
                    Log.e("ScannerVM", "[G3 Queue] Cache generation failed for page $pageId, continuing", e)
                }

                val nextIdx = idx + 1
                if (nextIdx < ids.size) {
                    _currentQueueIndex.value = nextIdx
                    prepareCurrentQueuePage(context)
                    saveSession()
                    Log.d("ScannerVM", "[G3 Queue] Crop queue advance → page ${nextIdx + 1}/${ids.size}")
                    queueAdvanceLock.set(false)
                    onNavigate("edge_crop")
                } else {
                    val batchSize = ids.size
                    Log.d("ScannerVM", "[G3.1] Crop complete for all $batchSize page(s). → Entering FILTER stage validation")
                    queueAdvanceLock.set(false) // Release lock so validate can run safely
                    validateBatchForFilterStage(context, batch, onNavigate)
                }
            }
        } else if (stage == QueueStage.FILTER) {
            Log.d("ScannerVM", "[G3.1] Filter stage complete (${ids.size} page(s)). → Finalizing and navigating to document_review")
            finalizeProcessedPages(
                onSuccess = {
                    queueAdvanceLock.set(false)
                    Log.d("ScannerVM", "[G3.1] Finalize success. Navigating to document_review")
                    onNavigate("document_review")
                },
                onError = {
                    queueAdvanceLock.set(false)
                    Log.e("ScannerVM", "[G3.1] Finalize FAILED — documentPages empty after filter")
                    // Let UI handle empty state
                }
            )
        } else {
            queueAdvanceLock.set(false)
        }
    }

    private fun validateBatchForFilterStage(context: Context, batch: ProcessingBatch, onNavigate: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val pages = _batchPages.value
            var allValid = true
            
            for (pageId in batch.pageIds) {
                val page = pages.find { it.id == pageId }
                if (page == null) {
                    allValid = false
                    break
                }
                
                val cacheUri = page.croppedCacheUri
                val isValidCache = if (cacheUri != null) {
                    val path = cacheUri.path
                    if (path != null && File(path).exists() && File(path).length() > 0) {
                        try {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(path, opts)
                            opts.outWidth > 0 && opts.outHeight > 0
                        } catch (e: Exception) {
                            false
                        }
                    } else false
                } else false
                
                if (!isValidCache) {
                    Log.e("ScannerVM", "[G3 Batch Validation] Page $pageId missing valid cropped cache. Regenerating.")
                    try {
                        generateCroppedBitmapCache(context, pageId, batch.batchId)
                        generateCachesSync(context, pageId, batch.batchId)
                    } catch (e: Exception) {
                        Log.e("ScannerVM", "[G3 Batch Validation] Regeneration failed for page $pageId", e)
                        allValid = false
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                if (allValid) {
                    _activeBatch.value = batch.copy(stage = QueueStage.FILTER)
                    _currentQueueIndex.value = 0
                    saveSession()
                    Log.d("ScannerVM", "[G3 Batch] Validation passed. Entering FILTER stage.")
                    logG3State("validateBatchForFilterStage pass")
                    onNavigate("batch_filter_pager")
                } else {
                    Log.e("ScannerVM", "[G3 Batch] Validation FAILED. Corrupt batch.")
                    logG3State("validateBatchForFilterStage fail")
                    // Clear batch or show error
                    _hasRecoveredSession.value = false // trigger UI refresh maybe
                }
            }
        }
    }

    /**
     * G1: Start a new sub-queue for newly added pages ONLY.
     * Existing FILTERED pages are never re-queued or touched.
     * New pages are appended after all finalized pages in review order.
     */
    fun startAddMoreQueue(
        context: Context, 
        onProceedToCrop: (() -> Unit)? = null, 
        onProceedToCropReview: (() -> Unit)? = null,
        onProceedToFilter: (() -> Unit)? = null
    ) {
        startBatchCropQueue(context, BatchSource.UNKNOWN, onProceedToCrop, onProceedToCropReview, onProceedToFilter)
        Log.d("ScannerVM", "[G3 Batch] Add-more sub-queue started via standard batch isolation.")
    }

    /**
     * G1: Save current edits for the active queue page without advancing.
     * Does NOT change ScanPageState — state transitions happen in advanceQueue only.
     */
    fun saveCurrentQueuePageEdits() {
        val batch = _activeBatch.value ?: return
        val ids = batch.pageIds
        val idx = _currentQueueIndex.value
        if (idx < 0 || idx >= ids.size) return

        val pageId = ids[idx]
        val list = _batchPages.value.toMutableList()
        val pageIdx = list.indexOfFirst { it.id == pageId }
        if (pageIdx == -1) {
            Log.e("ScannerVM", "[G3 Crop] saveCurrentQueuePageEdits: page $pageId not found in batch")
            return
        }

        val oldPage = list[pageIdx]
        val isCropStage = batch.stage == QueueStage.CROP
        val currentQuad = _activeQuad.value
        val currentRotation = _activeRotation.value
        val imgW = _activeImageWidth.value
        val imgH = _activeImageHeight.value

        // Invalidate old caches since transform is changing
        oldPage.previewCacheUri?.path?.let { File(it).delete() }
        oldPage.thumbnailCacheUri?.path?.let { File(it).delete() }

        // G2: Build unified CropTransform if we have valid dimensions and quad
        val cropTransform = if (currentQuad != null && imgW > 0 && imgH > 0) {
            CropTransform(
                quad = currentQuad,
                imageWidth = imgW,
                imageHeight = imgH,
                rotationDegrees = currentRotation
            )
        } else {
            Log.w("ScannerVM", "[G2 Crop] Cannot build CropTransform: quad=${currentQuad != null}, dims=${imgW}x${imgH}")
            null
        }

        list[pageIdx] = oldPage.copy(
            transform = PageTransform(
                cropQuad = currentQuad,
                filter = _selectedFilter.value,
                rotation = currentRotation,
                filterStrength = _filterStrength.value,
                cropMode = _cropMode.value,
                hasFilterOverride = oldPage.transform.hasFilterOverride,
                cropTransform = cropTransform
            ),
            // We do NOT clear previewCacheUri/thumbnailCacheUri here anymore because they rely on croppedCacheUri now.
            // But we will invalidate croppedCacheUri when crop/rotation actually changes in `generateCroppedBitmapCache`.
            state = oldPage.state,
            isEdited = true,
            processingVersion = if (isCropStage) System.currentTimeMillis() else activeProcessingVersion,
            lastModified = System.currentTimeMillis()
        )
        _batchPages.value = list
        sessionDirty = true
        Log.d("ScannerVM", "[G3 Crop] saveCurrentQueuePageEdits: page=$pageId stage=${batch.stage} hasCropTransform=${cropTransform != null}")
    }

    /**
     * G4: Transactional merge of active batch into finalized document pages.
     */
    fun finalizeProcessedPages(onSuccess: () -> Unit, onError: () -> Unit) {
        val batch = _activeBatch.value
        val filterPages = _batchPages.value
        val currentDocumentPages = _scannedPages.value
        
        Log.d("ScannerVM", "[G4] finalizeProcessedPages | beforeCommit | filterPages=${filterPages.size} | documentPages=${currentDocumentPages.size}")
        
        if (filterPages.isNotEmpty()) {
            // Snapshot and force state to FILTERED
            val finalized = filterPages.map { it.copy(state = ScanPageState.FILTERED) }
            _scannedPages.value = currentDocumentPages + finalized
            Log.d("ScannerVM", "[G4] afterCommit | documentPages=${_scannedPages.value.size}")
        } else {
            Log.w("ScannerVM", "[G4] finalizeProcessedPages | filterPages was empty!")
        }
        
        if (_scannedPages.value.isNotEmpty()) {
            // Commit to transient state first
            clearQueue()
            
            // Save the finalized state to disk
            saveSession()
            
            Log.d("ScannerVM", "[G4] afterCleanup | tempQueueCleared | documentPages=${_scannedPages.value.size}")
            onSuccess()
        } else {
            Log.e("ScannerVM", "[G4] beforeNavigation | Validation failed. documentPages is empty!")
            onError()
        }
    }

    /** Legacy method maintained for other calls if any, but forwards to finalizeProcessedPages */
    fun completeBatch() {
        finalizeProcessedPages(onSuccess = {}, onError = {})
    }

    fun clearQueue() {
        _activeBatch.value = null
        _batchPages.value = emptyList()
        _currentQueueIndex.value = -1
    }

    // ── Rotation ────────────────────────────────────────────────────────────

    /**
     * Rotate the active page 90° clockwise or counter-clockwise.
     * Remaps the active quad corners to match the rotated coordinate space.
     */
    fun rotateActivePage(clockwise: Boolean) {
        val delta = if (clockwise) 90 else -90
        val newRotation = ((_activeRotation.value + delta) % 360 + 360) % 360
        _activeRotation.value = newRotation
        activeProcessingVersion = System.currentTimeMillis()
        Log.d("ScannerVM", "rotateActivePage: ${if (clockwise) "CW" else "CCW"} → $newRotation°")
        
        // Note: Quad remapping is NOT needed here because:
        // - The quad is defined in the ORIGINAL image coordinate space
        // - Rotation is applied as a separate transform step during export/preview
        // - The display layer handles showing the rotated image with the quad overlay
    }

    // ── Image Processing & Cache ────────────────────────────────────────────

    fun setImageUri(context: Context, uri: Uri?) {
        _currentImageUri.value = uri
        _activeQuad.value = null
        _selectedFilter.value = ScanFilter.ENHANCED
        activeProcessingVersion = System.currentTimeMillis()
        hasManuallySelectedFilter = false
        _imageReady.value = false
        _tempEditPreviewUri.value = null
        
        if (uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // Perform any EXIF correction needed here
                val filePath = getFilePathFromUri(context, uri)
                if (filePath != null) {
                    try {
                        val exif = ExifInterface(filePath)
                        val rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        if (rotation != ExifInterface.ORIENTATION_NORMAL) {
                            val bitmap = BitmapFactory.decodeFile(filePath)
                            val matrix = Matrix()
                            when (rotation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            FileOutputStream(filePath).use { out ->
                                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                            exif.saveAttributes()
                            bitmap.recycle()
                            if (rotated !== bitmap) rotated.recycle()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    _imageReady.value = true
                }
            }
        }
    }

    fun enqueueCapture(context: Context, file: File, rotationDegrees: Int): Boolean {
        if (_pendingScansCount.value >= 5) return false // Queue safety

        _pendingScansCount.value += 1
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // DIAGNOSTIC LOG (Requested by User)
                val diagExif = ExifInterface(file.absolutePath)
                val diagOrientation = diagExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                Log.d("ScannerVM", "[DIAGNOSTIC] enqueueCapture: captureRotation=$rotationDegrees, fileExifOrientation=$diagOrientation")

                // Correct EXIF using the robust helper (handles EXIF and dimension mismatch)
                correctImageRotation(file, rotationDegrees)

                val uri = Uri.fromFile(file)
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                var detectionConfidence = 0f
                val quad = if (bitmap != null) {
                    val bmpW = bitmap.width
                    val bmpH = bitmap.height
                    val result = com.stutool.stutool.ui.screens.scanner.utils.EdgeDetection.detectWithConfidence(bitmap)
                    bitmap.recycle()
                    detectionConfidence = result.confidence
                    if (result.confidence > 0.8f) result.quad else com.stutool.stutool.ui.screens.scanner.utils.Quad.defaultForImage(bmpW, bmpH)
                } else null

                val newPage = ScanPage(
                    id = UUID.randomUUID().toString(),
                    originalUri = uri,
                    transform = PageTransform(cropQuad = quad, filter = ScanFilter.ENHANCED, rotation = 0, edgeConfidence = detectionConfidence),
                    previewCacheUri = null,
                    thumbnailCacheUri = null,
                    state = ScanPageState.IDLE,
                    isEdited = false,
                    processingVersion = System.currentTimeMillis(),
                    lastModified = System.currentTimeMillis(),
                    isNew = true
                )

                withContext(Dispatchers.Main) {
                    _scannedPages.value = _scannedPages.value + newPage
                    sessionDirty = true
                    saveSession()
                    viewModelScope.launch {
                        generateCachesSync(context, newPage.id)
                    }
                    _pendingScansCount.value -= 1
                    Log.d("ScannerVM", "enqueueCapture: added page ${newPage.id}, total=${_scannedPages.value.size}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _pendingScansCount.value -= 1
                }
            }
        }
        return true
    }

    fun setActiveQuad(quad: Quad?) {
        _activeQuad.value = quad
        activeProcessingVersion = System.currentTimeMillis()
    }

    fun resetActivePageTransformToFull(context: Context) {
        val uri = _currentImageUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val filePath = getFilePathFromUri(context, uri) ?: return@launch
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                // Full bounds use original (unrotated) image dimensions
                // The rotation is applied separately during rendering/export
                val quad = Quad.defaultForImage(opts.outWidth, opts.outHeight)
                Log.d("ScannerVM", "resetToFull: ${opts.outWidth}x${opts.outHeight}, rotation=${_activeRotation.value}, mode=${_cropMode.value}")
                withContext(Dispatchers.Main) {
                    _activeQuad.value = quad
                    activeProcessingVersion = System.currentTimeMillis()
                }
            }
        }
    }

    fun setApplyFilterToAll(apply: Boolean) {
        _applyFilterToAll.value = apply
    }

    fun setCropMode(mode: CropMode) {
        if (_cropMode.value == mode) return
        _cropMode.value = mode
        activeProcessingVersion = System.currentTimeMillis()
        
        val quad = _activeQuad.value ?: return
        if (mode == CropMode.RECTANGULAR) {
            // Perspective -> Rectangular: Set rectangular bounds to the bounding box
            val minX = listOf(quad.topLeft.x, quad.topRight.x, quad.bottomLeft.x, quad.bottomRight.x).minOrNull() ?: 0f
            val maxX = listOf(quad.topLeft.x, quad.topRight.x, quad.bottomLeft.x, quad.bottomRight.x).maxOrNull() ?: 100f
            val minY = listOf(quad.topLeft.y, quad.topRight.y, quad.bottomLeft.y, quad.bottomRight.y).minOrNull() ?: 0f
            val maxY = listOf(quad.topLeft.y, quad.topRight.y, quad.bottomLeft.y, quad.bottomRight.y).maxOrNull() ?: 100f
            
            _activeQuad.value = Quad(
                topLeft = android.graphics.PointF(minX, minY),
                topRight = android.graphics.PointF(maxX, minY),
                bottomRight = android.graphics.PointF(maxX, maxY),
                bottomLeft = android.graphics.PointF(minX, maxY)
            )
            Log.d("ScannerVM", "setCropMode: Converted quad to bounding box")
        }
    }

    /**
     * Expands the crop quad to the maximally safe bounds provided by the UI.
     * Prevents over-expanding if the quad is already at full bounds.
     */
    fun expandCrop(safeQuad: Quad) {
        val currentQuad = _activeQuad.value ?: return
        // Tolerance for checking if already expanded
        val tol = 5f
        if (kotlin.math.abs(currentQuad.topLeft.x - safeQuad.topLeft.x) < tol &&
            kotlin.math.abs(currentQuad.topLeft.y - safeQuad.topLeft.y) < tol &&
            kotlin.math.abs(currentQuad.bottomRight.x - safeQuad.bottomRight.x) < tol &&
            kotlin.math.abs(currentQuad.bottomRight.y - safeQuad.bottomRight.y) < tol
        ) {
            return // Already expanded, prevent snapping
        }
        
        _activeQuad.value = safeQuad
        activeProcessingVersion = System.currentTimeMillis()
        Log.d("ScannerVM", "expandCrop: Snapped to safe bounds")
    }

    // Batch Filter Application (Debounced & Snapshot-based)
    private var batchFilterJob: Job? = null

    fun applyFilterToBatch(context: Context, filter: ScanFilter, strength: Float) {
        if (!isQueueActive) return
        
        _selectedFilter.value = filter
        _filterStrength.value = strength
        activeProcessingVersion = System.currentTimeMillis()
        
        batchFilterJob?.cancel()
        batchFilterJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(120) // 120ms debounce

            // 1. Snapshot values
            val snapshotFilter = _selectedFilter.value
            val snapshotStrength = _filterStrength.value
            val token = activeProcessingVersion
            
            // 2. Propagate
            val batch = _activeBatch.value ?: return@launch
            val currentBatchId = batch.batchId
            val list = _batchPages.value.toMutableList()
            var propagatedCount = 0
            val idsToUpdate = mutableListOf<String>()
            
            for (pageId in batch.pageIds) {
                val idx = list.indexOfFirst { it.id == pageId }
                if (idx != -1) {
                    val page = list[idx]
                    if (!page.transform.hasFilterOverride) {
                        list[idx] = page.copy(
                            transform = page.transform.copy(
                                filter = snapshotFilter,
                                filterStrength = snapshotStrength,
                                processingToken = token
                            ),
                            processingVersion = token,
                            isEdited = true,
                            lastModified = System.currentTimeMillis(),
                            state = ScanPageState.FILTERED // Batch filter directly moves pages to FILTERED
                        )
                        propagatedCount++
                        idsToUpdate.add(page.id)
                    } else if (page.state == ScanPageState.CROPPED) {
                        // Even if overridden, ensure its state is advanced to FILTERED during batch mode if it was just sitting
                        list[idx] = page.copy(state = ScanPageState.FILTERED)
                    }
                }
            }
            
            if (propagatedCount > 0) {
                withContext(Dispatchers.Main) {
                    if (_activeBatch.value?.batchId != currentBatchId) {
                        Log.w("ScannerVM", "[G3 Batch] applyFilterToBatch: batch ownership failed, discarding.")
                        return@withContext
                    }
                    _batchPages.value = list
                    sessionDirty = true
                    saveSession()
                }
                Log.d("ScannerVM", "[G3 Batch] Propagated $snapshotFilter (str=$snapshotStrength) to $propagatedCount pages")
                
                // 3. Generate Thumbnails in background
                generateBatchCachesSequential(context, idsToUpdate, token, currentBatchId)
            }
        }
    }

    fun setSinglePageFilterOverride(context: Context, pageId: String, filter: ScanFilter, strength: Float) {
        val list = _scannedPages.value.toMutableList()
        val idx = list.indexOfFirst { it.id == pageId }
        if (idx == -1) return
        
        val token = System.currentTimeMillis()
        val page = list[idx]
        
        list[idx] = page.copy(
            transform = page.transform.copy(
                filter = filter,
                filterStrength = strength,
                hasFilterOverride = true,
                processingToken = token
            ),
            processingVersion = token,
            isEdited = true,
            lastModified = System.currentTimeMillis(),
            state = ScanPageState.FILTERED
        )
        
        _scannedPages.value = list
        sessionDirty = true
        Log.d("ScannerVM", "[G1 Batch] Applied override to $pageId: $filter (str=$strength)")
        
        viewModelScope.launch {
            generateCachesSync(context, pageId)
        }
    }
    
    // (generateBatchThumbnails removed as part of stability revert)

    // ── Batch Filter Pager Functions ─────────────────────────────────────────────

    /** Called by HorizontalPager when the user swipes to a new page. */
    fun setFilterPageIndex(index: Int) {
        val ids = _activeBatch.value?.pageIds ?: emptyList()
        val safeIdx = index.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        if (_currentFilterPageIndex.value != safeIdx) {
            _currentFilterPageIndex.value = safeIdx
            // Clear live preview from previous page so it doesn't flash
            val oldBitmap = _livePreviewBitmap.value
            _livePreviewBitmap.value = null
            oldBitmap?.recycle()
            // Cancel any pending live preview jobs for the old page
            previewJobs.values.forEach { it.cancel() }
            _isPreviewProcessing.value = false
            previewJobs.clear()
            // Save immediately so process-death restores to this page
            saveSession()
            
            // Note: DO NOT set activeProcessingVersion or launch batch cache generation here.
            // Pager swipes must NEVER cancel the background batch generation queue.
            // Live preview is handled separately by generateLowResPagePreview.
            Log.d("ScannerVM", "[G3 Pager] Swiped to page $safeIdx, live preview will handle display.")
        }
    }

    /**
     * Apply a filter to the batch.
     *
     * When [applyToAll] is true: snapshot and propagate to all pages without [hasFilterOverride].
     * When [applyToAll] is false: apply ONLY to the current pager page and mark it as overridden.
     *
     * [clearOverrides]: future-safe flag. If true, clears all existing overrides before propagating.
     * This allows the "Reapply to overridden pages" action without permanently hardcoding lock behavior.
     */
    fun applyFilterForPager(
        context: Context,
        filter: ScanFilter,
        strength: Float,
        applyToAll: Boolean,
        clearOverrides: Boolean = false
    ) {
        val batch = _activeBatch.value ?: return
        val ids = batch.pageIds
        val currentBatchId = batch.batchId
        if (ids.isEmpty()) return

        batchFilterJob?.cancel()
        batchFilterJob = viewModelScope.launch(Dispatchers.Default) {
            delay(120) // 120ms debounce

            // Snapshot values so rapid slider moves don't leak into stale jobs
            val snapshotFilter = filter
            val snapshotStrength = strength
            val token = System.currentTimeMillis()
            activeProcessingVersion = token // CRITICAL: Update global token to validate the new background job
            Log.d("ScannerVM", "[G3 Pager] applyFilterForPager started, new token=$token")

            val list = _batchPages.value.toMutableList()
            val updatedIds = mutableListOf<String>()

            if (applyToAll) {
                // Propagate to all non-overridden pages in the queue
                for (pageId in ids) {
                    val idx = list.indexOfFirst { it.id == pageId }
                    if (idx != -1) {
                        val page = list[idx]
                        val isEligible = clearOverrides || !page.transform.hasFilterOverride
                        if (isEligible) {
                            list[idx] = page.copy(
                                transform = page.transform.copy(
                                    filter = snapshotFilter,
                                    filterStrength = snapshotStrength,
                                    hasFilterOverride = if (clearOverrides) false else page.transform.hasFilterOverride,
                                    processingToken = token
                                ),
                                processingVersion = token,
                                thumbnailCacheUri = null,
                                isEdited = true,
                                lastModified = System.currentTimeMillis(),
                                state = ScanPageState.FILTERED
                            )
                            updatedIds.add(pageId)
                        } else if (page.state == ScanPageState.CROPPED) {
                            // Ensure overridden pages at least advance to FILTERED state
                            list[idx] = page.copy(state = ScanPageState.FILTERED)
                        }
                    }
                }
                Log.d("ScannerVM", "[Pager] Batch apply: $snapshotFilter (str=$snapshotStrength) to ${updatedIds.size} pages")
            } else {
                // Apply only to the current focused page → mark as override
                val currentPageId = ids.getOrNull(_currentFilterPageIndex.value)
                if (currentPageId != null) {
                    val idx = list.indexOfFirst { it.id == currentPageId }
                    if (idx != -1) {
                        val page = list[idx]
                        list[idx] = page.copy(
                            transform = page.transform.copy(
                                filter = snapshotFilter,
                                filterStrength = snapshotStrength,
                                hasFilterOverride = true,
                                processingToken = token
                            ),
                            processingVersion = token,
                            thumbnailCacheUri = null, // Invalidate stale thumbnail
                            isEdited = true,
                            lastModified = System.currentTimeMillis(),
                            state = ScanPageState.FILTERED
                        )
                        updatedIds.add(currentPageId)
                        Log.d("ScannerVM", "[Pager] Override applied to $currentPageId: $snapshotFilter (str=$snapshotStrength)")
                    }
                }
            }

            // Optimistic UI: commit state immediately, thumbnails load in background
            if (updatedIds.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    if (_activeBatch.value?.batchId != currentBatchId) {
                        Log.w("ScannerVM", "[G3 Batch] Batch ownership validation failed in applyFilterForPager, dropping.")
                        return@withContext
                    }
                    _batchPages.value = list
                    sessionDirty = true
                    Log.d("ScannerVM", "[G3.2.1 CHECKPOINT] Filter persisted for updated pages")
                    saveSession()
                    // Mark all updated pages as "loading" in filmstrip
                    _thumbnailLoadingIds.value = _thumbnailLoadingIds.value + updatedIds.toSet()
                }

                // Low-res preview for current page only (visible to user right now)
                val currentId = ids.getOrNull(_currentFilterPageIndex.value)
                if (currentId != null && currentId in updatedIds) {
                    val currentPage = list.find { it.id == currentId }
                    if (currentPage != null) {
                        generateLowResPagePreview(context, currentPage, snapshotFilter, snapshotStrength, token)
                    }
                }

                // Background sequential preload for remaining pages
                generateBatchCachesSequential(context, updatedIds, token, currentBatchId)
            }
        }
    }

    private fun generateBatchCachesSequential(context: Context, pageIds: List<String>, token: Long, batchId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            for (pageId in pageIds) {
                // Yield to visible page processing
                yield()
                
                // Skip if token changed (user slid the slider again)
                if (activeProcessingVersion != token) return@launch
                
                try {
                    // This generates previewCacheUri and thumbnailCacheUri silently
                    generateCachesSync(context, pageId, batchId)
                } catch (e: Exception) {
                    Log.e("ScannerVM", "Background preload failed for $pageId", e)
                }
            }
        }
    }

    /**
     * Generate a preview for the main pager view.
     * Target ~800px long edge to ensure sharp text rendering on modern screens.
     * Previous job for this page is cancelled before starting.
     */
    fun generateLowResPagePreview(
        context: Context,
        page: com.stutool.stutool.ui.screens.scanner.models.ScanPage,
        filter: ScanFilter,
        strength: Float,
        token: Long,
        isSinglePageMode: Boolean = false
    ) {
        val pageId = page.id
        val requestKey = "$pageId|${filter.name}|$strength"
        
        // Duplicate Job Protection: Skip if identical preview already exists and is valid
        if (lastPreviewRequests[pageId] == requestKey && _livePreviewBitmap.value != null) {
            return
        }
        
        previewJobs[pageId]?.cancel()
        _isPreviewProcessing.value = true
        lastPreviewRequests[pageId] = requestKey
        
        previewJobs[pageId] = viewModelScope.launch(Dispatchers.IO) {
            val scope = this
            try {
                val result: Bitmap? = withTimeoutOrNull(FILTER_PREVIEW_TIMEOUT_MS) {
                    val baseUri = page.croppedCacheUri ?: page.originalUri
                    val filePath = getFilePathFromUri(context, baseUri)
                    if (filePath == null) {
                        Log.e("ScannerVM", "[G3 Thumbnail] Low-res preview: cannot resolve path for $pageId")
                        return@withTimeoutOrNull null
                    }

                    var source: Bitmap? = null
                    if (baseUri == page.originalUri) {
                        Log.d("ScannerVM", "[G3.2.2 PREVIEW] Low-res preview cache missing, regenerating from original with crop transform")
                        val effectiveQuad = page.transform.effectiveCropQuad()
                        
                        var warpedBitmap: Bitmap? = null
                        if (effectiveQuad != null) {
                            warpedBitmap = PerspectiveTransform.warpFromFile(filePath, effectiveQuad)
                        }
                        
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(filePath, opts)
                        val longSide = maxOf(opts.outWidth, opts.outHeight)
                        val sampleSize = (longSide / 1500).coerceAtLeast(1)

                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        source = warpedBitmap ?: BitmapFactory.decodeFile(filePath, decodeOpts)
                        if (source == null) return@withTimeoutOrNull null

                        val effectiveRotation = page.transform.effectiveRotation()
                        if (effectiveRotation != 0) {
                            val matrix = Matrix()
                            matrix.postRotate(effectiveRotation.toFloat())
                            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                            if (rotated !== source) source.recycle()
                            source = rotated
                        }
                    } else {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(filePath, opts)
                        val longSide = maxOf(opts.outWidth, opts.outHeight)
                        val sampleSize = (longSide / 1500).coerceAtLeast(1)

                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        source = BitmapFactory.decodeFile(filePath, decodeOpts)
                    }
                    
                    if (source == null) return@withTimeoutOrNull null

                    // Apply filter (on low-res bitmap fast, with cancellation checks)
                    val filtered = ImageProcessing.applyFilter(source, filter, strength, isActive = { scope.isActive })

                    if (filtered !== source) source.recycle()
                    
                    if (!scope.isActive) {
                        filtered.recycle()
                        return@withTimeoutOrNull null
                    }
                    
                    filtered
                }

                // G3: Stale-result protection before publishing
                withContext(Dispatchers.Main) {
                    // Verify request token still matches latest request
                    if (!isSinglePageMode && token != activeProcessingVersion && token != page.processingVersion) {
                        Log.d("ScannerVM", "Discarding stale preview result for $pageId")
                        result?.recycle()
                        return@withContext
                    }
                    
                    if (result == null) {
                        if (previewJobs[pageId] == this@launch) _isPreviewProcessing.value = false
                        return@withContext
                    }
                    
                    if (!isSinglePageMode) {
                        val batch = _activeBatch.value
                        val currentIdx = _currentFilterPageIndex.value
                        val currentActivePageId = batch?.pageIds?.getOrNull(currentIdx)
                        
                        if (pageId != currentActivePageId) {
                            result.recycle()
                            Log.w("ScannerVM", "[G3 Thumbnail] Live preview: page $pageId is no longer active, discarding")
                            if (previewJobs[pageId] == this@launch) _isPreviewProcessing.value = false
                            return@withContext
                        }
                    }

                    // Check batch pages first, then fallback to finalized pages
                    val currentPage = _batchPages.value.find { it.id == pageId } ?: _scannedPages.value.find { it.id == pageId }
                    if (isSinglePageMode || currentPage?.processingVersion == token) {
                        val oldBitmap = _livePreviewBitmap.value
                        _livePreviewBitmap.value = result
                        oldBitmap?.recycle()
                    } else {
                        result.recycle()
                        Log.w("ScannerVM", "[G3 Thumbnail] Live preview: stale result for $pageId, discarding")
                    }
                    if (previewJobs[pageId] == this@launch) _isPreviewProcessing.value = false
                }
            } catch (e: Exception) {
                Log.e("ScannerVM", "[G3 Thumbnail] Live preview failed for $pageId", e)
                withContext(Dispatchers.Main) {
                    if (previewJobs[pageId] == this@launch) _isPreviewProcessing.value = false
                }
            }
        }
    }

    /** Called from DisposableEffect when BatchFilterPagerScreen leaves composition. */
    fun cancelAllFilterPreviews() {
        previewJobs.values.forEach { it.cancel() }
        previewJobs.clear()
        batchFilterJob?.cancel()
        batchFilterJob = null
        val oldBitmap = _livePreviewBitmap.value
        _livePreviewBitmap.value = null
        oldBitmap?.recycle()
        _isPreviewProcessing.value = false
        _thumbnailLoadingIds.value = emptySet()
        Log.d("ScannerVM", "[Pager] All filter preview jobs cancelled on screen dispose")
    }

    /** Called from DisposableEffect when SinglePageFilterEditorScreen leaves composition. */
    fun cancelSinglePageFilterPreview(pageId: String) {
        previewJobs[pageId]?.cancel()
        previewJobs.remove(pageId)
        lastPreviewRequests.remove(pageId)
        val oldBitmap = _livePreviewBitmap.value
        _livePreviewBitmap.value = null
        oldBitmap?.recycle()
        _isPreviewProcessing.value = false
        Log.d("ScannerVM", "[Single Editor] Single page filter preview cleaned up for $pageId")
    }

    /** Reset pager state when leaving the filter screen (back navigation safety). */
    fun clearFilterPagerState() {
        cancelAllFilterPreviews()
        // Do NOT reset _currentFilterPageIndex — it's persisted intentionally.
    }


    private fun generateTempPreview(context: Context) {
        val uri = _currentImageUri.value ?: return
        val quad = _activeQuad.value
        val filter = _selectedFilter.value
        val strength = _filterStrength.value
        val rotation = _activeRotation.value
        val token = activeProcessingVersion

        processingJobs["temp_preview"]?.cancel()
        processingJobs["temp_preview"] = viewModelScope.launch(Dispatchers.IO) {
            processingSemaphore.withPermit {
                try {
                    val result = withTimeoutOrNull(FILTER_PREVIEW_TIMEOUT_MS) {
                        val filePath = getFilePathFromUri(context, uri)
                        if (filePath == null) {
                            Log.e("ScannerVM", "[G2 Thumbnail] generateTempPreview: cannot resolve file path")
                            return@withTimeoutOrNull null
                        }
                        var warpedBitmap: Bitmap? = null
                        
                        if (quad != null) {
                            warpedBitmap = PerspectiveTransform.warpFromFile(filePath, quad)
                        }
                    
                        var sourceForFilter: Bitmap? = warpedBitmap
                        if (sourceForFilter == null) {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(filePath, opts)
                            val longSide = maxOf(opts.outWidth, opts.outHeight)
                            val sampleSize = if (longSide > 1500) longSide / 1500 else 1
                            
                            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            sourceForFilter = BitmapFactory.decodeFile(filePath, decodeOpts)
                        }
                        if (sourceForFilter == null) {
                            Log.e("ScannerVM", "[G2 Thumbnail] generateTempPreview: bitmap decode returned null")
                            return@withTimeoutOrNull null
                        }
                    
                        // Apply rotation
                        if (rotation != 0) {
                            val matrix = Matrix()
                            matrix.postRotate(rotation.toFloat())
                            val rotated = Bitmap.createBitmap(sourceForFilter, 0, 0, sourceForFilter.width, sourceForFilter.height, matrix, true)
                            if (rotated !== sourceForFilter) sourceForFilter.recycle()
                            sourceForFilter = rotated
                        }
                    
                        // Scale down for preview
                        val previewScale = 1800f / maxOf(sourceForFilter.width, sourceForFilter.height)
                        val previewBitmap = if (previewScale < 1f) {
                            Bitmap.createScaledBitmap(
                                sourceForFilter,
                                (sourceForFilter.width * previewScale).toInt(),
                                (sourceForFilter.height * previewScale).toInt(),
                                true
                            )
                        } else {
                            sourceForFilter.copy(sourceForFilter.config ?: Bitmap.Config.ARGB_8888, true)
                        }
                    
                        // Apply Filter with strength
                        val filteredPreview = ImageProcessing.applyFilter(previewBitmap, filter, strength)
                    
                        val cacheDir = File(context.cacheDir, "scanner_cache").apply { mkdirs() }
                        val previewFile = File.createTempFile("temp_prev_", ".jpg", cacheDir)
                        FileOutputStream(previewFile).use { out ->
                            filteredPreview.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                    
                        if (filteredPreview !== previewBitmap) filteredPreview.recycle()
                        previewBitmap.recycle()
                        if (warpedBitmap != null && warpedBitmap !== sourceForFilter) warpedBitmap.recycle()
                        sourceForFilter.recycle()
                        
                        previewFile
                    }

                    if (result == null) {
                        Log.e("ScannerVM", "[G2 Thumbnail] generateTempPreview: timed out or failed")
                        return@withPermit
                    }

                    // G2: Stale-job protection
                    withContext(Dispatchers.Main) {
                        if (activeProcessingVersion == token) {
                            _tempEditPreviewUri.value = Uri.fromFile(result)
                        } else {
                            result.delete()
                            Log.w("ScannerVM", "[G2 Thumbnail] generateTempPreview: stale result, discarding")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScannerVM", "[G3 Thumbnail] generateTempPreview FAILED", e)
                }
            }
        }
    }

    /**
     * Iteration 2: Auto-Crop Engine Foundation
     * Bulk processes auto-edges sequentially without blocking the main thread.
     */
    internal suspend fun bulkApplyAutoEdges(context: Context, pages: List<com.stutool.stutool.ui.screens.scanner.models.ScanPage>) {
        var pagesProcessed = 0
        for (page in pages) {
            yield() // Prevent blocking
            
            // Determine if regeneration is required
            // A page requires processing if it lacks a valid cropped cache
            val needsGeneration = page.croppedCacheUri == null

            if (!needsGeneration) {
                continue
            }

            var quad = page.transform.effectiveCropQuad()
            var edgeConfidence = page.transform.edgeConfidence
            
            if (quad == null) {
                withContext(Dispatchers.IO) {
                    try {
                        val filePath = getFilePathFromUri(context, page.originalUri)
                        if (filePath != null) {
                            val bitmap = BitmapFactory.decodeFile(filePath)
                            if (bitmap != null) {
                                val bmpW = bitmap.width
                                val bmpH = bitmap.height
                                val result = com.stutool.stutool.ui.screens.scanner.utils.EdgeDetection.detectWithConfidence(bitmap)
                                bitmap.recycle()
                                edgeConfidence = result.confidence
                                quad = if (result.confidence > 0.8f) {
                                    result.quad
                                } else {
                                    com.stutool.stutool.ui.screens.scanner.utils.Quad.defaultForImage(bmpW, bmpH)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ScannerVM", "Edge detection failed during bulk auto-crop for page ${page.id}", e)
                    }
                    
                    // Absolute fallback if everything fails, ensuring we never leave page unprocessed
                    if (quad == null) {
                        quad = com.stutool.stutool.ui.screens.scanner.utils.Quad.defaultForImage(1000, 1000)
                    }
                }
            }

            // Mark as autoEdgeApplied and update state
            withContext(Dispatchers.Main) {
                val batchList = _batchPages.value.toMutableList()
                val idx = batchList.indexOfFirst { it.id == page.id }
                if (idx != -1) {
                    batchList[idx] = batchList[idx].copy(
                        transform = batchList[idx].transform.copy(
                            cropQuad = quad,
                            edgeConfidence = edgeConfidence,
                            autoEdgeApplied = true
                        ),
                        state = ScanPageState.CROPPED
                    )
                    _batchPages.value = batchList
                }
                
                val scannedList = _scannedPages.value.toMutableList()
                val sIdx = scannedList.indexOfFirst { it.id == page.id }
                if (sIdx != -1) {
                    scannedList[sIdx] = scannedList[sIdx].copy(
                        transform = scannedList[sIdx].transform.copy(
                            cropQuad = quad,
                            edgeConfidence = edgeConfidence,
                            autoEdgeApplied = true
                        ),
                        state = ScanPageState.CROPPED
                    )
                    _scannedPages.value = scannedList
                }
            }

            // Generate caches
            generateCroppedBitmapCache(context, page.id, _activeBatch.value?.batchId)
            generateCachesSync(context, page.id, _activeBatch.value?.batchId)

            pagesProcessed++
            if (pagesProcessed % 5 == 0) {
                withContext(Dispatchers.Main) { saveSession() }
            }
        }
        
        // Final checkpoint
        if (pagesProcessed % 5 != 0 && pagesProcessed > 0) {
            withContext(Dispatchers.Main) { saveSession() }
        }
    }

    /**
     * G3: Bake-On-Commit Crop Architecture.
     * Generates a high-res, perspective-warped, rotated bitmap and saves it atomically.
     */
    private suspend fun generateCroppedBitmapCache(context: Context, pageId: String, batchId: String? = null) {
        withContext(Dispatchers.IO) {
            processingSemaphore.withPermit {
                val page = _batchPages.value.find { it.id == pageId } 
                    ?: _scannedPages.value.find { it.id == pageId } ?: return@withPermit
                
                try {
                    val result = withTimeoutOrNull(CACHE_GEN_TIMEOUT_MS) {
                        val filePath = getFilePathFromUri(context, page.originalUri) ?: return@withTimeoutOrNull null
                        
                        var warpedBitmap: Bitmap? = null
                        val effectiveQuad = page.transform.effectiveCropQuad()
                        if (effectiveQuad != null) {
                            warpedBitmap = PerspectiveTransform.warpFromFile(filePath, effectiveQuad)
                        }
                        
                        var source = warpedBitmap ?: BitmapFactory.decodeFile(filePath)
                        if (source == null || source.isRecycled) return@withTimeoutOrNull null
                        
                        val effectiveRotation = page.transform.effectiveRotation()
                        if (effectiveRotation != 0) {
                            val matrix = Matrix()
                            matrix.postRotate(effectiveRotation.toFloat())
                            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                            if (rotated !== source) source.recycle()
                            source = rotated
                        }
                        
                        if (source.isRecycled) return@withTimeoutOrNull null
                        
                        val cacheDir = File(context.cacheDir, "scanner_cache").apply { mkdirs() }
                        val tempFile = File(cacheDir, "temp_crop_${pageId}_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(tempFile).use { out ->
                            source.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        source.recycle()
                        
                        // Validate decode
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(tempFile.absolutePath, opts)
                        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                            tempFile.delete()
                            return@withTimeoutOrNull null
                        }
                        
                        val finalFile = File(cacheDir, "cropped_${pageId}_${System.currentTimeMillis()}.jpg")
                        if (!tempFile.renameTo(finalFile)) {
                            tempFile.delete()
                            return@withTimeoutOrNull null
                        }
                        
                        Uri.fromFile(finalFile)
                    }
                    
                    if (result == null) {
                        Log.e("ScannerVM", "[G3 Renderer] generateCroppedBitmapCache: failed or timed out for page $pageId")
                        return@withPermit
                    }
                    
                    Log.d("ScannerVM", "[G3 Renderer] Baked crop cache for page $pageId at $result")
                    
                    withContext(Dispatchers.Main) {
                        var found = false
                        
                        // Try updating in batch pages
                        val batchList = _batchPages.value.toMutableList()
                        val batchIdx = batchList.indexOfFirst { it.id == pageId }
                        if (batchIdx != -1) {
                            val currentPage = batchList[batchIdx]
                            if (currentPage.processingVersion == page.processingVersion) {
                                val oldPage = batchList[batchIdx]
                                oldPage.croppedCacheUri?.path?.let { File(it).delete() }
                                oldPage.previewCacheUri?.path?.let { File(it).delete() }
                                oldPage.thumbnailCacheUri?.path?.let { File(it).delete() }
                                
                                batchList[batchIdx] = oldPage.copy(
                                    croppedCacheUri = result,
                                    previewCacheUri = null,
                                    thumbnailCacheUri = null,
                                    state = ScanPageState.CROPPED
                                )
                                _batchPages.value = batchList
                                found = true
                            }
                        }
                        
                        // Try updating in scanned pages
                        val scannedList = _scannedPages.value.toMutableList()
                        val scannedIdx = scannedList.indexOfFirst { it.id == pageId }
                        if (scannedIdx != -1) {
                            val currentPage = scannedList[scannedIdx]
                            if (currentPage.processingVersion == page.processingVersion) {
                                val oldPage = scannedList[scannedIdx]
                                oldPage.croppedCacheUri?.path?.let { File(it).delete() }
                                oldPage.previewCacheUri?.path?.let { File(it).delete() }
                                oldPage.thumbnailCacheUri?.path?.let { File(it).delete() }
                                
                                scannedList[scannedIdx] = oldPage.copy(
                                    croppedCacheUri = result,
                                    previewCacheUri = null,
                                    thumbnailCacheUri = null,
                                    state = oldPage.state
                                )
                                _scannedPages.value = scannedList
                                found = true
                            }
                        }
                        
                        if (found) {
                            saveSession()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScannerVM", "[G3 Renderer] generateCroppedBitmapCache FAILED for page $pageId", e)
                }
            }
        }
    }

    private suspend fun generateCachesSync(context: Context, pageId: String, batchId: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                processingSemaphore.withPermit {
                    val page = _batchPages.value.find { it.id == pageId } 
                    ?: _scannedPages.value.find { it.id == pageId }
                if (page == null) {
                    Log.e("ScannerVM", "[G3 Thumbnail] generateCachesSync: page $pageId not found")
                    return@withPermit
                }
                
                // G3.2.6: Mark as HYDRATING
                withContext(Dispatchers.Main) {
                    val batchList = _batchPages.value.toMutableList()
                    val batchIdx = batchList.indexOfFirst { it.id == pageId }
                    if (batchIdx != -1) {
                        batchList[batchIdx] = batchList[batchIdx].copy(assetState = AssetState.HYDRATING)
                        _batchPages.value = batchList
                    }
                    val scannedList = _scannedPages.value.toMutableList()
                    val scannedIdx = scannedList.indexOfFirst { it.id == pageId }
                    if (scannedIdx != -1) {
                        scannedList[scannedIdx] = scannedList[scannedIdx].copy(assetState = AssetState.HYDRATING)
                        _scannedPages.value = scannedList
                    }
                }
                
                try {
                    val baseUri = page.croppedCacheUri ?: page.originalUri
                    val result = withTimeoutOrNull(CACHE_GEN_TIMEOUT_MS) {
                        val filePath = getFilePathFromUri(context, baseUri)
                        if (filePath == null) {
                            Log.e("ScannerVM", "[G3 Thumbnail] generateCachesSync: cannot resolve file path for page $pageId")
                            return@withTimeoutOrNull null
                        }
                        
                        var sourceForFilter: Bitmap? = null
                        var warpedBitmap: Bitmap? = null
                        if (baseUri == page.originalUri) {
                            // G3.2.1 CHECKPOINT: Export regeneration path
                            Log.d("ScannerVM", "[G3.2.1 CHECKPOINT] Export cache missing for page ${page.id}, regenerating from original")
                            Log.w("ScannerVM", "[G3 Thumbnail] Cropped cache missing for $pageId, regenerating from original")
                            val effectiveQuad = page.transform.effectiveCropQuad()
                            if (effectiveQuad != null) {
                                warpedBitmap = PerspectiveTransform.warpFromFile(filePath, effectiveQuad)
                            }
                            
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(filePath, opts)
                            val longSide = maxOf(opts.outWidth, opts.outHeight)
                            val sampleSize = if (longSide > 1500) longSide / 1500 else 1
                            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            sourceForFilter = warpedBitmap ?: BitmapFactory.decodeFile(filePath, decodeOpts)
                        
                            val effectiveRotation = page.transform.effectiveRotation()
                            if (effectiveRotation != 0 && sourceForFilter != null && !sourceForFilter.isRecycled) {
                                val matrix = Matrix()
                                matrix.postRotate(effectiveRotation.toFloat())
                                val rotated = Bitmap.createBitmap(sourceForFilter, 0, 0, sourceForFilter.width, sourceForFilter.height, matrix, true)
                                if (rotated !== sourceForFilter) sourceForFilter.recycle()
                                sourceForFilter = rotated
                            }
                        } else {
                            // G3 Fast Path: Load the baked crop cache!
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(filePath, opts)
                            val longSide = maxOf(opts.outWidth, opts.outHeight)
                            val sampleSize = if (longSide > 1500) longSide / 1500 else 1
                            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            sourceForFilter = BitmapFactory.decodeFile(filePath, decodeOpts)
                        }
                        if (sourceForFilter == null) {
                            Log.e("ScannerVM", "[G3 Thumbnail] generateCachesSync: decode returned null for $pageId")
                            return@withTimeoutOrNull null
                        }
                    
                        // Scale down for preview cache
                        val previewScale = 1800f / maxOf(sourceForFilter.width, sourceForFilter.height)
                        val previewBitmap = if (previewScale < 1f) {
                            Bitmap.createScaledBitmap(
                                sourceForFilter,
                                (sourceForFilter.width * previewScale).toInt(),
                                (sourceForFilter.height * previewScale).toInt(),
                                true
                            )
                        } else {
                            sourceForFilter.copy(sourceForFilter.config ?: Bitmap.Config.ARGB_8888, true)
                        }
                    
                        // Apply Filter with strength
                        val filteredPreview = ImageProcessing.applyFilter(previewBitmap, page.transform.filter, page.transform.filterStrength)
                    
                        // Generate low-res thumbnail from preview (max 300px edge for review scrolling)
                        val thumbScale = 300f / maxOf(filteredPreview.width, filteredPreview.height)
                        val thumbnailBitmap = Bitmap.createScaledBitmap(
                            filteredPreview,
                            (filteredPreview.width * thumbScale).toInt(),
                            (filteredPreview.height * thumbScale).toInt(),
                            true
                        )

                        // Save to temp files
                        val cacheDir = File(context.cacheDir, "scanner_cache").apply { mkdirs() }
                        
                        val previewFile = File.createTempFile("prev_${page.processingVersion}_", ".jpg", cacheDir)
                        FileOutputStream(previewFile).use { out ->
                            filteredPreview.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        
                        val thumbFile = File.createTempFile("thumb_${page.processingVersion}_", ".jpg", cacheDir)
                        FileOutputStream(thumbFile).use { out ->
                            thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        }
                    
                        // Cleanup bitmaps
                        if (filteredPreview !== previewBitmap) filteredPreview.recycle()
                        previewBitmap.recycle()
                        thumbnailBitmap.recycle()
                        if (warpedBitmap != null && warpedBitmap !== sourceForFilter) warpedBitmap.recycle()
                        sourceForFilter.recycle()

                        Pair(previewFile, thumbFile)
                    }

                    if (result == null) {
                        Log.e("ScannerVM", "[G3 Thumbnail] generateCachesSync: timed out or failed for page $pageId. Applying fallback to unblock UI.")
                        // G3.2.3: Separate 'Loading' vs 'Stuck' — apply fallback to prevent infinite spinner
                        withContext(Dispatchers.Main) {
                            var found = false
                            
                            val batchList = _batchPages.value.toMutableList()
                            val batchIdx = batchList.indexOfFirst { it.id == pageId }
                            if (batchIdx != -1) {
                                val currentPage = batchList[batchIdx]
                                if (currentPage.processingVersion == page.processingVersion) {
                                    val terminalState = if (batchId != null && _activeBatch.value?.stage == QueueStage.FILTER) ScanPageState.FILTERED else currentPage.state
                                    batchList[batchIdx] = currentPage.copy(
                                        previewCacheUri = currentPage.originalUri,
                                        thumbnailCacheUri = currentPage.originalUri,
                                        state = terminalState,
                                        isNew = false,
                                        assetState = AssetState.FAILED // G3.2.6
                                    )
                                    _batchPages.value = batchList
                                    found = true
                                }
                            }
                            
                            val scannedList = _scannedPages.value.toMutableList()
                            val scannedIdx = scannedList.indexOfFirst { it.id == pageId }
                            if (scannedIdx != -1) {
                                val currentPage = scannedList[scannedIdx]
                                if (currentPage.processingVersion == page.processingVersion) {
                                    val terminalState = if (batchId != null) ScanPageState.FILTERED else currentPage.state
                                    scannedList[scannedIdx] = currentPage.copy(
                                        previewCacheUri = currentPage.originalUri,
                                        thumbnailCacheUri = currentPage.originalUri,
                                        state = terminalState,
                                        isNew = false,
                                        assetState = AssetState.FAILED // G3.2.6
                                    )
                                    _scannedPages.value = scannedList
                                    found = true
                                }
                            }
                            
                            if (found) {
                                saveSession()
                            }
                        }
                        return@withPermit
                    }

                    val (previewFile, thumbFile) = result

                    // G3.2.5 FILE_COMMIT: Validate files are fully written and decodable before marking ready
                    val previewValid = validateCommittedFile(previewFile)
                    val thumbValid = validateCommittedFile(thumbFile)

                    if (!previewValid || !thumbValid) {
                        Log.w("ScannerVM", "[G3.2.5 FILE_COMMIT] File validation FAILED for page $pageId — falling back to originalUri to unblock UI")
                        previewFile.delete()
                        thumbFile.delete()
                        withContext(Dispatchers.Main) {
                            var found = false
                            
                            val batchList = _batchPages.value.toMutableList()
                            val batchIdx = batchList.indexOfFirst { it.id == pageId }
                            if (batchIdx != -1) {
                                val currentPage = batchList[batchIdx]
                                if (currentPage.processingVersion == page.processingVersion) {
                                    val terminalState = if (batchId != null && _activeBatch.value?.stage == QueueStage.FILTER) ScanPageState.FILTERED else currentPage.state
                                    batchList[batchIdx] = currentPage.copy(
                                        previewCacheUri = currentPage.originalUri,
                                        thumbnailCacheUri = currentPage.originalUri,
                                        state = terminalState,
                                        isNew = false,
                                        assetState = AssetState.FAILED // G3.2.6
                                    )
                                    _batchPages.value = batchList
                                    found = true
                                }
                            }
                            
                            val scannedList = _scannedPages.value.toMutableList()
                            val scannedIdx = scannedList.indexOfFirst { it.id == pageId }
                            if (scannedIdx != -1) {
                                val currentPage = scannedList[scannedIdx]
                                if (currentPage.processingVersion == page.processingVersion) {
                                    val terminalState = if (batchId != null) ScanPageState.FILTERED else currentPage.state
                                    scannedList[scannedIdx] = currentPage.copy(
                                        previewCacheUri = currentPage.originalUri,
                                        thumbnailCacheUri = currentPage.originalUri,
                                        state = terminalState,
                                        isNew = false,
                                        assetState = AssetState.FAILED // G3.2.6
                                    )
                                    _scannedPages.value = scannedList
                                    found = true
                                }
                            }
                            
                            if (found) {
                                saveSession()
                            }
                        }
                        return@withPermit
                    }

                    Log.d("ScannerVM", "[G3.2.5 FILE_COMMIT] File write completed and validated for page $pageId")

                    // G3: Stale-job protection — validate state before UI mutation
                    withContext(Dispatchers.Main) {
                        var found = false
                        var terminalStateFound: ScanPageState = ScanPageState.IDLE
                        
                        val batchList = _batchPages.value.toMutableList()
                        val batchIdx = batchList.indexOfFirst { it.id == pageId }
                        if (batchIdx != -1) {
                            val currentPage = batchList[batchIdx]
                            if (currentPage.processingVersion == page.processingVersion) {
                                val terminalState = if (batchId != null && _activeBatch.value?.stage == QueueStage.FILTER) ScanPageState.FILTERED else currentPage.state
                                terminalStateFound = terminalState
                                batchList[batchIdx] = currentPage.copy(
                                    previewCacheUri = Uri.fromFile(previewFile),
                                    thumbnailCacheUri = Uri.fromFile(thumbFile),
                                    state = terminalState,
                                    isNew = false,
                                    assetState = AssetState.READY // G3.2.6
                                )
                                _batchPages.value = batchList
                                found = true
                            }
                        }
                        
                        val scannedList = _scannedPages.value.toMutableList()
                        val scannedIdx = scannedList.indexOfFirst { it.id == pageId }
                        if (scannedIdx != -1) {
                            val currentPage = scannedList[scannedIdx]
                            if (currentPage.processingVersion == page.processingVersion) {
                                val terminalState = if (batchId != null) ScanPageState.FILTERED else currentPage.state
                                terminalStateFound = terminalState
                                scannedList[scannedIdx] = currentPage.copy(
                                    previewCacheUri = Uri.fromFile(previewFile),
                                    thumbnailCacheUri = Uri.fromFile(thumbFile),
                                    state = terminalState,
                                    isNew = false,
                                    assetState = AssetState.READY // G3.2.6
                                )
                                _scannedPages.value = scannedList
                                found = true
                            }
                        }
                        
                        if (found) {
                            saveSession()
                            Log.d("ScannerVM", "[G3.2.5 FILE_COMMIT] Restore barrier released for page $pageId — state=$terminalStateFound")
                            
                            val finalPage = _batchPages.value.find { it.id == pageId } ?: _scannedPages.value.find { it.id == pageId }
                            android.util.Log.e("CropTrace_FINAL", "--- FINAL PAGE STATE AFTER generateCachesSync ---")
                            android.util.Log.e("CropTrace_FINAL", "pageId: ${finalPage?.id}")
                            android.util.Log.e("CropTrace_FINAL", "state: ${finalPage?.state}")
                            android.util.Log.e("CropTrace_FINAL", "assetState: ${finalPage?.assetState}")
                            android.util.Log.e("CropTrace_FINAL", "croppedCacheUri: ${finalPage?.croppedCacheUri}")
                            android.util.Log.e("CropTrace_FINAL", "previewCacheUri: ${finalPage?.previewCacheUri}")
                            android.util.Log.e("CropTrace_FINAL", "thumbnailCacheUri: ${finalPage?.thumbnailCacheUri}")
                            android.util.Log.e("CropTrace_FINAL", "-----------------------------------------------")
                        } else {
                            Log.w("ScannerVM", "[G3 Thumbnail] generateCaches: version mismatch or absent for page $pageId — discarding cache")
                            previewFile.delete()
                            thumbFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScannerVM", "[G3 Thumbnail] generateCachesSync FAILED for page $pageId", e)
                    withContext(Dispatchers.Main) {
                        var found = false
                        val batchList = _batchPages.value.toMutableList()
                        val batchIdx = batchList.indexOfFirst { it.id == pageId }
                        if (batchIdx != -1) {
                            val currentPage = batchList[batchIdx]
                            batchList[batchIdx] = currentPage.copy(
                                previewCacheUri = currentPage.originalUri,
                                thumbnailCacheUri = currentPage.originalUri,
                                assetState = AssetState.FAILED
                            )
                            _batchPages.value = batchList
                            found = true
                        }
                        
                        val scannedList = _scannedPages.value.toMutableList()
                        val scannedIdx = scannedList.indexOfFirst { it.id == pageId }
                        if (scannedIdx != -1) {
                            val currentPage = scannedList[scannedIdx]
                            scannedList[scannedIdx] = currentPage.copy(
                                previewCacheUri = currentPage.originalUri,
                                thumbnailCacheUri = currentPage.originalUri,
                                assetState = AssetState.FAILED
                            )
                            _scannedPages.value = scannedList
                            found = true
                        }
                        
                        if (found) saveSession()
                    }
                }
            }
            } finally {
                _thumbnailLoadingIds.value = _thumbnailLoadingIds.value - pageId
            }
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        
        val tempFile = File(context.cacheDir, "temp_src_${System.currentTimeMillis()}.jpg")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * G3.1: Single-mode capture pipeline entry point.
     *
     * Creates a single-page batch queue for the captured file so that
     * advanceQueue() correctly routes: Crop → Filter (batch_filter_pager) → Review.
     *
     * Previously this only set transient (_currentImageUri) state, meaning
     * EdgeCropScreen saw inQueue=false and fell into the else-branch, calling
     * navController.popBackStack() back to the scanner screen.
     */
    fun captureAndPrepare(context: Context, file: File, displayRotation: Int, onNavigate: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ScannerVM", "[G3.1] captureAndPrepare: starting single-mode pipeline for ${file.name}")
            
            try {
                correctImageRotation(file, displayRotation)
            } catch (e: Exception) {
                Log.e("ScannerVM", "[G3.1] captureAndPrepare: rotation correction failed", e)
            }

            val pageId = UUID.randomUUID().toString()
            val pageUri = Uri.fromFile(file)
            
            // Read image dimensions for EdgeCropScreen quad initialization
            var imgW = 0; var imgH = 0
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                imgW = opts.outWidth; imgH = opts.outHeight
                Log.d("ScannerVM", "[G3.1] Image dimensions: ${imgW}x${imgH}")
            } catch (e: Exception) {
                Log.e("ScannerVM", "[G3.1] Failed to read image dimensions", e)
            }
            
            val newPage = ScanPage(
                id = pageId,
                originalUri = pageUri,
                transform = PageTransform(null, ScanFilter.ENHANCED, 0),
                previewCacheUri = null,
                thumbnailCacheUri = null,
                state = ScanPageState.IDLE,
                isEdited = false,
                processingVersion = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis()
            )

            withContext(Dispatchers.Main) {
                // Create an isolated single-page batch queue (same path as batch, but N=1)
                val batchId = UUID.randomUUID().toString()
                _activeBatch.value = ProcessingBatch(batchId, listOf(pageId), QueueStage.CROP, BatchSource.CAMERA)
                _batchPages.value = listOf(newPage)
                _currentQueueIndex.value = 0

                // Populate transient editing state for EdgeCropScreen
                _currentImageUri.value = pageUri
                _activeQuad.value = null
                _selectedFilter.value = ScanFilter.ENHANCED
                _filterStrength.value = 1.0f
                _activeRotation.value = 0
                _cropMode.value = CropMode.PERSPECTIVE
                _editingPageIndex.value = 0
                activeProcessingVersion = newPage.processingVersion
                if (imgW > 0 && imgH > 0) {
                    _activeImageWidth.value = imgW
                    _activeImageHeight.value = imgH
                }
                _imageReady.value = true

                sessionDirty = true
                saveSession()
                Log.d("ScannerVM", "[G3.1] Single-page batch created: batchId=$batchId pageId=$pageId → navigating to crop")
                onNavigate()
            }
        }
    }

    // Cache last export params for retry
    private var lastExportContext: Context? = null
    private var lastExportType: ExportType? = null
    private var lastExportParams: Map<String, Any> = emptyMap()

    fun startExport(context: Context, type: ExportType, params: Map<String, Any> = emptyMap()) {
        if (_scannedPages.value.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            if (!exportMutex.tryLock()) {
                Log.w("ScannerVM", "Export: already in progress, ignoring double tap.")
                return@launch
            }
            
            val validFiles = mutableListOf<File>()
            try {
                // Cache for retry
                lastExportContext = context
                lastExportType = type
                lastExportParams = params
                
                val mimeType = getMimeTypeForExport(type)
                Log.d("ScannerVM", "Export: starting type=$type, mime=$mimeType, pages=${_scannedPages.value.filter { !it.isNew }.size}")
                
                _exportState.value = ExportState.Preparing
                
                // Snapshot immutably, only READY pages (inserted)
                val exportPages = _scannedPages.value.filter { !it.isNew }.toList()
                
                // G3.2.6: Strict READY state verification
                val notReadyPages = exportPages.filter { it.assetState != AssetState.READY }
                if (notReadyPages.isNotEmpty()) {
                    Log.e("ScannerVM", "[G3.2.6 HYDRATION] Export blocked! ${notReadyPages.size} pages are not READY (States: ${notReadyPages.map { it.assetState }}).")
                    _exportState.value = ExportState.Failed(IllegalStateException("Please wait for all pages to finish loading before exporting."))
                    return@launch
                }
                
                for ((index, page) in exportPages.withIndex()) {
                    _exportState.value = ExportState.Exporting(index + 1)
                    var activePage = page
                    var activeBaseUri = activePage.croppedCacheUri ?: activePage.originalUri
                    val resolvedPath = getFilePathFromUri(context, activeBaseUri)
                    
                    if (resolvedPath == null) {
                        Log.e("ScannerVM", "[G3.2.6 HYDRATION] Export failed for page ${page.id} — path unresolvable.")
                        continue
                    }
                    
                    var sourceForFilter: Bitmap? = null
                    var warpedBitmap: Bitmap? = null
                    if (activeBaseUri == activePage.originalUri) {
                        // G3.2.1 CHECKPOINT: Export regeneration path
                        Log.d("ScannerVM", "[G3.2.1 CHECKPOINT] Export cache missing for page ${activePage.id}, regenerating from original")
                        val effectiveQuad = activePage.transform.effectiveCropQuad()
                        if (effectiveQuad != null && effectiveQuad.isValid()) {
                            warpedBitmap = PerspectiveTransform.warpFromFile(resolvedPath, effectiveQuad)
                            Log.w("ScannerVM", "[G3 Export] Fallback warp applied for page ${activePage.id}")
                        }
                        
                        sourceForFilter = warpedBitmap ?: BitmapFactory.decodeFile(resolvedPath)
                        if (sourceForFilter == null) {
                            Log.e("ScannerVM", "[G3 Export] page ${activePage.id}: fallback decode failed")
                            continue
                        }
                        
                        val effectiveRotation = activePage.transform.effectiveRotation()
                        if (effectiveRotation != 0 && !sourceForFilter.isRecycled) {
                            val matrix = Matrix()
                            matrix.postRotate(effectiveRotation.toFloat())
                            val rotated = Bitmap.createBitmap(
                                sourceForFilter, 0, 0,
                                sourceForFilter.width, sourceForFilter.height,
                                matrix, true
                            )
                            if (rotated !== sourceForFilter) sourceForFilter.recycle()
                            sourceForFilter = rotated
                        }
                    } else {
                        // G3 Fast Path: Load the baked crop cache!
                        sourceForFilter = BitmapFactory.decodeFile(resolvedPath)
                        if (sourceForFilter == null) {
                            Log.e("ScannerVM", "[G3 Export] page ${activePage.id}: crop cache decode failed")
                            continue
                        }
                    }
                    
                    // 3. Filter with strength
                    val filteredBitmap = ImageProcessing.applyFilter(sourceForFilter, page.transform.filter, page.transform.filterStrength)
                    
                    val exportDir = File(context.cacheDir, "scanner_exports").apply { mkdirs() }
                    val exportFile = File.createTempFile("export_${page.id}_", ".jpg", exportDir)
                    
                    FileOutputStream(exportFile).use { out ->
                        filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        out.flush()
                        out.fd.sync()
                    }
                    
                    // Validate exported file
                    if (exportFile.exists() && exportFile.length() > 100) {
                        validFiles.add(exportFile)
                        Log.d("ScannerVM", "[G2 Export] page ${page.id} written, size=${exportFile.length()} bytes")
                    } else {
                        Log.e("ScannerVM", "[G2 Export] page ${page.id} write validation failed, size=${exportFile.length()}")
                        exportFile.delete()
                    }
                    
                    if (filteredBitmap !== sourceForFilter) filteredBitmap.recycle()
                    sourceForFilter.recycle()
                    if (warpedBitmap != null && warpedBitmap !== sourceForFilter) warpedBitmap.recycle()
                }

                if (validFiles.isEmpty()) {
                    _exportState.value = ExportState.Failed(Exception("All pages corrupted or missing."))
                    return@launch
                }

                if (type == ExportType.PDF) {
                    val pdfUri = PdfGenerator.generatePdf(
                        context,
                        validFiles.map { Uri.fromFile(it) },
                        documentTitle.value.ifBlank { "Scanned_Document" },
                        params["pageSize"] as? String ?: "A4",
                        params["isGrayscale"] as? Boolean ?: false,
                        params["marginEnabled"] as? Boolean ?: false
                    )
                    if (pdfUri != null) {
                        val valid = validateExportUri(context, pdfUri, ExportType.PDF, validFiles.size)
                        if (valid) {
                            _exportState.value = ExportState.Success(pdfUri)
                        } else {
                            _exportState.value = ExportState.Failed(Exception("PDF validation failed — file may be corrupt or incomplete."))
                        }
                    } else {
                        _exportState.value = ExportState.Failed(Exception("PDF Generation failed"))
                    }
                } else {
                    val imgFormat = if (type == ExportType.JPG) ImageExporter.ImageFormat.JPG else ImageExporter.ImageFormat.PNG
                    val savedUris = ImageExporter.exportImages(
                        context,
                        validFiles.map { Uri.fromFile(it) },
                        imgFormat,
                        customName = documentTitle.value.ifBlank { "Scanned_Document" }
                    )
                    if (savedUris.isNotEmpty()) {
                        val allValid = savedUris.all { validateExportUri(context, it, type, 1) }
                        if (allValid) {
                            _exportState.value = ExportState.Success(savedUris.first())
                        } else {
                            _exportState.value = ExportState.Failed(Exception("Image validation failed — files may be corrupt."))
                        }
                    } else {
                        _exportState.value = ExportState.Failed(Exception("Image export failed"))
                    }
                }

            } catch (e: Exception) {
                Log.e("ScannerVM", "Export failed: ${e.message}", e)
                validFiles.forEach { it.delete() }
                _exportState.value = ExportState.Failed(e)
            } finally {
                exportMutex.unlock()
            }
        }
    }

    /** Returns correct MIME type string for the given export type */
    fun getMimeTypeForExport(type: ExportType): String = when (type) {
        ExportType.PDF -> "application/pdf"
        ExportType.JPG -> "image/jpeg"
        ExportType.PNG -> "image/png"
    }

    fun retryExport() {
        val ctx = lastExportContext ?: return
        val type = lastExportType ?: return
        startExport(ctx, type, lastExportParams)
    }

    private fun validateExportUri(context: Context, uri: Uri, expectedType: ExportType, expectedPageCount: Int): Boolean {
        return try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
            val size = fd.statSize
            if (size < 1024) { // Minimum 1KB for any valid export
                fd.close()
                return false
            }
            if (expectedType == ExportType.PDF) {
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                val actualPageCount = renderer.pageCount
                renderer.close()
                fd.close()
                actualPageCount == expectedPageCount
            } else {
                fd.close()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false 
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    // ── Temp File Management ────────────────────────────────────────────────

    fun cleanupUnusedTempFiles(context: Context, forceAll: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "scanner_cache")
            if (!cacheDir.exists()) return@launch
            
            // G2: Ensure live previews and temp previews are protected from deletion if in use
            val validCacheUris = _scannedPages.value.mapNotNull { it.previewCacheUri?.path } + 
                                 _scannedPages.value.mapNotNull { it.thumbnailCacheUri?.path } +
                                 _scannedPages.value.mapNotNull { it.croppedCacheUri?.path } +
                                 listOfNotNull(_tempEditPreviewUri.value?.path)
            
            cacheDir.listFiles()?.forEach { file ->
                if (forceAll || !validCacheUris.contains(file.absolutePath)) {
                    file.delete()
                    Log.d("ScannerVM", "[G2 Cleanup] Deleted orphaned cache file: ${file.name}")
                }
            }
            
            // Also clean scanner_exports if forceAll
            if (forceAll) {
                File(context.cacheDir, "scanner_exports").listFiles()?.forEach { 
                    it.delete()
                    Log.d("ScannerVM", "[G2 Cleanup] Deleted orphaned export file: ${it.name}")
                }
            }
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    fun createCaptureOutputFile(context: Context): File {
        val cacheDir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File.createTempFile("scan_", ".jpg", cacheDir)
    }

    fun correctImageRotation(file: File, displayRotation: Int) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            var rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationDegrees == 0f) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val isPortraitDisplay = (displayRotation == android.view.Surface.ROTATION_0 || displayRotation == android.view.Surface.ROTATION_180)
                val isLandscapeImage = options.outWidth > options.outHeight
                if (isPortraitDisplay && isLandscapeImage) rotationDegrees = 90f
                else if (!isPortraitDisplay && !isLandscapeImage) rotationDegrees = 90f
            }
            if (rotationDegrees == 0f) return

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            FileOutputStream(file).use { out -> rotated.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            if (rotated !== bitmap) bitmap.recycle()
            rotated.recycle()

            val newExif = ExifInterface(file.absolutePath)
            newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            newExif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
