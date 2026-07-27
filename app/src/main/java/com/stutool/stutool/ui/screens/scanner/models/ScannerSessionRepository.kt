package com.stutool.stutool.ui.screens.scanner.models

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.stutool.stutool.ui.screens.scanner.utils.Quad
import com.stutool.stutool.ui.screens.scanner.utils.ScanFilter
import com.stutool.stutool.ui.screens.scanner.models.ProcessingBatch
import com.stutool.stutool.ui.screens.scanner.models.QueueStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "ScannerSessionRepo"

class ScannerSessionRepository(private val context: Context) {

    private val sessionFile = File(context.filesDir, "scanner_session_datastore.json")
    private val atomicFile = AtomicFile(sessionFile)

    suspend fun saveSession(
        pages: List<ScanPage>,
        activeBatch: ProcessingBatch? = null,
        activeBatchPages: List<ScanPage> = emptyList()
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "[G3.2 RESTORE] Save start: pages=${pages.size}, batchPages=${activeBatchPages.size}")
        pages.forEach { Log.e("CropTrace_SAVE", "cropQuad before datastore write (page ${it.id}): ${it.transform.cropQuad}") }
        val root = JSONObject()

        fun serializePage(page: ScanPage): JSONObject {
            return JSONObject().apply {
                put("id", page.id)
                put("originalUri", page.originalUri.toString())

                val transformObj = JSONObject().apply {
                    put("filter", page.transform.filter.name)
                    put("rotation", page.transform.rotation)
                    put("filterStrength", page.transform.filterStrength.toDouble())
                    put("cropMode", page.transform.cropMode.name)
                    put("hasFilterOverride", page.transform.hasFilterOverride)
                    put("processingToken", page.transform.processingToken)
                    put("edgeConfidence", page.transform.edgeConfidence.toDouble())
                    put("autoEdgeApplied", page.transform.autoEdgeApplied)

                    page.transform.cropQuad?.let { quad ->
                        val quadObj = JSONObject().apply {
                            put("topLeftX", quad.topLeft.x.toDouble())
                            put("topLeftY", quad.topLeft.y.toDouble())
                            put("topRightX", quad.topRight.x.toDouble())
                            put("topRightY", quad.topRight.y.toDouble())
                            put("bottomRightX", quad.bottomRight.x.toDouble())
                            put("bottomRightY", quad.bottomRight.y.toDouble())
                            put("bottomLeftX", quad.bottomLeft.x.toDouble())
                            put("bottomLeftY", quad.bottomLeft.y.toDouble())
                        }
                        put("cropQuad", quadObj)
                    }

                    // G2: Serialize unified CropTransform
                    page.transform.cropTransform?.let { ct ->
                        val ctObj = JSONObject().apply {
                            put("imageWidth", ct.imageWidth)
                            put("imageHeight", ct.imageHeight)
                            put("rotationDegrees", ct.rotationDegrees)
                            val quadObj = JSONObject().apply {
                                put("topLeftX", ct.quad.topLeft.x.toDouble())
                                put("topLeftY", ct.quad.topLeft.y.toDouble())
                                put("topRightX", ct.quad.topRight.x.toDouble())
                                put("topRightY", ct.quad.topRight.y.toDouble())
                                put("bottomRightX", ct.quad.bottomRight.x.toDouble())
                                put("bottomRightY", ct.quad.bottomRight.y.toDouble())
                                put("bottomLeftX", ct.quad.bottomLeft.x.toDouble())
                                put("bottomLeftY", ct.quad.bottomLeft.y.toDouble())
                            }
                            put("quad", quadObj)
                        }
                        put("cropTransform", ctObj)
                    }
                }
                put("transform", transformObj)

                page.previewCacheUri?.let { put("previewCacheUri", it.toString()) }
                page.thumbnailCacheUri?.let { put("thumbnailCacheUri", it.toString()) }
                page.croppedCacheUri?.let { put("croppedCacheUri", it.toString()) }
                put("state", page.state.name)
                put("isEdited", page.isEdited)
                put("processingVersion", page.processingVersion)
                put("lastModified", page.lastModified)
                put("isNew", page.isNew)
            }
        }

        val finalizedPagesArray = JSONArray()
        for (page in pages) finalizedPagesArray.put(serializePage(page))
        root.put("pages", finalizedPagesArray)

        if (activeBatch != null && activeBatchPages.isNotEmpty()) {
            val batchObj = JSONObject().apply {
                put("batchId", activeBatch.batchId)
                put("pageIds", JSONArray(activeBatch.pageIds))
                put("stage", activeBatch.stage.name)
                put("source", activeBatch.source.name)
            }
            root.put("activeBatch", batchObj)

            val batchPagesArray = JSONArray()
            for (page in activeBatchPages) batchPagesArray.put(serializePage(page))
            root.put("activeBatchPages", batchPagesArray)
        }

        var fos: FileOutputStream? = null
        try {
            fos = atomicFile.startWrite()
            fos.write(root.toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
            Log.d(TAG, "[G3.2 RESTORE] Save success")
        } catch (e: Exception) {
            atomicFile.failWrite(fos)
            Log.e(TAG, "[G3.2 RESTORE] Failed to write session file", e)
        }
    }

    data class SessionData(
        val pages: List<ScanPage>,
        val activeBatch: ProcessingBatch?,
        val activeBatchPages: List<ScanPage>,
        val hadCorruption: Boolean = false  // G3: one-time recovery flag
    )

    suspend fun loadSession(): SessionData = withContext(Dispatchers.IO) {
        Log.d(TAG, "[G3.2 RESTORE] Restore start")
        if (!sessionFile.exists()) return@withContext SessionData(emptyList(), null, emptyList())

        var hadCorruption = false

        try {
            val jsonString = atomicFile.readFully().toString(Charsets.UTF_8)
            val root = JSONObject(jsonString)

            fun deserializePageArray(jsonArray: JSONArray): List<ScanPage> {
                val parsedPages = mutableListOf<ScanPage>()
                for (i in 0 until jsonArray.length()) {
                    try {
                        val pageObj = jsonArray.getJSONObject(i)

                    val transformObj = pageObj.getJSONObject("transform")
                    val filterName = transformObj.getString("filter")
                    val filter = when (filterName) {
                        "AUTO", "COLOR" -> ScanFilter.ENHANCED
                        "BW" -> ScanFilter.BW_1
                        else -> try {
                            ScanFilter.valueOf(filterName)
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "[G2 Restore] Invalid filter '$filterName', defaulting to ENHANCED")
                            ScanFilter.ENHANCED
                        }
                    }
                    val rotation = if (transformObj.has("rotation")) transformObj.getInt("rotation") else 0
                    val filterStrength = if (transformObj.has("filterStrength")) transformObj.getDouble("filterStrength").toFloat() else 1.0f
                    val cropMode = if (transformObj.has("cropMode")) {
                        try {
                            CropMode.valueOf(transformObj.getString("cropMode"))
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "[G2 Restore] Invalid cropMode, defaulting to PERSPECTIVE")
                            CropMode.PERSPECTIVE
                        }
                    } else CropMode.PERSPECTIVE
                    val hasFilterOverride = if (transformObj.has("hasFilterOverride")) {
                        transformObj.getBoolean("hasFilterOverride")
                    } else if (transformObj.has("isFilterLocked")) {
                        transformObj.getBoolean("isFilterLocked")
                    } else false
                    val processingToken = if (transformObj.has("processingToken")) transformObj.getLong("processingToken") else 0L
                    val edgeConfidence = if (transformObj.has("edgeConfidence")) transformObj.getDouble("edgeConfidence").toFloat() else 0f
                    val autoEdgeApplied = if (transformObj.has("autoEdgeApplied")) transformObj.getBoolean("autoEdgeApplied") else false

                    var quad: Quad? = null
                    if (transformObj.has("cropQuad")) {
                        val quadObj = transformObj.getJSONObject("cropQuad")
                        quad = Quad(
                            topLeft = android.graphics.PointF(quadObj.getDouble("topLeftX").toFloat(), quadObj.getDouble("topLeftY").toFloat()),
                            topRight = android.graphics.PointF(quadObj.getDouble("topRightX").toFloat(), quadObj.getDouble("topRightY").toFloat()),
                            bottomRight = android.graphics.PointF(quadObj.getDouble("bottomRightX").toFloat(), quadObj.getDouble("bottomRightY").toFloat()),
                            bottomLeft = android.graphics.PointF(quadObj.getDouble("bottomLeftX").toFloat(), quadObj.getDouble("bottomLeftY").toFloat())
                        )
                    }

                    // G2: Deserialize unified CropTransform (backward-compatible)
                    var cropTransform: CropTransform? = null
                    if (transformObj.has("cropTransform")) {
                        try {
                            val ctObj = transformObj.getJSONObject("cropTransform")
                            val ctQuadObj = ctObj.getJSONObject("quad")
                            cropTransform = CropTransform(
                                quad = Quad(
                                    topLeft = android.graphics.PointF(ctQuadObj.getDouble("topLeftX").toFloat(), ctQuadObj.getDouble("topLeftY").toFloat()),
                                    topRight = android.graphics.PointF(ctQuadObj.getDouble("topRightX").toFloat(), ctQuadObj.getDouble("topRightY").toFloat()),
                                    bottomRight = android.graphics.PointF(ctQuadObj.getDouble("bottomRightX").toFloat(), ctQuadObj.getDouble("bottomRightY").toFloat()),
                                    bottomLeft = android.graphics.PointF(ctQuadObj.getDouble("bottomLeftX").toFloat(), ctQuadObj.getDouble("bottomLeftY").toFloat())
                                ),
                                imageWidth = ctObj.getInt("imageWidth"),
                                imageHeight = ctObj.getInt("imageHeight"),
                                rotationDegrees = ctObj.getInt("rotationDegrees")
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "[G2 Restore] Failed to parse cropTransform, falling back to legacy quad", e)
                        }
                    }

                    // ── Legacy State Migration ──────────────────────────────────────────
                    // Remap old state names (CROPPING / FILTERING / READY) that may exist
                    // in sessions saved before G1 was applied.
                    val rawState = pageObj.getString("state")
                    val migratedState = when (rawState) {
                        "CROPPING" -> ScanPageState.CROPPED   // Was mid-crop → treat as crop complete
                        "FILTERING" -> ScanPageState.CROPPED  // Was mid-filter → restart filter from CROPPED
                        "READY" -> ScanPageState.FILTERED      // Was review-ready → treat as filtered
                        "FAILED" -> ScanPageState.IDLE         // Failed → reset to IDLE so user can re-process
                        else -> try {
                            ScanPageState.valueOf(rawState)
                        } catch (ex: IllegalArgumentException) {
                            Log.w(TAG, "[G2 Restore] Invalid state '$rawState', resetting to IDLE")
                            hadCorruption = true
                            ScanPageState.IDLE
                        }
                    }

                    // G2: Validate originalUri exists and is readable
                    val originalUri = Uri.parse(pageObj.getString("originalUri"))
                    val uriReadable = try {
                        if (originalUri.scheme == "file") {
                            val path = originalUri.path
                            path != null && File(path).exists() && File(path).length() > 0
                        } else {
                            val stream = context.contentResolver.openInputStream(originalUri)
                            val readable = stream != null
                            stream?.close()
                            readable
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[G3.2 RESTORE] Restore validation failed: URI unreadable for page ${pageObj.optString("id")}: $originalUri", e)
                        false
                    }

                    if (!uriReadable) {
                        Log.w(TAG, "[G3.2 RESTORE] Skipped invalid page ${pageObj.optString("id")} — original URI unreadable. Corruption trigger reason: Unreadable source URI.")
                        hadCorruption = true
                        continue  // Skip this page entirely
                    }

                    // G2: Validate cache files exist — reset to null if missing for background regen
                    var previewCacheUri: Uri? = if (pageObj.has("previewCacheUri")) Uri.parse(pageObj.getString("previewCacheUri")) else null
                    var thumbnailCacheUri: Uri? = if (pageObj.has("thumbnailCacheUri")) Uri.parse(pageObj.getString("thumbnailCacheUri")) else null
                    var croppedCacheUri: Uri? = if (pageObj.has("croppedCacheUri")) Uri.parse(pageObj.getString("croppedCacheUri")) else null

                    val processingVersion = pageObj.getLong("processingVersion")

                    if (previewCacheUri != null) {
                        val path = previewCacheUri.path
                        if (path == null || !File(path).exists() || File(path).length() == 0L || !path.contains(processingVersion.toString())) {
                            Log.w(TAG, "[G3.2.2 PREVIEW] Preview cache stale or missing for page ${pageObj.optString("id")}, invalidating")
                            previewCacheUri = null
                        }
                    }
                    if (thumbnailCacheUri != null) {
                        val path = thumbnailCacheUri.path
                        if (path == null || !File(path).exists() || File(path).length() == 0L || !path.contains(processingVersion.toString())) {
                            Log.w(TAG, "[G3.2.2 PREVIEW] Thumbnail cache stale or missing for page ${pageObj.optString("id")}, invalidating")
                            thumbnailCacheUri = null
                        }
                    }
                    // G3 Fallback: If crop cache unreadable, DO NOT discard page, just null it so it regenerates
                    if (croppedCacheUri != null) {
                        val path = croppedCacheUri.path
                        if (path == null || !File(path).exists() || File(path).length() == 0L) {
                            Log.w(TAG, "[G3 Restore] Cropped cache missing/corrupt for page ${pageObj.optString("id")}, will regenerate from original")
                            croppedCacheUri = null
                        }
                    }

                    Log.e("CropTrace_RESTORE", "cropQuad after datastore read (page ${pageObj.getString("id")}): $quad")
                    parsedPages.add(
                        ScanPage(
                            id = pageObj.getString("id"),
                            originalUri = originalUri,
                            transform = PageTransform(
                                cropQuad = quad,
                                filter = filter,
                                rotation = rotation,
                                filterStrength = filterStrength,
                                cropMode = cropMode,
                                hasFilterOverride = hasFilterOverride,
                                processingToken = processingToken,
                                edgeConfidence = edgeConfidence,
                                autoEdgeApplied = autoEdgeApplied,
                                cropTransform = cropTransform
                            ),
                            previewCacheUri = previewCacheUri,
                            thumbnailCacheUri = thumbnailCacheUri,
                            croppedCacheUri = croppedCacheUri,
                            state = migratedState,
                            isEdited = pageObj.getBoolean("isEdited"),
                            processingVersion = processingVersion,
                            lastModified = pageObj.getLong("lastModified"),
                            isNew = if (pageObj.has("isNew")) pageObj.getBoolean("isNew") else false,
                            assetState = AssetState.RESTORING // G3.2.6: Force state to RESTORING to enforce hydration barrier
                        )
                    )
                } catch (e: Exception) {
                    // G3: Fail-isolate per page — don't lose entire session for one bad entry
                    Log.e(TAG, "[G3.2 RESTORE] Skipped invalid page at index $i, corruption trigger reason: JSON parse error", e)
                    hadCorruption = true
                }
            }
            return parsedPages
        }

            val pages = if (root.has("pages")) deserializePageArray(root.getJSONArray("pages")) else emptyList()
            val activeBatchPages = if (root.has("activeBatchPages")) deserializePageArray(root.getJSONArray("activeBatchPages")) else emptyList()

            // Load active batch if present
            val activeBatch = if (root.has("activeBatch")) {
                try {
                    val batchObj = root.getJSONObject("activeBatch")
                    val idsArray = batchObj.getJSONArray("pageIds")
                    val ids = (0 until idsArray.length()).map { idsArray.getString(it) }
                    ProcessingBatch(
                        batchId = batchObj.getString("batchId"),
                        pageIds = ids,
                        stage = try {
                            QueueStage.valueOf(batchObj.optString("stage", "CROP"))
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "[G3.2 RESTORE] Corruption trigger reason: Invalid batch stage, defaulting to CROP")
                            hadCorruption = true
                            QueueStage.CROP
                        },
                        source = try {
                            BatchSource.valueOf(batchObj.optString("source", "UNKNOWN"))
                        } catch (e: IllegalArgumentException) {
                            BatchSource.UNKNOWN
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "[G3.2 RESTORE] Corruption trigger reason: Failed to parse active batch, discarding", e)
                    hadCorruption = true
                    null
                }
            } else null

            Log.e("CropTrace_RESTORE", "cropQuad after restore: " + pages.joinToString { "${it.id}: ${it.transform.cropQuad}" })
            if (hadCorruption) {
                Log.w(TAG, "[G3.2 RESTORE] Session loaded with corruption repairs (Crash recovery path)")
            } else {
                Log.d(TAG, "[G3.2 RESTORE] Session loaded cleanly: ${pages.size} finalized, ${activeBatchPages.size} active")
            }

            SessionData(pages, activeBatch, activeBatchPages, hadCorruption)
        } catch (e: Exception) {
            Log.e(TAG, "[G3.2 RESTORE] Corruption trigger reason: Failed to load session file entirely (Crash recovery path)", e)
            SessionData(emptyList(), null, emptyList(), true)
        }
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
    }
}
