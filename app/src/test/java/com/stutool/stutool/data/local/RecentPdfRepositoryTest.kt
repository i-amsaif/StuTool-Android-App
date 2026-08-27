package com.stutool.stutool.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.domain.model.RecentPdf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentPdfRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: RecentPdfRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        context.getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = RecentPdfRepository(context)
    }

    @Test
    fun testGetRecentPdfs_validArray_loadsAll() {
        val prefs = context.getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        jsonArray.put(createValidPdfJson("file1.pdf", "content://1", 100))
        jsonArray.put(createValidPdfJson("file2.pdf", "content://2", 200))
        jsonArray.put(createValidPdfJson("file3.pdf", "content://3", 300))
        prefs.edit().putString("recent_pdfs", jsonArray.toString()).commit()

        val list = repository.getRecentPdfs()

        assertEquals(3, list.size)
    }

    @Test
    fun testGetRecentPdfs_mixedValidAndCorrupted_loadsValidOnly() {
        val prefs = context.getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        jsonArray.put(createValidPdfJson("file1.pdf", "content://1", 100))

        // Corrupted one (missing uri)
        val corruptedJson = JSONObject().apply {
            put("name", "corrupted.pdf")
            put("size", 200L)
            put("lastOpened", 123456L)
        }
        jsonArray.put(corruptedJson)

        jsonArray.put(createValidPdfJson("file3.pdf", "content://3", 300))

        prefs.edit().putString("recent_pdfs", jsonArray.toString()).commit()

        val list = repository.getRecentPdfs()

        assertEquals(2, list.size)
        // Order might be sorted by lastOpened, but here they are just loaded.
        // Actually getRecentPdfs sorts by lastOpened. Let's make sure our test accounts for it.
        // Let's assert names are present.
        val names = list.map { it.name }
        assert(names.contains("file1.pdf"))
        assert(names.contains("file3.pdf"))
        assert(!names.contains("corrupted.pdf"))
    }

    @Test
    fun testGetRecentPdfs_completelyInvalidJsonString_returnsEmptyList() {
        val prefs = context.getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("recent_pdfs", "not_a_json_array").commit()

        val list = repository.getRecentPdfs()

        assertEquals(0, list.size)
    }

    @Test
    fun testGetRecentPdfs_nullString_returnsEmptyList() {
        val list = repository.getRecentPdfs()

        assertEquals(0, list.size)
    }

    private fun createValidPdfJson(name: String, uri: String, lastOpened: Long): JSONObject {
        return JSONObject().apply {
            put("uri", uri)
            put("name", name)
            put("size", 1024L)
            put("lastOpened", lastOpened)
        }
    }
}
