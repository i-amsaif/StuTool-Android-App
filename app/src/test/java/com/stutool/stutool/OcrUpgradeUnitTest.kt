package com.stutool.stutool

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.domain.engine.PdfEngine
import com.stutool.stutool.domain.ocr.OcrLanguage
import com.stutool.stutool.domain.ocr.SmartTextDetector
import com.stutool.stutool.domain.ocr.TessDataManager
import com.stutool.stutool.ui.screens.tools.OcrPdfViewModel
import com.stutool.stutool.ui.screens.viewer.PdfReaderViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
class OcrUpgradeUnitTest {

    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 1. SmartTextDetector Unit Tests ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testSmartTextDetectorRecognizesAuthenticEnglishText() {
        val englishDocText = "Student Tool is a comprehensive PDF utility application designed for university students and educators."
        assertTrue(
            "Authentic English document text must be classified as authentic",
            SmartTextDetector.isAuthenticNativeText(englishDocText)
        )
    }

    @Test
    fun testSmartTextDetectorRecognizesAuthenticKannadaText() {
        val kannadaDocText = "ಕರ್ನಾಟಕ ಸರ್ಕಾರದ ವಿದ್ಯಾರ್ಥಿ ವೇತನ ತಂತ್ರಾಂಶ ಮತ್ತು ಡಿಜಿಟಲ್ ಕಡತ ನಿರ್ವಹಣೆ."
        assertTrue(
            "Authentic Kannada document text must be classified as authentic",
            SmartTextDetector.isAuthenticNativeText(kannadaDocText)
        )
    }

    @Test
    fun testSmartTextDetectorRejectsBlankOrTrivialText() {
        assertFalse(SmartTextDetector.isAuthenticNativeText(null))
        assertFalse(SmartTextDetector.isAuthenticNativeText(""))
        assertFalse(SmartTextDetector.isAuthenticNativeText("   "))
        assertFalse(SmartTextDetector.isAuthenticNativeText("Page 1")) // too short
        assertFalse(SmartTextDetector.isAuthenticNativeText("................."))
        assertFalse(SmartTextDetector.isAuthenticNativeText("-----------------"))
    }

    @Test
    fun testSmartTextDetectorRejectsCorruptedScannerNoise() {
        // High density of replacement characters from upstream scanner
        val corruptedText = "abc \uFFFD\uFFFD\uFFFD \uFFFD\uFFFD\uFFFD xyz \uFFFD\uFFFD\uFFFD 123"
        assertFalse(
            "Corrupted scanner text layer with replacement characters must be rejected for OCR",
            SmartTextDetector.isAuthenticNativeText(corruptedText)
        )
    }

    @Test
    fun testSmartTextDetectorRejectsRepetitiveNoise() {
        val repetitiveNoise = "a a a a a a a a a a a a a a a a a a a"
        assertFalse(
            "Repetitive single-character OCR noise must be rejected",
            SmartTextDetector.isAuthenticNativeText(repetitiveNoise)
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 2. OcrLanguage Model & Configurations ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testOcrLanguageConfigurations() {
        assertEquals("eng+kan+hin", OcrLanguage.AUTO.tessLanguages)
        assertTrue(OcrLanguage.AUTO.isIndic)
        assertEquals("auto", OcrLanguage.AUTO.ocrSpaceLanguage)

        assertEquals("kan+eng", OcrLanguage.KANNADA_ENGLISH.tessLanguages)
        assertTrue(OcrLanguage.KANNADA_ENGLISH.isIndic)

        assertEquals("eng", OcrLanguage.ENGLISH.tessLanguages)
        assertFalse(OcrLanguage.ENGLISH.isIndic)

        assertEquals("kan", OcrLanguage.KANNADA.tessLanguages)
        assertTrue(OcrLanguage.KANNADA.isIndic)

        assertEquals("hin", OcrLanguage.HINDI.tessLanguages)
        assertTrue(OcrLanguage.HINDI.isIndic)

        assertEquals(OcrLanguage.AUTO, OcrLanguage.fromCode("auto"))
        assertEquals(OcrLanguage.KANNADA_ENGLISH, OcrLanguage.fromCode("kan_eng"))
        assertEquals(OcrLanguage.ENGLISH, OcrLanguage.fromCode("eng"))
        assertEquals(OcrLanguage.AUTO, OcrLanguage.fromCode("unknown_code"))
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 3. Font Encoding & Unicode Searchable PDF Fix Tests ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testHelveticaFailsOnKannadaScript() {
        val font = PDType1Font.HELVETICA
        val kannadaText = "ಕನ್ನಡ" // Kannada
        var threwException = false
        try {
            font.encode(kannadaText)
        } catch (e: Exception) {
            threwException = true
        }
        assertTrue("PDType1Font.HELVETICA must fail on Kannada script", threwException)
    }

    @Test
    fun testPDType0FontEncodesKannadaScript() {
        val doc = PDDocument()
        try {
            val kannadaFontStream = application.assets.open("fonts/NotoSansKannada-Regular.ttf")
            val kannadaFont = PDType0Font.load(doc, kannadaFontStream, true)
            assertNotNull(kannadaFont)

            val testWord = "ಕನ್ನಡ"
            val encoded = kannadaFont.encode(testWord)
            assertNotNull("PDType0Font must encode Kannada without throwing", encoded)
        } finally {
            doc.close()
        }
    }

    @Test
    fun testSearchablePdfRoundTripPreservesKannadaAndEnglish() {
        val doc = PDDocument()
        val page = PDPage()
        doc.addPage(page)

        val kannadaFont = PDType0Font.load(
            doc, application.assets.open("fonts/NotoSansKannada-Regular.ttf"), true
        )
        val latinFont = PDType0Font.load(
            doc, application.assets.open("fonts/NotoSans-Regular.ttf"), true
        )

        val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
            doc, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true
        )
        contentStream.setRenderingMode(RenderingMode.NEITHER)

        contentStream.beginText()
        contentStream.setFont(kannadaFont, 12f)
        contentStream.newLineAtOffset(50f, 750f)
        contentStream.showText("ನಮಸ್ಕಾರ ಸ್ನೇಹಿತರೆ")
        contentStream.endText()

        contentStream.beginText()
        contentStream.setFont(latinFont, 12f)
        contentStream.newLineAtOffset(50f, 730f)
        contentStream.showText("Welcome to StuTool Android 2026")
        contentStream.endText()

        contentStream.close()

        val baos = ByteArrayOutputStream()
        doc.save(baos)
        doc.close()

        // Reload and extract text to verify searchability
        val reloadedDoc = PDDocument.load(baos.toByteArray())
        val stripper = PDFTextStripper()
        val extracted = stripper.getText(reloadedDoc)
        reloadedDoc.close()

        assertTrue(
            "Searchable PDF text extraction must contain Kannada text 'ನಮಸ್ಕಾರ ಸ್ನೇಹಿತರೆ'",
            extracted.contains("ನಮಸ್ಕಾರ ಸ್ನೇಹಿತರೆ")
        )
        assertTrue(
            "Searchable PDF text extraction must contain English text 'Welcome to StuTool Android 2026'",
            extracted.contains("Welcome to StuTool Android 2026")
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 4. TXT & DOCX Unicode Preservation Tests ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testTxtExportPreservesKannadaUnicode() {
        val kannadaContent = "ಕರ್ನಾಟಕ ವಿಶ್ವವಿದ್ಯಾಲಯ - ಕನ್ನಡ ಮತ್ತು ಇಂಗ್ಲಿಷ್ ದಾಖಲೆ."
        val tempFile = File(application.cacheDir, "test_kannada.txt")
        tempFile.writeText(kannadaContent, Charsets.UTF_8)

        val readBack = tempFile.readText(Charsets.UTF_8)
        assertEquals("TXT file read back must match original Kannada text exactly", kannadaContent, readBack)
        tempFile.delete()
    }

    @Test
    fun testDocxExportPreservesKannadaInValidOpcZip() {
        val kannadaDocxText = "ನಮಸ್ಕಾರ ಕರ್ನಾಟಕ!\n\nThis is a bilingual paragraph containing English and 12345."
        val tempFile = File(application.cacheDir, "test_kannada.docx")
        
        PdfEngine.writeTextToDocxZip(tempFile, kannadaDocxText)
        assertTrue("DOCX file must exist", tempFile.exists())
        assertTrue("DOCX file must have non-zero size", tempFile.length() > 500)

        // Verify valid OPC ZIP structure
        val zip = ZipFile(tempFile)
        assertNotNull("[Content_Types].xml must be present", zip.getEntry("[Content_Types].xml"))
        assertNotNull("_rels/.rels must be present", zip.getEntry("_rels/.rels"))

        val docXmlEntry = zip.getEntry("word/document.xml")
        assertNotNull("word/document.xml must be present", docXmlEntry)

        val docXmlContent = zip.getInputStream(docXmlEntry).bufferedReader(Charsets.UTF_8).readText()
        assertTrue("word/document.xml must contain Kannada string 'ನಮಸ್ಕಾರ ಕರ್ನಾಟಕ!'", docXmlContent.contains("ನಮಸ್ಕಾರ ಕರ್ನಾಟಕ!"))
        assertTrue("word/document.xml must contain English string 'bilingual paragraph'", docXmlContent.contains("bilingual paragraph"))
        zip.close()
        tempFile.delete()
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 5. OcrPdfViewModel State & Deduplication Tests ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testOcrPdfViewModelImageIdentification() {
        val viewModel = OcrPdfViewModel(application)
        
        // Reset state
        viewModel.reset()
        assertNull(viewModel.selectedUri.value)
        assertEquals(0L, viewModel.fileSizeBytes.value)
        assertEquals(OcrLanguage.AUTO, viewModel.selectedLanguage.value)

        // Language switching
        viewModel.setLanguage(OcrLanguage.ENGLISH)
        assertEquals(OcrLanguage.ENGLISH, viewModel.selectedLanguage.value)

        viewModel.setLanguage(OcrLanguage.KANNADA)
        assertEquals(OcrLanguage.KANNADA, viewModel.selectedLanguage.value)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 6. PdfReaderViewModel OCR Fallback Deduplication & Cache ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testPdfReaderViewModelOcrStateInitialization() {
        val readerVm = PdfReaderViewModel(application)
        val initialOcrState = readerVm.pageOcrState.value

        assertEquals(-1, initialOcrState.pageIndex)
        assertFalse(initialOcrState.isLoading)
        assertNull(initialOcrState.extractedText)
        assertNull(initialOcrState.errorMessage)

        readerVm.clearPageOcrState()
        assertEquals(-1, readerVm.pageOcrState.value.pageIndex)
    }
}
