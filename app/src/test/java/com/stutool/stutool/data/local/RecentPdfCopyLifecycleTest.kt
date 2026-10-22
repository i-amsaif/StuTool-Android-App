package com.stutool.stutool.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.domain.model.RecentPdf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentPdfCopyLifecycleTest {

    private lateinit var context: Context
    private lateinit var repository: RecentPdfRepository
    private lateinit var copiesDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = RecentPdfRepository(context)

        copiesDir = File(context.filesDir, "pdf_copies").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun testDeleteRecentPdfWithUniqueCopy_removesFileFromDisk() {
        val uniqueCopy = File(copiesDir, "lecture_notes.pdf").apply {
            writeText("Lecture notes binary content")
        }
        assertTrue(uniqueCopy.exists())

        val pdf = RecentPdf(
            uri = "content://com.android.providers.media/document/101",
            name = "lecture_notes.pdf",
            size = uniqueCopy.length(),
            lastOpened = 1000L,
            localCopyPath = uniqueCopy.absolutePath
        )
        repository.saveRecentPdf(pdf)
        assertEquals(1, repository.getRecentPdfs().size)

        repository.deleteRecentPdf(pdf.uri)

        assertTrue("Recent list should be empty", repository.getRecentPdfs().isEmpty())
        assertFalse("Unique local copy file must be deleted from disk", uniqueCopy.exists())
    }

    @Test
    fun testDeleteRecentPdfSharedCopy_preservesFileUntilLastReferenceRemoved() {
        val sharedFile = File(copiesDir, "shared_syllabus.pdf").apply {
            writeText("Shared course syllabus")
        }
        assertTrue(sharedFile.exists())

        val entry1 = RecentPdf(
            uri = "content://source.a/doc/1",
            name = "shared_syllabus.pdf",
            size = sharedFile.length(),
            lastOpened = 1000L,
            localCopyPath = sharedFile.absolutePath
        )
        val entry2 = RecentPdf(
            uri = "content://source.b/doc/2",
            name = "shared_syllabus_alias.pdf",
            size = sharedFile.length(),
            lastOpened = 2000L,
            localCopyPath = sharedFile.absolutePath
        )

        repository.saveRecentPdf(entry1)
        repository.saveRecentPdf(entry2)
        assertEquals(2, repository.getRecentPdfs().size)

        // Delete entry 2; entry 1 still references sharedFile
        repository.deleteRecentPdf(entry2.uri)
        assertEquals(1, repository.getRecentPdfs().size)
        assertTrue("Shared file must NOT be deleted while entry1 still references it", sharedFile.exists())

        // Delete entry 1; no remaining entries reference sharedFile
        repository.deleteRecentPdf(entry1.uri)
        assertTrue(repository.getRecentPdfs().isEmpty())
        assertFalse("Shared file must be deleted once the last reference is removed", sharedFile.exists())
    }

    @Test
    fun testMaxRecentEviction_cleansUpOnlyUnreferencedCopies() {
        // Create a shared copy referenced by entry 0 and entry 50
        val sharedCopy = File(copiesDir, "shared_copy.pdf").apply { writeText("shared") }

        // Populate 50 entries
        for (i in 0 until 50) {
            val file = if (i == 0) sharedCopy else File(copiesDir, "doc_$i.pdf").apply { writeText("data_$i") }
            val item = RecentPdf(
                uri = "content://doc/$i",
                name = "doc_$i.pdf",
                size = file.length(),
                lastOpened = 1000L + i,
                localCopyPath = file.absolutePath
            )
            repository.saveRecentPdf(item)
        }
        assertEquals(50, repository.getRecentPdfs().size)

        // Add 51st entry with a unique copy; this will evict the oldest entry
        val evictedCopy = File(copiesDir, "doc_to_evict.pdf").apply { writeText("evict_me") }
        val item51 = RecentPdf(
            uri = "content://doc/new_top",
            name = "new_top.pdf",
            size = 100L,
            lastOpened = 999999L,
            localCopyPath = evictedCopy.absolutePath
        )
        repository.saveRecentPdf(item51)

        assertEquals("Recent list must be bounded to MAX_RECENT (50)", 50, repository.getRecentPdfs().size)
        val recentUris = repository.getRecentPdfs().map { it.uri }
        assertTrue("Newest item must be present", recentUris.contains("content://doc/new_top"))
    }

    @Test
    fun testClearAllRecentPdfs_removesAllReferencedCopiesSafely() {
        val file1 = File(copiesDir, "item1.pdf").apply { writeText("content1") }
        val file2 = File(copiesDir, "item2.pdf").apply { writeText("content2") }

        repository.saveRecentPdf(RecentPdf("content://1", "item1.pdf", 10L, 1000L, localCopyPath = file1.absolutePath))
        repository.saveRecentPdf(RecentPdf("content://2", "item2.pdf", 20L, 2000L, localCopyPath = file2.absolutePath))
        assertEquals(2, repository.getRecentPdfs().size)

        repository.clearAllRecentPdfs()

        assertTrue(repository.getRecentPdfs().isEmpty())
        assertFalse("file1 should be deleted after clearing history", file1.exists())
        assertFalse("file2 should be deleted after clearing history", file2.exists())
    }

    @Test
    fun testPruneInaccessibleEntries_removesOrphanedCopiesAndPreservesReferenced() {
        val activeFile = File(copiesDir, "active_report.pdf").apply { writeText("active report content") }
        val activeEntry = RecentPdf(
            uri = "content://active/1",
            name = "active_report.pdf",
            size = activeFile.length(),
            lastOpened = 1000L,
            localCopyPath = activeFile.absolutePath
        )
        repository.saveRecentPdf(activeEntry)

        // Create an unindexed orphaned file older than 60 seconds
        val orphanFile = File(copiesDir, "old_orphan.pdf").apply {
            writeText("orphaned copy from previous session")
            setLastModified(System.currentTimeMillis() - 120_000L)
        }
        assertTrue(orphanFile.exists())

        // Create an unindexed newly created file within 60-second grace window (in-flight copy simulation)
        val inFlightFile = File(copiesDir, "in_flight_copy.pdf").apply {
            writeText("actively copying")
            setLastModified(System.currentTimeMillis())
        }
        assertTrue(inFlightFile.exists())

        repository.pruneInaccessibleEntries()

        assertTrue("Referenced active file must be preserved", activeFile.exists())
        assertFalse("Old orphaned copy (>60s) must be deleted", orphanFile.exists())
        assertTrue("In-flight copy within 60s grace window must be preserved", inFlightFile.exists())
    }

    @Test
    fun testMissingFilesAndErrorsDoNotCrashRepositoryOperations() {
        val nonExistentPath = File(copiesDir, "non_existent.pdf").absolutePath
        val entryWithMissingFile = RecentPdf(
            uri = "content://ghost/1",
            name = "ghost.pdf",
            size = 500L,
            lastOpened = 1000L,
            localCopyPath = nonExistentPath
        )
        repository.saveRecentPdf(entryWithMissingFile)
        assertEquals(1, repository.getRecentPdfs().size)

        // Deleting entry pointing to non-existent file must not crash
        try {
            repository.deleteRecentPdf("content://ghost/1")
            assertTrue(repository.getRecentPdfs().isEmpty())
        } catch (e: Exception) {
            fail("deleteRecentPdf must not throw on missing file: ${e.message}")
        }

        // pruneInaccessibleEntries must not crash
        try {
            repository.pruneInaccessibleEntries()
        } catch (e: Exception) {
            fail("pruneInaccessibleEntries must not throw: ${e.message}")
        }

        // clearAllRecentPdfs must not crash
        try {
            repository.clearAllRecentPdfs()
        } catch (e: Exception) {
            fail("clearAllRecentPdfs must not throw: ${e.message}")
        }
    }

    @Test
    fun testSafeDeleteFileRefusesToDeleteOutsidePdfCopiesDirectory() {
        val outsideFile = File(context.filesDir, "important_user_file.txt").apply {
            writeText("Critical user data outside pdf_copies")
        }
        assertTrue(outsideFile.exists())

        val entry = RecentPdf(
            uri = "content://malicious/1",
            name = "escape.pdf",
            size = 100L,
            lastOpened = 1000L,
            localCopyPath = outsideFile.absolutePath
        )
        repository.saveRecentPdf(entry)

        repository.deleteRecentPdf(entry.uri)

        assertTrue("safeDeleteFile must refuse to delete files outside pdf_copies", outsideFile.exists())
        assertEquals("Critical user data outside pdf_copies", outsideFile.readText())
        outsideFile.delete()
    }

    @Test
    fun testTwoDifferentPdfsWithSameDisplayNameDoNotCollideOrReuseWrongContent() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.stutool.stutool.ui.screens.viewer.PdfReaderViewModel(app)

        val tempDir = File(context.cacheDir, "test_sources").apply { mkdirs() }
        val sourceFileA = File(tempDir, "sourceA.pdf").apply { writeText("Content Alpha for Document A") }
        val sourceFileB = File(tempDir, "sourceB.pdf").apply { writeText("Content Beta for Document B that is much longer") }

        val uriA = Uri.fromFile(sourceFileA)
        val uriB = Uri.fromFile(sourceFileB)

        // 1. Copy Document A with display name "document.pdf"
        val pathA = vm.copyUriToInternalStorage(uriA, "document.pdf", context, sourceFileA.length())
        assertNotNull(pathA)
        repository.saveRecentPdf(
            RecentPdf(
                uri = uriA.toString(),
                name = "document.pdf",
                size = sourceFileA.length(),
                lastOpened = 1000L,
                localCopyPath = pathA
            )
        )

        // 2. Re-open Document A: should reuse pathA without duplication
        val pathA2 = vm.copyUriToInternalStorage(uriA, "document.pdf", context, sourceFileA.length())
        assertEquals("Identical document must reuse existing local copy path", pathA, pathA2)

        // 3. Copy Document B with the EXACT SAME display name "document.pdf"
        val pathB = vm.copyUriToInternalStorage(uriB, "document.pdf", context, sourceFileB.length())
        assertNotNull(pathB)
        assertNotEquals("Distinct documents with same display name must NOT share the same copy path", pathA, pathB)

        val fileA = File(pathA!!)
        val fileB = File(pathB!!)
        assertTrue("Copy A must exist", fileA.exists())
        assertTrue("Copy B must exist", fileB.exists())

        assertEquals("Content Alpha for Document A", fileA.readText())
        assertEquals("Content Beta for Document B that is much longer", fileB.readText())

        // Save Document B to repository
        repository.saveRecentPdf(
            RecentPdf(
                uri = uriB.toString(),
                name = "document.pdf",
                size = sourceFileB.length(),
                lastOpened = 2000L,
                localCopyPath = pathB
            )
        )

        // 4. Delete Document A: fileA must be deleted, but fileB must remain intact
        repository.deleteRecentPdf(uriA.toString())
        assertFalse("Document A copy must be deleted after removal", fileA.exists())
        assertTrue("Document B copy must remain intact after Document A removal", fileB.exists())
        assertEquals("Content Beta for Document B that is much longer", fileB.readText())

        // Cleanup tempDir
        tempDir.deleteRecursively()
    }

    @Test
    fun testTwoDifferentPdfsWithSameNameAndExactSameSizeDoNotReuseOrDeleteEachOther() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.stutool.stutool.ui.screens.viewer.PdfReaderViewModel(app)

        val tempDir = File(context.cacheDir, "test_sources_same_size").apply { mkdirs() }
        // Both documents have EXACTLY 32 bytes of content
        val sourceFileA = File(tempDir, "docA.pdf").apply { writeText("ExactSizeDoc1234567890ContentAAA") }
        val sourceFileB = File(tempDir, "docB.pdf").apply { writeText("ExactSizeDoc1234567890ContentBBB") }
        assertEquals("Files must have the exact same length", sourceFileA.length(), sourceFileB.length())

        val fileUriA = Uri.fromFile(sourceFileA)
        val fileUriB = Uri.fromFile(sourceFileB)

        val pathA = vm.copyUriToInternalStorage(fileUriA, "same_name.pdf", context, sourceFileA.length())
        assertNotNull(pathA)
        repository.saveRecentPdf(
            RecentPdf(
                uri = fileUriA.toString(),
                name = "same_name.pdf",
                size = sourceFileA.length(),
                lastOpened = 1000L,
                localCopyPath = pathA
            )
        )

        // Copy document B with exact same name AND exact same size
        val pathB = vm.copyUriToInternalStorage(fileUriB, "same_name.pdf", context, sourceFileB.length())
        assertNotNull(pathB)
        assertNotEquals("Distinct content URIs with same name and same size must NOT reuse the same copy path", pathA, pathB)

        val copyFileA = File(pathA!!)
        val copyFileB = File(pathB!!)
        assertTrue("Copy A must exist", copyFileA.exists())
        assertTrue("Copy B must exist", copyFileB.exists())
        assertEquals("ExactSizeDoc1234567890ContentAAA", copyFileA.readText())
        assertEquals("ExactSizeDoc1234567890ContentBBB", copyFileB.readText())

        // Save document B
        repository.saveRecentPdf(
            RecentPdf(
                uri = fileUriB.toString(),
                name = "same_name.pdf",
                size = sourceFileB.length(),
                lastOpened = 2000L,
                localCopyPath = pathB
            )
        )

        // Ensure saving B did NOT delete A's copy
        assertTrue("Copy A must still exist after saving B", copyFileA.exists())
        assertTrue("Copy B must still exist after saving B", copyFileB.exists())

        // Reopen A: must reuse pathA without overwriting
        val pathAReopen = vm.copyUriToInternalStorage(fileUriA, "same_name.pdf", context, sourceFileA.length())
        assertEquals(pathA, pathAReopen)

        // Reopen B: must reuse pathB without overwriting
        val pathBReopen = vm.copyUriToInternalStorage(fileUriB, "same_name.pdf", context, sourceFileB.length())
        assertEquals(pathB, pathBReopen)

        tempDir.deleteRecursively()
    }

    @Test
    fun testInFlightCopyRegistration_protectsFileFromOrphanReconciliationAndDeletion() {
        val inFlightFile = File(copiesDir, "streaming_large_doc.pdf").apply {
            writeText("Large document being streamed over slow network...")
            // Set age to 5 minutes ago to simulate an operation older than the 60s grace period
            setLastModified(System.currentTimeMillis() - 300_000L)
        }
        assertTrue(inFlightFile.exists())

        // Without registration, reconcileOrphanedCopies would delete it because it is unindexed and >60s old
        // Register it as in-flight
        val registration = RecentPdfRepository.registerInFlightCopy(inFlightFile.canonicalPath)
        try {
            assertTrue(RecentPdfRepository.isInFlightCopy(inFlightFile.canonicalPath))

            // Reconcile orphans: in-flight file MUST be preserved
            repository.reconcileOrphanedCopies()
            assertTrue("In-flight copy must NOT be deleted by orphan reconciliation", inFlightFile.exists())
        } finally {
            registration.close()
        }

        assertFalse("Path should no longer be registered as in-flight", RecentPdfRepository.isInFlightCopy(inFlightFile.canonicalPath))

        // Now run reconciliation again after in-flight registration is closed
        repository.reconcileOrphanedCopies()
        assertFalse("Unreferenced file older than 60s should be deleted once in-flight registration is released", inFlightFile.exists())
    }
}
