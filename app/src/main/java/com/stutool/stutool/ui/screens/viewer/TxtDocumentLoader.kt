package com.stutool.stutool.ui.screens.viewer

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.min

/**
 * Result of loading a window/section of a plain-text (.txt) document.
 */
data class TxtDocumentWindow(
    val lines: List<TxtLine>,
    val detectedCharset: String,
    val isEmpty: Boolean,
    val hasMoreAfter: Boolean,
    val windowIndex: Int,
    val totalCharsInWindow: Int,
    val hadMalformedBytes: Boolean
)

data class TxtLine(
    val lineNumber: Int,
    val content: String,
    val isContinuation: Boolean = false
)

class TxtLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Lightweight, dependency-free plain text loader with BOM/encoding detection,
 * binary/unsupported format rejection, line-length clamping, and bounded window reading.
 */
object TxtDocumentLoader {

    /** Maximum characters loaded into memory for a single viewing window (~250 KB of text). */
    const val MAX_WINDOW_CHARS = 250_000

    /** Maximum lines loaded in a single viewing window to keep Compose LazyColumn fast. */
    const val MAX_WINDOW_LINES = 4_000

    /** Maximum characters in a single rendered line before soft-splitting for layout safety. */
    const val MAX_LINE_CHARS = 2_000

    private const val PREVIEW_BUFFER_SIZE = 4_096

    /**
     * Reads a bounded window of text from [inputStream].
     *
     * @param inputStream Raw stream from ContentResolver or File.
     * @param windowIndex 0-based window index for large files.
     * @param maxWindowChars Maximum characters per window (defaults to [MAX_WINDOW_CHARS]).
     * @param maxWindowLines Maximum lines per window (defaults to [MAX_WINDOW_LINES]).
     * @param maxLineChars Maximum characters per single line item (defaults to [MAX_LINE_CHARS]).
     * @param checkCancelled Cooperative cancellation callback invoked during streaming.
     */
    fun loadWindow(
        inputStream: InputStream,
        windowIndex: Int = 0,
        maxWindowChars: Int = MAX_WINDOW_CHARS,
        maxWindowLines: Int = MAX_WINDOW_LINES,
        maxLineChars: Int = MAX_LINE_CHARS,
        checkCancelled: () -> Unit = {}
    ): TxtDocumentWindow {
        require(windowIndex >= 0) { "windowIndex must be non-negative" }

        val buffered = if (inputStream is BufferedInputStream) {
            inputStream
        } else {
            BufferedInputStream(inputStream, 16 * 1024)
        }

        checkCancelled()

        buffered.mark(PREVIEW_BUFFER_SIZE + 16)
        val preview = ByteArray(PREVIEW_BUFFER_SIZE)
        val previewLen = readUpTo(buffered, preview, PREVIEW_BUFFER_SIZE)

        if (previewLen <= 0) {
            return TxtDocumentWindow(
                lines = emptyList(),
                detectedCharset = "UTF-8",
                isEmpty = true,
                hasMoreAfter = false,
                windowIndex = 0,
                totalCharsInWindow = 0,
                hadMalformedBytes = false
            )
        }

        // 1. Detect known non-text binary magic headers first
        checkKnownBinarySignatures(preview, previewLen)

        // 2. Detect encoding and BOM skip length
        val encodingInfo = detectEncoding(preview, previewLen)

        // 3. Reset stream and skip BOM bytes if present
        buffered.reset()
        if (encodingInfo.bomBytes > 0) {
            skipFully(buffered, encodingInfo.bomBytes.toLong())
        }

        // 4. Create CharsetDecoder withREPLACE action and track if replacement occurs
        val decoder = encodingInfo.charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)

        val reader = InputStreamReader(buffered, decoder)

        // 5. Stream through windows using a single character buffer so no buffered chars are lost
        val lines = ArrayList<TxtLine>(min(maxWindowLines, 256))
        var currentScanningWindow = 0
        var logicalLineNumber = 1
        var charsInCurrentWindow = 0
        var linesInCurrentWindow = 0
        var currentSegmentLen = 0
        var hasMoreAfter = false
        var hadMalformedBytes = false
        val lineBuilder = StringBuilder(128)
        var currentLineIsContinuation = false

        fun completeLineSegment(forceContinuationNext: Boolean) {
            if (currentScanningWindow == windowIndex) {
                val content = lineBuilder.toString()
                if (content.indexOf('\uFFFD') >= 0) {
                    hadMalformedBytes = true
                }
                lines.add(
                    TxtLine(
                        lineNumber = logicalLineNumber,
                        content = content,
                        isContinuation = currentLineIsContinuation
                    )
                )
                charsInCurrentWindow += content.length
                linesInCurrentWindow++
                lineBuilder.setLength(0)
            } else {
                charsInCurrentWindow += currentSegmentLen
                linesInCurrentWindow++
                currentSegmentLen = 0
            }

            if (forceContinuationNext) {
                currentLineIsContinuation = true
            } else {
                currentLineIsContinuation = false
                logicalLineNumber++
            }
        }

        var prevWasCr = false
        val charBuf = CharArray(4096)

        outer@ while (true) {
            checkCancelled()
            val readCount = reader.read(charBuf)
            if (readCount == -1) {
                if (currentScanningWindow == windowIndex &&
                    (lineBuilder.isNotEmpty() || currentLineIsContinuation)
                ) {
                    completeLineSegment(forceContinuationNext = false)
                }
                break
            }

            var i = 0
            while (i < readCount) {
                val ch = charBuf[i]
                i++

                if (prevWasCr) {
                    prevWasCr = false
                    if (ch == '\n') {
                        continue
                    }
                }

                if (ch == '\r') {
                    prevWasCr = true
                    completeLineSegment(forceContinuationNext = false)
                } else if (ch == '\n') {
                    completeLineSegment(forceContinuationNext = false)
                } else {
                    if (currentScanningWindow == windowIndex) {
                        lineBuilder.append(ch)
                        if (lineBuilder.length >= maxLineChars && !Character.isHighSurrogate(ch)) {
                            completeLineSegment(forceContinuationNext = true)
                        }
                    } else {
                        currentSegmentLen++
                        if (currentSegmentLen >= maxLineChars && !Character.isHighSurrogate(ch)) {
                            completeLineSegment(forceContinuationNext = true)
                        }
                    }
                }

                if (charsInCurrentWindow >= maxWindowChars || linesInCurrentWindow >= maxWindowLines) {
                    if (currentScanningWindow < windowIndex) {
                        currentScanningWindow++
                        charsInCurrentWindow = 0
                        linesInCurrentWindow = 0
                    } else {
                        // We filled the target window; check if more content exists after this point
                        while (i < readCount) {
                            val remCh = charBuf[i]
                            if (prevWasCr && remCh == '\n') {
                                prevWasCr = false
                                i++
                                continue
                            }
                            hasMoreAfter = true
                            break
                        }
                        if (!hasMoreAfter) {
                            val nextCh = reader.read()
                            if (nextCh != -1) {
                                if (prevWasCr && nextCh.toChar() == '\n') {
                                    if (reader.read() != -1) {
                                        hasMoreAfter = true
                                    }
                                } else {
                                    hasMoreAfter = true
                                }
                            }
                        }
                        break@outer
                    }
                }
            }
        }

        return TxtDocumentWindow(
            lines = lines,
            detectedCharset = encodingInfo.displayName,
            isEmpty = lines.isEmpty() && windowIndex == 0,
            hasMoreAfter = hasMoreAfter,
            windowIndex = windowIndex,
            totalCharsInWindow = charsInCurrentWindow,
            hadMalformedBytes = hadMalformedBytes
        )
    }

    private data class EncodingInfo(
        val charset: Charset,
        val bomBytes: Int,
        val displayName: String
    )

    private fun checkKnownBinarySignatures(preview: ByteArray, length: Int) {
        if (length >= 5 &&
            preview[0] == '%'.code.toByte() &&
            preview[1] == 'P'.code.toByte() &&
            preview[2] == 'D'.code.toByte() &&
            preview[3] == 'F'.code.toByte() &&
            preview[4] == '-'.code.toByte()
        ) {
            throw TxtLoadException("This file is a PDF document. Please use 'Open PDF' to view it.")
        }

        if (length >= 4 &&
            preview[0] == 0x50.toByte() &&
            preview[1] == 0x4B.toByte() &&
            preview[2] == 0x03.toByte() &&
            preview[3] == 0x04.toByte()
        ) {
            throw TxtLoadException("This file is a compressed archive or Office document (ZIP/DOCX/XLSX), not a plain text (.txt) file.")
        }

        if (length >= 8 &&
            preview[0] == 0xD0.toByte() &&
            preview[1] == 0xCF.toByte() &&
            preview[2] == 0x11.toByte() &&
            preview[3] == 0xE0.toByte()
        ) {
            throw TxtLoadException("Legacy Office binary documents (.doc / .xls) are not supported by the plain text viewer.")
        }

        if (length >= 4 &&
            preview[0] == 0x89.toByte() &&
            preview[1] == 0x50.toByte() &&
            preview[2] == 0x4E.toByte() &&
            preview[3] == 0x47.toByte()
        ) {
            throw TxtLoadException("This file is a PNG image, not a plain text file.")
        }

        if (length >= 3 &&
            preview[0] == 0xFF.toByte() &&
            preview[1] == 0xD8.toByte() &&
            preview[2] == 0xFF.toByte()
        ) {
            throw TxtLoadException("This file is a JPEG image, not a plain text file.")
        }
    }

    private fun detectEncoding(preview: ByteArray, length: Int): EncodingInfo {
        // 1. Check BOMs
        if (length >= 3 &&
            preview[0] == 0xEF.toByte() &&
            preview[1] == 0xBB.toByte() &&
            preview[2] == 0xBF.toByte()
        ) {
            return EncodingInfo(Charsets.UTF_8, 3, "UTF-8 (BOM)")
        }

        if (length >= 4 &&
            preview[0] == 0xFF.toByte() &&
            preview[1] == 0xFE.toByte() &&
            preview[2] == 0x00.toByte() &&
            preview[3] == 0x00.toByte()
        ) {
            return EncodingInfo(Charset.forName("UTF-32LE"), 4, "UTF-32LE")
        }

        if (length >= 4 &&
            preview[0] == 0x00.toByte() &&
            preview[1] == 0x00.toByte() &&
            preview[2] == 0xFE.toByte() &&
            preview[3] == 0xFF.toByte()
        ) {
            return EncodingInfo(Charset.forName("UTF-32BE"), 4, "UTF-32BE")
        }

        if (length >= 2 &&
            preview[0] == 0xFF.toByte() &&
            preview[1] == 0xFE.toByte()
        ) {
            return EncodingInfo(Charsets.UTF_16LE, 2, "UTF-16LE")
        }

        if (length >= 2 &&
            preview[0] == 0xFE.toByte() &&
            preview[1] == 0xFF.toByte()
        ) {
            return EncodingInfo(Charsets.UTF_16BE, 2, "UTF-16BE")
        }

        // 2. Check for UTF-16 without BOM vs binary null bytes
        var evenNulls = 0
        var oddNulls = 0
        var controlChars = 0
        for (i in 0 until length) {
            val b = preview[i].toInt() and 0xFF
            if (b == 0) {
                if (i % 2 == 0) evenNulls++ else oddNulls++
            } else if (b in 1..8 || b in 14..31) {
                controlChars++
            }
        }

        val totalNulls = evenNulls + oddNulls
        if (totalNulls > 0) {
            // Heuristic for UTF-16LE without BOM: many odd-indexed nulls, almost no even-indexed nulls
            if (length >= 4 && oddNulls > length / 4 && evenNulls == 0) {
                return EncodingInfo(Charsets.UTF_16LE, 0, "UTF-16LE")
            }
            // Heuristic for UTF-16BE without BOM: many even-indexed nulls, almost no odd-indexed nulls
            if (length >= 4 && evenNulls > length / 4 && oddNulls == 0) {
                return EncodingInfo(Charsets.UTF_16BE, 0, "UTF-16BE")
            }
            throw TxtLoadException("Unsupported or binary file. Null bytes detected in non-UTF-16 stream.")
        }

        // Reject files with excessive binary control characters (> 10% of sample)
        if (length >= 16 && controlChars * 10 > length) {
            throw TxtLoadException("Unsupported or corrupted file. The file contains excessive binary control bytes.")
        }

        return EncodingInfo(Charsets.UTF_8, 0, "UTF-8")
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

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                if (stream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
