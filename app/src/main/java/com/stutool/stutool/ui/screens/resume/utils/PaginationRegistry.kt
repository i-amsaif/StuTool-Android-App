package com.stutool.stutool.ui.screens.resume.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

data class BlockBounds(val top: Float, val bottom: Float, val isHeader: Boolean = false)

class PaginationRegistry {
    private val _blocks = mutableListOf<BlockBounds>()
    val blocks: List<BlockBounds> get() = _blocks

    fun addBlock(top: Float, bottom: Float, isHeader: Boolean = false) {
        // Find if this block already exists approximately (since onGloballyPositioned can be called multiple times)
        // Wait, onGloballyPositioned is called during layout. PdfExporter only exports AFTER layout has settled.
        // But to be safe, we can just clear blocks at the start of export?
        // PdfExporter creates a NEW PaginationRegistry instance for every export! So no clearing needed.
        _blocks.add(BlockBounds(top, bottom, isHeader))
    }
}

val LocalPaginationRegistry = compositionLocalOf<PaginationRegistry?> { null }

fun Modifier.pageBreakAware(isHeader: Boolean = false): Modifier = composed {
    val registry = LocalPaginationRegistry.current
    this.onGloballyPositioned { coordinates ->
        registry?.let {
            val top = coordinates.positionInRoot().y
            val height = coordinates.size.height.toFloat()
            // To prevent duplicates during multiple layout passes, we could filter, 
            // but PdfExporter relies on the final state.
            // A simple implementation is to just let it add, and PdfExporter will filter unique tops.
            it.addBlock(top, top + height, isHeader)
        }
    }
}

@Composable
fun PageBreakAwareBlock(modifier: Modifier = Modifier, isHeader: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier = modifier.pageBreakAware(isHeader = isHeader)) {
        content()
    }
}
