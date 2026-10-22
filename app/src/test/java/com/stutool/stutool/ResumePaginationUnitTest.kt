package com.stutool.stutool

import com.stutool.stutool.ui.screens.resume.utils.BlockBounds
import com.stutool.stutool.ui.screens.resume.utils.PaginationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePaginationUnitTest {

    /**
     * Replicates the exact slicing logic from PdfExporter to verify pagination decisions in pure JVM tests.
     */
    private data class PageSliceResult(
        val pageIndex: Int,
        val startY: Float,
        val sliceEndY: Float,
        val actualDrawnHeight: Float,
        val modernSidebarFillNeeded: Boolean,
        val modernSidebarFillTop: Float,
        val modernSidebarFillBottom: Float
    )

    private fun simulatePdfPagination(
        measuredHeight: Float,
        pdfHeightPx: Float,
        registry: PaginationRegistry,
        isModern: Boolean = true
    ): List<PageSliceResult> {
        val results = mutableListOf<PageSliceResult>()
        var pageIndex = 0
        var currentStartY = 0f

        while (currentStartY < measuredHeight) {
            val distinctBlocks = registry.blocks.distinctBy { it.top.toInt() }.sortedBy { it.top }

            // Guard: Trailing padding should not create an extra blank page
            if (pageIndex > 0 && distinctBlocks.isNotEmpty() && distinctBlocks.none { it.bottom > currentStartY }) {
                break
            }

            var sliceEndY = currentStartY + pdfHeightPx

            // Block splitting prevention
            for (block in distinctBlocks) {
                if (block.top > currentStartY && block.top < sliceEndY && block.bottom > sliceEndY) {
                    val blockHeight = block.bottom - block.top
                    if (blockHeight <= pdfHeightPx) {
                        sliceEndY = block.top
                    }
                }
            }

            // Orphan header prevention
            val lastBlockOnPage = distinctBlocks.lastOrNull { it.top >= currentStartY && it.top < sliceEndY }
            if (lastBlockOnPage != null && lastBlockOnPage.isHeader) {
                if (lastBlockOnPage.top > currentStartY) {
                    sliceEndY = lastBlockOnPage.top
                }
            }

            if (sliceEndY <= currentStartY) {
                sliceEndY = currentStartY + pdfHeightPx
            }

            val actualContentEndY = sliceEndY.coerceAtMost(measuredHeight)
            val actualDrawnHeight = (actualContentEndY - currentStartY).coerceAtLeast(0f)

            val sidebarNeeded = isModern && (actualDrawnHeight < pdfHeightPx)
            val sidebarTop = if (sidebarNeeded) actualDrawnHeight else 0f
            val sidebarBottom = if (sidebarNeeded) pdfHeightPx else 0f

            results.add(
                PageSliceResult(
                    pageIndex = pageIndex,
                    startY = currentStartY,
                    sliceEndY = sliceEndY,
                    actualDrawnHeight = actualDrawnHeight,
                    modernSidebarFillNeeded = sidebarNeeded,
                    modernSidebarFillTop = sidebarTop,
                    modernSidebarFillBottom = sidebarBottom
                )
            )

            currentStartY = sliceEndY
            pageIndex++
        }

        return results
    }

    @Test
    fun testDeclarationStaysOnPageOneWhenBodyIsRegistered() {
        // Scenario: A resume with experience and education that end at Y = 2800.
        // Declaration header is at Y = 2850, and Declaration body ends at Y = 3100.
        // A4 page height = 3507 px.
        val registry = PaginationRegistry()
        registry.addBlock(top = 100f, bottom = 200f, isHeader = true) // WORK EXPERIENCE header
        registry.addBlock(top = 210f, bottom = 1200f, isHeader = false) // Exp item 1
        registry.addBlock(top = 1250f, bottom = 1350f, isHeader = true) // EDUCATION header
        registry.addBlock(top = 1360f, bottom = 2200f, isHeader = false) // Edu item 1
        registry.addBlock(top = 2250f, bottom = 2350f, isHeader = true) // PROJECTS header
        registry.addBlock(top = 2360f, bottom = 2800f, isHeader = false) // Proj item 1
        registry.addBlock(top = 2850f, bottom = 2900f, isHeader = true) // DECLARATION header
        registry.addBlock(top = 2910f, bottom = 3100f, isHeader = false) // DECLARATION body (registered!)

        val measuredHeight = 3150f
        val pdfHeightPx = 3507f

        val pages = simulatePdfPagination(measuredHeight, pdfHeightPx, registry, isModern = true)

        // With the body registered, Declaration fits comfortably on Page 1!
        assertEquals("Should only generate 1 page", 1, pages.size)
        assertEquals(0f, pages[0].startY, 0.01f)
        assertEquals(3150f, pages[0].actualDrawnHeight, 0.01f)
    }

    @Test
    fun testDeclarationPushedToPageTwoIfBodyWasUnregistered_DemonstratingBugFix() {
        // Reproduce the previous bug: If Declaration body was NOT registered,
        // the last block on page 1 was the DECLARATION header (isHeader = true).
        val registry = PaginationRegistry()
        registry.addBlock(top = 100f, bottom = 200f, isHeader = true)
        registry.addBlock(top = 210f, bottom = 2800f, isHeader = false)
        registry.addBlock(top = 2850f, bottom = 2900f, isHeader = true) // DECLARATION header
        // Notice: body is NOT registered here!

        val measuredHeight = 3150f
        val pdfHeightPx = 3507f

        val pages = simulatePdfPagination(measuredHeight, pdfHeightPx, registry, isModern = true)

        // Without the body registered, the orphan header check falsely kicked DECLARATION to Page 2!
        assertEquals(2, pages.size)
        assertEquals(2850f, pages[0].sliceEndY, 0.01f) // Cut before header!
        assertEquals(2850f, pages[1].startY, 0.01f) // Page 2 starts with DECLARATION
    }

    @Test
    fun testModernSidebarFillOnContinuationPage() {
        // Multi-page resume: Page 1 fills 3507 px.
        // Page 2 has only 823 px of content (measuredHeight = 4330 px).
        val registry = PaginationRegistry()
        registry.addBlock(top = 100f, bottom = 3400f, isHeader = false)
        registry.addBlock(top = 3520f, bottom = 4300f, isHeader = false)

        val measuredHeight = 4330f
        val pdfHeightPx = 3507f

        val pages = simulatePdfPagination(measuredHeight, pdfHeightPx, registry, isModern = true)

        assertEquals("Should produce 2 pages", 2, pages.size)

        val page2 = pages[1]
        assertEquals("Page 2 starts at 3507 px", 3507f, page2.startY, 0.01f)
        assertEquals("Page 2 drawn height must be 823 px", 823f, page2.actualDrawnHeight, 0.01f)
        assertTrue("Modern sidebar fill must be needed on Page 2", page2.modernSidebarFillNeeded)
        assertEquals("Sidebar fill must start at bottom of drawn content (823 px)", 823f, page2.modernSidebarFillTop, 0.01f)
        assertEquals("Sidebar fill must stretch to bottom of physical page (3507 px)", 3507f, page2.modernSidebarFillBottom, 0.01f)
    }

    @Test
    fun testTrailingPaddingDoesNotGenerateBlankContinuationPage() {
        // Single page resume where content finishes at Y = 3400 px,
        // but container padding extends measuredHeight to 3550 px (> 3507 px).
        val registry = PaginationRegistry()
        registry.addBlock(top = 100f, bottom = 3400f, isHeader = false)

        val measuredHeight = 3550f
        val pdfHeightPx = 3507f

        val pages = simulatePdfPagination(measuredHeight, pdfHeightPx, registry, isModern = true)

        assertEquals("Trailing padding must not create a blank Page 2", 1, pages.size)
        assertEquals(0f, pages[0].startY, 0.01f)
    }

    @Test
    fun testGenuineOrphanHeaderMovedToNextPage() {
        // A section header that starts right at the bottom of Page 1 (e.g. Y = 3480, height = 40)
        // and its body starts on Page 2 (Y = 3525).
        val registry = PaginationRegistry()
        registry.addBlock(top = 100f, bottom = 3450f, isHeader = false)
        registry.addBlock(top = 3480f, bottom = 3500f, isHeader = true) // Section header at bottom
        registry.addBlock(top = 3525f, bottom = 4000f, isHeader = false) // Body starts on Page 2

        val measuredHeight = 4050f
        val pdfHeightPx = 3507f

        val pages = simulatePdfPagination(measuredHeight, pdfHeightPx, registry, isModern = true)

        assertEquals("Should produce 2 pages", 2, pages.size)
        assertEquals("Page 1 must cut at header top so header is not orphaned", 3480f, pages[0].sliceEndY, 0.01f)
        assertEquals("Page 2 must start with the header", 3480f, pages[1].startY, 0.01f)
    }
}
