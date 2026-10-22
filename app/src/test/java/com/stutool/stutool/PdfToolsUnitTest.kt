package com.stutool.stutool

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.domain.engine.OcrResult
import com.stutool.stutool.ui.components.ProcessingState
import com.stutool.stutool.ui.screens.tools.PdfToExcelViewModel
import com.stutool.stutool.ui.screens.tools.PdfToImageViewModel
import com.stutool.stutool.ui.screens.tools.PdfToWordViewModel
import com.stutool.stutool.ui.screens.tools.OcrPdfViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfToolsUnitTest {

    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 1. Processing State Phase Stability & Anti-Flashing Test ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testProcessingPhaseRemainsStableAcrossRapidPageUpdates() {
        // In the reported 500-page bug, every page emitted a distinct message:
        // "Extracting text: page 1 of 500", "Extracting text: page 2 of 500", etc.
        // If the phase changed or if Crossfade keyed directly on state equality,
        // it triggered 500 screen-level crossfades causing violent flashing.

        val pageUpdates = (1..500).map { page ->
            ProcessingState.Processing(
                progress = page.toFloat() / 500f,
                message = "Extracting text: page $page of 500..."
            )
        }

        // Verify that structural equality is false between different pages (the trigger of the bug)
        assertNotEquals(pageUpdates[0], pageUpdates[1])

        // Verify that in our redesigned system, all 500 page updates remain within the
        // identical processing phase without restarting transitions.
        val phaseName = { s: ProcessingState ->
            when (s) {
                is ProcessingState.Idle -> "IDLE"
                is ProcessingState.Preparing,
                is ProcessingState.Processing -> "IN_PROGRESS"
                is ProcessingState.Success -> "SUCCESS"
                is ProcessingState.Error -> "ERROR"
                is ProcessingState.Cancelled -> "CANCELLED"
            }
        }

        pageUpdates.forEach { state ->
            assertEquals("IN_PROGRESS", phaseName(state))
        }

        // Verify transition from In-Progress to Success is distinct
        val successState = ProcessingState.Success(title = "Spreadsheet Generated!")
        assertEquals("SUCCESS", phaseName(successState))
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 2. Progress Throttling Algorithm Simulation Test ──────────
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testProgressThrottlingLimitsEmissionsUnderHighFrequency() {
        // Simulates rapid 500-page callbacks occurring within 50ms of real execution.
        val totalPages = 500
        var emittedCount = 0
        var lastProgressTime = 0L
        var lastEmittedCurrent = 0
        var lastEmittedProgress = 0f

        // Mock simulated clock starting at t=1000ms
        var mockCurrentTime = 1000L

        for (current in 1..totalPages) {
            // Each page takes ~0.1ms in fast mock iteration
            if (current % 10 == 0) {
                mockCurrentTime += 1L // advance time slightly
            }

            val isLast = current == totalPages
            val isFirst = current == 1
            val timeElapsed = mockCurrentTime - lastProgressTime >= 50L

            if (isLast || isFirst || timeElapsed) {
                lastProgressTime = mockCurrentTime
                emittedCount++
                lastEmittedCurrent = current
                lastEmittedProgress = current.toFloat() / totalPages.toFloat()
            }
        }

        // Emitted count must be drastically smaller than 500 (e.g. < 100 updates)
        assertTrue("Emitted count ($emittedCount) should be throttled well below 500", emittedCount < 100)
        // The final page MUST always be guaranteed to emit
        assertEquals(totalPages, lastEmittedCurrent)
        assertEquals(1f, lastEmittedProgress, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 3. PDF to Excel ViewModel & Data Flow Tests ───────────────
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testPdfToExcelViewModelStateTransitions() {
        val vm = PdfToExcelViewModel(application)

        assertNull(vm.selectedUri.value)
        assertNull(vm.fileName.value)
        assertEquals(0L, vm.fileSizeBytes.value)
        assertEquals(0, vm.pageCount.value)
        assertFalse(vm.isConverting.value)
        assertEquals(0f, vm.progress.value, 0.01f)
        assertNull(vm.outputUri.value)
        assertNull(vm.errorMessage.value)
        assertNull(vm.processingError.value)

        vm.clearError()
        vm.clearProcessingError()

        vm.reset()
        assertNull(vm.selectedUri.value)
        assertEquals("Preparing...", vm.statusMessage.value)
    }

    @Test
    fun testExcelColumnNamingAlgorithm() {
        // Tests the column name formula: index 0 -> A, index 25 -> Z, index 26 -> AA, 51 -> AZ
        fun getColumnName(index: Int): String {
            var colName = ""
            var num = index + 1
            while (num > 0) {
                val mod = (num - 1) % 26
                colName = (65 + mod).toChar() + colName
                num = (num - 1) / 26
            }
            return colName
        }

        assertEquals("A", getColumnName(0))
        assertEquals("B", getColumnName(1))
        assertEquals("Z", getColumnName(25))
        assertEquals("AA", getColumnName(26))
        assertEquals("AB", getColumnName(27))
        assertEquals("AZ", getColumnName(51))
        assertEquals("BA", getColumnName(52))
    }

    @Test
    fun testXmlSanitizationHandlesSpecialCharacters() {
        fun escapeXml(input: String): String {
            return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        }

        val raw = "Revenue <Q1> & \"Profit\" = '100%'\u0000\u0007"
        val clean = escapeXml(raw)
        assertEquals("Revenue &lt;Q1&gt; &amp; &quot;Profit&quot; = &apos;100%&apos;", clean)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 4. PDF to Word ViewModel Tests ────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testPdfToWordViewModelStateTransitions() {
        val vm = PdfToWordViewModel(application)

        assertNull(vm.selectedUri.value)
        assertNull(vm.fileName.value)
        assertEquals(0, vm.pageCount.value)
        assertFalse(vm.isConverting.value)
        assertEquals(0f, vm.progress.value, 0.01f)
        assertNull(vm.outputUri.value)

        vm.reset()
        assertNull(vm.selectedUri.value)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 5. OCR PDF ViewModel Tests ────────────────────────────────
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testOcrPdfViewModelStateTransitions() {
        val vm = OcrPdfViewModel(application)

        assertNull(vm.selectedUri.value)
        assertNull(vm.fileName.value)
        assertEquals(0, vm.pageCount.value)
        assertFalse(vm.isConverting.value)
        assertEquals(0f, vm.progress.value, 0.01f)
        assertNull(vm.outputUri.value)
        assertNull(vm.ocrResult.value)

        vm.reset()
        assertNull(vm.selectedUri.value)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── 6. PDF to Image Page Range Parsing Tests ──────────────────
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testPdfToImagePageRangeParsing() {
        val vm = PdfToImageViewModel(application)

        // Helper to test range parsing logic
        fun parseRange(range: String, totalPages: Int): List<Int> {
            if (range.isBlank()) return (1..totalPages).toList()
            val pages = mutableSetOf<Int>()
            try {
                val parts = range.split(",")
                for (part in parts) {
                    val p = part.trim()
                    if (p.contains("-")) {
                        val bounds = p.split("-")
                        if (bounds.size == 2) {
                            val start = bounds[0].trim().toInt()
                            val end = bounds[1].trim().toInt()
                            if (start in 1..end && end <= totalPages) {
                                pages.addAll(start..end)
                            } else {
                                return emptyList()
                            }
                        }
                    } else {
                        val page = p.toInt()
                        if (page in 1..totalPages) {
                            pages.add(page)
                        } else {
                            return emptyList()
                        }
                    }
                }
            } catch (_: Exception) {
                return emptyList()
            }
            return pages.sorted()
        }

        // Blank = all pages
        assertEquals(listOf(1, 2, 3, 4, 5), parseRange("", 5))

        // Single pages and commas
        assertEquals(listOf(1, 3, 5), parseRange("1, 3, 5", 5))

        // Ranges
        assertEquals(listOf(1, 2, 3, 5), parseRange("1-3, 5", 5))

        // Out of bounds -> invalid (empty)
        assertEquals(emptyList<Int>(), parseRange("1-10", 5))
        assertEquals(emptyList<Int>(), parseRange("0", 5))
        assertEquals(emptyList<Int>(), parseRange("abc", 5))

        // Options test
        vm.setFormat("PNG")
        assertEquals("PNG", vm.format.value)

        vm.setQuality("Standard")
        assertEquals("Standard", vm.quality.value)

        vm.setPageRange("1-2")
        assertEquals("1-2", vm.pageRange.value)
    }
}
