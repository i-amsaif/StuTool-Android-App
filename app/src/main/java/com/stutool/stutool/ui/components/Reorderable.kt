package com.stutool.stutool.ui.components


import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════
// ── Reorderable Item Interface ──
// ═══════════════════════════════════════════════

/**
 * Any item that participates in reordering must implement this interface.
 * The [id] must be a stable, unique identifier (typically a UUID string).
 */
interface ReorderableItem {
    val id: String
}

// ═══════════════════════════════════════════════
// ── Reorderable State ──
// ═══════════════════════════════════════════════

/**
 * Encapsulates all state required for reordering a LazyColumn list.
 * Created once via [rememberReorderableState] and shared across
 * [Modifier.reorderableItem] and [Modifier.reorderHandle].
 */
class ReorderableState(
    val lazyListState: LazyListState,
    val spacingPx: Float
) {
    var draggedItemKey by mutableStateOf<String?>(null)
        internal set
    var draggedItemInitialIndex by mutableStateOf(-1)
        internal set
    var currentDraggedIndex by mutableStateOf(-1)
        internal set
    var absoluteDragOffset by mutableStateOf(0f)
        internal set
    var itemHeights by mutableStateOf(mapOf<String, Int>())
        internal set
    var autoScrollSpeed by mutableStateOf(0f)
        internal set

    val isDragging: Boolean get() = draggedItemKey != null

    fun isDraggingItem(key: String): Boolean = draggedItemKey == key

    /**
     * Record the measured height of an item. Called from [onGloballyPositioned].
     */
    fun updateItemHeight(key: String, height: Int) {
        if (itemHeights[key] != height) {
            itemHeights = itemHeights.toMutableMap().apply { put(key, height) }
        }
    }

    /**
     * Midpoint-crossing check: determines if the dragged item has
     * crossed the boundary of an adjacent item and triggers a reorder.
     */
    internal fun checkReorder(
        items: List<ReorderableItem>,
        onReorder: (String, Int) -> Unit,
        haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
    ) {
        val key = draggedItemKey ?: return
        val itemHeight = itemHeights[key] ?: return
        val itemAndSpacing = itemHeight + spacingPx
        if (itemAndSpacing <= 0) return

        val rawOffset = absoluteDragOffset / itemAndSpacing
        
        // Calculate the proposed new index purely based on mathematical rounding
        val proposedOffsetIndex = rawOffset.roundToInt()
        val proposedTargetIndex = (draggedItemInitialIndex + proposedOffsetIndex).coerceIn(0, items.size - 1)

        if (proposedTargetIndex != currentDraggedIndex && currentDraggedIndex != -1) {
            // HYSTERESIS: Require crossing 60% of the way to the target item to prevent hover oscillation
            val difference = rawOffset - (currentDraggedIndex - draggedItemInitialIndex)
            val direction = proposedTargetIndex - currentDraggedIndex
            
            val threshold = 0.60f

            val shouldSwap = if (direction > 0) {
                difference > threshold
            } else {
                difference < -threshold
            }

            if (shouldSwap) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onReorder(key, proposedTargetIndex)
                currentDraggedIndex = proposedTargetIndex
            }
        }
    }

    /**
     * Reset all drag state. Called on drag end/cancel.
     */
    internal fun resetDrag() {
        draggedItemKey = null
        autoScrollSpeed = 0f
    }
}

// ═══════════════════════════════════════════════
// ── Remember Function ──
// ═══════════════════════════════════════════════

/**
 * Creates and remembers a [ReorderableState] tied to the provided [LazyListState].
 *
 * @param lazyListState The state of the LazyColumn hosting the reorderable items.
 * @param itemSpacing The vertical spacing between items (must match LazyColumn's `Arrangement.spacedBy`).
 */
@Composable
fun rememberReorderableState(
    lazyListState: LazyListState,
    itemSpacing: Dp = 10.dp
): ReorderableState {
    val density = LocalDensity.current
    val spacingPx = with(density) { itemSpacing.toPx() }

    return remember(lazyListState) {
        ReorderableState(lazyListState, spacingPx)
    }
}

// ═══════════════════════════════════════════════
// ── Auto-Scroll Effect ──
// ═══════════════════════════════════════════════

/**
 * Must be called once in the composable hosting the LazyColumn.
 * Drives frame-based auto-scrolling while the user is dragging near the viewport edges.
 */
@Composable
fun ReorderableAutoScrollEffect(
    state: ReorderableState,
    items: List<ReorderableItem>,
    onReorder: (String, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // Keep references fresh across recompositions so the LaunchedEffect
    // never operates on stale list snapshots after a swap.
    val currentItems by rememberUpdatedState(items)
    val currentOnReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(state.autoScrollSpeed) {
        if (state.autoScrollSpeed != 0f) {
            while (state.autoScrollSpeed != 0f) {
                val scrolled = state.lazyListState.scrollBy(state.autoScrollSpeed)
                state.absoluteDragOffset += scrolled
                state.checkReorder(currentItems, currentOnReorder, haptic)
                delay(16) // ~60fps
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── Modifier Extensions ──
// ═══════════════════════════════════════════════

/**
 * Apply to the outer Box wrapping each item in the LazyColumn.
 * Handles: zIndex, graphicsLayer transforms (translation, scale, elevation, opacity),
 * and item height measurement.
 * 
 * NOTE: animateItemPlacement() must be applied at the call site within LazyItemScope,
 * as it is only available within that scope. Apply it conditionally when !isDragging.
 */
fun Modifier.reorderableItem(
    state: ReorderableState,
    key: String
): Modifier {
    val isDragging = state.isDraggingItem(key)

    return this
        .zIndex(if (isDragging) 1f else 0f)
        .onGloballyPositioned { coordinates ->
            state.updateItemHeight(key, coordinates.size.height)
        }
        .graphicsLayer {
            if (isDragging) {
                val itemHeight = state.itemHeights[key] ?: 0
                val itemAndSpacing = itemHeight + state.spacingPx
                val listDelta = (state.currentDraggedIndex - state.draggedItemInitialIndex) * itemAndSpacing
                translationY = state.absoluteDragOffset - listDelta

                scaleX = 1.04f
                scaleY = 1.04f
                shadowElevation = 8.dp.toPx()
                alpha = 0.9f
            }
        }
}

/**
 * Apply to the drag handle Icon/Box within each item.
 * Handles: long-press drag gesture detection, midpoint-crossing reorder checks,
 * and auto-scroll speed calculation based on the dragged item's visual position.
 */
@Composable
fun Modifier.reorderHandle(
    state: ReorderableState,
    key: String,
    items: List<ReorderableItem>,
    onReorder: (String, Int) -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    // CRITICAL: pointerInput(key) captures its lambda at first composition
    // and never restarts (key is the item's stable ID). Without
    // rememberUpdatedState, subsequent swaps use stale list references
    // and move the wrong items, causing the dragged item to snap back.
    val currentItems by rememberUpdatedState(items)
    val currentOnReorder by rememberUpdatedState(onReorder)

    return this.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                state.draggedItemKey = key
                state.draggedItemInitialIndex = currentItems.indexOfFirst { it.id == key }
                state.currentDraggedIndex = state.draggedItemInitialIndex
                state.absoluteDragOffset = 0f
            },
            onDragEnd = {
                state.resetDrag()
            },
            onDragCancel = {
                state.resetDrag()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.absoluteDragOffset += dragAmount.y

                state.checkReorder(currentItems, currentOnReorder, haptic)

                // Calculate auto-scroll speed based on visual position
                val layoutInfo = state.lazyListState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

                val itemInfo = layoutInfo.visibleItemsInfo.find { it.key == state.draggedItemKey }
                if (itemInfo != null) {
                    val itemHeight = state.itemHeights[key] ?: 0
                    val itemAndSpacing = itemHeight + state.spacingPx
                    val listDelta = (state.currentDraggedIndex - state.draggedItemInitialIndex) * itemAndSpacing
                    val visualTop = itemInfo.offset + (state.absoluteDragOffset - listDelta)
                    val visualCenter = visualTop + itemHeight / 2f

                    val edgeSize = 150f
                    val maxScrollSpeed = 25f

                    state.autoScrollSpeed = when {
                        visualCenter < edgeSize -> {
                            -((edgeSize - visualCenter) / edgeSize).coerceIn(0f, 1f) * maxScrollSpeed
                        }
                        visualCenter > viewportHeight - edgeSize -> {
                            ((visualCenter - (viewportHeight - edgeSize)) / edgeSize).coerceIn(0f, 1f) * maxScrollSpeed
                        }
                        else -> 0f
                    }
                }
            }
        )
    }
}
