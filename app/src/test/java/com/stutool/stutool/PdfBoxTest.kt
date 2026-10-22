package com.stutool.stutool

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfBoxTest {
    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PDFBoxResourceLoader.init(app)
    }

    @Test
    fun testFontEncoding() {
        val font = PDType1Font.HELVETICA
        try {
            font.encode("A")
        } catch(e: Exception) {
            fail("Should encode A")
        }
    }

    @Test
    fun testHelveticaFailsOnKannadaUnicode() {
        val font = PDType1Font.HELVETICA
        val kannadaChar = "ಕ" // Kannada Ka (U+0C95)
        var failed = false
        try {
            font.encode(kannadaChar)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Helvetica MUST throw when attempting to encode Kannada Unicode character", failed)
    }

    @Test
    fun testPDType0FontSucceedsOnKannadaUnicode() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        try {
            val fontStream = app.assets.open("fonts/NotoSansKannada-Regular.ttf")
            val kannadaFont = com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(doc, fontStream, true)
            assertNotNull(kannadaFont)
            val latinFontStream = app.assets.open("fonts/NotoSans-Regular.ttf")
            val latinFont = com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(doc, latinFontStream, true)
            assertNotNull(latinFont)

            val kannadaWord = "ನಮಸ್ಕಾರ" // Namaskara
            val latinWord = "Hello 123!"

            // Kannada font encodes Kannada
            assertNotNull(kannadaFont.encode(kannadaWord))

            // Latin font encodes Latin
            assertNotNull(latinFont.encode(latinWord))

            // Composite / chunk check
            val mixed = "ನಮಸ್ಕಾರ Hello 2026"
            val tokens = mixed.split(" ")
            for (token in tokens) {
                val canKannada = try { kannadaFont.encode(token); true } catch (e: Exception) { false }
                val canLatin = try { latinFont.encode(token); true } catch (e: Exception) { false }
                assertTrue("Token '$token' must be encodable by either Kannada or Latin font", canKannada || canLatin)
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun testSearchablePdfRoundTripWithKannadaAndLatin() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        val page = com.tom_roush.pdfbox.pdmodel.PDPage()
        doc.addPage(page)

        val kannadaFont = com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(
            doc, app.assets.open("fonts/NotoSansKannada-Regular.ttf"), true
        )
        val latinFont = com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(
            doc, app.assets.open("fonts/NotoSans-Regular.ttf"), true
        )

        val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
            doc, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true
        )
        contentStream.setRenderingMode(com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER)

        contentStream.beginText()
        contentStream.setFont(kannadaFont, 14f)
        contentStream.newLineAtOffset(50f, 700f)
        contentStream.showText("ನಮಸ್ಕಾರ")
        contentStream.endText()

        contentStream.beginText()
        contentStream.setFont(latinFont, 14f)
        contentStream.newLineAtOffset(50f, 680f)
        contentStream.showText("Karnataka StuTool 2026")
        contentStream.endText()

        contentStream.close()

        val baos = java.io.ByteArrayOutputStream()
        doc.save(baos)
        doc.close()

        // Reload and extract text with PDFTextStripper to verify searchability
        val reloadedDoc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(baos.toByteArray())
        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
        val extracted = stripper.getText(reloadedDoc)
        reloadedDoc.close()

        println("Extracted searchable text: $extracted")
        assertTrue("Extracted text must contain Kannada word 'ನಮಸ್ಕಾರ'", extracted.contains("ನಮಸ್ಕಾರ"))
        assertTrue("Extracted text must contain 'Karnataka StuTool 2026'", extracted.contains("Karnataka StuTool 2026"))
    }
}




