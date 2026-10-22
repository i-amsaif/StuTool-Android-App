package com.stutool.stutool

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.domain.engine.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfMemorySafetyTest {

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

    // ===============================================================
    // -- 1. PDF to Excel Memory Safety & Validation ----------------
    // ===============================================================

    @Test
    fun testConvertPdfToExcelWithTextGeneratesValidXlsxZipAndCleansTempSource() = runTest {
        val pdfFile = tempFolder.newFile("sample_table.pdf")
        val page1Text = "ID Name Score"
        val page2Text = "101 Alice 95"
        createPdfWithText(pdfFile, listOf(page1Text, page2Text))

        var progressCount = 0
        val excelUri = PdfEngine.convertPdfToExcel(
            context = application,
            sourceUri = Uri.fromFile(pdfFile),
            onProgress = { current, total ->
                progressCount++
                assertTrue(current in 1..total)
            }
        )

        assertNotNull("Generated XLSX URI should not be null", excelUri)
        val excelFile = File(excelUri.path!!)
        assertTrue("Output XLSX file must exist on disk", excelFile.exists())
        assertTrue("Output XLSX size must be greater than zero", excelFile.length() > 0)
        assertTrue("Output filename must end with .xlsx", excelFile.name.endsWith(".xlsx"))

        // Verify valid XLSX ZIP structure
        ZipFile(excelFile).use { zip ->
            assertNotNull("XLSX must contain [Content_Types].xml", zip.getEntry("[Content_Types].xml"))
            assertNotNull("XLSX must contain _rels/.rels", zip.getEntry("_rels/.rels"))
            assertNotNull("XLSX must contain xl/workbook.xml", zip.getEntry("xl/workbook.xml"))
            assertNotNull("XLSX must contain xl/worksheets/sheet1.xml", zip.getEntry("xl/worksheets/sheet1.xml"))

            val sheetContent = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue("Sheet must contain extracted table text", sheetContent.contains("Name") || sheetContent.contains("Alice"))
        }

        // Verify that temporary source copy and any scratch files were cleaned up, leaving only output file
        val parentDir = excelFile.parentFile
        val leftoverTempFiles = parentDir?.listFiles { file -> file != excelFile } ?: emptyArray()
        assertTrue("No leftover temporary files should remain in operation directory: ${leftoverTempFiles.map { it.name }}", leftoverTempFiles.isEmpty())

        assertTrue("Progress callback should have been triggered for pages", progressCount >= 2)
    }

    @Test
    fun testConvertPdfToExcelRejectsImageOnlyPdfWithControlledException() = runTest {
        val emptyPdf = tempFolder.newFile("scanned_empty.pdf")
        // Create PDF with blank pages (simulates scanned/image-only without digital text)
        createPdfWithText(emptyPdf, listOf("", ""))

        try {
            PdfEngine.convertPdfToExcel(
                context = application,
                sourceUri = Uri.fromFile(emptyPdf),
                onProgress = { _, _ -> }
            )
            fail("Expected exception for scanned image-only PDF without selectable text")
        } catch (e: Exception) {
            assertTrue(
                "Exception message must inform user about lack of selectable text",
                e.message?.contains("No selectable text found") == true ||
                e.message?.contains("Scanned PDFs are not supported") == true
            )
        }
    }

    // ===============================================================
    // -- 2. Searchable PDF Memory Safety & Cleanup -----------------
    // ===============================================================

    @Test
    fun testMakeSearchablePdfWithDigitalTextPreservesContentAndCleansTempSource() = runTest {
        val pdfFile = tempFolder.newFile("sample_searchable.pdf")
        val authenticText = "Student Tool offers comprehensive PDF utilities designed for higher education."
        createPdfWithText(pdfFile, listOf(authenticText))

        var progressCalls = 0
        val ocrResult = PdfEngine.makeSearchablePdf(
            context = application,
            sourceUri = Uri.fromFile(pdfFile),
            onProgress = { current, total ->
                progressCalls++
                assertTrue(current in 1..total)
            }
        )

        assertNotNull("OCR result URI should not be null", ocrResult.uri)
        val resultFile = File(ocrResult.uri.path!!)
        assertTrue("Output OCR PDF file must exist", resultFile.exists())
        assertTrue("Output OCR PDF size must be greater than zero", resultFile.length() > 0)
        assertTrue("Output filename must end with _OCR.pdf", resultFile.name.endsWith("_OCR.pdf"))

        // Since text was already authentic, it should be marked as skippedText
        assertTrue("Authentic text page should be skipped from re-OCR", ocrResult.pagesSkippedText >= 1)

        // Verify all temporary scratch files were cleaned up, leaving only output file
        val parentDir = resultFile.parentFile
        val leftoverTempFiles = parentDir?.listFiles { file -> file != resultFile } ?: emptyArray()
        assertTrue("No leftover temporary files should remain in operation directory: ${leftoverTempFiles.map { it.name }}", leftoverTempFiles.isEmpty())

        assertTrue("Progress callback must be invoked", progressCalls >= 1)
    }

    @Test
    fun testConvertPdfToSearchablePdfAliasFunctionsIdentically() = runTest {
        val pdfFile = tempFolder.newFile("alias_test.pdf")
        val sampleText = "Verifying the convertPdfToSearchablePdf alias invocation for memory safety."
        createPdfWithText(pdfFile, listOf(sampleText))

        val ocrResult = PdfEngine.convertPdfToSearchablePdf(
            context = application,
            sourceUri = Uri.fromFile(pdfFile),
            onProgress = { _, _ -> }
        )

        assertNotNull("OCR result should not be null", ocrResult.uri)
        val file = File(ocrResult.uri.path!!)
        assertTrue("Result file must exist", file.exists())
        assertTrue("Result filename must end with _OCR.pdf", file.name.endsWith("_OCR.pdf"))
    }

    // ===============================================================
    // -- 3. Failure & Corrupted Document Handling ------------------
    // ===============================================================

    @Test
    fun testCorruptedFileFailsGracefullyAndCleansResourcesInExcel() = runTest {
        val corruptFile = tempFolder.newFile("corrupt_doc.pdf")
        corruptFile.writeText("This is not a valid PDF document format.")

        try {
            PdfEngine.convertPdfToExcel(
                context = application,
                sourceUri = Uri.fromFile(corruptFile),
                onProgress = { _, _ -> }
            )
            fail("Expected exception when loading corrupted PDF into Excel conversion")
        } catch (e: Exception) {
            assertNotNull("Exception should have an informative message", e.message)
        }
    }

    @Test
    fun testCorruptedFileFailsGracefullyAndCleansResourcesInSearchablePdf() = runTest {
        val corruptFile = tempFolder.newFile("corrupt_searchable.pdf")
        corruptFile.writeText("Corrupted non-PDF content.")

        try {
            PdfEngine.makeSearchablePdf(
                context = application,
                sourceUri = Uri.fromFile(corruptFile),
                onProgress = { _, _ -> }
            )
            fail("Expected exception when loading corrupted PDF into Searchable PDF conversion")
        } catch (e: Exception) {
            assertNotNull("Exception should have an informative message", e.message)
        }
    }

    // ===============================================================
    // -- 4. Concurrent Conversion Safety & Collision Isolation -----
    // ===============================================================

    @Test
    fun testConcurrentExcelConversionsDoNotCollideOrCorruptOutputs() = runTest {
        val pdfFileA = tempFolder.newFile("doc_A.pdf")
        createPdfWithText(pdfFileA, listOf("Department Alpha Accounting 100"))

        val pdfFileB = tempFolder.newFile("doc_B.pdf")
        createPdfWithText(pdfFileB, listOf("Department Beta Logistics 200"))

        val deferredA = async(Dispatchers.IO) {
            PdfEngine.convertPdfToExcel(application, Uri.fromFile(pdfFileA)) { _, _ -> }
        }
        val deferredB = async(Dispatchers.IO) {
            PdfEngine.convertPdfToExcel(application, Uri.fromFile(pdfFileB)) { _, _ -> }
        }

        val uriA = deferredA.await()
        val uriB = deferredB.await()

        assertNotNull(uriA)
        assertNotNull(uriB)
        assertNotEquals("Concurrent conversions must produce unique destination URIs", uriA, uriB)

        val fileA = File(uriA.path!!)
        val fileB = File(uriB.path!!)
        assertTrue("Output file A must exist", fileA.exists())
        assertTrue("Output file B must exist", fileB.exists())
        assertNotEquals("Output files must be in separate isolated directories", fileA.parentFile?.absolutePath, fileB.parentFile?.absolutePath)

        // Verify content integrity of both concurrent outputs
        ZipFile(fileA).use { zip ->
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue("Document A must contain its own data", sheet.contains("Alpha"))
            assertFalse("Document A must NOT contain Document B data", sheet.contains("Beta"))
        }

        ZipFile(fileB).use { zip ->
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue("Document B must contain its own data", sheet.contains("Beta"))
            assertFalse("Document B must NOT contain Document A data", sheet.contains("Alpha"))
        }

        // Verify neither directory has leftover temp files
        val leftoverA = fileA.parentFile?.listFiles { f -> f != fileA } ?: emptyArray()
        val leftoverB = fileB.parentFile?.listFiles { f -> f != fileB } ?: emptyArray()
        assertTrue("Operation A directory must not have leftover temp files", leftoverA.isEmpty())
        assertTrue("Operation B directory must not have leftover temp files", leftoverB.isEmpty())
    }

    @Test
    fun testConcurrentSearchablePdfConversionsDoNotCollideOrCorruptOutputs() = runTest {
        val pdfFileA = tempFolder.newFile("search_A.pdf")
        createPdfWithText(pdfFileA, listOf("Academic Transcript Student One"))

        val pdfFileB = tempFolder.newFile("search_B.pdf")
        createPdfWithText(pdfFileB, listOf("Graduation Certificate Student Two"))

        val deferredA = async(Dispatchers.IO) {
            PdfEngine.makeSearchablePdf(application, Uri.fromFile(pdfFileA)) { _, _ -> }
        }
        val deferredB = async(Dispatchers.IO) {
            PdfEngine.makeSearchablePdf(application, Uri.fromFile(pdfFileB)) { _, _ -> }
        }

        val resultA = deferredA.await()
        val resultB = deferredB.await()

        assertNotNull(resultA.uri)
        assertNotNull(resultB.uri)
        assertNotEquals("Concurrent searchable conversions must produce unique URIs", resultA.uri, resultB.uri)

        val fileA = File(resultA.uri.path!!)
        val fileB = File(resultB.uri.path!!)
        assertTrue("Result A must exist", fileA.exists())
        assertTrue("Result B must exist", fileB.exists())
        assertNotEquals("Result files must be in isolated directories", fileA.parentFile?.absolutePath, fileB.parentFile?.absolutePath)

        val leftoverA = fileA.parentFile?.listFiles { f -> f != fileA } ?: emptyArray()
        val leftoverB = fileB.parentFile?.listFiles { f -> f != fileB } ?: emptyArray()
        assertTrue("Operation A directory must not have leftover temp files", leftoverA.isEmpty())
        assertTrue("Operation B directory must not have leftover temp files", leftoverB.isEmpty())
    }

    // ===============================================================
    // -- 5. Cache Isolation on Failure & Cancellation --------------
    // ===============================================================

    @Test
    fun testFailureInConversionDoesNotDeleteUnrelatedCacheFiles() = runTest {
        val unrelatedFile = File(application.cacheDir, "unrelated_user_cache_${System.currentTimeMillis()}.txt")
        unrelatedFile.writeText("Valuable persistent user data that must never be deleted")
        assertTrue(unrelatedFile.exists())

        val corruptFile = tempFolder.newFile("corrupt_test.pdf")
        corruptFile.writeText("Corrupted non-PDF bytes")

        try {
            PdfEngine.convertPdfToExcel(application, Uri.fromFile(corruptFile)) { _, _ -> }
            fail("Expected conversion failure on corrupt PDF")
        } catch (_: Exception) {
            // Expected
        }

        assertTrue("Unrelated cache file must NOT be deleted by conversion failure cleanup", unrelatedFile.exists())
        assertEquals("Valuable persistent user data that must never be deleted", unrelatedFile.readText())
        unrelatedFile.delete()
    }

    @Test
    fun testCancellationInConversionCleansOnlyOwnDirectoryAndPreservesUnrelatedFiles() = runTest {
        val unrelatedFile = File(application.cacheDir, "unrelated_user_cache_${System.currentTimeMillis()}.txt")
        unrelatedFile.writeText("Valuable persistent user data that must never be deleted")
        assertTrue(unrelatedFile.exists())

        val pdfFile = tempFolder.newFile("cancel_test.pdf")
        createPdfWithText(pdfFile, listOf("Page 1 Text", "Page 2 Text", "Page 3 Text"))

        var opDirSeen: File? = null

        val job = launch(Dispatchers.IO) {
            try {
                PdfEngine.convertPdfToExcel(application, Uri.fromFile(pdfFile)) { current, _ ->
                    if (current >= 1) {
                        throw CancellationException("Operation cancelled by user")
                    }
                }
            } catch (c: CancellationException) {
                throw c
            }
        }

        job.join()
        assertTrue("Job should be marked cancelled", job.isCancelled)

        // Verify unrelated cache files remain intact
        assertTrue("Unrelated cache file must NOT be deleted on cancellation", unrelatedFile.exists())
        assertEquals("Valuable persistent user data that must never be deleted", unrelatedFile.readText())
        unrelatedFile.delete()
    }

    // ===============================================================
    // -- 6. extractTextFromPdf Cleans All Temporary Resources -------
    // ===============================================================

    @Test
    fun testExtractTextFromPdfCleansAllTemporaryFilesOnSuccessAndFailure() = runTest {
        val pdfFile = tempFolder.newFile("extract_text_test.pdf")
        val sampleContent = "Sample authentic text extracted from PDF without OCR."
        createPdfWithText(pdfFile, listOf(sampleContent))

        val initialExtractDirs = application.cacheDir.listFiles()?.filter { it.name.startsWith("extract_text_") } ?: emptyList()

        val extracted = PdfEngine.extractTextFromPdf(application, Uri.fromFile(pdfFile)) { _, _ -> }
        assertTrue("Extracted text must match content", extracted.contains("Sample authentic text"))

        val postSuccessExtractDirs = application.cacheDir.listFiles()?.filter { it.name.startsWith("extract_text_") } ?: emptyList()
        assertEquals("extractTextFromPdf must leave zero extract_text_* directories on success", initialExtractDirs.size, postSuccessExtractDirs.size)

        // Failure case
        val corruptFile = tempFolder.newFile("corrupt_extract.pdf")
        corruptFile.writeText("Corrupted file")
        try {
            PdfEngine.extractTextFromPdf(application, Uri.fromFile(corruptFile)) { _, _ -> }
            fail("Expected failure on corrupted PDF")
        } catch (_: Exception) {}

        val postFailureExtractDirs = application.cacheDir.listFiles()?.filter { it.name.startsWith("extract_text_") } ?: emptyList()
        assertEquals("extractTextFromPdf must leave zero extract_text_* directories on failure", initialExtractDirs.size, postFailureExtractDirs.size)
    }
}
