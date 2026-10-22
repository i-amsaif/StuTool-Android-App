package com.stutool.stutool.ui.screens.viewer

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.math.BigDecimal
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import kotlin.math.max

/**
 * Parsed representation of an Excel (.xlsx) workbook for lightweight read-only viewing.
 */
data class XlsxWorkbook(
    val sheets: List<XlsxSheet>,
    val isEmpty: Boolean,
    val isTruncated: Boolean = false
)

data class XlsxSheet(
    val name: String,
    val rows: List<XlsxRow>,
    val columnCount: Int,
    val isEmpty: Boolean,
    val isTruncated: Boolean = false
)

data class XlsxRow(
    val rowNumber: Int,
    val cells: List<String>
)

class XlsxLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Dependency-free OOXML (.xlsx) parser using [ZipInputStream] and SAX XML parsing.
 * Streams archive entries directly without extracting temporary files to disk or
 * loading duplicate copies of the archive into memory.
 */
object XlsxDocumentLoader {

    const val MAX_SHEETS = 30
    const val MAX_ROWS_PER_SHEET = 2_000
    const val MAX_COLS_PER_SHEET = 100
    const val MAX_SHARED_STRINGS = 50_000
    const val MAX_CELL_CHARS = 2_000

    /** Maximum gap of completely omitted XML rows that will be filled as blank rows in the grid. */
    private const val MAX_FILLED_EMPTY_ROW_GAP = 25

    /** Maximum uncompressed bytes read from any single XML entry inside the archive (32 MB). */
    private const val MAX_XML_ENTRY_BYTES = 32L * 1024L * 1024L

    private const val PREVIEW_HEADER_BYTES = 16

    fun loadWorkbook(
        inputStream: InputStream,
        maxSheets: Int = MAX_SHEETS,
        maxRowsPerSheet: Int = MAX_ROWS_PER_SHEET,
        maxColsPerSheet: Int = MAX_COLS_PER_SHEET,
        maxSharedStrings: Int = MAX_SHARED_STRINGS,
        checkCancelled: () -> Unit = {}
    ): XlsxWorkbook {
        val buffered = if (inputStream is BufferedInputStream) {
            inputStream
        } else {
            BufferedInputStream(inputStream, 16 * 1024)
        }

        checkCancelled()

        buffered.mark(PREVIEW_HEADER_BYTES + 4)
        val header = ByteArray(PREVIEW_HEADER_BYTES)
        val headerLen = readUpTo(buffered, header, PREVIEW_HEADER_BYTES)

        if (headerLen <= 0) {
            throw XlsxLoadException("This file is empty (0 bytes) and is not a valid .xlsx workbook.")
        }

        validateHeaderSignature(header, headerLen)
        buffered.reset()

        val workbookSheets = ArrayList<WorkbookSheetMeta>()
        val relIdToTarget = HashMap<String, String>()
        val sharedStrings = ArrayList<String>()
        val rawWorksheetsByPath = LinkedHashMap<String, RawWorksheet>()

        var foundWorkbookXml = false
        var foundWordDocXml = false
        var foundPresentationXml = false
        var foundEncryptedPackage = false
        var sharedStringsTruncated = false
        var totalEntriesSeen = 0

        try {
            val zipStream = ZipInputStream(buffered)
            while (true) {
                checkCancelled()
                val entry = try {
                    zipStream.nextEntry
                } catch (e: ZipException) {
                    throw XlsxLoadException(
                        "Corrupted or invalid .xlsx archive: ${e.localizedMessage ?: "Invalid ZIP structure"}",
                        e
                    )
                } ?: break

                totalEntriesSeen++
                val normalizedPath = normalizeZipPath(entry.name)
                val lowerPath = normalizedPath.lowercase(Locale.ROOT)

                when {
                    lowerPath == "xl/workbook.xml" -> {
                        foundWorkbookXml = true
                        val handler = WorkbookXmlHandler(checkCancelled)
                        parseXmlEntry(zipStream, handler)
                        workbookSheets.addAll(handler.sheets)
                    }

                    lowerPath == "xl/_rels/workbook.xml.rels" -> {
                        try {
                            val handler = WorkbookRelsXmlHandler(checkCancelled)
                            parseXmlEntry(zipStream, handler)
                            relIdToTarget.putAll(handler.relMap)
                        } catch (_: Exception) {
                            // Fall back to default sheet path resolution if rels XML is malformed
                        }
                    }

                    lowerPath == "xl/sharedstrings.xml" -> {
                        val handler = SharedStringsXmlHandler(
                            maxStrings = maxSharedStrings,
                            checkCancelled = checkCancelled
                        )
                        parseXmlEntry(zipStream, handler)
                        sharedStrings.addAll(handler.strings)
                        if (handler.isTruncated) {
                            sharedStringsTruncated = true
                        }
                    }

                    lowerPath.startsWith("xl/worksheets/") && lowerPath.endsWith(".xml") -> {
                        if (rawWorksheetsByPath.size < maxSheets) {
                            val handler = WorksheetXmlHandler(
                                maxRows = maxRowsPerSheet,
                                maxCols = maxColsPerSheet,
                                checkCancelled = checkCancelled
                            )
                            parseXmlEntry(zipStream, handler)
                            rawWorksheetsByPath[lowerPath] = RawWorksheet(
                                rows = handler.rows,
                                maxColIndex = handler.maxColIndex,
                                isTruncated = handler.isTruncated
                            )
                        }
                    }

                    lowerPath == "word/document.xml" -> {
                        foundWordDocXml = true
                    }

                    lowerPath == "ppt/presentation.xml" -> {
                        foundPresentationXml = true
                    }

                    lowerPath == "encryptedpackage" || lowerPath == "encryptioninfo" -> {
                        foundEncryptedPackage = true
                    }
                }

                zipStream.closeEntry()
            }
        } catch (e: XlsxLoadException) {
            throw e
        } catch (e: ZipException) {
            throw XlsxLoadException("Invalid or corrupted .xlsx archive.", e)
        } catch (e: IOException) {
            throw XlsxLoadException("Failed to read .xlsx file: ${e.localizedMessage ?: "I/O error"}", e)
        }

        checkCancelled()

        if (!foundWorkbookXml) {
            if (foundEncryptedPackage) {
                throw XlsxLoadException("Password-protected or encrypted Excel (.xlsx) files are not supported.")
            }
            if (foundWordDocXml) {
                throw XlsxLoadException("This file is a Word document (.docx), not an Excel (.xlsx) workbook.")
            }
            if (foundPresentationXml) {
                throw XlsxLoadException("This file is a PowerPoint presentation (.pptx), not an Excel (.xlsx) workbook.")
            }
            if (totalEntriesSeen == 0) {
                throw XlsxLoadException("Invalid or corrupted .xlsx file: archive contains no entries.")
            }
            throw XlsxLoadException("Invalid .xlsx file: missing xl/workbook.xml metadata.")
        }

        if (workbookSheets.isEmpty()) {
            throw XlsxLoadException("Invalid .xlsx workbook: no worksheets are defined in xl/workbook.xml.")
        }

        if (rawWorksheetsByPath.isEmpty()) {
            throw XlsxLoadException("Invalid .xlsx workbook: no worksheet XML files were found in xl/worksheets/.")
        }

        val resolvedSheets = ArrayList<XlsxSheet>()
        val usedPaths = HashSet<String>()
        var anySheetTruncated = sharedStringsTruncated || workbookSheets.size > maxSheets

        for ((index, sheetMeta) in workbookSheets.take(maxSheets).withIndex()) {
            checkCancelled()
            val rawSheet = resolveWorksheetForMeta(
                index = index,
                meta = sheetMeta,
                relIdToTarget = relIdToTarget,
                rawWorksheetsByPath = rawWorksheetsByPath,
                usedPaths = usedPaths
            ) ?: continue

            val builtSheet = buildResolvedSheet(
                name = sheetMeta.name.ifBlank { "Sheet${index + 1}" },
                raw = rawSheet,
                sharedStrings = sharedStrings,
                checkCancelled = checkCancelled
            )
            if (builtSheet.isTruncated) {
                anySheetTruncated = true
            }
            resolvedSheets.add(builtSheet)
        }

        if (resolvedSheets.isEmpty()) {
            throw XlsxLoadException("Invalid .xlsx workbook: worksheet relationships could not be resolved to any valid worksheet.")
        }

        val allEmpty = resolvedSheets.all { it.isEmpty }
        return XlsxWorkbook(
            sheets = resolvedSheets,
            isEmpty = allEmpty,
            isTruncated = anySheetTruncated
        )
    }

    /**
     * Converts a 0-based column index into an Excel column label (`0 -> "A"`, `25 -> "Z"`, `26 -> "AA"`).
     */
    fun columnIndexToLabel(colIndex: Int): String {
        if (colIndex < 0) return ""
        val sb = StringBuilder(4)
        var num = colIndex + 1
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A'.code + rem).toChar())
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }

    /**
     * Parses a cell reference such as `"A1"`, `"C5"`, `"AA12"`, or `"$B$4"` into a 0-based column index.
     * Returns `null` if no valid column letters are present.
     */
    fun parseColumnIndexFromCellRef(cellRef: String?): Int? {
        if (cellRef.isNullOrBlank()) return null
        var colValue = 0
        var foundLetter = false
        for (i in cellRef.indices) {
            val ch = cellRef[i]
            when {
                ch == '$' -> continue
                ch in 'A'..'Z' -> {
                    foundLetter = true
                    colValue = colValue * 26 + (ch.code - 'A'.code + 1)
                }
                ch in 'a'..'z' -> {
                    foundLetter = true
                    colValue = colValue * 26 + (ch.code - 'a'.code + 1)
                }
                ch in '0'..'9' -> break
                else -> return null
            }
            // Prevent integer overflow on malformed cell refs
            if (colValue > 16_384) break
        }
        return if (foundLetter && colValue >= 1) colValue - 1 else null
    }

    private fun resolveWorksheetForMeta(
        index: Int,
        meta: WorkbookSheetMeta,
        relIdToTarget: Map<String, String>,
        rawWorksheetsByPath: Map<String, RawWorksheet>,
        usedPaths: MutableSet<String>
    ): RawWorksheet? {
        // 1. Try explicit r:id -> xl/_rels/workbook.xml.rels target
        val relTarget = meta.relId?.let { relIdToTarget[it] }
        if (!relTarget.isNullOrBlank()) {
            val candidatePath = resolveWorkbookRelTarget(relTarget).lowercase(Locale.ROOT)
            val matched = rawWorksheetsByPath[candidatePath]
            if (matched != null) {
                usedPaths.add(candidatePath)
                return matched
            }
            // If relId was explicitly present in relIdToTarget but pointed to a non-existent file,
            // do not silently substitute an unrelated worksheet if workbook.xml.rels was provided.
            if (relIdToTarget.isNotEmpty()) {
                return null
            }
        }

        // 2. Fallback when xl/_rels/workbook.xml.rels is absent: try sheetId or 1-based position
        if (relIdToTarget.isEmpty()) {
            val bySheetId = meta.sheetId?.let { "xl/worksheets/sheet$it.xml".lowercase(Locale.ROOT) }
            if (bySheetId != null && rawWorksheetsByPath.containsKey(bySheetId)) {
                usedPaths.add(bySheetId)
                return rawWorksheetsByPath[bySheetId]
            }
            val byPosition = "xl/worksheets/sheet${index + 1}.xml".lowercase(Locale.ROOT)
            if (rawWorksheetsByPath.containsKey(byPosition)) {
                usedPaths.add(byPosition)
                return rawWorksheetsByPath[byPosition]
            }
            // Last resort when only 1 worksheet exists and rels were omitted
            val unused = rawWorksheetsByPath.entries.firstOrNull { it.key !in usedPaths }
            if (unused != null) {
                usedPaths.add(unused.key)
                return unused.value
            }
        }

        return null
    }

    private fun resolveWorkbookRelTarget(target: String): String {
        val cleaned = normalizeZipPath(target)
        return when {
            cleaned.startsWith("xl/", ignoreCase = true) -> cleaned
            cleaned.startsWith("../xl/", ignoreCase = true) -> cleaned.substring(3)
            else -> "xl/$cleaned"
        }
    }

    private fun normalizeZipPath(path: String): String {
        val segments = path.replace('\\', '/').trimStart('/').split('/')
        val resolved = ArrayList<String>(segments.size)
        for (seg in segments) {
            when (seg) {
                "", "." -> continue
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
                else -> resolved.add(seg)
            }
        }
        return resolved.joinToString("/")
    }

    private fun buildResolvedSheet(
        name: String,
        raw: RawWorksheet,
        sharedStrings: List<String>,
        checkCancelled: () -> Unit
    ): XlsxSheet {
        if (raw.rows.isEmpty() || raw.maxColIndex < 0) {
            return XlsxSheet(
                name = name,
                rows = emptyList(),
                columnCount = 0,
                isEmpty = true,
                isTruncated = raw.isTruncated
            )
        }

        val colCount = (raw.maxColIndex + 1).coerceAtLeast(1)
        val resolvedRows = ArrayList<XlsxRow>(raw.rows.size)
        var hasAnyNonBlankCell = false
        var prevRowNum = 0

        for (rawRow in raw.rows) {
            checkCancelled()
            // Fill small gaps of omitted rows so sparse row jumps <= 25 rows stay aligned visually
            val gap = rawRow.rowNumber - prevRowNum - 1
            if (prevRowNum >= 0 && gap in 1..MAX_FILLED_EMPTY_ROW_GAP &&
                resolvedRows.size + gap < MAX_ROWS_PER_SHEET
            ) {
                val emptyCells = List(colCount) { "" }
                for (missingRowNum in (prevRowNum + 1) until rawRow.rowNumber) {
                    resolvedRows.add(XlsxRow(rowNumber = missingRowNum, cells = emptyCells))
                }
            }

            val rowCells = MutableList(colCount) { "" }
            for ((colIdx, rawCell) in rawRow.cellsByCol) {
                if (colIdx in 0 until colCount) {
                    val text = resolveCellValue(rawCell, sharedStrings)
                    if (text.isNotEmpty()) {
                        hasAnyNonBlankCell = true
                        rowCells[colIdx] = text
                    }
                }
            }
            resolvedRows.add(XlsxRow(rowNumber = rawRow.rowNumber, cells = rowCells))
            prevRowNum = rawRow.rowNumber
        }

        // Trim trailing completely empty rows if no data follows them
        while (resolvedRows.isNotEmpty() && resolvedRows.last().cells.all { it.isEmpty() }) {
            resolvedRows.removeAt(resolvedRows.lastIndex)
        }

        return XlsxSheet(
            name = name,
            rows = if (hasAnyNonBlankCell) resolvedRows else emptyList(),
            columnCount = if (hasAnyNonBlankCell) colCount else 0,
            isEmpty = !hasAnyNonBlankCell,
            isTruncated = raw.isTruncated
        )
    }

    private fun resolveCellValue(rawCell: RawCell, sharedStrings: List<String>): String {
        val rawVal = rawCell.rawValue
        return when (rawCell.type) {
            "s" -> {
                val idx = rawVal.trim().toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) {
                    sharedStrings[idx]
                } else {
                    ""
                }
            }
            "inlineStr", "str", "e" -> rawVal
            "b" -> {
                when (rawVal.trim().lowercase(Locale.ROOT)) {
                    "1", "true" -> "TRUE"
                    "0", "false" -> "FALSE"
                    else -> rawVal
                }
            }
            else -> formatNumericOrRawValue(rawVal)
        }
    }

    private fun formatNumericOrRawValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        // Fast path for plain integers
        if (trimmed.all { it in '0'..'9' } || (trimmed.startsWith('-') && trimmed.length > 1 && trimmed.substring(1).all { it in '0'..'9' })) {
            return trimmed
        }
        return try {
            val bd = BigDecimal(trimmed)
            val stripped = bd.stripTrailingZeros()
            // Avoid turning large integers like 1000 into 1E+3 via toPlainString() for reasonable magnitudes
            if (stripped.scale() <= 0 && stripped.precision() <= 15) {
                stripped.toPlainString()
            } else if (stripped.precision() <= 15 && kotlin.math.abs(stripped.scale()) <= 10) {
                // Clean up IEEE-754 artefacts such as 19.990000000000002
                val asDouble = trimmed.toDouble()
                if (asDouble.isFinite()) {
                    val cleanBd = BigDecimal.valueOf(asDouble).stripTrailingZeros()
                    cleanBd.toPlainString()
                } else {
                    trimmed
                }
            } else {
                trimmed
            }
        } catch (_: NumberFormatException) {
            raw
        }
    }

    private fun validateHeaderSignature(header: ByteArray, length: Int) {
        // Legacy OLE2 Compound Document (.xls / .doc / .ppt)
        if (length >= 8 &&
            header[0] == 0xD0.toByte() &&
            header[1] == 0xCF.toByte() &&
            header[2] == 0x11.toByte() &&
            header[3] == 0xE0.toByte() &&
            header[4] == 0xA1.toByte() &&
            header[5] == 0xB1.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0xE1.toByte()
        ) {
            throw XlsxLoadException(
                "Legacy binary Excel (.xls) files are not supported by this viewer. " +
                    "Please open the spreadsheet in Microsoft Excel or a compatible editor and save/export it as .xlsx."
            )
        }

        // PDF header
        if (length >= 5 &&
            header[0] == '%'.code.toByte() &&
            header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[4] == '-'.code.toByte()
        ) {
            throw XlsxLoadException("This file is a PDF document, not an Excel (.xlsx) workbook.")
        }

        // Standard ZIP local file header ("PK\x03\x04")
        if (length < 4 ||
            header[0] != 0x50.toByte() ||
            header[1] != 0x4B.toByte() ||
            header[2] != 0x03.toByte() ||
            header[3] != 0x04.toByte()
        ) {
            throw XlsxLoadException("Invalid or corrupted .xlsx file. The selected file is not a valid Excel (.xlsx) package.")
        }
    }

    private fun parseXmlEntry(zipStream: InputStream, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

        val parser = factory.newSAXParser()
        val xmlReader = parser.xmlReader
        xmlReader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        xmlReader.contentHandler = handler

        val nonClosingBoundedStream = NonClosingBoundedInputStream(zipStream, MAX_XML_ENTRY_BYTES)
        try {
            xmlReader.parse(InputSource(nonClosingBoundedStream))
        } catch (_: StopXlsxParsingEarlyException) {
            // Reached maxRows / maxStrings limit cleanly
        } catch (e: SAXException) {
            val cause = e.exception ?: e.cause
            if (cause is RuntimeException && cause.javaClass.simpleName.contains("Cancellation")) {
                throw cause
            }
            throw XlsxLoadException("Malformed XML content inside .xlsx workbook.", e)
        }
    }

    private fun trySetFeature(factory: SAXParserFactory, feature: String, value: Boolean) {
        try {
            factory.setFeature(feature, value)
        } catch (_: Exception) {
            // Ignore features not supported by the platform's SAX implementation
        }
    }

    private data class WorkbookSheetMeta(
        val name: String,
        val sheetId: String?,
        val relId: String?
    )

    private data class RawWorksheet(
        val rows: List<RawRow>,
        val maxColIndex: Int,
        val isTruncated: Boolean
    )

    private data class RawRow(
        val rowNumber: Int,
        val cellsByCol: Map<Int, RawCell>
    )

    private data class RawCell(
        val type: String?,
        val rawValue: String
    )

    private class StopXlsxParsingEarlyException : SAXException()

    /**
     * SAX handler for `xl/workbook.xml`.
     */
    private class WorkbookXmlHandler(
        private val checkCancelled: () -> Unit
    ) : DefaultHandler() {
        val sheets = ArrayList<WorkbookSheetMeta>()
        private var elementCounter = 0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            elementCounter++
            if (elementCounter % 100 == 0) checkCancelled()

            if (resolveTag(localName, qName) == "sheet") {
                val name = getAttrValue(attributes, "name") ?: "Sheet${sheets.size + 1}"
                val sheetId = getAttrValue(attributes, "sheetId")
                val relId = getAttrValue(attributes, "id")
                sheets.add(WorkbookSheetMeta(name = name, sheetId = sheetId, relId = relId))
            }
        }
    }

    /**
     * SAX handler for `xl/_rels/workbook.xml.rels`.
     */
    private class WorkbookRelsXmlHandler(
        private val checkCancelled: () -> Unit
    ) : DefaultHandler() {
        val relMap = HashMap<String, String>()
        private var elementCounter = 0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            elementCounter++
            if (elementCounter % 100 == 0) checkCancelled()

            if (resolveTag(localName, qName).equals("Relationship", ignoreCase = true)) {
                val id = getAttrValue(attributes, "Id")
                val target = getAttrValue(attributes, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    relMap[id] = target
                }
            }
        }
    }

    /**
     * SAX handler for `xl/sharedStrings.xml`.
     */
    private class SharedStringsXmlHandler(
        private val maxStrings: Int,
        private val checkCancelled: () -> Unit
    ) : DefaultHandler() {
        val strings = ArrayList<String>()
        var isTruncated = false
            private set

        private var inSi = false
        private var inT = false
        private var phoneticDepth = 0
        private val siBuilder = StringBuilder()
        private var elementCounter = 0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            elementCounter++
            if (elementCounter % 250 == 0) checkCancelled()

            val tag = resolveTag(localName, qName)
            if (phoneticDepth > 0) {
                phoneticDepth++
                return
            }
            // Ignore phonetic pronunciation guides (<rPh>) inside shared strings
            if (tag == "rPh") {
                phoneticDepth = 1
                return
            }

            when (tag) {
                "si" -> {
                    inSi = true
                    siBuilder.setLength(0)
                }
                "t" -> {
                    if (inSi) {
                        inT = true
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (phoneticDepth > 0) return
            if (inSi && inT && length > 0) {
                val remaining = MAX_CELL_CHARS - siBuilder.length
                if (remaining > 0) {
                    siBuilder.append(ch, start, length.coerceAtMost(remaining))
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (phoneticDepth > 0) {
                phoneticDepth--
                return
            }
            when (resolveTag(localName, qName)) {
                "t" -> inT = false
                "si" -> {
                    if (inSi) {
                        inSi = false
                        inT = false
                        strings.add(siBuilder.toString())
                        siBuilder.setLength(0)
                        if (strings.size >= maxStrings) {
                            isTruncated = true
                            throw StopXlsxParsingEarlyException()
                        }
                    }
                }
            }
        }
    }

    /**
     * SAX handler for `xl/worksheets/sheet*.xml`.
     */
    private class WorksheetXmlHandler(
        private val maxRows: Int,
        private val maxCols: Int,
        private val checkCancelled: () -> Unit
    ) : DefaultHandler() {
        val rows = ArrayList<RawRow>()
        var maxColIndex = -1
            private set
        var isTruncated = false
            private set

        private var inSheetData = false
        private var inRow = false
        private var currentRowNumber = 0
        private var currentRowCells = LinkedHashMap<Int, RawCell>()
        private var lastColIndexInRow = -1

        private var inCell = false
        private var currentCellColIndex = 0
        private var currentCellType: String? = null
        private var inValueTag = false
        private var inInlineStr = false
        private var inInlineTextTag = false
        private var ignoredDepth = 0
        private val cellValueBuilder = StringBuilder()

        private var elementCounter = 0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            elementCounter++
            if (elementCounter % 250 == 0) checkCancelled()

            val tag = resolveTag(localName, qName)
            if (ignoredDepth > 0) {
                ignoredDepth++
                return
            }
            // Ignore formula expression text (<f>) and phonetic text (<rPh>) so only cached <v> or <is><t> is captured
            if (tag == "f" || tag == "rPh") {
                ignoredDepth = 1
                return
            }

            when (tag) {
                "sheetData" -> {
                    inSheetData = true
                }

                "row" -> {
                    if (inSheetData) {
                        inRow = true
                        val explicitRow = getAttrValue(attributes, "r")?.toIntOrNull()
                        currentRowNumber = if (explicitRow != null && explicitRow >= 1) {
                            explicitRow
                        } else {
                            currentRowNumber + 1
                        }
                        currentRowCells = LinkedHashMap()
                        lastColIndexInRow = -1
                    }
                }

                "c" -> {
                    if (inRow) {
                        inCell = true
                        val cellRef = getAttrValue(attributes, "r")
                        val explicitCol = parseColumnIndexFromCellRef(cellRef)
                        currentCellColIndex = explicitCol ?: (lastColIndexInRow + 1)
                        lastColIndexInRow = currentCellColIndex
                        currentCellType = getAttrValue(attributes, "t")
                        cellValueBuilder.setLength(0)
                    }
                }

                "v" -> {
                    if (inCell) {
                        inValueTag = true
                    }
                }

                "is" -> {
                    if (inCell) {
                        inInlineStr = true
                        if (currentCellType == null) {
                            currentCellType = "inlineStr"
                        }
                    }
                }

                "t" -> {
                    if (inCell && (inInlineStr || currentCellType == "inlineStr" || currentCellType == "str")) {
                        inInlineTextTag = true
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (ignoredDepth > 0) return
            if (inCell && (inValueTag || inInlineTextTag) && length > 0) {
                val remaining = MAX_CELL_CHARS - cellValueBuilder.length
                if (remaining > 0) {
                    cellValueBuilder.append(ch, start, length.coerceAtMost(remaining))
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (ignoredDepth > 0) {
                ignoredDepth--
                return
            }

            when (resolveTag(localName, qName)) {
                "v" -> inValueTag = false
                "t" -> inInlineTextTag = false
                "is" -> inInlineStr = false

                "c" -> {
                    if (inCell) {
                        inCell = false
                        inValueTag = false
                        inInlineStr = false
                        inInlineTextTag = false

                        if (currentCellColIndex >= maxCols) {
                            isTruncated = true
                        } else if (currentCellColIndex >= 0) {
                            val rawText = cellValueBuilder.toString()
                            if (rawText.isNotEmpty() || currentCellType != null) {
                                currentRowCells[currentCellColIndex] = RawCell(
                                    type = currentCellType,
                                    rawValue = rawText
                                )
                                maxColIndex = max(maxColIndex, currentCellColIndex)
                            }
                        }
                        cellValueBuilder.setLength(0)
                    }
                }

                "row" -> {
                    if (inRow) {
                        inRow = false
                        if (currentRowCells.isNotEmpty()) {
                            rows.add(
                                RawRow(
                                    rowNumber = currentRowNumber,
                                    cellsByCol = currentRowCells
                                )
                            )
                            if (rows.size >= maxRows) {
                                isTruncated = true
                                throw StopXlsxParsingEarlyException()
                            }
                        }
                    }
                }

                "sheetData" -> {
                    inSheetData = false
                }
            }
        }
    }

    private fun resolveTag(localName: String?, qName: String?): String {
        if (!localName.isNullOrEmpty()) return localName
        if (!qName.isNullOrEmpty()) return qName.substringAfter(':')
        return ""
    }

    private fun getAttrValue(attributes: Attributes, targetLocalName: String): String? {
        for (i in 0 until attributes.length) {
            val local = attributes.getLocalName(i)
            val qName = attributes.getQName(i)
            val name = if (!local.isNullOrEmpty()) local else qName.substringAfter(':')
            if (name == targetLocalName) {
                return attributes.getValue(i)
            }
        }
        return null
    }

    private fun readUpTo(stream: InputStream, buffer: ByteArray, maxBytes: Int): Int {
        var total = 0
        while (total < maxBytes) {
            val read = stream.read(buffer, total, maxBytes - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private class NonClosingBoundedInputStream(
        delegate: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(delegate) {
        private var totalRead = 0L

        override fun read(): Int {
            if (totalRead >= maxBytes) {
                throw XlsxLoadException("Worksheet XML exceeds maximum safe size limit.")
            }
            val b = super.read()
            if (b != -1) totalRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (totalRead >= maxBytes) {
                throw XlsxLoadException("Worksheet XML exceeds maximum safe size limit.")
            }
            val maxRead = (maxBytes - totalRead).coerceAtMost(len.toLong()).toInt()
            val count = super.read(b, off, maxRead)
            if (count > 0) totalRead += count
            return count
        }

        override fun close() {
            // Intentionally do not close the underlying ZipInputStream
        }
    }
}
