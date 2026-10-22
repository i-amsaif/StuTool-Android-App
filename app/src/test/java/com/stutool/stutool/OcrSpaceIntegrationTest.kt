package com.stutool.stutool

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.data.remote.ocr.OcrSpaceClient
import com.stutool.stutool.domain.ocr.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OcrSpaceIntegrationTest {

    private lateinit var application: Application
    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var ocrSpaceClient: OcrSpaceClient

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)

        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        ocrSpaceClient = OcrSpaceClient(
            client = okHttpClient,
            baseUrl = mockWebServer.url("/parse/image").toString(),
            apiKeyProvider = { "TEST_API_KEY_12345" }
        )
        OcrSpaceClient.networkAvailabilityOverrideForTesting = true
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        OcrSpaceClient.networkAvailabilityOverrideForTesting = null
        OcrEngineService.offlineRecognizerOverrideForTesting = null
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 1. OCR.space Client Limit Enforcements ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testRejectsOversizedImageBytesWithoutNetworkCall() {
        // Create dummy bytes exceeding 1024 KB
        val oversizedBytes = ByteArray(1024 * 1024 + 10)

        try {
            kotlinx.coroutines.runBlocking {
                ocrSpaceClient.recognizeImageBytes(
                    bytes = oversizedBytes,
                    filename = "large_image.jpg",
                    language = OcrLanguage.KANNADA_ENGLISH
                )
            }
            fail("Expected OcrLimitExceededException for file exceeding 1 MB")
        } catch (e: OcrLimitExceededException) {
            assertTrue("Exception must be marked as size limit", e.isSizeLimit)
            assertEquals("Mock server must not receive requests for oversized files", 0, mockWebServer.requestCount)
        }
    }

    @Test
    fun testRejectsOversizedPdfBytesWithoutNetworkCall() {
        val oversizedPdfBytes = ByteArray(1024 * 1024 + 500)

        try {
            kotlinx.coroutines.runBlocking {
                ocrSpaceClient.recognizePdfBytes(
                    bytes = oversizedPdfBytes,
                    filename = "large.pdf",
                    language = OcrLanguage.KANNADA_ENGLISH
                )
            }
            fail("Expected OcrLimitExceededException for PDF exceeding 1 MB")
        } catch (e: OcrLimitExceededException) {
            assertTrue(e.isSizeLimit)
            assertEquals(0, mockWebServer.requestCount)
        }
    }

    @Test
    fun testEligibilityChecker() {
        val eligible = OcrSpaceClient.checkEligibility(fileSizeBytes = 500 * 1024, pageCount = 2)
        assertTrue(eligible is OcrSpaceClient.CloudEligibility.Eligible)

        val sizeIneligible = OcrSpaceClient.checkEligibility(fileSizeBytes = 2 * 1024 * 1024, pageCount = 1)
        assertTrue(sizeIneligible is OcrSpaceClient.CloudEligibility.IneligibleSize)

        val pageIneligible = OcrSpaceClient.checkEligibility(fileSizeBytes = 300 * 1024, pageCount = 5)
        assertTrue(pageIneligible is OcrSpaceClient.CloudEligibility.IneligiblePages)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 2. Request Construction & Zero Watermark Verification ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testRequestDoesNotRequestSearchablePdfToAvoidWatermark() = kotlinx.coroutines.runBlocking {
        val sampleResponseJson = """
            {
              "ParsedResults": [
                {
                  "TextOverlay": {
                    "Lines": [
                      {
                        "LineText": "ಕರ್ನಾಟಕ Student Tool",
                        "Words": [
                          { "WordText": "ಕರ್ನಾಟಕ", "Left": 10, "Top": 20, "Height": 30, "Width": 80 },
                          { "WordText": "Student", "Left": 95, "Top": 20, "Height": 30, "Width": 60 },
                          { "WordText": "Tool", "Left": 160, "Top": 20, "Height": 30, "Width": 40 }
                        ],
                        "MaxHeight": 30,
                        "MinTop": 20
                      }
                    ],
                    "HasOverlay": true
                  },
                  "ParsedText": "ಕರ್ನಾಟಕ Student Tool\r\n",
                  "FileParseExitCode": 1
                }
              ],
              "OCRExitCode": 1,
              "IsErroredOnProcessing": false
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sampleResponseJson))

        val dummyBytes = ByteArray(500) { 1 }
        val result = ocrSpaceClient.recognizeImageBytes(
            bytes = dummyBytes,
            filename = "sample.jpg",
            language = OcrLanguage.AUTO
        )

        val recordedRequest = mockWebServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()

        // Verify API key header
        assertEquals("TEST_API_KEY_12345", recordedRequest.getHeader("apikey"))

        // CRITICAL: isCreateSearchablePdf must NOT be set to true (prevents free-tier watermark)
        assertFalse(
            "Request must NEVER include isCreateSearchablePdf=true to prevent watermark",
            requestBody.contains("isCreateSearchablePdf")
        )

        // Overlay must be requested for local coordinate generation
        assertTrue("Overlay must be requested", requestBody.contains("isOverlayRequired"))
        assertTrue("Language parameter must be passed as auto", requestBody.contains("name=\"language\"") && requestBody.contains("auto"))
        assertTrue("OCREngine parameter must be 2 for auto detection", requestBody.contains("name=\"OCREngine\"") && requestBody.contains("2"))
        assertFalse("Request must NEVER contain compound codes like kan+eng", requestBody.contains("kan+eng"))

        // Verify parsed result
        assertEquals("ಕರ್ನಾಟಕ Student Tool", result.text)
        assertEquals(OcrEngineSource.CLOUD_OCR_SPACE, result.engineSource)
        assertEquals(1, result.lines.size)
        val line = result.lines[0]
        assertEquals("ಕರ್ನಾಟಕ Student Tool", line.text)
        assertEquals(3, line.words.size)
        assertEquals("ಕರ್ನಾಟಕ", line.words[0].text)
        assertEquals(Rect(10, 20, 90, 50), line.words[0].boundingBox)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 3. HTTP Status and Error Code Mappings ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testHttp403MapsToAuthException() {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden: Invalid API Key"))

        try {
            kotlinx.coroutines.runBlocking {
                ocrSpaceClient.recognizeImageBytes(ByteArray(100), "test.jpg")
            }
            fail("Expected OcrAuthException")
        } catch (e: OcrAuthException) {
            assertTrue(e.message!!.contains("invalid or quota", ignoreCase = true))
        }
    }

    @Test
    fun testHttp413MapsToLimitExceededException() {
        mockWebServer.enqueue(MockResponse().setResponseCode(413).setBody("Payload Too Large"))

        try {
            kotlinx.coroutines.runBlocking {
                ocrSpaceClient.recognizeImageBytes(ByteArray(100), "test.jpg")
            }
            fail("Expected OcrLimitExceededException")
        } catch (e: OcrLimitExceededException) {
            assertTrue(e.isSizeLimit)
        }
    }

    @Test
    fun testHttp429MapsToRateLimitException() {
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))

        try {
            kotlinx.coroutines.runBlocking {
                ocrSpaceClient.recognizeImageBytes(ByteArray(100), "test.jpg")
            }
            fail("Expected OcrRateLimitException")
        } catch (e: OcrRateLimitException) {
            assertTrue(e.message!!.contains("rate limit", ignoreCase = true))
        }
    }

    @Test
    fun testApiJsonErrorHandling() {
        val errorJson = """
            {
              "ErrorMessage": ["File failed to process. Maximum file size exceeded."],
              "OCRExitCode": 4,
              "IsErroredOnProcessing": true
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(errorJson))

        try {
            kotlinx.coroutines.runBlocking {
                ocrSpaceClient.recognizeImageBytes(ByteArray(100), "test.jpg")
            }
            fail("Expected OcrLimitExceededException")
        } catch (e: OcrLimitExceededException) {
            assertTrue(e.message!!.contains("size", ignoreCase = true))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 4. Hybrid Routing & Fallback Verification ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testOcrModeAllowsCloudFlag() {
        assertTrue(OcrMode.SMART_AUTO.allowsCloud)
        assertTrue(OcrMode.CLOUD_ONLY.allowsCloud)
        assertFalse("Offline mode must strictly disallow cloud", OcrMode.OFFLINE_ONLY.allowsCloud)
    }

    @Test
    fun testSmartAutoFallbackOnCloudFailure() = kotlinx.coroutines.runBlocking {
        // Enqueue 500 Server Error for Cloud OCR
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        // Set the test client and offline fallback on OcrEngineService
        OcrEngineService.ocrSpaceClient = ocrSpaceClient
        OcrEngineService.offlineRecognizerOverrideForTesting = { _, _, lang ->
            OcrPageResult(
                text = "Fallback Text",
                languageUsed = lang,
                engineSource = OcrEngineSource.LOCAL_TESSERACT
            )
        }

        // Create a small test bitmap
        val bitmap = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)

        // In SMART_AUTO mode, cloud failure must fall back gracefully to offline Tesseract/ML Kit
        val result = OcrEngineService.recognizeBitmap(
            context = application,
            bitmap = bitmap,
            language = OcrLanguage.ENGLISH,
            mode = OcrMode.SMART_AUTO,
            useMlKitForEnglish = true
        )

        // Result should succeed via local engine without throwing exception to user
        assertNotNull(result)
        assertEquals("Fallback Text", result.text)
        assertEquals(OcrEngineSource.LOCAL_TESSERACT, result.engineSource)
    }

    @Test
    fun testCloudOnlyThrowsExceptionOnFailure() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        OcrEngineService.ocrSpaceClient = ocrSpaceClient

        val bitmap = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)

        try {
            kotlinx.coroutines.runBlocking {
                OcrEngineService.recognizeBitmap(
                    context = application,
                    bitmap = bitmap,
                    language = OcrLanguage.ENGLISH,
                    mode = OcrMode.CLOUD_ONLY
                )
            }
            fail("CLOUD_ONLY mode must throw exception when cloud fails")
        } catch (e: OcrApiException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun testNeverSendsCompoundLanguageCodeToCloud() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
              "ParsedResults": [{ "ParsedText": "Hello", "FileParseExitCode": 1 }],
              "OCRExitCode": 1,
              "IsErroredOnProcessing": false
            }
        """.trimIndent()))

        // Explicitly pass KANNADA_ENGLISH to ensure it maps safely to auto
        ocrSpaceClient.recognizeImageBytes(
            bytes = ByteArray(100) { 1 },
            language = OcrLanguage.KANNADA_ENGLISH
        )

        val recordedRequest = mockWebServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertTrue("Language must be auto", requestBody.contains("name=\"language\"") && requestBody.contains("auto"))
        assertTrue("Engine must be 2", requestBody.contains("name=\"OCREngine\"") && requestBody.contains("2"))
        assertFalse("Must never contain kan+eng", requestBody.contains("kan+eng"))
    }

    @Test
    fun testMissingApiKeyThrowsActionableAuthException() {
        val clientWithoutKey = OcrSpaceClient(
            client = okHttpClient,
            baseUrl = mockWebServer.url("/parse/image").toString(),
            apiKeyProvider = { "" }
        )

        try {
            kotlinx.coroutines.runBlocking {
                clientWithoutKey.recognizeImageBytes(ByteArray(100), "test.jpg")
            }
            fail("Expected OcrAuthException when API key is missing")
        } catch (e: OcrAuthException) {
            assertTrue("Exception must mention local.properties or OCR_SPACE_API_KEY",
                e.message!!.contains("OCR_SPACE_API_KEY") || e.message!!.contains("local.properties"))
        }
    }

    @Test
    fun testGalleryImageDecodingAndDimensions() {
        val sourceBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val tempFile = java.io.File(application.cacheDir, "test_gallery_img.jpg")
        java.io.FileOutputStream(tempFile).use { out ->
            sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val fileUri = android.net.Uri.fromFile(tempFile)

        // 1. Dimensions check
        val dims = com.stutool.stutool.domain.engine.PdfEngine.getImageDimensions(application, fileUri)
        assertNotNull(dims)
        assertEquals(800, dims!!.first)
        assertEquals(600, dims.second)

        // 2. Decode sampled bitmap
        val decoded = com.stutool.stutool.domain.engine.PdfEngine.decodeSampledBitmapFromUri(application, fileUri, 1000)
        assertNotNull(decoded)
        assertEquals(800, decoded!!.width)
        assertEquals(600, decoded.height)
        decoded.recycle()
        sourceBitmap.recycle()
        tempFile.delete()
    }

    @Test
    fun testSmartAutoEnforcesLimitsOnImages() {
        val oversizedTempFile = java.io.File(application.cacheDir, "oversized_img.bin")
        oversizedTempFile.writeBytes(ByteArray(1500 * 1024) { 1 })
        val fileUri = android.net.Uri.fromFile(oversizedTempFile)

        assertEquals(1500 * 1024L, com.stutool.stutool.domain.engine.PdfEngine.getFileSizeBytes(application, fileUri))

        try {
            kotlinx.coroutines.runBlocking {
                com.stutool.stutool.domain.engine.PdfEngine.performImageOcr(
                    context = application,
                    imageUri = fileUri,
                    mode = OcrMode.CLOUD_ONLY
                )
            }
            fail("CLOUD_ONLY mode must throw for image > 1 MB")
        } catch (e: OcrLimitExceededException) {
            assertTrue(e.isSizeLimit)
        } finally {
            oversizedTempFile.delete()
        }
    }
}
