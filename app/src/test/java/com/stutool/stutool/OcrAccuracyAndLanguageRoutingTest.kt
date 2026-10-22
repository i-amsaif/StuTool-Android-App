package com.stutool.stutool

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.domain.ocr.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrAccuracyAndLanguageRoutingTest {

    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
        OcrEngineService.offlineRecognizerOverrideForTesting = null
        OcrEngineService.mlKitRecognizerOverrideForTesting = null
        OcrEngineService.tesseractRecognizerOverrideForTesting = null
    }

    @After
    fun teardown() {
        OcrEngineService.offlineRecognizerOverrideForTesting = null
        OcrEngineService.mlKitRecognizerOverrideForTesting = null
        OcrEngineService.tesseractRecognizerOverrideForTesting = null
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 1. English Documents in AUTO Mode (Zero Kannada Gibberish) ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testEnglishDocumentInAutoModeUsesMlKitAndHasZeroKannadaGibberish() = runBlocking {
        val englishText = """
            StuTool Android Workstation v1.2.0
            Student PDF and Document Tools
            Author: Mohammed Saif
            Education: Computer Science Engineering
            This comprehensive report outlines the architecture and performance metrics.
        """.trimIndent()

        // Mock ML Kit returning authentic English text
        OcrEngineService.mlKitRecognizerOverrideForTesting = { _ ->
            OcrPageResult(
                text = englishText,
                lines = listOf(
                    OcrLine("StuTool Android Workstation v1.2.0", Rect(50, 100, 800, 140)),
                    OcrLine("Student PDF and Document Tools", Rect(50, 160, 750, 200)),
                    OcrLine("Author: Mohammed Saif", Rect(50, 220, 500, 260)),
                    OcrLine("Education: Computer Science Engineering", Rect(50, 280, 850, 320)),
                    OcrLine("This comprehensive report outlines the architecture and performance metrics.", Rect(50, 340, 950, 380))
                ),
                languageUsed = OcrLanguage.ENGLISH,
                engineSource = OcrEngineSource.LOCAL_MLKIT
            )
        }

        // Tesseract mock (would have hallucinated Kannada in flawed versions)
        OcrEngineService.tesseractRecognizerOverrideForTesting = { _, _, _ ->
            OcrPageResult(
                text = "StuTool ರ ಮ Workstation v1.2.0 ದ",
                languageUsed = OcrLanguage.AUTO,
                engineSource = OcrEngineSource.LOCAL_TESSERACT
            )
        }

        val dummyBitmap = Bitmap.createBitmap(1000, 1500, Bitmap.Config.ARGB_8888)
        val result = OcrEngineService.recognizeBitmap(
            context = application,
            bitmap = dummyBitmap,
            language = OcrLanguage.AUTO,
            mode = OcrMode.OFFLINE_ONLY
        )

        // 1. Must use ML Kit engine source
        assertEquals("English document in AUTO mode must select LOCAL_MLKIT", OcrEngineSource.LOCAL_MLKIT, result.engineSource)
        assertEquals("Language used must be ENGLISH", OcrLanguage.ENGLISH, result.languageUsed)

        // 2. Output text must match English exactly
        assertEquals(englishText, result.text)

        // 3. ZERO Kannada characters allowed
        val kannadaCount = result.text.count { it.code in 0x0C80..0x0CFF }
        assertEquals("English document must contain 0 Kannada characters", 0, kannadaCount)

        // 4. ZERO Hindi/Devanagari characters allowed
        val hindiCount = result.text.count { it.code in 0x0900..0x097F }
        assertEquals("English document must contain 0 Hindi characters", 0, hindiCount)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 2. Pure Kannada Document Recognition in AUTO Mode ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testKannadaDocumentInAutoModeRoutesToTesseractAndPreservesKannada() = runBlocking {
        val kannadaText = """
            ಕರ್ನಾಟಕ ಸರ್ಕಾರದ ಉನ್ನತ ಶಿಕ್ಷಣ ಇಲಾಖೆ
            ವಿದ್ಯಾರ್ಥಿ ವೇತನ ತಂತ್ರಾಂಶ ಮತ್ತು ಡಿಜಿಟಲ್ ಕಡತ ನಿರ್ವಹಣೆ
            ಬೆಂಗಳೂರು ವಿಶ್ವವಿದ್ಯಾಲಯ
        """.trimIndent()

        // ML Kit Latin cannot read Kannada, returns empty or negligible noise
        OcrEngineService.mlKitRecognizerOverrideForTesting = { _ ->
            OcrPageResult(
                text = "",
                lines = emptyList(),
                languageUsed = OcrLanguage.ENGLISH,
                engineSource = OcrEngineSource.LOCAL_MLKIT
            )
        }

        // Tesseract handles Kannada
        OcrEngineService.tesseractRecognizerOverrideForTesting = { _, _, _ ->
            OcrPageResult(
                text = kannadaText,
                lines = listOf(
                    OcrLine("ಕರ್ನಾಟಕ ಸರ್ಕಾರದ ಉನ್ನತ ಶಿಕ್ಷಣ ಇಲಾಖೆ", Rect(50, 100, 800, 140)),
                    OcrLine("ವಿದ್ಯಾರ್ಥಿ ವೇತನ ತಂತ್ರಾಂಶ ಮತ್ತು ಡಿಜಿಟಲ್ ಕಡತ ನಿರ್ವಹಣೆ", Rect(50, 160, 900, 200)),
                    OcrLine("ಬೆಂಗಳೂರು ವಿಶ್ವವಿದ್ಯಾಲಯ", Rect(50, 220, 500, 260))
                ),
                languageUsed = OcrLanguage.KANNADA,
                engineSource = OcrEngineSource.LOCAL_TESSERACT
            )
        }

        val dummyBitmap = Bitmap.createBitmap(1000, 1500, Bitmap.Config.ARGB_8888)
        val result = OcrEngineService.recognizeBitmap(
            context = application,
            bitmap = dummyBitmap,
            language = OcrLanguage.AUTO,
            mode = OcrMode.OFFLINE_ONLY
        )

        assertEquals("Kannada document in AUTO must route to LOCAL_TESSERACT", OcrEngineSource.LOCAL_TESSERACT, result.engineSource)
        assertEquals("Kannada document must resolve language to KANNADA", OcrLanguage.KANNADA, result.languageUsed)
        assertTrue("Output must contain authentic Kannada text", result.text.contains("ಕರ್ನಾಟಕ ಸರ್ಕಾರದ"))
        val kannadaCount = result.text.count { it.code in 0x0C80..0x0CFF }
        assertTrue("Result must have high density of Kannada characters", kannadaCount > 30)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 3. Pure Hindi Document Recognition in AUTO Mode ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testHindiDocumentInAutoModeRoutesToTesseractAndPreservesHindi() = runBlocking {
        val hindiText = """
            भारत सरकार शिक्षा मंत्रालय
            राष्ट्रीय शिक्षा नीति 2026
            विश्वविद्यालय अनुदान आयोग नई दिल्ली
        """.trimIndent()

        // ML Kit Latin cannot read Devanagari, returns empty
        OcrEngineService.mlKitRecognizerOverrideForTesting = { _ ->
            OcrPageResult(
                text = "",
                lines = emptyList(),
                languageUsed = OcrLanguage.ENGLISH,
                engineSource = OcrEngineSource.LOCAL_MLKIT
            )
        }

        // Tesseract extracts Hindi
        OcrEngineService.tesseractRecognizerOverrideForTesting = { _, _, _ ->
            OcrPageResult(
                text = hindiText,
                lines = listOf(
                    OcrLine("भारत सरकार शिक्षा मंत्रालय", Rect(50, 100, 800, 140)),
                    OcrLine("राष्ट्रीय शिक्षा नीति 2026", Rect(50, 160, 750, 200)),
                    OcrLine("विश्वविद्यालय अनुदान आयोग नई दिल्ली", Rect(50, 220, 950, 260))
                ),
                languageUsed = OcrLanguage.HINDI,
                engineSource = OcrEngineSource.LOCAL_TESSERACT
            )
        }

        val dummyBitmap = Bitmap.createBitmap(1000, 1500, Bitmap.Config.ARGB_8888)
        val result = OcrEngineService.recognizeBitmap(
            context = application,
            bitmap = dummyBitmap,
            language = OcrLanguage.AUTO,
            mode = OcrMode.OFFLINE_ONLY
        )

        assertEquals(OcrEngineSource.LOCAL_TESSERACT, result.engineSource)
        assertEquals(OcrLanguage.HINDI, result.languageUsed)
        assertTrue("Output must contain authentic Hindi text", result.text.contains("भारत सरकार"))
        val hindiCount = result.text.count { it.code in 0x0900..0x097F }
        assertTrue("Result must have high density of Hindi characters", hindiCount > 25)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 4. Mixed Language Document Handling (Kannada + English) ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testMixedLanguageDocumentPreservesBothKannadaAndEnglish() = runBlocking {
        val mixedText = """
            GOVERNMENT OF KARNATAKA
            DEPARTMENT OF TECHNICAL EDUCATION
            ಕರ್ನಾಟಕ ರಾಜ್ಯ ಡಿಜಿಟಲ್ ಪ್ರಮಾಣಪತ್ರ
            Candidate Name: Mohammed Saif
            ವಿದ್ಯಾರ್ಥಿ ವಿವರಗಳು ಮತ್ತು ಅಂಕಪಟ್ಟಿ
        """.trimIndent()

        // ML Kit only sees the English lines (small fragment)
        OcrEngineService.mlKitRecognizerOverrideForTesting = { _ ->
            OcrPageResult(
                text = "GOVERNMENT OF KARNATAKA\nDEPARTMENT OF TECHNICAL EDUCATION\nCandidate Name: Mohammed Saif",
                lines = listOf(
                    OcrLine("GOVERNMENT OF KARNATAKA", Rect(50, 50, 600, 90)),
                    OcrLine("DEPARTMENT OF TECHNICAL EDUCATION", Rect(50, 100, 750, 140)),
                    OcrLine("Candidate Name: Mohammed Saif", Rect(50, 200, 600, 240))
                ),
                languageUsed = OcrLanguage.ENGLISH,
                engineSource = OcrEngineSource.LOCAL_MLKIT
            )
        }

        // Tesseract multi-script captures full bilingual content
        OcrEngineService.tesseractRecognizerOverrideForTesting = { _, _, _ ->
            OcrPageResult(
                text = mixedText,
                lines = listOf(
                    OcrLine("GOVERNMENT OF KARNATAKA", Rect(50, 50, 600, 90)),
                    OcrLine("DEPARTMENT OF TECHNICAL EDUCATION", Rect(50, 100, 750, 140)),
                    OcrLine("ಕರ್ನಾಟಕ ರಾಜ್ಯ ಡಿಜಿಟಲ್ ಪ್ರಮಾಣಪತ್ರ", Rect(50, 150, 700, 190)),
                    OcrLine("Candidate Name: Mohammed Saif", Rect(50, 200, 600, 240)),
                    OcrLine("ವಿದ್ಯಾರ್ಥಿ ವಿವರಗಳು ಮತ್ತು ಅಂಕಪಟ್ಟಿ", Rect(50, 250, 700, 290))
                ),
                languageUsed = OcrLanguage.AUTO,
                engineSource = OcrEngineSource.LOCAL_TESSERACT
            )
        }

        val dummyBitmap = Bitmap.createBitmap(1000, 1500, Bitmap.Config.ARGB_8888)
        val result = OcrEngineService.recognizeBitmap(
            context = application,
            bitmap = dummyBitmap,
            language = OcrLanguage.AUTO,
            mode = OcrMode.OFFLINE_ONLY
        )

        // Must retain Tesseract's complete bilingual output
        assertEquals(OcrEngineSource.LOCAL_TESSERACT, result.engineSource)
        assertTrue("Mixed document must preserve English header", result.text.contains("GOVERNMENT OF KARNATAKA"))
        assertTrue("Mixed document must preserve English candidate name", result.text.contains("Mohammed Saif"))
        assertTrue("Mixed document must preserve Kannada body", result.text.contains("ಕರ್ನಾಟಕ ರಾಜ್ಯ ಡಿಜಿಟಲ್"))
        assertTrue("Mixed document must preserve Kannada details", result.text.contains("ವಿದ್ಯಾರ್ಥಿ ವಿವರಗಳು"))
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 5. Explicit English Mode Fallback Immunity ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testExplicitEnglishModeNeverInvokesIndicModels() = runBlocking {
        var tesseractLangRequested: OcrLanguage? = null

        // ML Kit throws exception (simulating edge case)
        OcrEngineService.mlKitRecognizerOverrideForTesting = { _ ->
            throw RuntimeException("Simulated ML Kit initialization failure")
        }

        // Tesseract fallback must receive ENGLISH (eng only, never kan+eng+hin)
        OcrEngineService.tesseractRecognizerOverrideForTesting = { _, _, lang ->
            tesseractLangRequested = lang
            OcrPageResult(
                text = "Fallback pure English text from Tesseract eng",
                languageUsed = lang,
                engineSource = OcrEngineSource.LOCAL_TESSERACT
            )
        }

        val dummyBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val result = OcrEngineService.recognizeBitmap(
            context = application,
            bitmap = dummyBitmap,
            language = OcrLanguage.ENGLISH,
            mode = OcrMode.OFFLINE_ONLY
        )

        assertEquals("Tesseract fallback in English mode must strictly use ENGLISH language model", OcrLanguage.ENGLISH, tesseractLangRequested)
        assertEquals("eng", tesseractLangRequested?.tessLanguages)
        assertFalse("English model must not be marked as Indic", tesseractLangRequested!!.isIndic)
        assertEquals("Fallback pure English text from Tesseract eng", result.text)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 6. OcrImagePreprocessor Correctness & Safety ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testPreprocessorClampsOversizedDimensions() {
        val oversized = Bitmap.createBitmap(3600, 2700, Bitmap.Config.ARGB_8888)
        val prepResult = OcrImagePreprocessor.preprocess(oversized)

        try {
            assertTrue("Oversized bitmap must result in a new clamped bitmap", prepResult.isNewBitmap)
            assertTrue("Clamped width must be <= 2400", prepResult.bitmap.width <= 2400)
            assertTrue("Clamped height must be <= 2400", prepResult.bitmap.height <= 2400)
            // Aspect ratio check (3600 / 2700 = 1.333)
            val origRatio = 3600f / 2700f
            val newRatio = prepResult.bitmap.width.toFloat() / prepResult.bitmap.height.toFloat()
            assertEquals("Aspect ratio must be preserved during downscaling", origRatio, newRatio, 0.02f)
        } finally {
            prepResult.recycleIfTemporary()
            oversized.recycle()
        }
    }

    @Test
    fun testPreprocessorDarkBackgroundDetection() {
        // Dark bitmap (black background)
        val darkBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        darkBitmap.eraseColor(Color.BLACK)
        assertTrue("Pure black bitmap must be detected as dark background", OcrImagePreprocessor.isDarkBackground(darkBitmap))

        // Light bitmap (white background)
        val lightBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        lightBitmap.eraseColor(Color.WHITE)
        assertFalse("Pure white bitmap must not be detected as dark background", OcrImagePreprocessor.isDarkBackground(lightBitmap))

        darkBitmap.recycle()
        lightBitmap.recycle()
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 7. Indic Noise Sanitization Helper ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testSanitizeIndicNoiseFromLatinText() {
        val contaminated = "The quick brown fox jumps over the lazy dog ರ ಮ"
        val cleaned = OcrEngineService.sanitizeIndicNoiseFromLatinText(contaminated)
        assertEquals("The quick brown fox jumps over the lazy dog", cleaned)

        val devanagariContaminated = "Invoice Total: $500.00 भारत"
        val cleanedDev = OcrEngineService.sanitizeIndicNoiseFromLatinText(devanagariContaminated)
        assertEquals("Invoice Total: $500.00", cleanedDev)
    }
}
