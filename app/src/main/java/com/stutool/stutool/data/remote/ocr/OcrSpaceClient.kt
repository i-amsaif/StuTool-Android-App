package com.stutool.stutool.data.remote.ocr

import android.content.Context
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.stutool.stutool.BuildConfig
import com.stutool.stutool.domain.ocr.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

open class OcrSpaceClient(
    private val client: OkHttpClient = defaultOkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val apiKeyProvider: () -> String = { BuildConfig.OCR_SPACE_API_KEY }
) {
    companion object {
        private const val TAG = "OcrSpaceClient"
        const val DEFAULT_BASE_URL = "https://api.ocr.space/parse/image"

        // OCR.space Free Tier Limits
        const val MAX_FILE_SIZE_BYTES = 1024L * 1024L // 1 MB (1,048,576 bytes)
        const val MAX_PDF_PAGES = 3

        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }

        @Volatile
        var networkAvailabilityOverrideForTesting: Boolean? = null

        fun isNetworkAvailable(context: Context): Boolean {
            networkAvailabilityOverrideForTesting?.let { return it }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isTestEnv = Build.FINGERPRINT == "robolectric" || Build.HARDWARE == "robolectric"
                return if (isTestEnv) hasInternet else (hasInternet && isValidated)
            } else {
                @Suppress("DEPRECATION")
                val netInfo = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                return netInfo != null && netInfo.isConnected
            }
        }

        fun checkEligibility(fileSizeBytes: Long, pageCount: Int): CloudEligibility {
            if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
                return CloudEligibility.IneligibleSize(fileSizeBytes, MAX_FILE_SIZE_BYTES)
            }
            if (pageCount > MAX_PDF_PAGES) {
                return CloudEligibility.IneligiblePages(pageCount, MAX_PDF_PAGES)
            }
            return CloudEligibility.Eligible
        }
    }

    sealed class CloudEligibility {
        object Eligible : CloudEligibility()
        data class IneligibleSize(val actualBytes: Long, val maxBytes: Long) : CloudEligibility()
        data class IneligiblePages(val actualPages: Int, val maxPages: Int) : CloudEligibility()
    }

    /**
     * Executes cloud OCR on an image byte array.
     * Guaranteed watermark-free because isCreateSearchablePdf is NEVER requested.
     */
    open suspend fun recognizeImageBytes(
        bytes: ByteArray,
        filename: String = "image.jpg",
        mimeType: String = "image/jpeg",
        language: OcrLanguage = OcrLanguage.AUTO
    ): OcrPageResult = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            throw OcrLimitExceededException(
                "Image size (${bytes.size / 1024} KB) exceeds the 1 MB cloud OCR limit.",
                isSizeLimit = true
            )
        }

        val results = executeOcrRequest(bytes, filename, mimeType, language)
        if (results.isEmpty()) {
            throw OcrApiException(exitCode = 4, message = "No OCR results returned from OCR.space.")
        }
        results.first()
    }

    /**
     * Executes cloud OCR on a PDF document (up to 3 pages).
     * Returns a list of OcrPageResult, one for each page in the PDF.
     */
    open suspend fun recognizePdfBytes(
        bytes: ByteArray,
        filename: String = "document.pdf",
        language: OcrLanguage = OcrLanguage.AUTO
    ): List<OcrPageResult> = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            throw OcrLimitExceededException(
                "PDF size (${bytes.size / 1024} KB) exceeds the 1 MB cloud OCR limit.",
                isSizeLimit = true
            )
        }

        executeOcrRequest(bytes, filename, "application/pdf", language)
    }

    private suspend fun executeOcrRequest(
        bytes: ByteArray,
        filename: String,
        mimeType: String,
        language: OcrLanguage
    ): List<OcrPageResult> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty() || apiKey == "PLACEHOLDER") {
            throw OcrAuthException("OCR.space API key is not configured. Please add OCR_SPACE_API_KEY to local.properties or switch to Offline OCR.")
        }

        val startTime = System.currentTimeMillis()

        // Configure language and engine for OCR.space
        // Never send Tesseract compound codes like "kan+eng" to OCR.space API.
        // Use "auto" with OCREngine=2 for auto-detection.
        val apiLanguage = when {
            language == OcrLanguage.AUTO || language.ocrSpaceLanguage == "auto" -> "auto"
            language.ocrSpaceLanguage.contains("+") || language.ocrSpaceLanguage.isBlank() -> "auto"
            else -> language.ocrSpaceLanguage
        }
        val ocrEngine = if (apiLanguage == "auto") "2" else "1"

        // Build multipart request
        // NOTE: isCreateSearchablePdf is explicitly NOT included to prevent watermarked PDFs
        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("language", apiLanguage)
            .addFormDataPart("isOverlayRequired", "true")
            .addFormDataPart("detectOrientation", "true")
            .addFormDataPart("scale", "true")
            .addFormDataPart("OCREngine", ocrEngine)
            .addFormDataPart(
                "file",
                filename,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("apikey", apiKey)
            .post(requestBodyBuilder.build())
            .build()

        val response: Response
        try {
            response = client.newCall(request).execute()
        } catch (e: IOException) {
            throw OcrNetworkException("Failed to connect to OCR.space: ${e.localizedMessage ?: "Network error"}", e)
        }

        val elapsed = System.currentTimeMillis() - startTime

        response.use { resp ->
            val code = resp.code
            val bodyString = resp.body?.string().orEmpty()

            when (code) {
                200 -> {
                    parseOcrSpaceResponse(bodyString, language, elapsed)
                }
                403 -> {
                    throw OcrAuthException("OCR.space API key is invalid or quota has been exceeded.")
                }
                413 -> {
                    throw OcrLimitExceededException(
                        "Payload too large: File exceeds the OCR.space 1 MB limit.",
                        isSizeLimit = true
                    )
                }
                429 -> {
                    throw OcrRateLimitException(
                        "OCR.space rate limit reached (max 10 requests per 10 minutes on free tier). Please wait or switch to Offline OCR."
                    )
                }
                else -> {
                    val errorDetail = extractErrorMessage(bodyString)
                    throw OcrApiException(
                        exitCode = code,
                        message = "OCR.space request failed (HTTP $code): ${errorDetail.ifBlank { resp.message }}"
                    )
                }
            }
        }
    }

    private fun parseOcrSpaceResponse(
        jsonString: String,
        language: OcrLanguage,
        processingTimeMs: Long
    ): List<OcrPageResult> {
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw OcrApiException(exitCode = 4, message = "Malformed response received from OCR.space.")
        }

        val isErrored = root.optBoolean("IsErroredOnProcessing", false)
        val exitCode = root.optInt("OCRExitCode", 1)

        if (isErrored || exitCode == 4 || exitCode == 3) {
            val errMsg = extractErrorMessage(root)
            if (errMsg.contains("rate limit", ignoreCase = true) || errMsg.contains("E403", ignoreCase = true)) {
                throw OcrRateLimitException("OCR.space rate limit reached: $errMsg")
            }
            if (errMsg.contains("size", ignoreCase = true) || errMsg.contains("maximum", ignoreCase = true)) {
                throw OcrLimitExceededException("OCR.space limit exceeded: $errMsg", isSizeLimit = true)
            }
            throw OcrApiException(exitCode = exitCode, message = errMsg.ifBlank { "OCR processing failed on cloud engine." })
        }

        val parsedResultsArray = root.optJSONArray("ParsedResults") ?: JSONArray()
        val resultsList = mutableListOf<OcrPageResult>()

        for (i in 0 until parsedResultsArray.length()) {
            val resultObj = parsedResultsArray.optJSONObject(i) ?: continue
            val parsedText = resultObj.optString("ParsedText", "").trim()
            val textOverlay = resultObj.optJSONObject("TextOverlay")

            val lines = mutableListOf<OcrLine>()

            if (textOverlay != null) {
                val linesArray = textOverlay.optJSONArray("Lines") ?: JSONArray()
                for (j in 0 until linesArray.length()) {
                    val lineObj = linesArray.optJSONObject(j) ?: continue
                    val lineText = lineObj.optString("LineText", "").trim()
                    if (lineText.isBlank()) continue

                    val wordsArray = lineObj.optJSONArray("Words") ?: JSONArray()
                    val words = mutableListOf<OcrWord>()

                    var minX = Int.MAX_VALUE
                    var minY = Int.MAX_VALUE
                    var maxX = Int.MIN_VALUE
                    var maxY = Int.MIN_VALUE

                    for (k in 0 until wordsArray.length()) {
                        val wordObj = wordsArray.optJSONObject(k) ?: continue
                        val wordText = wordObj.optString("WordText", "")
                        val left = wordObj.optInt("Left", 0)
                        val top = wordObj.optInt("Top", 0)
                        val height = wordObj.optInt("Height", 0)
                        val width = wordObj.optInt("Width", 0)

                        minX = minOf(minX, left)
                        minY = minOf(minY, top)
                        maxX = maxOf(maxX, left + width)
                        maxY = maxOf(maxY, top + height)

                        words.add(
                            OcrWord(
                                text = wordText,
                                boundingBox = Rect(left, top, left + width, top + height)
                            )
                        )
                    }

                    val lineRect = if (minX < maxX && minY < maxY) {
                        Rect(minX, minY, maxX, maxY)
                    } else {
                        val minTop = lineObj.optInt("MinTop", 0)
                        val maxHeight = lineObj.optInt("MaxHeight", 20)
                        Rect(0, minTop, 100, minTop + maxHeight)
                    }

                    lines.add(
                        OcrLine(
                            text = lineText,
                            boundingBox = lineRect,
                            words = words
                        )
                    )
                }
            }

            resultsList.add(
                OcrPageResult(
                    text = parsedText,
                    lines = lines,
                    languageUsed = language,
                    engineSource = OcrEngineSource.CLOUD_OCR_SPACE,
                    processingTimeMs = processingTimeMs
                )
            )
        }

        return resultsList
    }

    private fun extractErrorMessage(root: JSONObject): String {
        val errorMsgObj = root.opt("ErrorMessage")
        if (errorMsgObj is JSONArray && errorMsgObj.length() > 0) {
            val list = mutableListOf<String>()
            for (i in 0 until errorMsgObj.length()) {
                list.add(errorMsgObj.optString(i))
            }
            return list.joinToString(", ")
        } else if (errorMsgObj is String && errorMsgObj.isNotBlank()) {
            return errorMsgObj
        }

        val details = root.optString("ErrorDetails", "")
        if (details.isNotBlank()) return details

        return ""
    }

    private fun extractErrorMessage(bodyString: String): String {
        return try {
            val root = JSONObject(bodyString)
            extractErrorMessage(root)
        } catch (_: Exception) {
            bodyString.take(200)
        }
    }
}
