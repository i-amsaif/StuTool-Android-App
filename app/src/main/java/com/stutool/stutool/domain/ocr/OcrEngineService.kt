package com.stutool.stutool.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.stutool.stutool.data.remote.ocr.OcrSpaceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrEngineService {
    private const val TAG = "OcrEngineService"
    private val tesseractExecutionMutex = Mutex()

    @Volatile
    var ocrSpaceClient: OcrSpaceClient = OcrSpaceClient()

    /**
     * Executes OCR on a given bitmap using hybrid routing:
     * - OFFLINE_ONLY: strictly Tesseract or ML Kit (100% on-device).
     * - SMART_AUTO: prefers OCR.space when online and eligible; falls back to offline OCR on error.
     * - CLOUD_ONLY: enforces OCR.space cloud processing, throwing targeted exceptions on failure.
     */
    suspend fun recognizeBitmap(
        context: Context,
        bitmap: Bitmap,
        language: OcrLanguage = OcrLanguage.AUTO,
        mode: OcrMode = OcrMode.DEFAULT,
        useMlKitForEnglish: Boolean = true
    ): OcrPageResult = withContext(Dispatchers.IO) {
        ensureActive()

        // 1. Strict Offline Mode
        if (mode == OcrMode.OFFLINE_ONLY) {
            return@withContext recognizeOffline(context, bitmap, language, useMlKitForEnglish)
        }

        // 2. Cloud-Only Mode
        if (mode == OcrMode.CLOUD_ONLY) {
            if (!OcrSpaceClient.isNetworkAvailable(context)) {
                throw OcrNetworkException("Internet connection is required for Cloud OCR. Please connect to Wi-Fi/data or switch to Offline OCR.")
            }

            val compressed = compressBitmapForCloud(bitmap)
                ?: throw OcrLimitExceededException(
                    "Image could not be compressed under the 1 MB Cloud OCR limit. Please switch to Offline OCR.",
                    isSizeLimit = true
                )

            val rawResult = ocrSpaceClient.recognizeImageBytes(
                bytes = compressed.bytes,
                filename = "page_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                language = language
            )

            return@withContext rescaleResultIfNeeded(rawResult, compressed.scaleX, compressed.scaleY)
        }

        // 3. Smart Auto Mode (Recommended)
        // Prefer Cloud if online and eligible; fallback cleanly to offline on any failure
        if (OcrSpaceClient.isNetworkAvailable(context)) {
            try {
                val compressed = compressBitmapForCloud(bitmap)
                if (compressed != null) {
                    val rawResult = ocrSpaceClient.recognizeImageBytes(
                        bytes = compressed.bytes,
                        filename = "page_${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg",
                        language = language
                    )
                    if (rawResult.text.isNotBlank() || rawResult.lines.isNotEmpty()) {
                        return@withContext rescaleResultIfNeeded(rawResult, compressed.scaleX, compressed.scaleY)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Smart Auto: Cloud OCR failed or ineligible (${e.message}). Falling back to local offline OCR.")
            }
        }

        // Offline fallback
        recognizeOffline(context, bitmap, language, useMlKitForEnglish)
    }

    @Volatile
    var offlineRecognizerOverrideForTesting: (suspend (Context, Bitmap, OcrLanguage) -> OcrPageResult)? = null

    @Volatile
    var mlKitRecognizerOverrideForTesting: (suspend (Bitmap) -> OcrPageResult)? = null

    @Volatile
    var tesseractRecognizerOverrideForTesting: (suspend (Context, Bitmap, OcrLanguage) -> OcrPageResult)? = null

    internal suspend fun recognizeOffline(
        context: Context,
        bitmap: Bitmap,
        language: OcrLanguage,
        useMlKitForEnglish: Boolean = true
    ): OcrPageResult {
        offlineRecognizerOverrideForTesting?.let { override ->
            return override(context, bitmap, language)
        }

        // Apply OCR-safe preprocessing (dimension safety, white background flattening, inversion)
        val preprocessed = OcrImagePreprocessor.preprocess(bitmap)
        val workingBitmap = preprocessed.bitmap

        try {
            when (language) {
                OcrLanguage.ENGLISH -> {
                    // English mode: ML Kit Latin is primary (100% immune to Kannada hallucinations)
                    try {
                        return recognizeWithMlKit(workingBitmap)
                    } catch (e: Exception) {
                        Log.w(TAG, "ML Kit recognition failed, falling back to Tesseract eng: ${e.message}")
                        return recognizeWithTesseract(context, workingBitmap, OcrLanguage.ENGLISH)
                    }
                }

                OcrLanguage.KANNADA, OcrLanguage.HINDI, OcrLanguage.KANNADA_ENGLISH -> {
                    // Explicit Indic / Bilingual selection: Tesseract with targeted models
                    return recognizeWithTesseract(context, workingBitmap, language)
                }

                OcrLanguage.AUTO -> {
                    // Intelligent multi-script auto-detection
                    return recognizeAutoScript(context, workingBitmap)
                }
            }
        } finally {
            preprocessed.recycleIfTemporary()
        }
    }

    internal suspend fun recognizeAutoScript(
        context: Context,
        bitmap: Bitmap
    ): OcrPageResult {
        // Pass 1: Google ML Kit Latin pass (fast, robust against noise, no Indic contamination)
        var mlKitResult: OcrPageResult? = null
        try {
            mlKitResult = recognizeWithMlKit(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit pre-pass encountered an error: ${e.message}")
        }

        // If ML Kit found a comprehensive, authentic Latin/English document:
        if (mlKitResult != null && isComprehensiveLatinDocument(mlKitResult, bitmap.width, bitmap.height)) {
            Log.d(TAG, "Document confidently identified as Latin/English via ML Kit. Returning high-fidelity result.")
            return mlKitResult
        }

        // Pass 2: Tesseract multi-script pass (eng+kan+hin)
        // Invoked when ML Kit output is blank/sparse (indicating Kannada/Hindi script) or partial header.
        val tessResult = try {
            recognizeWithTesseract(context, bitmap, OcrLanguage.AUTO)
        } catch (e: Exception) {
            Log.w(TAG, "Tesseract multi-script recognition failed: ${e.message}")
            if (mlKitResult != null && mlKitResult.text.isNotBlank()) {
                return mlKitResult
            }
            throw e
        }

        // Script analysis on Tesseract output
        val tessText = tessResult.text
        val kannadaCount = tessText.count { it.code in 0x0C80..0x0CFF }
        val hindiCount = tessText.count { it.code in 0x0900..0x097F }
        val totalIndic = kannadaCount + hindiCount
        val totalNonSpace = tessText.count { !it.isWhitespace() }
        val indicRatio = if (totalNonSpace > 0) totalIndic.toFloat() / totalNonSpace.toFloat() else 0f

        // Document genuinely contains Indic script if:
        // - At least 25 Indic characters, OR
        // - At least 12 Indic characters AND at least 10% of non-whitespace characters are Indic
        val hasGenuineIndicScript = totalIndic >= 25 || (totalIndic >= 12 && indicRatio >= 0.10f)

        if (hasGenuineIndicScript) {
            val resolvedLang = when {
                kannadaCount > 0 && hindiCount == 0 -> OcrLanguage.KANNADA
                hindiCount > 0 && kannadaCount == 0 -> OcrLanguage.HINDI
                else -> OcrLanguage.AUTO
            }
            return tessResult.copy(languageUsed = resolvedLang)
        }

        // No genuine Indic script found: document is Latin/English.
        // If ML Kit found authentic text, ML Kit is vastly superior (zero Indic contamination, higher accuracy).
        if (mlKitResult != null && SmartTextDetector.isAuthenticNativeText(mlKitResult.text)) {
            return mlKitResult
        }

        // Fallback: If ML Kit was blank or unavailable, use Tesseract result with any stray Indic glyphs sanitized
        val cleanedText = sanitizeIndicNoiseFromLatinText(tessText)
        return tessResult.copy(
            text = cleanedText,
            languageUsed = OcrLanguage.ENGLISH
        )
    }

    internal fun isComprehensiveLatinDocument(result: OcrPageResult, pageW: Int, pageH: Int): Boolean {
        val text = result.text.trim()
        if (text.length < 80) return false
        if (!SmartTextDetector.isAuthenticNativeText(text)) return false

        val words = text.split("\\s+".toRegex()).filter { it.length >= 2 }
        if (words.size < 15) return false

        // Substantial word count (>= 35 words) confirms a full-page Latin document body
        if (words.size >= 35) {
            return true
        }

        // For moderate length (15-34 words), verify that text lines span across the page body
        if (pageH > 0 && result.lines.size >= 4) {
            val minY = result.lines.minOfOrNull { it.boundingBox.top } ?: 0
            val maxY = result.lines.maxOfOrNull { it.boundingBox.bottom } ?: 0
            val span = (maxY - minY).toFloat() / pageH.toFloat()
            if (span >= 0.35f) {
                val commonEnglish = setOf(
                    "the", "and", "to", "of", "in", "is", "for", "with", "on", "at", "by",
                    "this", "that", "from", "are", "be", "as", "an", "was", "we", "you",
                    "student", "tool", "resume", "page", "date", "name", "total", "skills",
                    "experience", "education", "project", "university", "school", "phone"
                )
                val matchCount = words.count { commonEnglish.contains(it.lowercase()) }
                return matchCount >= 2
            }
        }

        return false
    }

    internal fun sanitizeIndicNoiseFromLatinText(text: String): String {
        return text.filter { c ->
            val code = c.code
            code !in 0x0C80..0x0CFF && code !in 0x0900..0x097F
        }.trim()
    }

    private class CompressedBitmapResult(
        val bytes: ByteArray,
        val scaleX: Float,
        val scaleY: Float
    )

    private fun compressBitmapForCloud(bitmap: Bitmap): CompressedBitmapResult? {
        val stream = ByteArrayOutputStream()
        var quality = 85
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        var bytes = stream.toByteArray()

        if (bytes.size <= OcrSpaceClient.MAX_FILE_SIZE_BYTES) {
            return CompressedBitmapResult(bytes, 1.0f, 1.0f)
        }

        // Try quality 65
        stream.reset()
        quality = 65
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        bytes = stream.toByteArray()
        if (bytes.size <= OcrSpaceClient.MAX_FILE_SIZE_BYTES) {
            return CompressedBitmapResult(bytes, 1.0f, 1.0f)
        }

        // Scale down dimensions to fit within 1 MB limit
        val ratio = Math.sqrt(OcrSpaceClient.MAX_FILE_SIZE_BYTES.toDouble() / bytes.size.toDouble()) * 0.92
        val newW = (bitmap.width * ratio).toInt().coerceAtLeast(100)
        val newH = (bitmap.height * ratio).toInt().coerceAtLeast(100)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)

        try {
            stream.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            bytes = stream.toByteArray()

            if (bytes.size <= OcrSpaceClient.MAX_FILE_SIZE_BYTES) {
                val scaleX = bitmap.width.toFloat() / newW.toFloat()
                val scaleY = bitmap.height.toFloat() / newH.toFloat()
                return CompressedBitmapResult(bytes, scaleX, scaleY)
            }
        } finally {
            if (scaled != bitmap) {
                scaled.recycle()
            }
        }

        return null
    }

    private fun rescaleResultIfNeeded(
        result: OcrPageResult,
        scaleX: Float,
        scaleY: Float
    ): OcrPageResult {
        if (scaleX == 1.0f && scaleY == 1.0f) return result

        val scaledLines = result.lines.map { line ->
            val origLineBox = line.boundingBox
            val scaledLineBox = Rect(
                (origLineBox.left * scaleX).toInt(),
                (origLineBox.top * scaleY).toInt(),
                (origLineBox.right * scaleX).toInt(),
                (origLineBox.bottom * scaleY).toInt()
            )

            val scaledWords = line.words.map { word ->
                val origWordBox = word.boundingBox
                OcrWord(
                    text = word.text,
                    boundingBox = Rect(
                        (origWordBox.left * scaleX).toInt(),
                        (origWordBox.top * scaleY).toInt(),
                        (origWordBox.right * scaleX).toInt(),
                        (origWordBox.bottom * scaleY).toInt()
                    ),
                    confidence = word.confidence
                )
            }

            OcrLine(
                text = line.text,
                boundingBox = scaledLineBox,
                words = scaledWords
            )
        }

        return result.copy(lines = scaledLines)
    }

    internal suspend fun recognizeWithTesseract(
        context: Context,
        bitmap: Bitmap,
        language: OcrLanguage
    ): OcrPageResult = withContext(Dispatchers.IO) {
        tesseractRecognizerOverrideForTesting?.let { override ->
            return@withContext override(context, bitmap, language)
        }

        tesseractExecutionMutex.withLock {
            ensureActive()
            val dataPath = TessDataManager.ensureTessData(context, language)
            val tessBaseAPI = TessBaseAPI()

            try {
                val initialized = tessBaseAPI.init(dataPath, language.tessLanguages)
                if (!initialized) {
                    throw IllegalStateException("Failed to initialize Tesseract with language model: ${language.tessLanguages}")
                }

                ensureActive()
                tessBaseAPI.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                tessBaseAPI.setImage(bitmap)

                // Trigger OCR processing
                val extractedText = tessBaseAPI.utF8Text ?: ""
                ensureActive()

                val lines = mutableListOf<OcrLine>()
                val blocks = mutableListOf<OcrBlock>()

                val iterator = tessBaseAPI.resultIterator
                if (iterator != null) {
                    try {
                        iterator.begin()
                        val levelLine = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE

                        do {
                            ensureActive()
                            val lineText = iterator.getUTF8Text(levelLine) ?: ""
                            val lineRect = iterator.getBoundingRect(levelLine) ?: Rect(0, 0, 0, 0)

                            if (lineText.isNotBlank()) {
                                lines.add(
                                    OcrLine(
                                        text = lineText.trim(),
                                        boundingBox = lineRect,
                                        words = emptyList()
                                    )
                                )
                            }
                        } while (iterator.next(levelLine))
                    } finally {
                        iterator.delete()
                    }
                }

                OcrPageResult(
                    text = extractedText.trim(),
                    lines = lines,
                    blocks = blocks,
                    languageUsed = language,
                    engineSource = OcrEngineSource.LOCAL_TESSERACT
                )
            } finally {
                try {
                    tessBaseAPI.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Error recycling TessBaseAPI: ${e.message}")
                }
            }
        }
    }

    internal suspend fun recognizeWithMlKit(bitmap: Bitmap): OcrPageResult = withContext(Dispatchers.IO) {
        mlKitRecognizerOverrideForTesting?.let { override ->
            return@withContext override(bitmap)
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            }

            val lines = mutableListOf<OcrLine>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val box = line.boundingBox ?: Rect(0, 0, 0, 0)
                    lines.add(
                        OcrLine(
                            text = line.text,
                            boundingBox = box,
                            words = line.elements.map { el ->
                                OcrWord(
                                    text = el.text,
                                    boundingBox = el.boundingBox ?: Rect(0, 0, 0, 0)
                                )
                            }
                        )
                    )
                }
            }

            OcrPageResult(
                text = visionText.text.trim(),
                lines = lines,
                languageUsed = OcrLanguage.ENGLISH,
                engineSource = OcrEngineSource.LOCAL_MLKIT
            )
        } finally {
            try {
                recognizer.close()
            } catch (_: Exception) {}
        }
    }
}
