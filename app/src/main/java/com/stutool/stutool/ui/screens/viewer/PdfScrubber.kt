package com.stutool.stutool.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun PdfScrubber(
    pageCount: Int,
    currentPage: Int,
    onPageSelect: (Int) -> Unit,
    isVertical: Boolean,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    val handleSizeDp = 48.dp
    val handleThicknessDp = 32.dp
    val handleSizePx = with(LocalDensity.current) { handleSizeDp.toPx() }

    var trackSize by remember { mutableFloatStateOf(1f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync dragOffset with currentPage when not dragging
    LaunchedEffect(currentPage, pageCount, trackSize, isDragging) {
        if (!isDragging && trackSize > 0) {
            val maxDrag = (trackSize - handleSizePx).coerceAtLeast(0f)
            dragOffset = (currentPage.toFloat() / (pageCount - 1).coerceAtLeast(1)) * maxDrag
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(if (isVertical) PaddingValues(end = 8.dp, top = 64.dp, bottom = 64.dp) else PaddingValues(bottom = 64.dp, start = 16.dp, end = 16.dp))
            .onSizeChanged { size ->
                trackSize = if (isVertical) size.height.toFloat() else size.width.toFloat()
            },
        contentAlignment = if (isVertical) Alignment.TopEnd else Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .offset {
                    if (isVertical) {
                        IntOffset(0, dragOffset.roundToInt())
                    } else {
                        IntOffset(dragOffset.roundToInt(), 0)
                    }
                }
                .size(if (isVertical) handleThicknessDp else handleSizeDp, if (isVertical) handleSizeDp else handleThicknessDp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(trackSize, pageCount) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val amount = if (isVertical) dragAmount.y else dragAmount.x
                        val maxDrag = (trackSize - handleSizePx).coerceAtLeast(0f)
                        dragOffset = (dragOffset + amount).coerceIn(0f, maxDrag)

                        val fraction = if (maxDrag > 0) (dragOffset / maxDrag).coerceIn(0f, 1f) else 0f
                        val targetPage = (fraction * (pageCount - 1)).roundToInt()
                        onPageSelect(targetPage)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = "Scrubber",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
