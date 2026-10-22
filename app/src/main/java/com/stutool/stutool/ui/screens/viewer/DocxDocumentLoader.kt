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
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Parsed representation of a Word (.docx) document for lightweight reading.
 */
data class DocxDocument(
    val blocks: List<DocxBlock>,
    val isEmpty: Boolean,
    val isTruncated: Boolean = false,
    val totalParagraphCount: Int = 0,
    val totalTableCount: Int = 0
)

sealed interface DocxBlock {
    data class Paragraph(
        val runs: List<DocxRun>,
        val headingLevel: Int? = null,
        val isTitle: Boolean = false,
        val isSubtitle: Boolean = false,
        val listPrefix: String? = null,
        val indentLevel: Int = 0
    ) : DocxBlock {
        val plainText: String
            get() = runs.joinToString(separator = "") { it.text }
    }

    data class Table(
        val rows: List<DocxTableRow>
    ) : DocxBlock
}

data class DocxRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrike: Boolean = false
)

data class DocxTableRow(
    val cells: List<DocxTableCell>,
    val isHeader: Boolean = false
)

data class DocxTableCell(
    val paragraphs: List<DocxBlock.Paragraph>,
    val gridSpan: Int = 1
) {
    val plainText: String
        get() = paragraphs.joinToString(separator = "\n") { it.plainText }
}

class DocxLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Dependency-free OOXML (.docx) parser using [ZipInputStream] and SAX XML parsing.
 * Streams entries directly without extracting temporary files to disk or loading
 * duplicate archive copies into memory.
 */
object DocxDocumentLoader {

    /** Maximum number of top-level blocks (paragraphs + tables) retained in memory. */
    const val MAX_DOCUMENT_BLOCKS = 4_000

    /** Maximum characters per individual paragraph to prevent layout stalls on pathological inputs. */
    const val MAX_PARAGRAPH_CHARS = 20_000

    /** Maximum uncompressed bytes read from a single XML entry inside the archive (32 MB). */
    private const val MAX_XML_ENTRY_BYTES = 32L * 1024L * 1024L

    private const val PREVIEW_HEADER_BYTES = 16

    fun loadDocument(
        inputStream: InputStream,
        maxBlocks: Int = MAX_DOCUMENT_BLOCKS,
        checkCancelled: () -> Unit = {}
    ): DocxDocument {
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
            throw DocxLoadException("This file is empty (0 bytes) and is not a valid .docx document.")
        }

        validateHeaderSignature(header, headerLen)
        buffered.reset()

        val rawBlocks = ArrayList<RawBlock>()
        val styleMap = HashMap<String, StyleDef>()
        val abstractNumMap = HashMap<String, HashMap<Int, String>>() // abstractNumId -> (ilvl -> numFmt)
        val numIdToAbstractId = HashMap<String, String>() // numId -> abstractNumId

        var foundDocumentXml = false
        var foundWorkbookXml = false
        var foundPresentationXml = false
        var foundEncryptedPackage = false
        var isTruncated = false
        var totalEntriesSeen = 0

        try {
            val zipStream = ZipInputStream(buffered)
            while (true) {
                checkCancelled()
                val entry = try {
                    zipStream.nextEntry
                } catch (e: ZipException) {
                    throw DocxLoadException("Corrupted or invalid .docx archive: ${e.localizedMessage ?: "Invalid ZIP structure"}", e)
                } ?: break

                totalEntriesSeen++
                val normalizedName = entry.name.trimStart('/').replace('\\', '/')

                when {
                    normalizedName.equals("word/document.xml", ignoreCase = true) -> {
                        foundDocumentXml = true
                        val handler = DocumentXmlHandler(
                            maxBlocks = maxBlocks,
                            checkCancelled = checkCancelled
                        )
                        parseXmlEntry(zipStream, handler)
                        rawBlocks.addAll(handler.blocks)
                        if (handler.isTruncated) {
                            isTruncated = true
                        }
                    }

                    normalizedName.equals("word/styles.xml", ignoreCase = true) -> {
                        try {
                            val handler = StylesXmlHandler(checkCancelled)
                            parseXmlEntry(zipStream, handler)
                            styleMap.putAll(handler.styles)
                        } catch (_: Exception) {
                            // Optional metadata file: ignore malformed styles.xml and continue
                        }
                    }

                    normalizedName.equals("word/numbering.xml", ignoreCase = true) -> {
                        try {
                            val handler = NumberingXmlHandler(checkCancelled)
                            parseXmlEntry(zipStream, handler)
                            abstractNumMap.putAll(handler.abstractNums)
                            numIdToAbstractId.putAll(handler.numToAbstract)
                        } catch (_: Exception) {
                            // Optional metadata file: ignore malformed numbering.xml and continue
                        }
                    }

                    normalizedName.equals("xl/workbook.xml", ignoreCase = true) -> {
                        foundWorkbookXml = true
                    }

                    normalizedName.equals("ppt/presentation.xml", ignoreCase = true) -> {
                        foundPresentationXml = true
                    }

                    normalizedName.equals("EncryptedPackage", ignoreCase = true) ||
                        normalizedName.equals("EncryptionInfo", ignoreCase = true) -> {
                        foundEncryptedPackage = true
                    }
                }

                zipStream.closeEntry()
            }
        } catch (e: DocxLoadException) {
            throw e
        } catch (e: ZipException) {
            throw DocxLoadException("Invalid or corrupted .docx archive.", e)
        } catch (e: IOException) {
            throw DocxLoadException("Failed to read .docx file: ${e.localizedMessage ?: "I/O error"}", e)
        }

        checkCancelled()

        if (!foundDocumentXml) {
            if (foundEncryptedPackage) {
                throw DocxLoadException("Password-protected or encrypted Word (.docx) files are not supported.")
            }
            if (foundWorkbookXml) {
                throw DocxLoadException("This file is an Excel spreadsheet (.xlsx), not a Word (.docx) document.")
            }
            if (foundPresentationXml) {
                throw DocxLoadException("This file is a PowerPoint presentation (.pptx), not a Word (.docx) document.")
            }
            if (totalEntriesSeen == 0) {
                throw DocxLoadException("Invalid or corrupted .docx file: archive contains no entries.")
            }
            throw DocxLoadException("Invalid .docx file: missing word/document.xml content.")
        }

        // Resolve styles, headings, and list numbering across all blocks
        val listCounters = HashMap<String, Int>()
        val resolvedBlocks = ArrayList<DocxBlock>(rawBlocks.size)
        var paragraphCount = 0
        var tableCount = 0

        for (rawBlock in rawBlocks) {
            checkCancelled()
            when (rawBlock) {
                is RawBlock.ParagraphBlock -> {
                    val paragraph = resolveParagraph(
                        raw = rawBlock.paragraph,
                        styleMap = styleMap,
                        abstractNumMap = abstractNumMap,
                        numIdToAbstractId = numIdToAbstractId,
                        listCounters = listCounters
                    )
                    if (paragraph != null) {
                        resolvedBlocks.add(paragraph)
                        paragraphCount++
                    }
                }

                is RawBlock.TableBlock -> {
                    val rows = ArrayList<DocxTableRow>(rawBlock.rows.size)
                    for (rawRow in rawBlock.rows) {
                        val cells = ArrayList<DocxTableCell>(rawRow.cells.size)
                        for (rawCell in rawRow.cells) {
                            val cellParagraphs = ArrayList<DocxBlock.Paragraph>()
                            for (rawCellPara in rawCell.paragraphs) {
                                val resolvedCellPara = resolveParagraph(
                                    raw = rawCellPara,
                                    styleMap = styleMap,
                                    abstractNumMap = abstractNumMap,
                                    numIdToAbstractId = numIdToAbstractId,
                                    listCounters = listCounters,
                                    keepEmptyInTableCell = false
                                )
                                if (resolvedCellPara != null) {
                                    cellParagraphs.add(resolvedCellPara)
                                }
                            }
                            cells.add(
                                DocxTableCell(
                                    paragraphs = cellParagraphs,
                                    gridSpan = rawCell.gridSpan.coerceAtLeast(1)
                                )
                            )
                        }
                        if (cells.isNotEmpty()) {
                            rows.add(DocxTableRow(cells = cells, isHeader = rawRow.isHeader))
                        }
                    }
                    if (rows.isNotEmpty()) {
                        resolvedBlocks.add(DocxBlock.Table(rows = rows))
                        tableCount++
                    }
                }
            }
        }

        return DocxDocument(
            blocks = resolvedBlocks,
            isEmpty = resolvedBlocks.isEmpty(),
            isTruncated = isTruncated,
            totalParagraphCount = paragraphCount,
            totalTableCount = tableCount
        )
    }

    private fun validateHeaderSignature(header: ByteArray, length: Int) {
        // Legacy OLE2 Compound Document (.doc / .xls / .ppt) or encrypted Office wrapper
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
            throw DocxLoadException(
                "Legacy binary Word (.doc) files are not supported by this viewer. " +
                    "Please open the file in Microsoft Word or a compatible editor and save/export it as .docx."
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
            throw DocxLoadException("This file is a PDF document, not a Word (.docx) file.")
        }

        // RTF header ("{\rtf")
        if (length >= 5 &&
            header[0] == '{'.code.toByte() &&
            header[1] == '\\'.code.toByte() &&
            header[2] == 'r'.code.toByte() &&
            header[3] == 't'.code.toByte() &&
            header[4] == 'f'.code.toByte()
        ) {
            throw DocxLoadException("Rich Text Format (.rtf) files are not supported. Please save the document as .docx.")
        }

        // Standard ZIP local file header ("PK\x03\x04")
        if (length < 4 ||
            header[0] != 0x50.toByte() ||
            header[1] != 0x4B.toByte() ||
            header[2] != 0x03.toByte() ||
            header[3] != 0x04.toByte()
        ) {
            throw DocxLoadException("Invalid or corrupted .docx file. The selected file is not a valid Word (.docx) package.")
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
        } catch (e: StopParsingEarlyException) {
            // Reached maxBlocks limit cleanly
        } catch (e: SAXException) {
            val cause = e.exception ?: e.cause
            if (cause is RuntimeException && cause.javaClass.simpleName.contains("Cancellation")) {
                throw cause
            }
            throw DocxLoadException("Malformed XML content inside .docx document.", e)
        }
    }

    private fun trySetFeature(factory: SAXParserFactory, feature: String, value: Boolean) {
        try {
            factory.setFeature(feature, value)
        } catch (_: Exception) {
            // Ignore features not supported by the platform's SAX implementation
        }
    }

    private fun resolveParagraph(
        raw: RawParagraph,
        styleMap: Map<String, StyleDef>,
        abstractNumMap: Map<String, Map<Int, String>>,
        numIdToAbstractId: Map<String, String>,
        listCounters: MutableMap<String, Int>,
        keepEmptyInTableCell: Boolean = false
    ): DocxBlock.Paragraph? {
        val styleDef = raw.styleId?.let { styleMap[it] }
        val resolvedHeadingInfo = detectHeading(raw.styleId, styleDef, raw.directOutlineLevel)

        // Apply style-level bold/italic defaults if run didn't explicitly override
        val coalescedRuns = coalesceRuns(
            raw.runs.map { run ->
                run.copy(
                    isBold = run.isBold || (styleDef?.isBold == true),
                    isItalic = run.isItalic || (styleDef?.isItalic == true)
                )
            }
        )

        val hasVisibleText = coalescedRuns.any { it.text.isNotEmpty() }
        if (!hasVisibleText && !keepEmptyInTableCell) {
            return null
        }

        // Resolve list prefix if numId or list style is present
        var listPrefix: String? = null
        var indentLevel = raw.ilvl?.coerceIn(0, 8) ?: 0

        val numId = raw.numId
        if (!numId.isNullOrBlank() && numId != "0") {
            val ilvl = indentLevel
            // Reset deeper list levels when a parent level advances
            for (deeper in (ilvl + 1)..8) {
                listCounters.remove("$numId:$deeper")
            }
            val abstractId = numIdToAbstractId[numId]
            val numFmt = abstractId?.let { abstractNumMap[it]?.get(ilvl) }
                ?: inferListFormatFromStyle(raw.styleId, styleDef)

            listPrefix = if (isOrderedFormat(numFmt)) {
                val key = "$numId:$ilvl"
                val nextVal = (listCounters[key] ?: 0) + 1
                listCounters[key] = nextVal
                formatOrderedMarker(nextVal, numFmt)
            } else {
                bulletForLevel(ilvl)
            }
        } else if (isBulletStyle(raw.styleId, styleDef)) {
            listPrefix = bulletForLevel(indentLevel)
        }

        return DocxBlock.Paragraph(
            runs = coalescedRuns,
            headingLevel = resolvedHeadingInfo.headingLevel,
            isTitle = resolvedHeadingInfo.isTitle,
            isSubtitle = resolvedHeadingInfo.isSubtitle,
            listPrefix = listPrefix,
            indentLevel = indentLevel
        )
    }

    private data class HeadingInfo(
        val headingLevel: Int? = null,
        val isTitle: Boolean = false,
        val isSubtitle: Boolean = false
    )

    private fun detectHeading(
        styleId: String?,
        styleDef: StyleDef?,
        directOutlineLvl: Int?
    ): HeadingInfo {
        if (directOutlineLvl != null && directOutlineLvl in 0..5) {
            return HeadingInfo(headingLevel = directOutlineLvl + 1)
        }
        if (styleDef?.outlineLevel != null && styleDef.outlineLevel in 0..5) {
            return HeadingInfo(headingLevel = styleDef.outlineLevel + 1)
        }

        val candidates = listOfNotNull(styleId, styleDef?.name)
        for (candidate in candidates) {
            val normalized = candidate.trim().lowercase()
            if (normalized == "title") {
                return HeadingInfo(headingLevel = 1, isTitle = true)
            }
            if (normalized == "subtitle") {
                return HeadingInfo(headingLevel = 2, isSubtitle = true)
            }
            val headingMatch = Regex("^heading\\s*([1-6])$|^h([1-6])$").find(normalized)
            if (headingMatch != null) {
                val level = (headingMatch.groupValues[1].ifEmpty { headingMatch.groupValues[2] }).toIntOrNull()
                if (level != null) {
                    return HeadingInfo(headingLevel = level)
                }
            }
        }
        return HeadingInfo()
    }

    private fun inferListFormatFromStyle(styleId: String?, styleDef: StyleDef?): String {
        val combined = "${styleId.orEmpty()} ${styleDef?.name.orEmpty()}".lowercase()
        return if (combined.contains("number")) "decimal" else "bullet"
    }

    private fun isBulletStyle(styleId: String?, styleDef: StyleDef?): Boolean {
        val combined = "${styleId.orEmpty()} ${styleDef?.name.orEmpty()}".lowercase()
        return combined.contains("listbullet") || combined.contains("list bullet")
    }

    private fun isOrderedFormat(numFmt: String?): Boolean {
        return when (numFmt?.lowercase()) {
            "decimal", "lowerletter", "upperletter", "lowerroman", "upperroman", "decimalzero" -> true
            else -> false
        }
    }

    private fun formatOrderedMarker(index: Int, numFmt: String?): String {
        return when (numFmt?.lowercase()) {
            "lowerletter" -> {
                val ch = ('a'.code + ((index - 1).coerceAtLeast(0) % 26)).toChar()
                "$ch."
            }
            "upperletter" -> {
                val ch = ('A'.code + ((index - 1).coerceAtLeast(0) % 26)).toChar()
                "$ch."
            }
            "lowerroman" -> "${toRoman(index).lowercase()}."
            "upperroman" -> "${toRoman(index)}."
            else -> "$index."
        }
    }

    private fun toRoman(number: Int): String {
        if (number <= 0 || number > 3999) return number.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val numerals = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        val sb = StringBuilder()
        var rem = number
        for (i in values.indices) {
            while (rem >= values[i]) {
                sb.append(numerals[i])
                rem -= values[i]
            }
        }
        return sb.toString()
    }

    private fun bulletForLevel(ilvl: Int): String {
        return when (ilvl % 3) {
            0 -> "•"
            1 -> "◦"
            else -> "▪"
        }
    }

    private fun coalesceRuns(runs: List<DocxRun>): List<DocxRun> {
        if (runs.isEmpty()) return emptyList()
        val result = ArrayList<DocxRun>(runs.size)
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = result.lastOrNull()
            if (last != null &&
                last.isBold == run.isBold &&
                last.isItalic == run.isItalic &&
                last.isUnderline == run.isUnderline &&
                last.isStrike == run.isStrike
            ) {
                result[result.lastIndex] = last.copy(text = last.text + run.text)
            } else {
                result.add(run)
            }
        }
        return result
    }

    private sealed interface RawBlock {
        data class ParagraphBlock(val paragraph: RawParagraph) : RawBlock
        data class TableBlock(val rows: List<RawTableRow>) : RawBlock
    }

    private data class RawParagraph(
        val runs: List<DocxRun>,
        val styleId: String?,
        val directOutlineLevel: Int?,
        val numId: String?,
        val ilvl: Int?
    )

    private data class RawTableRow(
        val cells: List<RawTableCell>,
        val isHeader: Boolean
    )

    private data class RawTableCell(
        val paragraphs: List<RawParagraph>,
        val gridSpan: Int
    )

    private data class StyleDef(
        val styleId: String,
        val name: String? = null,
        val outlineLevel: Int? = null,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    )

    private class StopParsingEarlyException : SAXException()

    /**
     * SAX handler for `word/document.xml`.
     */
    private class DocumentXmlHandler(
        private val maxBlocks: Int,
        private val checkCancelled: () -> Unit
    ) : DefaultHandler() {

        val blocks = ArrayList<RawBlock>()
        var isTruncated = false
            private set

        private var elementCounter = 0

        // Table state (supports top-level tables; nested tables flatten into cell paragraphs)
        private var tableDepth = 0
        private var currentTableRows: ArrayList<RawTableRow>? = null
        private var currentRowCells: ArrayList<RawTableCell>? = null
        private var currentRowIsHeader = false
        private var currentCellParagraphs: ArrayList<RawParagraph>? = null
        private var currentCellGridSpan = 1

        // Paragraph state
        private var inParagraph = false
        private var inParagraphPr = false
        private var inNumPr = false
        private var currentParagraphRuns = ArrayList<DocxRun>()
        private var currentParagraphCharCount = 0
        private var currentStyleId: String? = null
        private var currentOutlineLvl: Int? = null
        private var currentNumId: String? = null
        private var currentIlvl: Int? = null

        // Run state
        private var inRun = false
        private var inRunPr = false
        private var runBold = false
        private var runItalic = false
        private var runUnderline = false
        private var runStrike = false
        private var inTextElement = false
        private val textBuffer = StringBuilder()

        // Ignore text inside deleted tracked changes (<w:delText>, <w:instrText>)
        private var ignoredContentDepth = 0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            elementCounter++
            if (elementCounter % 250 == 0) {
                checkCancelled()
            }

            val tag = resolveTag(localName, qName)

            if (ignoredContentDepth > 0) {
                ignoredContentDepth++
                return
            }
            if (tag == "del" || tag == "instrText" || tag == "delText" || tag == "fallback" || tag == "Fallback") {
                ignoredContentDepth = 1
                return
            }

            when (tag) {
                "tbl" -> {
                    tableDepth++
                    if (tableDepth == 1) {
                        currentTableRows = ArrayList()
                    }
                }

                "tr" -> {
                    if (tableDepth == 1) {
                        currentRowCells = ArrayList()
                        currentRowIsHeader = false
                    }
                }

                "tblHeader" -> {
                    if (tableDepth == 1 && isValEnabled(attributes)) {
                        currentRowIsHeader = true
                    }
                }

                "tc" -> {
                    if (tableDepth == 1) {
                        currentCellParagraphs = ArrayList()
                        currentCellGridSpan = 1
                    }
                }

                "gridSpan" -> {
                    if (tableDepth == 1) {
                        val span = getAttrValue(attributes, "val")?.toIntOrNull()
                        if (span != null && span > 1) {
                            currentCellGridSpan = span
                        }
                    }
                }

                "p" -> {
                    inParagraph = true
                    currentParagraphRuns = ArrayList()
                    currentParagraphCharCount = 0
                    currentStyleId = null
                    currentOutlineLvl = null
                    currentNumId = null
                    currentIlvl = null
                }

                "pPr" -> {
                    if (inParagraph && !inRun) {
                        inParagraphPr = true
                    }
                }

                "pStyle" -> {
                    if (inParagraphPr) {
                        currentStyleId = getAttrValue(attributes, "val")
                    }
                }

                "outlineLvl" -> {
                    if (inParagraphPr) {
                        currentOutlineLvl = getAttrValue(attributes, "val")?.toIntOrNull()
                    }
                }

                "numPr" -> {
                    if (inParagraphPr) {
                        inNumPr = true
                    }
                }

                "numId" -> {
                    if (inNumPr) {
                        currentNumId = getAttrValue(attributes, "val")
                    }
                }

                "ilvl" -> {
                    if (inNumPr) {
                        currentIlvl = getAttrValue(attributes, "val")?.toIntOrNull()
                    }
                }

                "r" -> {
                    if (inParagraph) {
                        inRun = true
                        runBold = false
                        runItalic = false
                        runUnderline = false
                        runStrike = false
                    }
                }

                "rPr" -> {
                    if (inRun) {
                        inRunPr = true
                    }
                }

                "b", "bCs" -> {
                    if (inRunPr) {
                        runBold = isValEnabled(attributes)
                    }
                }

                "i", "iCs" -> {
                    if (inRunPr) {
                        runItalic = isValEnabled(attributes)
                    }
                }

                "u" -> {
                    if (inRunPr) {
                        val uVal = getAttrValue(attributes, "val")
                        runUnderline = uVal == null || !uVal.equals("none", ignoreCase = true)
                    }
                }

                "strike", "dstrike" -> {
                    if (inRunPr) {
                        runStrike = isValEnabled(attributes)
                    }
                }

                "t" -> {
                    if (inParagraph) {
                        inTextElement = true
                        textBuffer.setLength(0)
                    }
                }

                "br", "cr" -> {
                    if (inParagraph) {
                        appendRunSegment("\n")
                    }
                }

                "tab" -> {
                    if (inParagraph) {
                        appendRunSegment("\t")
                    }
                }

                "noBreakHyphen" -> {
                    if (inParagraph) {
                        appendRunSegment("-")
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (ignoredContentDepth > 0) return
            if (inTextElement && length > 0) {
                val remainingAllowed = MAX_PARAGRAPH_CHARS - (currentParagraphCharCount + textBuffer.length)
                if (remainingAllowed > 0) {
                    val toCopy = length.coerceAtMost(remainingAllowed)
                    textBuffer.append(ch, start, toCopy)
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (ignoredContentDepth > 0) {
                ignoredContentDepth--
                return
            }

            val tag = resolveTag(localName, qName)
            when (tag) {
                "t" -> {
                    if (inTextElement) {
                        inTextElement = false
                        if (textBuffer.isNotEmpty()) {
                            appendRunSegment(textBuffer.toString())
                            textBuffer.setLength(0)
                        }
                    }
                }

                "rPr" -> {
                    inRunPr = false
                }

                "r" -> {
                    inRun = false
                    inRunPr = false
                }

                "numPr" -> {
                    inNumPr = false
                }

                "pPr" -> {
                    inParagraphPr = false
                    inNumPr = false
                }

                "p" -> {
                    if (inParagraph) {
                        inParagraph = false
                        inParagraphPr = false
                        inNumPr = false
                        val rawPara = RawParagraph(
                            runs = currentParagraphRuns,
                            styleId = currentStyleId,
                            directOutlineLevel = currentOutlineLvl,
                            numId = currentNumId,
                            ilvl = currentIlvl
                        )
                        if (tableDepth >= 1) {
                            currentCellParagraphs?.add(rawPara)
                        } else {
                            val hasContent = rawPara.runs.any { it.text.isNotEmpty() }
                            if (hasContent) {
                                blocks.add(RawBlock.ParagraphBlock(rawPara))
                                if (blocks.size >= maxBlocks) {
                                    isTruncated = true
                                    throw StopParsingEarlyException()
                                }
                            }
                        }
                    }
                }

                "tc" -> {
                    if (tableDepth == 1) {
                        val paras = currentCellParagraphs ?: emptyList()
                        currentRowCells?.add(
                            RawTableCell(
                                paragraphs = paras,
                                gridSpan = currentCellGridSpan
                            )
                        )
                        currentCellParagraphs = null
                    }
                }

                "tr" -> {
                    if (tableDepth == 1) {
                        val cells = currentRowCells ?: emptyList()
                        if (cells.isNotEmpty()) {
                            currentTableRows?.add(
                                RawTableRow(
                                    cells = cells,
                                    isHeader = currentRowIsHeader
                                )
                            )
                        }
                        currentRowCells = null
                    }
                }

                "tbl" -> {
                    if (tableDepth == 1) {
                        val rows = currentTableRows ?: emptyList()
                        if (rows.isNotEmpty()) {
                            blocks.add(RawBlock.TableBlock(rows))
                            if (blocks.size >= maxBlocks) {
                                isTruncated = true
                                throw StopParsingEarlyException()
                            }
                        }
                        currentTableRows = null
                    }
                    if (tableDepth > 0) {
                        tableDepth--
                    }
                }
            }
        }

        private fun appendRunSegment(segment: String) {
            if (segment.isEmpty()) return
            val remaining = MAX_PARAGRAPH_CHARS - currentParagraphCharCount
            if (remaining <= 0) return
            val clipped = if (segment.length > remaining) segment.substring(0, remaining) else segment
            currentParagraphCharCount += clipped.length
            currentParagraphRuns.add(
                DocxRun(
                    text = clipped,
                    isBold = runBold,
                    isItalic = runItalic,
                    isUnderline = runUnderline,
                    isStrike = runStrike
                )
            )
        }
    }

    /**
     * Lightweight SAX handler for `word/styles.xml`.
     */
    private class StylesXmlHandler(
        private val checkCancelled: () -> Unit
    ) : DefaultHandler() {
        val styles = HashMap<String, StyleDef>()

        private var currentStyleId: String? = null
        private var currentStyleName: String? = null
        private var currentOutlineLvl: Int? = null
        private var currentBold = false
        private var currentItalic = false
        private var inStylePPr = false
        private var inStyleRPr = false
        private var elementCounter = 0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            elementCounter++
            if (elementCounter % 250 == 0) checkCancelled()

            when (resolveTag(localName, qName)) {
                "style" -> {
                    currentStyleId = getAttrValue(attributes, "styleId")
                    currentStyleName = null
                    currentOutlineLvl = null
                    currentBold = false
                    currentItalic = false
                    inStylePPr = false
                    inStyleRPr = false
                }
                "name" -> {
                    if (currentStyleId != null) {
                        currentStyleName = getAttrValue(attributes, "val")
                    }
                }
                "pPr" -> if (currentStyleId != null) inStylePPr = true
                "rPr" -> if (currentStyleId != null) inStyleRPr = true
                "outlineLvl" -> {
                    if (inStylePPr) {
                        currentOutlineLvl = getAttrValue(attributes, "val")?.toIntOrNull()
                    }
                }
                "b" -> if (inStyleRPr) currentBold = isValEnabled(attributes)
                "i" -> if (inStyleRPr) currentItalic = isValEnabled(attributes)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (resolveTag(localName, qName)) {
                "pPr" -> inStylePPr = false
                "rPr" -> inStyleRPr = false
                "style" -> {
                    val id = currentStyleId
                    if (!id.isNullOrBlank()) {
                        styles[id] = StyleDef(
                            styleId = id,
                            name = currentStyleName,
                            outlineLevel = currentOutlineLvl,
                            isBold = currentBold,
                            isItalic = currentItalic
                        )
                    }
                    currentStyleId = null
                }
            }
        }
    }

    /**
     * Lightweight SAX handler for `word/numbering.xml`.
     */
    private class NumberingXmlHandler(
        private val checkCancelled: () -> Unit
    ) : DefaultHandler() {
        val abstractNums = HashMap<String, HashMap<Int, String>>()
        val numToAbstract = HashMap<String, String>()

        private var currentAbstractNumId: String? = null
        private var currentIlvl: Int? = null
        private var currentNumId: String? = null
        private var elementCounter = 0

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            elementCounter++
            if (elementCounter % 250 == 0) checkCancelled()

            when (resolveTag(localName, qName)) {
                "abstractNum" -> {
                    val id = getAttrValue(attributes, "abstractNumId")
                    currentAbstractNumId = id
                    if (!id.isNullOrBlank()) {
                        abstractNums.putIfAbsent(id, HashMap())
                    }
                }
                "lvl" -> {
                    if (currentAbstractNumId != null) {
                        currentIlvl = getAttrValue(attributes, "ilvl")?.toIntOrNull()
                    }
                }
                "numFmt" -> {
                    val absId = currentAbstractNumId
                    val lvl = currentIlvl
                    val fmt = getAttrValue(attributes, "val")
                    if (absId != null && lvl != null && !fmt.isNullOrBlank()) {
                        abstractNums[absId]?.put(lvl, fmt)
                    }
                }
                "num" -> {
                    currentNumId = getAttrValue(attributes, "numId")
                }
                "abstractNumId" -> {
                    val nId = currentNumId
                    val absId = getAttrValue(attributes, "val")
                    if (!nId.isNullOrBlank() && !absId.isNullOrBlank()) {
                        numToAbstract[nId] = absId
                    }
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (resolveTag(localName, qName)) {
                "lvl" -> currentIlvl = null
                "abstractNum" -> currentAbstractNumId = null
                "num" -> currentNumId = null
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

    private fun isValEnabled(attributes: Attributes): Boolean {
        val raw = getAttrValue(attributes, "val")?.trim()?.lowercase() ?: return true
        return raw != "0" && raw != "false" && raw != "off" && raw != "none"
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

    /**
     * Wraps a [ZipInputStream] entry so that [SAXParser] does not close the outer
     * [ZipInputStream] when finishing an entry, and enforces a maximum byte bound.
     */
    private class NonClosingBoundedInputStream(
        delegate: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(delegate) {
        private var totalRead = 0L

        override fun read(): Int {
            if (totalRead >= maxBytes) {
                throw DocxLoadException("Document XML exceeds maximum safe size limit.")
            }
            val b = super.read()
            if (b != -1) totalRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (totalRead >= maxBytes) {
                throw DocxLoadException("Document XML exceeds maximum safe size limit.")
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
