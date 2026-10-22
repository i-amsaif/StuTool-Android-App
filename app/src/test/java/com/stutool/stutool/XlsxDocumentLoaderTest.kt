package com.stutool.stutool

import com.stutool.stutool.domain.model.RecentXlsxFile
import com.stutool.stutool.ui.screens.viewer.XlsxDocumentLoader
import com.stutool.stutool.ui.screens.viewer.XlsxLoadException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxDocumentLoaderTest {

    private val defaultWorkbookXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private val defaultRelsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildXlsxBytes(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun loadWorkbook_minimalSingleSheet_returnsRowsAndCells() {
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Summary" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent()

        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="inlineStr"><is><t>Roll No</t></is></c>
                  <c r="B1" t="inlineStr"><is><t>Student Name</t></is></c>
                  <c r="C1" t="inlineStr"><is><t>Marks</t></is></c>
                </row>
                <row r="2">
                  <c r="A2"><v>101</v></c>
                  <c r="B2" t="inlineStr"><is><t>Aarav</t></is></c>
                  <c r="C2"><v>94.5</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        // Intentionally place worksheet before workbook.xml to verify entry-order independence
        val zipBytes = buildXlsxBytes(
            linkedMapOf(
                "xl/worksheets/sheet1.xml" to sheet1Xml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml,
                "xl/workbook.xml" to workbookXml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(zipBytes))

        assertFalse(result.isEmpty)
        assertFalse(result.isTruncated)
        assertEquals(1, result.sheets.size)
        val sheet = result.sheets[0]
        assertEquals("Summary", sheet.name)
        assertEquals(2, sheet.rows.size)
        assertEquals(3, sheet.columnCount)
        assertEquals(listOf("Roll No", "Student Name", "Marks"), sheet.rows[0].cells)
        assertEquals(listOf("101", "Aarav", "94.5"), sheet.rows[1].cells)
    }

    @Test
    fun loadWorkbook_multipleSheets_preservesWorkbookOrderAndNames() {
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Semester 1" sheetId="1" r:id="rId1"/>
                <sheet name="Semester 2" sheetId="2" r:id="rId2"/>
              </sheets>
            </workbook>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Target="/xl/worksheets/sheet2.xml"/>
              <Relationship Id="rId2" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()

        val sheet1Xml = """
            <worksheet><sheetData>
              <row r="1"><c r="A1" t="inlineStr"><is><t>Sem 2 Data</t></is></c></row>
            </sheetData></worksheet>
        """.trimIndent()

        val sheet2Xml = """
            <worksheet><sheetData>
              <row r="1"><c r="A1" t="inlineStr"><is><t>Sem 1 Data</t></is></c></row>
            </sheetData></worksheet>
        """.trimIndent()

        val zipBytes = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to workbookXml,
                "xl/_rels/workbook.xml.rels" to relsXml,
                "xl/worksheets/sheet1.xml" to sheet1Xml,
                "xl/worksheets/sheet2.xml" to sheet2Xml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(zipBytes))

        assertEquals(2, result.sheets.size)
        assertEquals("Semester 1", result.sheets[0].name)
        assertEquals("Sem 1 Data", result.sheets[0].rows[0].cells[0])
        assertEquals("Semester 2", result.sheets[1].name)
        assertEquals("Sem 2 Data", result.sheets[1].rows[0].cells[0])
    }

    @Test
    fun loadWorkbook_sharedStrings_resolvesIndicesAndIgnoresPhoneticRuns() {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
              <si><t>Department</t></si>
              <si>
                <r><t>Computer </t></r>
                <r><t>Science</t></r>
                <rPh sb="0" eb="2"><t>PhoneticIgnored</t></rPh>
              </si>
              <si><t xml:space="preserve">  Lab A  </t></si>
            </sst>
        """.trimIndent()

        val sheetXml = """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="s"><v>0</v></c>
                <c r="B1" t="s"><v>1</v></c>
                <c r="C1" t="s"><v>2</v></c>
              </row>
            </sheetData></worksheet>
        """.trimIndent()

        // Put sheet1.xml before sharedStrings.xml to test deferred resolution
        val zipBytes = buildXlsxBytes(
            linkedMapOf(
                "xl/worksheets/sheet1.xml" to sheetXml,
                "xl/sharedStrings.xml" to sharedStringsXml,
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(zipBytes))

        val cells = result.sheets[0].rows[0].cells
        assertEquals(listOf("Department", "Computer Science", "  Lab A  "), cells)
    }

    @Test
    fun loadWorkbook_numericBooleanAndFormulaCachedValues_parsesCorrectly() {
        val sheetXml = """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="b"><v>1</v></c>
                <c r="B1" t="b"><v>0</v></c>
                <c r="C1"><f>SUM(D1:D10)</f><v>420.75</v></c>
                <c r="D1" t="str"><f>CONCAT("A","B")</f><v>AB</v></c>
                <c r="E1" t="e"><f>1/0</f><v>#DIV/0!</v></c>
              </row>
            </sheetData></worksheet>
        """.trimIndent()

        val zipBytes = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml,
                "xl/worksheets/sheet1.xml" to sheetXml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(zipBytes))

        val cells = result.sheets[0].rows[0].cells
        assertEquals(listOf("TRUE", "FALSE", "420.75", "AB", "#DIV/0!"), cells)
    }

    @Test
    fun loadWorkbook_sparseRowsAndColumns_preservesColumnAlignmentAndGaps() {
        val sheetXml = """
            <worksheet><sheetData>
              <row r="2">
                <c r="C2" t="inlineStr"><is><t>Col C Row 2</t></is></c>
              </row>
              <row r="5">
                <c r="A5" t="inlineStr"><is><t>Col A Row 5</t></is></c>
                <c r="D5" t="inlineStr"><is><t>Col D Row 5</t></is></c>
              </row>
            </sheetData></worksheet>
        """.trimIndent()

        val zipBytes = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml,
                "xl/worksheets/sheet1.xml" to sheetXml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(zipBytes))

        val sheet = result.sheets[0]
        // Row 5 is the highest row -> 5 rows total (1..5), Col D is index 3 -> 4 cols total (A..D)
        assertEquals(5, sheet.rows.size)
        assertEquals(4, sheet.columnCount)

        // Row 1 (index 0) is empty gap row
        assertEquals(listOf("", "", "", ""), sheet.rows[0].cells)
        // Row 2 (index 1) has value at C (index 2)
        assertEquals(listOf("", "", "Col C Row 2", ""), sheet.rows[1].cells)
        // Row 3 and 4 are empty gap rows
        assertEquals(listOf("", "", "", ""), sheet.rows[2].cells)
        assertEquals(listOf("", "", "", ""), sheet.rows[3].cells)
        // Row 5 (index 4) has values at A (0) and D (3)
        assertEquals(listOf("Col A Row 5", "", "", "Col D Row 5"), sheet.rows[4].cells)
    }

    @Test
    fun loadWorkbook_unicodeText_preservesCharacters() {
        val sheetXml = """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t>नमस्ते दुनिया</t></is></c>
                <c r="B1" t="inlineStr"><is><t>日本語テスト 📊</t></is></c>
                <c r="C1" t="inlineStr"><is><t>∑ (xᵢ - μ)²</t></is></c>
              </row>
            </sheetData></worksheet>
        """.trimIndent()

        val zipBytes = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml,
                "xl/worksheets/sheet1.xml" to sheetXml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(zipBytes))

        assertEquals(
            listOf("नमस्ते दुनिया", "日本語テスト 📊", "∑ (xᵢ - μ)²"),
            result.sheets[0].rows[0].cells
        )
    }

    @Test
    fun loadWorkbook_emptyWorksheets_marksWorkbookAndSheetEmpty() {
        val sheetXml = """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t></t></is></c>
              </row>
            </sheetData></worksheet>
        """.trimIndent()

        val zipBytes = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml,
                "xl/worksheets/sheet1.xml" to sheetXml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(zipBytes))
        assertTrue(result.isEmpty)
        assertTrue(result.sheets[0].isEmpty)
        assertTrue(result.sheets[0].rows.isEmpty())
    }

    @Test
    fun loadWorkbook_nonXlsxFilesAndMalformedArchives_throwsXlsxLoadException() {
        // 1. Legacy .xls OLE2 header (D0 CF 11 E0 A1 B1 1A E1)
        val ole2Bytes = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
            0x00, 0x01, 0x02, 0x03
        )
        try {
            XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(ole2Bytes))
            fail("Expected XlsxLoadException for legacy .xls")
        } catch (e: XlsxLoadException) {
            assertTrue(e.message!!.contains(".xlsx", ignoreCase = true))
        }

        // 2. DOCX archive passed to XLSX loader
        val docxZip = buildXlsxBytes(mapOf("word/document.xml" to "<w:document/>"))
        try {
            XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(docxZip))
            fail("Expected XlsxLoadException for DOCX")
        } catch (e: XlsxLoadException) {
            assertTrue(e.message!!.contains("Word", ignoreCase = true))
        }

        // 3. Generic ZIP without xl/workbook.xml
        val plainZip = buildXlsxBytes(mapOf("readme.txt" to "hello"))
        try {
            XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(plainZip))
            fail("Expected XlsxLoadException for generic ZIP")
        } catch (e: XlsxLoadException) {
            assertNotNull(e.message)
        }

        // 4. Encrypted OOXML package
        val encryptedZip = buildXlsxBytes(
            mapOf(
                "EncryptionInfo" to "enc",
                "EncryptedPackage" to "pkg"
            )
        )
        try {
            XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(encryptedZip))
            fail("Expected XlsxLoadException for encrypted package")
        } catch (e: XlsxLoadException) {
            assertTrue(e.message!!.contains("Password-protected", ignoreCase = true))
        }

        // 5. Invalid workbook relationships pointing to missing worksheet
        val brokenRelsZip = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to """
                    <Relationships>
                      <Relationship Id="rId1" Target="worksheets/missing.xml"/>
                    </Relationships>
                """.trimIndent(),
                "xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData></worksheet>"
            )
        )
        try {
            XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(brokenRelsZip))
            fail("Expected XlsxLoadException for unresolved worksheet relationships")
        } catch (e: XlsxLoadException) {
            assertTrue(e.message!!.contains("relationships", ignoreCase = true))
        }

        // 6. Malformed XML in worksheet
        val malformedXmlZip = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml,
                "xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r=\"1\"><c r=\"A1\"><v>unclosed"
            )
        )
        try {
            XlsxDocumentLoader.loadWorkbook(ByteArrayInputStream(malformedXmlZip))
            fail("Expected XlsxLoadException for malformed XML")
        } catch (e: XlsxLoadException) {
            assertTrue(e.message!!.contains("Malformed XML", ignoreCase = true))
        }
    }

    @Test
    fun loadWorkbook_exceedingRowAndColumnLimits_setsTruncatedFlag() {
        // Column DW is column index 126 (> maxColsPerSheet = 3)
        val sheetXml = """
            <worksheet><sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t>R1C1</t></is></c>
                <c r="DW1" t="inlineStr"><is><t>Beyond Col Cap</t></is></c>
              </row>
              <row r="2">
                <c r="A2" t="inlineStr"><is><t>R2C1</t></is></c>
              </row>
              <row r="3">
                <c r="A3" t="inlineStr"><is><t>Beyond Row Cap</t></is></c>
              </row>
            </sheetData></worksheet>
        """.trimIndent()

        val zipBytes = buildXlsxBytes(
            mapOf(
                "xl/workbook.xml" to defaultWorkbookXml,
                "xl/_rels/workbook.xml.rels" to defaultRelsXml,
                "xl/worksheets/sheet1.xml" to sheetXml
            )
        )

        val result = XlsxDocumentLoader.loadWorkbook(
            inputStream = ByteArrayInputStream(zipBytes),
            maxRowsPerSheet = 2,
            maxColsPerSheet = 3
        )

        assertTrue(result.isTruncated)
        assertTrue(result.sheets[0].isTruncated)
        assertEquals(2, result.sheets[0].rows.size)
        assertEquals(1, result.sheets[0].columnCount)
        assertEquals("R1C1", result.sheets[0].rows[0].cells[0])
        assertEquals("R2C1", result.sheets[0].rows[1].cells[0])
    }

    @Test
    fun columnHelpers_parseAndFormatColumnReferencesAccurate() {
        assertEquals(0, XlsxDocumentLoader.parseColumnIndexFromCellRef("A1"))
        assertEquals(2, XlsxDocumentLoader.parseColumnIndexFromCellRef("C5"))
        assertEquals(25, XlsxDocumentLoader.parseColumnIndexFromCellRef("Z10"))
        assertEquals(26, XlsxDocumentLoader.parseColumnIndexFromCellRef("AA1"))
        assertEquals(27, XlsxDocumentLoader.parseColumnIndexFromCellRef("AB99"))
        assertEquals(51, XlsxDocumentLoader.parseColumnIndexFromCellRef("AZ1"))
        assertEquals(52, XlsxDocumentLoader.parseColumnIndexFromCellRef("BA1"))

        assertEquals("A", XlsxDocumentLoader.columnIndexToLabel(0))
        assertEquals("C", XlsxDocumentLoader.columnIndexToLabel(2))
        assertEquals("Z", XlsxDocumentLoader.columnIndexToLabel(25))
        assertEquals("AA", XlsxDocumentLoader.columnIndexToLabel(26))
        assertEquals("AB", XlsxDocumentLoader.columnIndexToLabel(27))
        assertEquals("AZ", XlsxDocumentLoader.columnIndexToLabel(51))
        assertEquals("BA", XlsxDocumentLoader.columnIndexToLabel(52))
    }

    @Test
    fun recentXlsxFile_encodedRecordRoundTripAndDeduplication() {
        val file1 = RecentXlsxFile(
            uri = "content://com.android.providers.downloads.documents/document/55",
            name = "Attendance Sheet.xlsx",
            size = 18432L,
            lastOpened = 1700000000000L,
            localCopyPath = "/data/user/0/com.stutool.stutool/files/xlsx_copies/Attendance_Sheet.xlsx"
        )
        val encoded = file1.toEncodedRecord()
        val decoded = RecentXlsxFile.fromEncodedRecord(encoded)
        assertNotNull(decoded)
        assertEquals(file1, decoded)

        val file2SameLogical = RecentXlsxFile(
            uri = "content://com.google.android.apps.nbu.files.provider/2/999",
            name = "attendance sheet",
            size = 18432L,
            lastOpened = 1700000050000L
        )
        assertEquals(file1.fileIdentity, file2SameLogical.fileIdentity)
    }
}
