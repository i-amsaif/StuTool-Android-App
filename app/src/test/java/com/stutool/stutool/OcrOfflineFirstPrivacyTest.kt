package com.stutool.stutool

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.data.local.OcrPreferences
import com.stutool.stutool.data.remote.ocr.OcrSpaceClient
import com.stutool.stutool.domain.ocr.OcrEngineService
import com.stutool.stutool.domain.ocr.OcrLanguage
import com.stutool.stutool.domain.ocr.OcrMode
import com.stutool.stutool.domain.ocr.OcrRouter
import com.stutool.stutool.ui.screens.tools.OcrPdfViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrOfflineFirstPrivacyTest {

    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
        // Clear shared preferences for clean test baseline
        application.getSharedPreferences(OcrPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun teardown() {
        application.getSharedPreferences(OcrPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        OcrEngineService.offlineRecognizerOverrideForTesting = null
        OcrSpaceClient.networkAvailabilityOverrideForTesting = null
    }

    @Test
    fun testDefaultOcrModeIsStrictlyOfflineOnly() {
        assertEquals("Default OCR mode must be OFFLINE_ONLY", OcrMode.OFFLINE_ONLY, OcrMode.DEFAULT)
        assertFalse("Default OCR mode must never allow cloud calls", OcrMode.DEFAULT.allowsCloud)
        assertEquals("offline", OcrMode.DEFAULT.code)
    }

    @Test
    fun testOcrModePrivacyBoundaries() {
        // OFFLINE_ONLY
        assertFalse("OFFLINE_ONLY must not allow cloud", OcrMode.OFFLINE_ONLY.allowsCloud)

        // SMART_AUTO (hybrid)
        assertTrue("SMART_AUTO allows cloud for eligible files", OcrMode.SMART_AUTO.allowsCloud)

        // CLOUD_ONLY
        assertTrue("CLOUD_ONLY allows cloud", OcrMode.CLOUD_ONLY.allowsCloud)

        // fromCode resolution
        assertEquals(OcrMode.OFFLINE_ONLY, OcrMode.fromCode("offline"))
        assertEquals(OcrMode.SMART_AUTO, OcrMode.fromCode("smart_auto"))
        assertEquals(OcrMode.CLOUD_ONLY, OcrMode.fromCode("cloud"))
        // Unknown or legacy fallback must resolve safely to DEFAULT (OFFLINE_ONLY)
        assertEquals(OcrMode.DEFAULT, OcrMode.fromCode("unknown_or_future_mode"))
    }

    @Test
    fun testOcrPreferencesDefaultsToOfflineOnlyWhenUnconfigured() {
        val ocrPrefs = OcrPreferences(application)
        assertEquals(
            "New installation or unconfigured upgrade must return OFFLINE_ONLY",
            OcrMode.OFFLINE_ONLY,
            ocrPrefs.getOcrMode()
        )
        assertFalse(
            "Cloud opt-in must be unacknowledged by default",
            ocrPrefs.isCloudOptInAcknowledged()
        )
    }

    @Test
    fun testOcrPreferencesPersistsExplicitUserChoices() {
        val ocrPrefs = OcrPreferences(application)

        // User opts into SMART_AUTO
        ocrPrefs.setOcrMode(OcrMode.SMART_AUTO)
        ocrPrefs.setCloudOptInAcknowledged(true)

        assertEquals(OcrMode.SMART_AUTO, ocrPrefs.getOcrMode())
        assertTrue(ocrPrefs.isCloudOptInAcknowledged())

        // Re-read from a separate instance (simulating app restart)
        val freshInstance = OcrPreferences(application)
        assertEquals(OcrMode.SMART_AUTO, freshInstance.getOcrMode())
        assertTrue(freshInstance.isCloudOptInAcknowledged())

        // User changes back to OFFLINE_ONLY
        freshInstance.setOcrMode(OcrMode.OFFLINE_ONLY)
        assertEquals(OcrMode.OFFLINE_ONLY, freshInstance.getOcrMode())
    }

    @Test
    fun testOcrPdfViewModelInitializesWithOfflineDefault() {
        val viewModel = OcrPdfViewModel(application)
        assertEquals(
            "OcrPdfViewModel must start in OFFLINE_ONLY mode",
            OcrMode.OFFLINE_ONLY,
            viewModel.selectedMode.value
        )
        assertNull(
            "Cloud opt-in dialog must be null initially",
            viewModel.showCloudOptInDialog.value
        )
        assertFalse(
            "showConsentDialog must be false initially",
            viewModel.showConsentDialog.value
        )
    }

    @Test
    fun testOcrPdfViewModelCloudOptInGating() {
        val viewModel = OcrPdfViewModel(application)
        val ocrPrefs = OcrPreferences(application)

        // Attempt to switch to SMART_AUTO without prior acknowledgment
        viewModel.setMode(OcrMode.SMART_AUTO)

        // Mode must remain OFFLINE_ONLY until user acknowledges
        assertEquals(OcrMode.OFFLINE_ONLY, viewModel.selectedMode.value)
        assertEquals(OcrMode.SMART_AUTO, viewModel.showCloudOptInDialog.value)
        assertTrue(viewModel.showConsentDialog.value)

        // User dismisses dialog
        viewModel.onCloudOptInDismissed()
        assertNull(viewModel.showCloudOptInDialog.value)
        assertFalse(viewModel.showConsentDialog.value)
        assertEquals(OcrMode.OFFLINE_ONLY, viewModel.selectedMode.value)
        assertFalse(ocrPrefs.isCloudOptInAcknowledged())

        // User triggers SMART_AUTO again and confirms
        viewModel.setMode(OcrMode.SMART_AUTO)
        assertEquals(OcrMode.SMART_AUTO, viewModel.showCloudOptInDialog.value)
        assertTrue(viewModel.showConsentDialog.value)
        viewModel.onCloudOptInConfirmed()

        assertNull(viewModel.showCloudOptInDialog.value)
        assertFalse(viewModel.showConsentDialog.value)
        assertEquals(OcrMode.SMART_AUTO, viewModel.selectedMode.value)
        assertTrue(ocrPrefs.isCloudOptInAcknowledged())

        // Subsequent switches do not re-prompt if already acknowledged
        viewModel.setMode(OcrMode.CLOUD_ONLY)
        assertEquals(OcrMode.CLOUD_ONLY, viewModel.selectedMode.value)
        assertNull(viewModel.showCloudOptInDialog.value)
    }

    // =========================================================================
    // 13 Required Specification Scenarios for StuTool v1.2.0
    // =========================================================================

    /** Scenario 1: Native PDF text is extracted without unnecessary OCR */
    @Test
    fun testScenario1_nativePdfTextExtractedWithoutUnnecessaryOcr() {
        val decision = OcrRouter.evaluate(
            context = application,
            fileSizeBytes = 500_000L,
            pageCount = 2,
            hasExtractableText = true,
            userForceOffline = false,
            ocrPreferences = OcrPreferences(application),
            isNetworkAvailable = { true }
        )
        assertEquals(OcrRouter.RouteDecision.NativeTextSufficient, decision)
    }

    /** Scenario 2: Eligible file + internet available + no prior consent triggers initial consent flow */
    @Test
    fun testScenario2_eligibleFileOnlineNoPriorConsentTriggersInitialConsent() {
        val ocrPrefs = OcrPreferences(application)
        assertFalse(ocrPrefs.isCloudOptInAcknowledged())

        val decision = OcrRouter.evaluate(
            context = application,
            fileSizeBytes = 500_000L,
            pageCount = 1,
            hasExtractableText = false,
            userForceOffline = false,
            ocrPreferences = ocrPrefs,
            isNetworkAvailable = { true }
        )
        assertEquals(OcrRouter.RouteDecision.NeedsConsent, decision)
    }

    /** Scenario 3: Eligible file + no internet automatically uses local OCR without consent dialog */
    @Test
    fun testScenario3_eligibleFileNoInternetAutomaticallyUsesLocalWithoutConsentDialog() {
        val ocrPrefs = OcrPreferences(application)
        assertFalse(ocrPrefs.isCloudOptInAcknowledged())

        val decision = OcrRouter.evaluate(
            context = application,
            fileSizeBytes = 500_000L,
            pageCount = 1,
            hasExtractableText = false,
            userForceOffline = false,
            ocrPreferences = ocrPrefs,
            isNetworkAvailable = { false }
        )
        assertTrue(decision is OcrRouter.RouteDecision.UseLocal)
        assertEquals(
            OcrRouter.LocalReason.OFFLINE_NO_INTERNET,
            (decision as OcrRouter.RouteDecision.UseLocal).reason
        )
    }

    /** Scenario 4: Ineligible file automatically uses local OCR without consent dialog */
    @Test
    fun testScenario4_ineligibleFileAutomaticallyUsesLocalWithoutConsentDialog() {
        val ocrPrefs = OcrPreferences(application)

        // Oversized file (> 1 MB)
        val decisionLargeFile = OcrRouter.evaluate(
            context = application,
            fileSizeBytes = 2_000_000L,
            pageCount = 1,
            hasExtractableText = false,
            ocrPreferences = ocrPrefs,
            isNetworkAvailable = { true }
        )
        assertTrue(decisionLargeFile is OcrRouter.RouteDecision.UseLocal)
        assertEquals(
            OcrRouter.LocalReason.INELIGIBLE_FILE_SIZE,
            (decisionLargeFile as OcrRouter.RouteDecision.UseLocal).reason
        )

        // Excess pages (> 3 pages)
        val decisionExcessPages = OcrRouter.evaluate(
            context = application,
            fileSizeBytes = 500_000L,
            pageCount = 4,
            hasExtractableText = false,
            ocrPreferences = ocrPrefs,
            isNetworkAvailable = { true }
        )
        assertTrue(decisionExcessPages is OcrRouter.RouteDecision.UseLocal)
        assertEquals(
            OcrRouter.LocalReason.INELIGIBLE_PAGE_COUNT,
            (decisionExcessPages as OcrRouter.RouteDecision.UseLocal).reason
        )
    }

    /** Scenario 5: Previously accepted consent + eligible file + internet available uses cloud OCR without repeating warning */
    @Test
    fun testScenario5_previouslyAcceptedConsentEligibleFileOnlineUsesCloudWithoutPrompt() {
        val ocrPrefs = OcrPreferences(application)
        ocrPrefs.setCloudOptInAcknowledged(true)

        val decision = OcrRouter.evaluate(
            context = application,
            fileSizeBytes = 500_000L,
            pageCount = 2,
            hasExtractableText = false,
            userForceOffline = false,
            ocrPreferences = ocrPrefs,
            isNetworkAvailable = { true }
        )
        assertEquals(OcrRouter.RouteDecision.UseCloud, decision)
    }

    /** Scenario 6: Explicit offline selection never sends network request */
    @Test
    fun testScenario6_explicitOfflineSelectionNeverSendsNetworkRequest() {
        val ocrPrefs = OcrPreferences(application)
        ocrPrefs.setCloudOptInAcknowledged(true)

        val decision = OcrRouter.evaluate(
            context = application,
            fileSizeBytes = 100_000L,
            pageCount = 1,
            hasExtractableText = false,
            userForceOffline = true,
            ocrPreferences = ocrPrefs,
            isNetworkAvailable = { true }
        )
        assertTrue(decision is OcrRouter.RouteDecision.UseLocal)
        assertEquals(
            OcrRouter.LocalReason.USER_EXPLICIT_OFFLINE,
            (decision as OcrRouter.RouteDecision.UseLocal).reason
        )
    }

    /** Scenario 7: Dismissing initial consent dialog never uploads file */
    @Test
    fun testScenario7_dismissingInitialConsentDialogNeverUploadsFile() {
        val ocrPrefs = OcrPreferences(application)
        val viewModel = OcrPdfViewModel(application)

        viewModel.setMode(OcrMode.SMART_AUTO)
        assertTrue(viewModel.showConsentDialog.value)

        // Dismiss
        viewModel.onConsentDismissed()

        assertFalse("Consent must NOT be saved on dismiss", ocrPrefs.isCloudOptInAcknowledged())
        assertFalse("Consent dialog must be dismissed", viewModel.showConsentDialog.value)
        assertEquals(OcrMode.OFFLINE_ONLY, viewModel.selectedMode.value)
    }

    /** Scenario 8: Cloud network failure falls back to local OCR and returns result if successful */
    @Test
    fun testScenario8_cloudNetworkFailureFallsBackToLocalOcrAndReturnsResult() {
        var localOcrCalled = false
        OcrEngineService.offlineRecognizerOverrideForTesting = { _, _, _ ->
            localOcrCalled = true
            com.stutool.stutool.domain.ocr.OcrPageResult(
                text = "Extracted via local fallback",
                engineSource = com.stutool.stutool.domain.ocr.OcrEngineSource.LOCAL_TESSERACT
            )
        }

        val mockClient = object : OcrSpaceClient() {
            override suspend fun recognizeImageBytes(
                bytes: ByteArray,
                filename: String,
                mimeType: String,
                language: OcrLanguage
            ): com.stutool.stutool.domain.ocr.OcrPageResult {
                throw com.stutool.stutool.domain.ocr.OcrNetworkException("Connection reset by peer")
            }
        }
        val originalClient = OcrEngineService.ocrSpaceClient
        OcrEngineService.ocrSpaceClient = mockClient
        OcrSpaceClient.networkAvailabilityOverrideForTesting = true

        try {
            val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
            val result = kotlinx.coroutines.runBlocking {
                OcrEngineService.recognizeBitmap(
                    context = application,
                    bitmap = bitmap,
                    language = OcrLanguage.ENGLISH,
                    mode = OcrMode.SMART_AUTO
                )
            }
            assertTrue("Local OCR must be invoked on network failure", localOcrCalled)
            assertEquals("Extracted via local fallback", result.text)
            assertEquals(com.stutool.stutool.domain.ocr.OcrEngineSource.LOCAL_TESSERACT, result.engineSource)
        } finally {
            OcrEngineService.ocrSpaceClient = originalClient
            OcrSpaceClient.networkAvailabilityOverrideForTesting = null
        }
    }

    /** Scenario 9: Cloud server failure falls back locally without repeated error popup */
    @Test
    fun testScenario9_cloudServerFailureFallsBackLocallyWithoutRepeatedError() {
        var localOcrCalled = false
        OcrEngineService.offlineRecognizerOverrideForTesting = { _, _, _ ->
            localOcrCalled = true
            com.stutool.stutool.domain.ocr.OcrPageResult(
                text = "Server fallback success",
                engineSource = com.stutool.stutool.domain.ocr.OcrEngineSource.LOCAL_MLKIT
            )
        }

        val mockClient = object : OcrSpaceClient() {
            override suspend fun recognizeImageBytes(
                bytes: ByteArray,
                filename: String,
                mimeType: String,
                language: OcrLanguage
            ): com.stutool.stutool.domain.ocr.OcrPageResult {
                throw com.stutool.stutool.domain.ocr.OcrApiException(exitCode = 99, message = "OCR.space 503 Service Unavailable")
            }
        }
        val originalClient = OcrEngineService.ocrSpaceClient
        OcrEngineService.ocrSpaceClient = mockClient
        OcrSpaceClient.networkAvailabilityOverrideForTesting = true

        try {
            val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
            val result = kotlinx.coroutines.runBlocking {
                OcrEngineService.recognizeBitmap(
                    context = application,
                    bitmap = bitmap,
                    language = OcrLanguage.ENGLISH,
                    mode = OcrMode.SMART_AUTO
                )
            }
            assertTrue("Local OCR must be invoked on server failure", localOcrCalled)
            assertEquals("Server fallback success", result.text)
            assertEquals(com.stutool.stutool.domain.ocr.OcrEngineSource.LOCAL_MLKIT, result.engineSource)
        } finally {
            OcrEngineService.ocrSpaceClient = originalClient
            OcrSpaceClient.networkAvailabilityOverrideForTesting = null
        }
    }

    /** Scenario 10: Local OCR failure produces clear final error without uploading document */
    @Test
    fun testScenario10_localOcrFailureProducesClearFinalErrorWithoutUpload() {
        OcrEngineService.offlineRecognizerOverrideForTesting = { _, _, _ ->
            throw RuntimeException("ML Kit initialization failed")
        }

        val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            kotlin.runCatching {
                kotlinx.coroutines.runBlocking {
                    OcrEngineService.recognizeBitmap(
                        context = application,
                        bitmap = bitmap,
                        language = OcrLanguage.ENGLISH,
                        mode = OcrMode.OFFLINE_ONLY
                    )
                }
            }.onFailure { e ->
                assertTrue(e.message?.contains("ML Kit initialization failed") == true)
            }.onSuccess {
                fail("Should have failed when local OCR fails")
            }
        } finally {
            OcrEngineService.offlineRecognizerOverrideForTesting = null
        }
    }

    /** Scenario 11: Connectivity loss during cloud request is handled safely */
    @Test
    fun testScenario11_connectivityLossDuringCloudRequestHandledSafely() {
        var localOcrCalled = false
        OcrEngineService.offlineRecognizerOverrideForTesting = { _, _, _ ->
            localOcrCalled = true
            com.stutool.stutool.domain.ocr.OcrPageResult(
                text = "Timeout fallback",
                engineSource = com.stutool.stutool.domain.ocr.OcrEngineSource.LOCAL_TESSERACT
            )
        }

        val mockClient = object : OcrSpaceClient() {
            override suspend fun recognizeImageBytes(
                bytes: ByteArray,
                filename: String,
                mimeType: String,
                language: OcrLanguage
            ): com.stutool.stutool.domain.ocr.OcrPageResult {
                throw java.net.SocketTimeoutException("Read timed out mid-transfer")
            }
        }
        val originalClient = OcrEngineService.ocrSpaceClient
        OcrEngineService.ocrSpaceClient = mockClient
        OcrSpaceClient.networkAvailabilityOverrideForTesting = true

        try {
            val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
            val result = kotlinx.coroutines.runBlocking {
                OcrEngineService.recognizeBitmap(
                    context = application,
                    bitmap = bitmap,
                    language = OcrLanguage.ENGLISH,
                    mode = OcrMode.SMART_AUTO
                )
            }
            assertTrue("Timeout must safely fall back to local OCR", localOcrCalled)
            assertEquals("Timeout fallback", result.text)
        } finally {
            OcrEngineService.ocrSpaceClient = originalClient
            OcrSpaceClient.networkAvailabilityOverrideForTesting = null
        }
    }

    /** Scenario 12: Saved consent persists across app restarts and is shared by OCR tool and PDF Viewer */
    @Test
    fun testScenario12_savedConsentPersistsAcrossRestartsAndSharedAcrossTools() {
        val ocrPrefsTool = OcrPreferences(application)
        assertFalse(ocrPrefsTool.isCloudOptInAcknowledged())

        // User accepts consent in standalone OCR tool
        ocrPrefsTool.setCloudOptInAcknowledged(true)

        // PDF Viewer loads preferences in a separate instance
        val ocrPrefsViewer = OcrPreferences(application)
        assertTrue(
            "PDF Viewer must share the same persisted consent",
            ocrPrefsViewer.isCloudOptInAcknowledged()
        )
    }

    /** Scenario 13: Repeated failures cannot cause infinite fallback loops or duplicate outputs */
    @Test
    fun testScenario13_repeatedFailuresCannotCauseInfiniteFallbackLoopsOrDuplicateOutputs() {
        var cloudAttemptCount = 0
        var localAttemptCount = 0

        val mockClient = object : OcrSpaceClient() {
            override suspend fun recognizeImageBytes(
                bytes: ByteArray,
                filename: String,
                mimeType: String,
                language: OcrLanguage
            ): com.stutool.stutool.domain.ocr.OcrPageResult {
                cloudAttemptCount++
                throw com.stutool.stutool.domain.ocr.OcrApiException(exitCode = 99, message = "Internal error")
            }
        }
        val originalClient = OcrEngineService.ocrSpaceClient
        OcrEngineService.ocrSpaceClient = mockClient
        OcrSpaceClient.networkAvailabilityOverrideForTesting = true

        OcrEngineService.offlineRecognizerOverrideForTesting = { _, _, _ ->
            localAttemptCount++
            throw RuntimeException("Local OCR failed permanently")
        }

        try {
            val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
            val res = kotlin.runCatching {
                kotlinx.coroutines.runBlocking {
                    OcrEngineService.recognizeBitmap(
                        context = application,
                        bitmap = bitmap,
                        language = OcrLanguage.ENGLISH,
                        mode = OcrMode.SMART_AUTO
                    )
                }
            }
            assertTrue(res.isFailure)
            assertEquals("Cloud must only be attempted once per operation", 1, cloudAttemptCount)
            assertEquals("Local OCR must only be attempted once per operation", 1, localAttemptCount)
        } finally {
            OcrEngineService.ocrSpaceClient = originalClient
            OcrSpaceClient.networkAvailabilityOverrideForTesting = null
            OcrEngineService.offlineRecognizerOverrideForTesting = null
        }
    }

    /** Extra: Coroutine cancellation immediately rethrows and does NOT fall back */
    @Test
    fun testCancellationRethrownImmediatelyWithoutFallback() {
        var localOcrCalled = false
        OcrEngineService.offlineRecognizerOverrideForTesting = { _, _, _ ->
            localOcrCalled = true
            com.stutool.stutool.domain.ocr.OcrPageResult(text = "Should not be called")
        }

        val mockClient = object : OcrSpaceClient() {
            override suspend fun recognizeImageBytes(
                bytes: ByteArray,
                filename: String,
                mimeType: String,
                language: OcrLanguage
            ): com.stutool.stutool.domain.ocr.OcrPageResult {
                throw kotlinx.coroutines.CancellationException("User cancelled OCR")
            }
        }
        val originalClient = OcrEngineService.ocrSpaceClient
        OcrEngineService.ocrSpaceClient = mockClient
        OcrSpaceClient.networkAvailabilityOverrideForTesting = true

        try {
            val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
            assertThrows(kotlinx.coroutines.CancellationException::class.java) {
                kotlinx.coroutines.runBlocking {
                    OcrEngineService.recognizeBitmap(
                        context = application,
                        bitmap = bitmap,
                        language = OcrLanguage.ENGLISH,
                        mode = OcrMode.SMART_AUTO
                    )
                }
            }
            assertFalse("Local OCR fallback must NEVER execute on coroutine cancellation", localOcrCalled)
        } finally {
            OcrEngineService.ocrSpaceClient = originalClient
            OcrSpaceClient.networkAvailabilityOverrideForTesting = null
            OcrEngineService.offlineRecognizerOverrideForTesting = null
        }
    }
}
