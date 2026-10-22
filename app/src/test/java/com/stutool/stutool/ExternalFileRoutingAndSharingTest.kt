package com.stutool.stutool

import android.app.Application
import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.navigation.resolveReaderRouteForEncodedUri
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalFileRoutingAndSharingTest {

    private lateinit var application: Application

    class MockDocProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? {
            return when (uri.lastPathSegment) {
                "opaque_pdf" -> "application/pdf"
                "opaque_txt" -> "text/plain"
                "opaque_docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "opaque_doc" -> "application/msword"
                "opaque_xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "opaque_xls" -> "application/vnd.ms-excel"
                else -> null
            }
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            val name = when (uri.lastPathSegment) {
                "named_pdf" -> "document.pdf"
                "named_txt" -> "document.txt"
                "named_docx" -> "document.docx"
                "named_doc" -> "document.doc"
                "named_xlsx" -> "document.xlsx"
                "named_xls" -> "document.xls"
                else -> null
            } ?: return null

            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                addRow(arrayOf(name))
            }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        Robolectric.setupContentProvider(MockDocProvider::class.java, "com.stutool.test")
    }

    private fun encodeUri(uriString: String): String {
        return Base64.encodeToString(
            uriString.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. Tests for MainActivity.extractUriFromIntent and getUriFromIntent
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testActionViewExtractsDataUri() {
        val testUri = Uri.parse("content://com.android.providers.downloads/documents/100")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = testUri
        }

        val extracted = MainActivity.extractUriFromIntent(intent)
        val actual = MainActivity.getUriFromIntent(intent)

        assertEquals("content://com.android.providers.downloads/documents/100", extracted)
        assertEquals(testUri, actual)
    }

    @Test
    fun testActionSendWithExtraStreamExtractsUri() {
        val testUri = Uri.parse("content://com.whatsapp.provider.media/files/document.pdf")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, testUri)
        }

        val extracted = MainActivity.extractUriFromIntent(intent)
        val actual = MainActivity.getUriFromIntent(intent)

        assertEquals("content://com.whatsapp.provider.media/files/document.pdf", extracted)
        assertEquals(testUri, actual)
    }

    @Test
    fun testActionSendWithClipDataExtractsUri() {
        val testUri = Uri.parse("content://com.google.android.apps.docs.storage/doc.docx")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            clipData = ClipData.newUri(application.contentResolver, "DOCX", testUri)
        }

        val extracted = MainActivity.extractUriFromIntent(intent)
        val actual = MainActivity.getUriFromIntent(intent)

        assertEquals("content://com.google.android.apps.docs.storage/doc.docx", extracted)
        assertEquals(testUri, actual)
    }

    @Test
    fun testActionSendTextSnippetWithoutStreamReturnsNull() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/article?id=123")
        }

        val extracted = MainActivity.extractUriFromIntent(intent)
        val actual = MainActivity.getUriFromIntent(intent)

        assertNull("ACTION_SEND without a file stream must return null", extracted)
        assertNull("ACTION_SEND without a file stream must return null", actual)
    }

    @Test
    fun testNullOrUnrelatedActionReturnsNull() {
        assertNull(MainActivity.extractUriFromIntent(null))
        assertNull(MainActivity.getUriFromIntent(null))

        val mainIntent = Intent(Intent.ACTION_MAIN)
        assertNull(MainActivity.extractUriFromIntent(mainIntent))
        assertNull(MainActivity.getUriFromIntent(mainIntent))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. Tests for resolveReaderRouteForEncodedUri across all formats
    // ─────────────────────────────────────────────────────────────────────────────

    // Direct file:// path tests
    @Test
    fun testResolveRouteByFileExtension_Pdf() {
        val uri = "file:///storage/emulated/0/Download/sample.pdf"
        val encoded = encodeUri(uri)
        assertEquals("reader_screen/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByFileExtension_Txt() {
        val uri = "file:///storage/emulated/0/Download/notes.txt"
        val encoded = encodeUri(uri)
        assertEquals("txt_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByFileExtension_Docx() {
        val uri = "file:///storage/emulated/0/Download/report.docx"
        val encoded = encodeUri(uri)
        assertEquals("docx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByFileExtension_LegacyDoc() {
        val uri = "file:///storage/emulated/0/Download/old_resume.doc"
        val encoded = encodeUri(uri)
        assertEquals("docx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByFileExtension_Xlsx() {
        val uri = "file:///storage/emulated/0/Download/data.xlsx"
        val encoded = encodeUri(uri)
        assertEquals("xlsx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByFileExtension_LegacyXlsRoutesToSpreadsheetViewer() {
        val uri = "file:///storage/emulated/0/Download/budget.xls"
        val encoded = encodeUri(uri)
        assertEquals("xlsx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    // Content URI MIME-type tests (opaque URI paths)
    @Test
    fun testResolveRouteByMimeType_LegacyXlsMimeRoutesToSpreadsheetViewer() {
        val uri = "content://com.stutool.test/opaque_xls"
        val encoded = encodeUri(uri)
        assertEquals("xlsx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByMimeType_XlsxMime() {
        val uri = "content://com.stutool.test/opaque_xlsx"
        val encoded = encodeUri(uri)
        assertEquals("xlsx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByMimeType_DocxMime() {
        val uri = "content://com.stutool.test/opaque_docx"
        val encoded = encodeUri(uri)
        assertEquals("docx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByMimeType_LegacyDocMime() {
        val uri = "content://com.stutool.test/opaque_doc"
        val encoded = encodeUri(uri)
        assertEquals("docx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByMimeType_TextPlain() {
        val uri = "content://com.stutool.test/opaque_txt"
        val encoded = encodeUri(uri)
        assertEquals("txt_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByMimeType_ApplicationPdf() {
        val uri = "content://com.stutool.test/opaque_pdf"
        val encoded = encodeUri(uri)
        assertEquals("reader_screen/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    // Content URI query(DISPLAY_NAME) tests
    @Test
    fun testResolveRouteByDisplayName_LegacyXls() {
        val uri = "content://com.stutool.test/named_xls"
        val encoded = encodeUri(uri)
        assertEquals("xlsx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByDisplayName_Xlsx() {
        val uri = "content://com.stutool.test/named_xlsx"
        val encoded = encodeUri(uri)
        assertEquals("xlsx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByDisplayName_Docx() {
        val uri = "content://com.stutool.test/named_docx"
        val encoded = encodeUri(uri)
        assertEquals("docx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByDisplayName_LegacyDoc() {
        val uri = "content://com.stutool.test/named_doc"
        val encoded = encodeUri(uri)
        assertEquals("docx_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByDisplayName_Txt() {
        val uri = "content://com.stutool.test/named_txt"
        val encoded = encodeUri(uri)
        assertEquals("txt_reader/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }

    @Test
    fun testResolveRouteByDisplayName_Pdf() {
        val uri = "content://com.stutool.test/named_pdf"
        val encoded = encodeUri(uri)
        assertEquals("reader_screen/$encoded", resolveReaderRouteForEncodedUri(application, encoded))
    }
}