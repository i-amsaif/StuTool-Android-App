package com.stutool.stutool

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.domain.engine.PdfEngine
import com.stutool.stutool.ui.screens.tools.PdfToWordViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfToWordConversionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
    }

    private fun createPdfWithText(file: File, pageTexts: List<String>) {
        PDDocument().use { document ->
            for (text in pageTexts) {
                val page = PDPage()
                document.addPage(page)
                if (text.isNotBlank()) {
                    PDPageContentStream(document, page).use { contentStream ->
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA, 12f)
                        contentStream.newLineAtOffset(50f, 700f)
                        contentStream.showText(text)
                        contentStream.endText()
                    }
                }
            }
            document.save(file)
        }
    }

    @Test
    fun testNormalTextBasedPdfConvertsToValidDocx() = runTest {
        val pdfFile = tempFolder.newFile("sample_text.pdf")
        val authenticTextPage1 = "This is authentic digital text on page one for testing StuTool PDF to Word conversion."
        val authenticTextPage2 = "Here is the second page containing authentic digital paragraphs for verification."
        createPdfWithText(pdfFile, listOf(authenticTextPage1, authenticTextPage2))

        var progressCalls = 0
        val docxUri = PdfEngine.convertPdfToWord(
            context = application,
            sourceUri = Uri.fromFile(pdfFile),
            onProgress = { current, total ->
                progressCalls++
                assertTrue(current in 1..total)
            }
        )

        assertNotNull("Generated DOCX URI should not be null", docxUri)
        val docxFile = File(docxUri.path!!)
        assertTrue("Output DOCX file must exist", docxFile.exists())
        assertTrue("Output DOCX size should be greater than zero", docxFile.length() > 0)

        // Verify the output is a valid DOCX ZIP package with the expected text
        ZipFile(docxFile).use { zip ->
            val contentTypesEntry = zip.getEntry("[Content_Types].xml")
            assertNotNull("DOCX must contain [Content_Types].xml", contentTypesEntry)

            val docXmlEntry = zip.getEntry("word/document.xml")
            assertNotNull("DOCX must contain word/document.xml", docXmlEntry)

            val xmlContent = zip.getInputStream(docXmlEntry).bufferedReader().readText()
            assertTrue("DOCX must contain authentic text from page 1", xmlContent.contains("authentic digital text on page one"))
            assertTrue("DOCX must contain authentic text from page 2", xmlContent.contains("second page containing authentic digital paragraphs"))
        }

        assertTrue("Progress should have been called for both pages", progressCalls >= 2)
    }

    @Test
    fun testImageOnlyPdfDirectsUserToOcrPdfWithoutUnboundedLoop() = runTest {
        // Create a 4-page PDF with no text layer (simulating scanned/image-only PDF > 3 pages)
        val imageOnlyPdfFile = tempFolder.newFile("image_only_4pages.pdf")
        createPdfWithText(imageOnlyPdfFile, listOf("", "", "", ""))

        var progressReported = 0
        try {
            PdfEngine.convertPdfToWord(
                context = application,
                sourceUri = Uri.fromFile(imageOnlyPdfFile),
                onProgress = { current, total ->
                    progressReported = current
                    assertEquals(4, total)
                }
            )
            fail("Expected convertPdfToWord to fail on multi-page image-only PDF")
        } catch (e: Exception) {
            val message = e.message ?: ""
            assertTrue(
                "Error message should direct the user to StuTool's dedicated OCR PDF tool, got: $message",
                message.contains("OCR PDF tool", ignoreCase = true)
            )
            assertEquals("Progress should inspect all 4 pages in the fast pre-pass before rejecting", 4, progressReported)
        }
    }

    @Test
    fun testMixedTextAndImagePdfConvertsExtractableText() = runTest {
        // 4-page mixed document: page 1 has authentic digital text, pages 2-4 are image/empty
        val mixedPdfFile = tempFolder.newFile("mixed_document.pdf")
        val page1Text = "This authentic native text on page one must be converted into the Word document."
        createPdfWithText(mixedPdfFile, listOf(page1Text, "", "", ""))

        val docxUri = PdfEngine.convertPdfToWord(
            context = application,
            sourceUri = Uri.fromFile(mixedPdfFile),
            onProgress = { _, _ -> }
        )

        assertNotNull(docxUri)
        val docxFile = File(docxUri.path!!)
        assertTrue(docxFile.exists())

        ZipFile(docxFile).use { zip ->
            val docXmlEntry = zip.getEntry("word/document.xml")
            assertNotNull(docXmlEntry)
            val xmlContent = zip.getInputStream(docXmlEntry).bufferedReader().readText()
            assertTrue(
                "DOCX must contain extracted text from page 1",
                xmlContent.contains("This authentic native text on page one")
            )
        }
    }

    @Test
    fun testPdfLoadingFailureProducesControlledError() = runTest {
        // Corrupted / invalid file
        val corruptedFile = tempFolder.newFile("corrupted.pdf")
        corruptedFile.writeText("This is not a valid PDF file content.")

        try {
            PdfEngine.convertPdfToWord(
                context = application,
                sourceUri = Uri.fromFile(corruptedFile),
                onProgress = { _, _ -> }
            )
            fail("Expected failure on corrupted PDF file")
        } catch (e: Exception) {
            assertNotNull(e.message)
            assertFalse("Exception should be controlled, not unhandled", e.message!!.isBlank())
        }
    }

    @Test
    fun testPdfToWordViewModelErrorStateHandling() = runTest {
        val vm = PdfToWordViewModel(application)

        val imageOnlyPdfFile = tempFolder.newFile("vm_image_only.pdf")
        createPdfWithText(imageOnlyPdfFile, listOf("", "", "", ""))

        vm.setPdf(Uri.fromFile(imageOnlyPdfFile))
        ShadowLooper.idleMainLooper()

        assertFalse(vm.isConverting.value)
        assertNull(vm.processingError.value)

        vm.startConversion()
        ShadowLooper.idleMainLooper()

        // Wait for async conversion job to complete while pumping main looper
        val startTime = System.currentTimeMillis()
        while (vm.isConverting.value && System.currentTimeMillis() - startTime < 5000) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
        ShadowLooper.idleMainLooper()

        // Conversion should fail gracefully with an informative message directing to OCR PDF tool
        assertFalse("isConverting should be false after failure", vm.isConverting.value)
        assertNotNull("processingError should be populated", vm.processingError.value)
        assertTrue(
            "processingError should mention OCR PDF tool, got: ${vm.processingError.value}",
            vm.processingError.value!!.contains("OCR PDF tool", ignoreCase = true)
        )
        assertNull("outputUri should remain null on failure", vm.outputUri.value)

        // Clear error
        vm.clearProcessingError()
        assertNull(vm.processingError.value)
    }

    @Test
    fun testCancellationCleansUpResources() = runTest {
        val pdfFile = tempFolder.newFile("cancellation_test.pdf")
        val longTextList = (1..10).map { "Page $it authentic digital text content for cancellation test." }
        createPdfWithText(pdfFile, longTextList)

        val cacheFilesBefore = application.cacheDir.listFiles()?.toList() ?: emptyList()

        val job = launch {
            try {
                PdfEngine.convertPdfToWord(
                    context = application,
                    sourceUri = Uri.fromFile(pdfFile),
                    onProgress = { current, _ ->
                        if (current >= 1) {
                            throw kotlinx.coroutines.CancellationException("User cancelled operation")
                        }
                    }
                )
            } catch (c: kotlinx.coroutines.CancellationException) {
                // Cancellation is properly propagated
                throw c
            }
        }

        job.join()
        assertTrue("Job should be cancelled", job.isCancelled)

        // Verify that temporary cache folders created during the failed/cancelled conversion were cleaned up
        val cacheFilesAfter = application.cacheDir.listFiles()?.toList() ?: emptyList()
        val leftoverPdfToWordDirs = cacheFilesAfter.filter {
            it.name.startsWith("pdf_to_word_") && !cacheFilesBefore.contains(it)
        }
        assertTrue("No orphaned pdf_to_word_* cache directories should remain after cancellation", leftoverPdfToWordDirs.isEmpty())
    }
}
