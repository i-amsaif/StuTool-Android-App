package com.stutool.stutool.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PdfShareUtilsTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // â”€â”€ sanitizeFilename â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun testSanitizeFilename_RemovesInvalidCharacters() {
        val input = "My/Awesome\\Resume:2026?.pdf"
        val expected = "My_Awesome_Resume_2026_.pdf"
        assertEquals(expected, PdfShareUtils.sanitizeFilename(input))
    }

    @Test
    fun testSanitizeFilename_EnsuresPdfExtension() {
        assertEquals("JustAFile.pdf", PdfShareUtils.sanitizeFilename("JustAFile"))
    }

    @Test
    fun testSanitizeFilename_HandlesExistingExtension() {
        assertEquals("document.pdf", PdfShareUtils.sanitizeFilename("document.pdf"))
    }

    @Test
    fun testSanitizeFilename_HandlesEmptyOrBlank() {
        assertEquals("Document.pdf", PdfShareUtils.sanitizeFilename("   "))
    }

    @Test
    fun testSanitizeFilename_PreservesSpacesAndHyphens() {
        assertEquals("My-cool resume v2.0.pdf", PdfShareUtils.sanitizeFilename("My-cool resume v2.0.pdf"))
    }

    @Test
    fun testSanitizeFilename_PathTraversalPrevented() {
        // ".." should not survive sanitization
        val result = PdfShareUtils.sanitizeFilename("../../etc/passwd")
        assert(!result.contains("..")) { "Path traversal characters must not survive sanitization" }
    }

    // â”€â”€ resolveReadableFile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun testResolveReadableFile_ActiveTempFileTakesPriority() {
        val context = RuntimeEnvironment.getApplication()
        val tempFile = tmpFolder.newFile("decrypted.pdf").apply { writeText("pdf-content") }
        val localFile = tmpFolder.newFile("local.pdf").apply { writeText("local-content") }

        val result = PdfShareUtils.resolveReadableFile(
            context = context,
            localCopyPath = localFile.absolutePath,
            originalUri = null,
            activeTempPdfFile = tempFile,
            displayName = "TestDoc",
            cacheSubDir = "share_cache"
        )
        // activeTempPdfFile has highest priority; result is a copy in cache (different file)
        assertNotNull(result)
        assert(result!!.exists())
        assert(result.length() > 0L)
    }

    @Test
    fun testResolveReadableFile_ValidLocalCopyIsReturnedDirectly() {
        val context = RuntimeEnvironment.getApplication()
        val localFile = tmpFolder.newFile("local.pdf").apply { writeText("local-content") }

        val result = PdfShareUtils.resolveReadableFile(
            context = context,
            localCopyPath = localFile.absolutePath,
            originalUri = null,
            activeTempPdfFile = null,    // no decrypted temp
            displayName = "TestDoc",
            cacheSubDir = "share_cache"
        )
        assertNotNull(result)
        assertEquals(localFile.absolutePath, result!!.absolutePath)
    }

    @Test
    fun testResolveReadableFile_MissingLocalCopyReturnsNull_WhenNoUriEither() {
        val context = RuntimeEnvironment.getApplication()

        val result = PdfShareUtils.resolveReadableFile(
            context = context,
            localCopyPath = "/non/existent/file.pdf",
            originalUri = null,
            activeTempPdfFile = null,
            displayName = "TestDoc",
            cacheSubDir = "share_cache"
        )
        assertNull(result)
    }

    @Test
    fun testResolveReadableFile_AllNullReturnsNull() {
        val context = RuntimeEnvironment.getApplication()

        val result = PdfShareUtils.resolveReadableFile(
            context = context,
            localCopyPath = null,
            originalUri = null,
            activeTempPdfFile = null,
            displayName = "TestDoc",
            cacheSubDir = "print_cache"
        )
        assertNull(result)
    }
}
