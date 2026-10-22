package com.stutool.stutool.ui.screens.resume.utils

object PaginationUtils {
    const val A4_RATIO = 1.4142f
    
    // The fixed pixel width for high-density PDF generation mapped to standard A4 300DPI
    const val PDF_WIDTH_PX = 2480
    val PDF_HEIGHT_PX = (PDF_WIDTH_PX * A4_RATIO).toInt()

    // Virtual Dp scale. The base virtual A4 width is 595f in Compose.
    // By determining header height exactly via virtual dp mapping (e.g., 36dp), 
    // it perfectly matches the screen density resolution scale!
    const val HEADER_VIRTUAL_DP = 36f 
    const val VIRTUAL_BASE_WIDTH = 595f

    /**
     * Calculates dynamic header height in actual pixels respecting the device density scaling
     * inherited by the rendering engine width.
     */
    fun getHeaderHeightPx(pageWidthPx: Float): Float {
        // Multi-page 'Continued' header was completely removed per requirements, so no height is reserved.
        return 0f
    }

    /**
     * Calculates the contiguous, unbounded source graphic boundaries that belong on a specific page.
     * @param pageIndex 0-indexed page number
     * @param pageWidthPx The width context (either UI visualWidth or PDF_WIDTH_PX)
     * @return Pair containing (startY, endY) representing the span of source layout captured
     */
    fun getSourceBoundsForPage(pageIndex: Int, pageWidthPx: Float): Pair<Float, Float> {
        val pageHeightPx = pageWidthPx * A4_RATIO
        val headerHeightPx = getHeaderHeightPx(pageWidthPx)
        
        if (pageIndex == 0) {
            return Pair(0f, pageHeightPx)
        }
        
        // Every subsequent page starts where the last one ended, 
        // but can only consume (pageHeightPx - headerHeightPx) pixels before full.
        val startY = pageHeightPx + (pageIndex - 1) * (pageHeightPx - headerHeightPx)
        val endY = startY + (pageHeightPx - headerHeightPx)
        
        return Pair(startY, endY)
    }

    /**
     * Determines how much to translate the physical Canvas.
     * Target formulation: y_physical = y_source + dY  ->  dY = y_physical - y_source
     */
    fun getCanvasTranslationY(pageIndex: Int, pageWidthPx: Float): Float {
        val (startY, _) = getSourceBoundsForPage(pageIndex, pageWidthPx)
        val headerHeightPx = getHeaderHeightPx(pageWidthPx)
        
        // Page 0 has no header. Page 1+ have a header block reserving the top physical pixels.
        val targetPhysicalY = if (pageIndex == 0) 0f else headerHeightPx
        
        return targetPhysicalY - startY
    }
}
