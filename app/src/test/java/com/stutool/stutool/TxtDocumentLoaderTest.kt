package com.stutool.stutool

import com.stutool.stutool.data.local.RecentTxtRepository
import com.stutool.stutool.domain.model.RecentTxtFile
import com.stutool.stutool.ui.screens.viewer.TxtDocumentLoader
import com.stutool.stutool.ui.screens.viewer.TxtLoadException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class TxtDocumentLoaderTest {

    @Test
    fun testSmallUtf8FileWithMixedLineEndings() {
        val text = "First line\nSecond line\r\nThird line\rFourth line"
        val stream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

        val window = TxtDocumentLoader.loadWindow(stream)

        assertFalse(window.isEmpty)
        assertFalse(window.hasMoreAfter)
        assertEquals("UTF-8", window.detectedCharset)
        assertEquals(4, window.lines.size)
        assertEquals("First line", window.lines[0].content)
        assertEquals(1, window.lines[0].lineNumber)
        assertEquals("Second line", window.lines[1].content)
        assertEquals(2, window.lines[1].lineNumber)
        assertEquals("Third line", window.lines[2].content)
        assertEquals(3, window.lines[2].lineNumber)
        assertEquals("Fourth line", window.lines[3].content)
        assertEquals(4, window.lines[3].lineNumber)
    }

    @Test
    fun testUnicodeCharactersAndUtf8Bom() {
        val content = "Hello 世界 🚀 Café naïve ∑ ∫\nLine 2: नमस्ते"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + content.toByteArray(Charsets.UTF_8)

        val window = TxtDocumentLoader.loadWindow(ByteArrayInputStream(bytes))

        assertFalse(window.isEmpty)
        assertEquals("UTF-8 (BOM)", window.detectedCharset)
        assertEquals(2, window.lines.size)
        assertEquals("Hello 世界 🚀 Café naïve ∑ ∫", window.lines[0].content)
        assertEquals("Line 2: नमस्ते", window.lines[1].content)
        assertFalse(window.hadMalformedBytes)
    }

    @Test
    fun testUtf16LeAndBeWithBom() {
        val content = "UTF-16 Test Line 1\nUTF-16 Line 2 Ω"
        val leBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val leBytes = leBom + content.toByteArray(Charsets.UTF_16LE)

        val leWindow = TxtDocumentLoader.loadWindow(ByteArrayInputStream(leBytes))
        assertEquals("UTF-16LE", leWindow.detectedCharset)
        assertEquals(2, leWindow.lines.size)
        assertEquals("UTF-16 Test Line 1", leWindow.lines[0].content)
        assertEquals("UTF-16 Line 2 Ω", leWindow.lines[1].content)

        val beBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val beBytes = beBom + content.toByteArray(Charsets.UTF_16BE)

        val beWindow = TxtDocumentLoader.loadWindow(ByteArrayInputStream(beBytes))
        assertEquals("UTF-16BE", beWindow.detectedCharset)
        assertEquals(2, beWindow.lines.size)
        assertEquals("UTF-16 Test Line 1", beWindow.lines[0].content)
        assertEquals("UTF-16 Line 2 Ω", beWindow.lines[1].content)
    }

    @Test
    fun testEmptyFile() {
        val window = TxtDocumentLoader.loadWindow(ByteArrayInputStream(ByteArray(0)))
        assertTrue(window.isEmpty)
        assertTrue(window.lines.isEmpty())
        assertFalse(window.hasMoreAfter)
    }

    @Test
    fun testMalformedUtf8BytesReplacedSafely() {
        val validPrefix = "Valid start ".toByteArray(Charsets.UTF_8)
        val invalidSeq = byteArrayOf(0xC3.toByte(), 0x28.toByte()) // Invalid UTF-8 2-byte sequence
        val validSuffix = " valid end".toByteArray(Charsets.UTF_8)

        val window = TxtDocumentLoader.loadWindow(ByteArrayInputStream(validPrefix + invalidSeq + validSuffix))
        assertFalse(window.isEmpty)
        assertTrue(window.hadMalformedBytes)
        assertTrue(window.lines[0].content.contains("Valid start "))
        assertTrue(window.lines[0].content.contains("valid end"))
    }

    @Test(expected = TxtLoadException::class)
    fun testRejectPdfDisguisedAsTxt() {
        val pdfBytes = "%PDF-1.7\n1 0 obj\n<<>>\nendobj".toByteArray(Charsets.US_ASCII)
        TxtDocumentLoader.loadWindow(ByteArrayInputStream(pdfBytes))
    }

    @Test(expected = TxtLoadException::class)
    fun testRejectZipOrDocxDisguisedAsTxt() {
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)
        TxtDocumentLoader.loadWindow(ByteArrayInputStream(zipBytes))
    }

    @Test(expected = TxtLoadException::class)
    fun testRejectBinaryWithNullBytes() {
        val binaryBytes = byteArrayOf(0x41, 0x42, 0x00, 0x00, 0x43, 0x00, 0x44, 0x02)
        TxtDocumentLoader.loadWindow(ByteArrayInputStream(binaryBytes))
    }

    @Test
    fun testLargeFileBoundedWindowingAndContinuation() {
        val totalLines = 250
        val sb = StringBuilder()
        for (i in 1..totalLines) {
            sb.append("Line number $i with some sample text payload.\n")
        }
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)

        // Load window 0 with small maxWindowLines = 100
        val window0 = TxtDocumentLoader.loadWindow(
            inputStream = ByteArrayInputStream(bytes),
            windowIndex = 0,
            maxWindowChars = 50_000,
            maxWindowLines = 100
        )
        assertEquals(100, window0.lines.size)
        assertTrue(window0.hasMoreAfter)
        assertEquals(1, window0.lines.first().lineNumber)
        assertEquals(100, window0.lines.last().lineNumber)

        // Load window 1
        val window1 = TxtDocumentLoader.loadWindow(
            inputStream = ByteArrayInputStream(bytes),
            windowIndex = 1,
            maxWindowChars = 50_000,
            maxWindowLines = 100
        )
        assertEquals(100, window1.lines.size)
        assertTrue(window1.hasMoreAfter)
        assertEquals(101, window1.lines.first().lineNumber)
        assertEquals(200, window1.lines.last().lineNumber)

        // Load window 2 (final 50 lines)
        val window2 = TxtDocumentLoader.loadWindow(
            inputStream = ByteArrayInputStream(bytes),
            windowIndex = 2,
            maxWindowChars = 50_000,
            maxWindowLines = 100
        )
        assertEquals(50, window2.lines.size)
        assertFalse(window2.hasMoreAfter)
        assertEquals(201, window2.lines.first().lineNumber)
        assertEquals(250, window2.lines.last().lineNumber)
    }

    @Test
    fun testLongSingleLineSplitSafely() {
        val longLine = "A".repeat(450)
        val window = TxtDocumentLoader.loadWindow(
            inputStream = ByteArrayInputStream(longLine.toByteArray(Charsets.UTF_8)),
            maxLineChars = 200
        )
        assertEquals(3, window.lines.size)
        assertEquals(200, window.lines[0].content.length)
        assertFalse(window.lines[0].isContinuation)
        assertEquals(1, window.lines[0].lineNumber)

        assertEquals(200, window.lines[1].content.length)
        assertTrue(window.lines[1].isContinuation)
        assertEquals(1, window.lines[1].lineNumber)

        assertEquals(50, window.lines[2].content.length)
        assertTrue(window.lines[2].isContinuation)
        assertEquals(1, window.lines[2].lineNumber)
    }

    @Test(expected = CancellationException::class)
    fun testCooperativeCancellation() {
        val text = "Line 1\nLine 2\nLine 3"
        TxtDocumentLoader.loadWindow(
            inputStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
            checkCancelled = { throw CancellationException("User closed viewer") }
        )
    }

    @Test
    fun testRecentTxtFileJsonRoundTripAndFileIdentity() {
        val item1 = RecentTxtFile(
            uri = "content://com.android.providers.downloads/document/101",
            name = "lecture_notes.txt",
            size = 2048L,
            lastOpened = 1700000050000L,
            localCopyPath = "/data/user/0/com.stutool.stutool/files/txt_copies/lecture_notes.txt"
        )
        val item2 = RecentTxtFile(
            uri = "content://com.externalstorage/document/primary%3Alecture_notes.txt",
            name = "Lecture_Notes",
            size = 2048L,
            lastOpened = 1700000000000L,
            localCopyPath = null
        )

        // Identical fileIdentity even when one name has .txt suffix and different casing
        assertEquals(item1.fileIdentity, item2.fileIdentity)

        val json = RecentTxtRepository.serializeListToJson(listOf(item1, item2))
        val parsed = RecentTxtRepository.parseListFromJson(json)

        assertEquals(2, parsed.size)
        assertEquals(item1.uri, parsed[0].uri)
        assertEquals(item1.name, parsed[0].name)
        assertEquals(item1.size, parsed[0].size)
        assertEquals(item1.lastOpened, parsed[0].lastOpened)
        assertEquals(item1.localCopyPath, parsed[0].localCopyPath)
        assertNull(parsed[1].localCopyPath)
    }

    @Test
    fun testRecentTxtDeduplicationAndOrdering() {
        val older = RecentTxtFile(
            uri = "content://downloads/1",
            name = "notes",
            size = 512L,
            lastOpened = 1000L,
            localCopyPath = "/internal/txt_copies/notes.txt"
        )
        val other = RecentTxtFile(
            uri = "content://downloads/2",
            name = "todo",
            size = 128L,
            lastOpened = 2000L,
            localCopyPath = null
        )

        // Re-opening "notes.txt" from a different URI without a new localCopyPath should:
        // 1. Deduplicate by fileIdentity ("notes|512")
        // 2. Preserve existing localCopyPath
        // 3. Bump it to the top of the list with the newer timestamp
        val reopened = RecentTxtFile(
            uri = "content://external/notes.txt",
            name = "notes.txt",
            size = 512L,
            lastOpened = 3000L,
            localCopyPath = null
        )

        val updated = RecentTxtRepository.deduplicateAndInsert(listOf(other, older), reopened)

        assertEquals(2, updated.size)
        assertEquals("content://external/notes.txt", updated[0].uri)
        assertEquals(3000L, updated[0].lastOpened)
        assertEquals("/internal/txt_copies/notes.txt", updated[0].localCopyPath)
        assertEquals("content://downloads/2", updated[1].uri)
    }
}
