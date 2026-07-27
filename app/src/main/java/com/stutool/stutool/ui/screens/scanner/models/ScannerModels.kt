package com.stutool.stutool.ui.screens.scanner.models

import android.net.Uri
import com.stutool.stutool.ui.screens.scanner.utils.Quad
import com.stutool.stutool.ui.screens.scanner.utils.ScanFilter

/**
 * Strict pipeline state machine.
 * Pages MUST advance through stages in order — no skipping allowed.
 *
 * IDLE → CROPPED → FILTERED → EXPORTED
 *
 * Migration note: legacy values CROPPING / FILTERING / READY are
 * remapped to their nearest safe state in ScannerSessionRepository.loadSession().
 */
enum class ScanPageState {
    IDLE,         // Freshly captured/imported — not yet entered the queue
    CROPPED,      // Crop stage committed successfully — awaiting filter stage
    FILTERED,     // Filter stage committed successfully — eligible for review
    EXPORTED      // Successfully exported; terminal state
}

/**
 * G3.2.6: Hard runtime state for processed asset hydration tracking.
 * Never persisted to JSON.
 */
enum class AssetState {
    EMPTY,       // No assets generated yet
    RESTORING,   // Needs hydration (set on JSON deserialize)
    HYDRATING,   // Currently regenerating assets
    READY,       // Assets validated successfully
    FAILED       // Asset generation/validation failed
}

enum class QueueStage {
    DECISION, CROP, FILTER, REVIEW
}

enum class BatchSource {
    CAMERA, GALLERY, MIXED, UNKNOWN
}

/**
 * G3: Strictly isolates active capture/import queues from finalized review pages.
 */
data class ProcessingBatch(
    val batchId: String,
    val pageIds: List<String>,
    val stage: QueueStage,
    val source: BatchSource = BatchSource.UNKNOWN
)

enum class CropMode {
    PERSPECTIVE, RECTANGULAR
}

enum class ExportType {
    PDF, JPG, PNG
}

sealed class ExportState {
    object Idle : ExportState()
    object Preparing : ExportState()
    data class Exporting(val pageIndex: Int) : ExportState()
    data class Success(val uri: Uri) : ExportState()
    data class Failed(val error: Throwable) : ExportState()
}

/**
 * G2: Unified crop geometry — single source of truth.
 * Every rendering path (crop preview, filter preview, thumbnail, export)
 * MUST use the exact same CropTransform. No independent recalculation.
 */
data class CropTransform(
    val quad: Quad,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int
)

data class PageTransform(
    val cropQuad: Quad?,             // Legacy — kept for backward compat during migration
    val filter: ScanFilter,
    val rotation: Int = 0,           // 0, 90, 180, 270 degrees clockwise
    val filterStrength: Float = 1.0f,
    val cropMode: CropMode = CropMode.PERSPECTIVE,
    val hasFilterOverride: Boolean = false, // True if user manually edited this page. Skipped by Batch Apply.
    val processingToken: Long = 0L,
    val edgeConfidence: Float = 0f,
    val autoEdgeApplied: Boolean = false,
    val cropTransform: CropTransform? = null  // G2: unified crop transform — preferred over cropQuad
) {
    /**
     * G2: Returns the crop quad to use, preferring the unified CropTransform.
     * All rendering paths must call this instead of accessing cropQuad directly.
     */
    fun effectiveCropQuad(): Quad? = cropTransform?.quad ?: cropQuad

    /**
     * G2: Returns the rotation to use, preferring the unified CropTransform.
     */
    fun effectiveRotation(): Int = cropTransform?.rotationDegrees ?: rotation
}

data class ScanPage(
    val id: String,
    val originalUri: Uri,
    val transform: PageTransform,
    val previewCacheUri: Uri?,
    val thumbnailCacheUri: Uri?,
    val croppedCacheUri: Uri? = null, // G3: Baked crop cache uri
    val state: ScanPageState,
    val isEdited: Boolean,
    val processingVersion: Long,
    val lastModified: Long,
    val isNew: Boolean = false,
    @Transient val assetState: AssetState = AssetState.EMPTY // G3.2.6: Runtime-only hydration state
)
