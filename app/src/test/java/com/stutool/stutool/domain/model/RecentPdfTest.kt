package com.stutool.stutool.domain.model

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentPdfTest {

    @Test
    fun testFromJson_validJson_parsesCorrectly() {
        val json = JSONObject().apply {
            put("uri", "content://test/uri")
            put("name", "test.pdf")
            put("size", 1024L)
            put("lastOpened", 123456789L)
            put("isFavorite", true)
            put("localCopyPath", "/path/to/local/copy.pdf")
            put("pageCount", 10)
        }

        val pdf = RecentPdf.fromJson(json)

        assertEquals("content://test/uri", pdf.uri)
        assertEquals("test.pdf", pdf.name)
        assertEquals(1024L, pdf.size)
        assertEquals(123456789L, pdf.lastOpened)
        assertEquals(true, pdf.isFavorite)
        assertEquals("/path/to/local/copy.pdf", pdf.localCopyPath)
        assertEquals(10, pdf.pageCount)
    }

    @Test
    fun testFromJson_missingName_throwsException() {
        val json = JSONObject().apply {
            put("uri", "content://test/uri")
            put("size", 1024L)
            put("lastOpened", 123456789L)
        }

        assertThrows(JSONException::class.java) {
            RecentPdf.fromJson(json)
        }
    }

    @Test
    fun testFromJson_blankName_throwsException() {
        val json = JSONObject().apply {
            put("uri", "content://test/uri")
            put("name", "   ")
            put("size", 1024L)
            put("lastOpened", 123456789L)
        }

        assertThrows(JSONException::class.java) {
            RecentPdf.fromJson(json)
        }
    }

    @Test
    fun testFromJson_missingUri_throwsException() {
        val json = JSONObject().apply {
            put("name", "test.pdf")
            put("size", 1024L)
            put("lastOpened", 123456789L)
        }

        assertThrows(JSONException::class.java) {
            RecentPdf.fromJson(json)
        }
    }

    @Test
    fun testFromJson_blankUri_throwsException() {
        val json = JSONObject().apply {
            put("uri", "")
            put("name", "test.pdf")
            put("size", 1024L)
            put("lastOpened", 123456789L)
        }

        assertThrows(JSONException::class.java) {
            RecentPdf.fromJson(json)
        }
    }

    @Test
    fun testFromJson_missingSize_throwsException() {
        val json = JSONObject().apply {
            put("uri", "content://test/uri")
            put("name", "test.pdf")
            put("lastOpened", 123456789L)
        }

        assertThrows(JSONException::class.java) {
            RecentPdf.fromJson(json)
        }
    }

    @Test
    fun testFromJson_missingOptionalFields_parsesWithDefaults() {
        val json = JSONObject().apply {
            put("uri", "content://test/uri")
            put("name", "test.pdf")
            put("size", 1024L)
            put("lastOpened", 123456789L)
        }

        val pdf = RecentPdf.fromJson(json)

        assertEquals(false, pdf.isFavorite)
        assertNull(pdf.localCopyPath)
        assertNull(pdf.pageCount)
    }

    @Test
    fun testFromJson_emptyJsonObject_throwsException() {
        val json = JSONObject()

        assertThrows(JSONException::class.java) {
            RecentPdf.fromJson(json)
        }
    }
}
