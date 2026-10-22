package com.stutool.stutool

import com.stutool.stutool.data.local.RecentDocxRepository
import com.stutool.stutool.domain.model.RecentDocxFile
import com.stutool.stutool.ui.screens.viewer.DocxBlock
import com.stutool.stutool.ui.screens.viewer.DocxDocumentLoader
import com.stutool.stutool.ui.screens.viewer.DocxLoadException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxDocumentLoaderTest {

    private fun buildDocxBytes(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((path, xml) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun testValidMinimalDocx() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r><w:t>Hello StuTool DOCX Viewer</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val bytes = buildDocxBytes(mapOf("word/document.xml" to documentXml))
        val doc = DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))

        assertFalse(doc.isEmpty)
        assertFalse(doc.isTruncated)
        assertEquals(1, doc.blocks.size)
        val para = doc.blocks[0] as DocxBlock.Paragraph
        assertEquals("Hello StuTool DOCX Viewer", para.plainText)
        assertNull(para.headingLevel)
    }

    @Test
    fun testMultipleParagraphsUnicodeAndLineBreaks() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                        <w:r><w:t>Chapter 1: Introduction</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:r><w:t>Unicode test: नमस्ते • 日本語 • Café • 🚀 &amp; &lt;tags&gt;</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:r>
                            <w:t>Line A</w:t>
                            <w:br/>
                            <w:t>Line B</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val bytes = buildDocxBytes(mapOf("word/document.xml" to documentXml))
        val doc = DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))

        assertEquals(3, doc.blocks.size)

        val h1 = doc.blocks[0] as DocxBlock.Paragraph
        assertEquals("Chapter 1: Introduction", h1.plainText)
        assertEquals(1, h1.headingLevel)

        val p2 = doc.blocks[1] as DocxBlock.Paragraph
        assertEquals("Unicode test: नमस्ते • 日本語 • Café • 🚀 & <tags>", p2.plainText)

        val p3 = doc.blocks[2] as DocxBlock.Paragraph
        assertEquals("Line A\nLine B", p3.plainText)
    }

    @Test
    fun testBasicRunFormattingAndCoalescing() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r>
                            <w:rPr><w:b/></w:rPr>
                            <w:t>Bold </w:t>
                        </w:r>
                        <w:r>
                            <w:rPr><w:b w:val="true"/></w:rPr>
                            <w:t>Merged</w:t>
                        </w:r>
                        <w:r>
                            <w:rPr><w:i/><w:u w:val="single"/></w:rPr>
                            <w:t> ItalicUnderline</w:t>
                        </w:r>
                        <w:r>
                            <w:rPr><w:b w:val="0"/><w:u w:val="none"/><w:strike/></w:rPr>
                            <w:t> StrikeOnly</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val bytes = buildDocxBytes(mapOf("word/document.xml" to documentXml))
        val doc = DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))

        assertEquals(1, doc.blocks.size)
        val para = doc.blocks[0] as DocxBlock.Paragraph
        assertEquals(3, para.runs.size)

        assertEquals("Bold Merged", para.runs[0].text)
        assertTrue(para.runs[0].isBold)
        assertFalse(para.runs[0].isItalic)
        assertFalse(para.runs[0].isUnderline)

        assertEquals(" ItalicUnderline", para.runs[1].text)
        assertFalse(para.runs[1].isBold)
        assertTrue(para.runs[1].isItalic)
        assertTrue(para.runs[1].isUnderline)

        assertEquals(" StrikeOnly", para.runs[2].text)
        assertFalse(para.runs[2].isBold)
        assertFalse(para.runs[2].isUnderline)
        assertTrue(para.runs[2].isStrike)
    }

    @Test
    fun testNumberedAndBulletedListsWithNumberingXmlAfterDocumentXml() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                        <w:r><w:t>First bullet</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr>
                        <w:r><w:t>Step One</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr>
                        <w:r><w:t>Step Two</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val numberingXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:abstractNum w:abstractNumId="10">
                    <w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl>
                </w:abstractNum>
                <w:abstractNum w:abstractNumId="20">
                    <w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl>
                </w:abstractNum>
                <w:num w:numId="1"><w:abstractNumId w:val="10"/></w:num>
                <w:num w:numId="2"><w:abstractNumId w:val="20"/></w:num>
            </w:numbering>
        """.trimIndent()

        // Intentionally place word/numbering.xml AFTER word/document.xml in the ZIP
        val bytes = buildDocxBytes(
            linkedMapOf(
                "word/document.xml" to documentXml,
                "word/numbering.xml" to numberingXml
            )
        )
        val doc = DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))

        assertEquals(3, doc.blocks.size)
        val b0 = doc.blocks[0] as DocxBlock.Paragraph
        val b1 = doc.blocks[1] as DocxBlock.Paragraph
        val b2 = doc.blocks[2] as DocxBlock.Paragraph

        assertEquals("•", b0.listPrefix)
        assertEquals("First bullet", b0.plainText)

        assertEquals("1.", b1.listPrefix)
        assertEquals("Step One", b1.plainText)

        assertEquals("2.", b2.listPrefix)
        assertEquals("Step Two", b2.plainText)
    }

    @Test
    fun testSimpleTableParsing() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:tbl>
                        <w:tr>
                            <w:trPr><w:tblHeader/></w:trPr>
                            <w:tc><w:p><w:r><w:t>Subject</w:t></w:r></w:p></w:tc>
                            <w:tc><w:p><w:r><w:t>Score</w:t></w:r></w:p></w:tc>
                        </w:tr>
                        <w:tr>
                            <w:tc><w:p><w:r><w:t>Mathematics</w:t></w:r></w:p></w:tc>
                            <w:tc><w:p><w:r><w:t>98</w:t></w:r></w:p></w:tc>
                        </w:tr>
                    </w:tbl>
                </w:body>
            </w:document>
        """.trimIndent()

        val bytes = buildDocxBytes(mapOf("word/document.xml" to documentXml))
        val doc = DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))

        assertEquals(1, doc.blocks.size)
        assertEquals(1, doc.totalTableCount)
        val table = doc.blocks[0] as DocxBlock.Table
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[0].isHeader)
        assertEquals("Subject", table.rows[0].cells[0].plainText)
        assertEquals("Score", table.rows[0].cells[1].plainText)
        assertEquals("Mathematics", table.rows[1].cells[0].plainText)
        assertEquals("98", table.rows[1].cells[1].plainText)
    }

    @Test
    fun testEmptyDocxDocument() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p/>
                </w:body>
            </w:document>
        """.trimIndent()

        val bytes = buildDocxBytes(mapOf("word/document.xml" to documentXml))
        val doc = DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))

        assertTrue(doc.isEmpty)
        assertTrue(doc.blocks.isEmpty())
    }

    @Test(expected = DocxLoadException::class)
    fun testMalformedDocumentXmlThrowsDocxLoadException() {
        val brokenXml = "<w:document><w:body><w:p><w:r><w:t>Unclosed"
        val bytes = buildDocxBytes(mapOf("word/document.xml" to brokenXml))
        DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))
    }

    @Test
    fun testLegacyBinaryDocRejectedWithHelpfulMessage() {
        val ole2Header = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
            0x00, 0x00, 0x00, 0x00
        )
        try {
            DocxDocumentLoader.loadDocument(ByteArrayInputStream(ole2Header))
            fail("Expected DocxLoadException for legacy .doc header")
        } catch (e: DocxLoadException) {
            assertTrue(e.message!!.contains(".doc", ignoreCase = true))
            assertTrue(e.message!!.contains(".docx", ignoreCase = true))
        }
    }

    @Test(expected = DocxLoadException::class)
    fun testRejectXlsxArchive() {
        val bytes = buildDocxBytes(mapOf("xl/workbook.xml" to "<workbook/>"))
        DocxDocumentLoader.loadDocument(ByteArrayInputStream(bytes))
    }

    @Test(expected = DocxLoadException::class)
    fun testRejectNonZipInput() {
        val randomBytes = "Just plain text, not a ZIP archive".toByteArray(Charsets.UTF_8)
        DocxDocumentLoader.loadDocument(ByteArrayInputStream(randomBytes))
    }

    @Test(expected = CancellationException::class)
    fun testCooperativeCancellation() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body><w:p><w:r><w:t>Cancel me</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent()
        val bytes = buildDocxBytes(mapOf("word/document.xml" to documentXml))
        DocxDocumentLoader.loadDocument(
            inputStream = ByteArrayInputStream(bytes),
            checkCancelled = { throw CancellationException("Cancelled by user") }
        )
    }

    @Test
    fun testLargeDocumentMaxBlocksTruncation() {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        for (i in 1..25) {
            sb.append("<w:p><w:r><w:t>Paragraph $i</w:t></w:r></w:p>")
        }
        sb.append("</w:body></w:document>")

        val bytes = buildDocxBytes(mapOf("word/document.xml" to sb.toString()))
        val doc = DocxDocumentLoader.loadDocument(
            inputStream = ByteArrayInputStream(bytes),
            maxBlocks = 10
        )

        assertTrue(doc.isTruncated)
        assertEquals(10, doc.blocks.size)
        assertEquals("Paragraph 1", (doc.blocks.first() as DocxBlock.Paragraph).plainText)
        assertEquals("Paragraph 10", (doc.blocks.last() as DocxBlock.Paragraph).plainText)
    }

    @Test
    fun testRecentDocxSerializationAndDeduplication() {
        val first = RecentDocxFile(
            uri = "content://downloads/10",
            name = "project_report.docx",
            size = 15360L,
            lastOpened = 1000L,
            localCopyPath = "/data/user/0/com.stutool.stutool/files/docx_copies/project_report.docx"
        )
        val second = RecentDocxFile(
            uri = "content://external/project_report.docx",
            name = "Project_Report",
            size = 15360L,
            lastOpened = 2000L,
            localCopyPath = null
        )

        assertEquals(first.fileIdentity, second.fileIdentity)

        val deduped = RecentDocxRepository.deduplicateAndInsert(listOf(first), second)
        assertEquals(1, deduped.size)
        assertEquals("content://external/project_report.docx", deduped[0].uri)
        assertEquals(2000L, deduped[0].lastOpened)
        assertEquals("/data/user/0/com.stutool.stutool/files/docx_copies/project_report.docx", deduped[0].localCopyPath)

        val serialized = RecentDocxRepository.serializeList(deduped)
        val parsed = RecentDocxRepository.parseListFromSerialized(serialized)
        assertEquals(1, parsed.size)
        assertEquals(deduped[0], parsed[0])
    }
}
