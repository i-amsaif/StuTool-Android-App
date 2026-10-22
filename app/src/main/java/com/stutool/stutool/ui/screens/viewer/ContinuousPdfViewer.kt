package com.stutool.stutool.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

suspend fun PointerInputScope.detectPinchZoomGestures(
    onZoom: (centroid: Offset, zoomChange: Float) -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val isMultiTouch = event.changes.count { it.pressed } > 1

                if (!pastTouchSlop && isMultiTouch) {
                    zoom *= zoomChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                    if (zoomMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop && isMultiTouch) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (centroid.isSpecified) {
                        val effectiveZoomChange = event.calculateZoom()
                        onZoom(centroid, effectiveZoomChange)
                        event.changes.forEach {
                            if (it.positionChanged()) it.consume()
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

suspend fun PointerInputScope.detectPassiveCrossAxisPan(
    readingDirection: ReadingDirection,
    onCrossAxisPan: (Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pastSlop = false
        var slopAccumulator = Offset.Zero
        val touchSlop = viewConfiguration.touchSlop

        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val changes = event.changes
            if (changes.size == 1) {
                val change = changes.first()
                if (!change.isConsumed) {
                    val delta = change.positionChange()

                    if (!pastSlop) {
                        slopAccumulator += delta
                        if (slopAccumulator.getDistance() > touchSlop) {
                            pastSlop = true
                        }
                    }

                    if (pastSlop) {
                        val crossAxisDelta = if (readingDirection == ReadingDirection.Vertical) {
                            Offset(delta.x, 0f)
                        } else {
                            Offset(0f, delta.y)
                        }
                        if (crossAxisDelta != Offset.Zero) {
                            onCrossAxisPan(crossAxisDelta)
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
fun ContinuousPdfViewer(
    pageCount: Int,
    viewModel: PdfReaderViewModel,
    backgroundMode: BackgroundMode,
    readingDirection: ReadingDirection,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit,
    onLinkTap: (PdfLinkAnnotation) -> Unit = {}
) {
    val savedScale by viewModel.continuousScale.collectAsState()
    val savedOffsetX by viewModel.continuousOffsetX.collectAsState()
    val savedOffsetY by viewModel.continuousOffsetY.collectAsState()

    var scale by remember { mutableFloatStateOf(savedScale) }
    var offset by remember { mutableStateOf(Offset(savedOffsetX, savedOffsetY)) }

    LaunchedEffect(scale, offset) {
        viewModel.updateContinuousTransform(scale, offset.x, offset.y)
    }

    val minScale = 1f
    val maxScale = 5f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewerWidth = maxWidth.value * androidx.compose.ui.platform.LocalDensity.current.density
        val viewerHeight = maxHeight.value * androidx.compose.ui.platform.LocalDensity.current.density
        val containerWidthDp = maxWidth
        val containerHeightDp = maxHeight

        val maxX = ((viewerWidth * scale) - viewerWidth) / 2f
        val maxY = ((viewerHeight * scale) - viewerHeight) / 2f

        val coroutineScope = rememberCoroutineScope()
        var zoomAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        LaunchedEffect(scale, maxX, maxY) {
            val clampedX = offset.x.coerceIn(-maxX, maxX)
            val clampedY = offset.y.coerceIn(-maxY, maxY)
            if (clampedX != offset.x || clampedY != offset.y) {
                offset = Offset(clampedX, clampedY)
            }
        }

        // Main axis nested scroll handoff
        val nestedScrollConnection = remember(scale, maxX, maxY, readingDirection) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (scale <= 1f) return Offset.Zero

                    var consumedLocalX = 0f
                    var consumedLocalY = 0f

                    if (readingDirection == ReadingDirection.Vertical) {
                        // Main axis is Y. 'available' is in child's local coordinates.
                        val availableScreenY = available.y * scale
                        val targetY = offset.y + availableScreenY
                        val clampedY = targetY.coerceIn(-maxY, maxY)
                        val consumedScreenY = clampedY - offset.y
                        if (!consumedScreenY.isNaN() && !consumedScreenY.isInfinite()) {
                            offset = Offset(offset.x, clampedY)
                        }
                        consumedLocalY = consumedScreenY / scale
                    } else {
                        // Main axis is X
                        val availableScreenX = available.x * scale
                        val targetX = offset.x + availableScreenX
                        val clampedX = targetX.coerceIn(-maxX, maxX)
                        val consumedScreenX = clampedX - offset.x
                        if (!consumedScreenX.isNaN() && !consumedScreenX.isInfinite()) {
                            offset = Offset(clampedX, offset.y)
                        }
                        consumedLocalX = consumedScreenX / scale
                    }
                    return Offset(consumedLocalX, consumedLocalY)
                }
            }
        }

        val currentOnSingleTap by rememberUpdatedState(onSingleTap)
        val currentOnLinkTap by rememberUpdatedState(onLinkTap)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val targetScale = when {
                                scale < 2.4f -> 2.5f
                                scale < 4.9f -> 5f
                                else -> 1f
                            }

                            val startScale = scale
                            val startOffset = offset

                            val zoomFactor = targetScale / startScale
                            val targetOffsetX = startOffset.x * zoomFactor - (tapOffset.x - viewerWidth / 2f) * (zoomFactor - 1f)
                            val targetOffsetY = startOffset.y * zoomFactor - (tapOffset.y - viewerHeight / 2f) * (zoomFactor - 1f)

                            val limitX = (((viewerWidth * targetScale) - viewerWidth) / 2f).coerceAtLeast(0f)
                            val limitY = (((viewerHeight * targetScale) - viewerHeight) / 2f).coerceAtLeast(0f)

                            val finalTargetOffsetX = targetOffsetX.coerceIn(-limitX, limitX)
                            val finalTargetOffsetY = targetOffsetY.coerceIn(-limitY, limitY)

                            zoomAnimationJob?.cancel()
                            zoomAnimationJob = coroutineScope.launch {
                                androidx.compose.animation.core.Animatable(0f).animateTo(
                                    targetValue = 1f,
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                ) {
                                    val fraction = this.value
                                    scale = androidx.compose.ui.util.lerp(startScale, targetScale, fraction)
                                    offset = Offset(
                                        x = androidx.compose.ui.util.lerp(startOffset.x, finalTargetOffsetX, fraction),
                                        y = androidx.compose.ui.util.lerp(startOffset.y, finalTargetOffsetY, fraction)
                                    )
                                }
                            }
                        },
                        onTap = { tapOffset ->
                            val isVertical = readingDirection == ReadingDirection.Vertical
                            val currentScale = scale
                            val currentOffset = offset
                            coroutineScope.launch {
                                val layoutInfo = listState.layoutInfo
                                val viewportStart = layoutInfo.viewportStartOffset
                                var hitAnnotation: PdfLinkAnnotation? = null

                                for (item in layoutInfo.visibleItemsInfo) {
                                    val normalizedPoint = PdfHyperlinkSupport.mapContinuousViewportTapToNormalizedPoint(
                                        tapX = tapOffset.x,
                                        tapY = tapOffset.y,
                                        viewerWidth = viewerWidth,
                                        viewerHeight = viewerHeight,
                                        scale = currentScale,
                                        offsetX = currentOffset.x,
                                        offsetY = currentOffset.y,
                                        isVertical = isVertical,
                                        viewportStartOffset = viewportStart,
                                        itemOffset = item.offset,
                                        itemSize = item.size
                                    )
                                    if (normalizedPoint != null) {
                                        val links = viewModel.getPageLinks(item.index)
                                        hitAnnotation = PdfHyperlinkSupport.findHitHyperlink(
                                            links = links,
                                            normalizedX = normalizedPoint.first,
                                            normalizedY = normalizedPoint.second
                                        )
                                        break
                                    }
                                }

                                if (hitAnnotation != null) {
                                    currentOnLinkTap(hitAnnotation)
                                } else {
                                    currentOnSingleTap()
                                }
                            }
                        }
                    )
                }
                .pointerInput(scale, readingDirection, maxX, maxY) {
                    if (scale > 1f) {
                        detectPassiveCrossAxisPan(readingDirection) { delta ->
                            if (readingDirection == ReadingDirection.Vertical) {
                                val targetX = offset.x + delta.x
                                val clampedX = targetX.coerceIn(-maxX, maxX)
                                if (!clampedX.isNaN() && !clampedX.isInfinite()) {
                                    offset = Offset(clampedX, offset.y)
                                }
                            } else {
                                val targetY = offset.y + delta.y
                                val clampedY = targetY.coerceIn(-maxY, maxY)
                                if (!clampedY.isNaN() && !clampedY.isInfinite()) {
                                    offset = Offset(offset.x, clampedY)
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectPinchZoomGestures { centroid, zoomChange ->
                        if (zoomChange.isNaN() || zoomChange.isInfinite() || !centroid.isSpecified) {
                            return@detectPinchZoomGestures
                        }

                        zoomAnimationJob?.cancel()

                        val oldScale = scale
                        val targetScale = (scale * zoomChange).coerceIn(minScale, maxScale)
                        if (targetScale.isNaN() || targetScale.isInfinite()) return@detectPinchZoomGestures

                        scale = targetScale

                        val zoomFactor = scale / oldScale
                        if (zoomFactor.isNaN() || zoomFactor.isInfinite()) return@detectPinchZoomGestures

                        val newOffsetX = offset.x * zoomFactor - (centroid.x - viewerWidth / 2f) * (zoomFactor - 1f)
                        val newOffsetY = offset.y * zoomFactor - (centroid.y - viewerHeight / 2f) * (zoomFactor - 1f)

                        if (newOffsetX.isNaN() || newOffsetX.isInfinite() ||
                            newOffsetY.isNaN() || newOffsetY.isInfinite()) {
                            return@detectPinchZoomGestures
                        }

                        val limitX = ((viewerWidth * scale) - viewerWidth) / 2f
                        val limitY = ((viewerHeight * scale) - viewerHeight) / 2f

                        offset = Offset(
                            newOffsetX.coerceIn(-limitX, limitX),
                            newOffsetY.coerceIn(-limitY, limitY)
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            if (readingDirection == ReadingDirection.Vertical) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 80.dp)
                ) {
                    items(pageCount) { index ->
                        ContinuousPdfPage(
                            index = index,
                            viewModel = viewModel,
                            backgroundMode = backgroundMode,
                            containerWidthDp = containerWidthDp,
                            containerHeightDp = containerHeightDp,
                            isVertical = true,
                            onSingleTap = onSingleTap
                        )
                    }
                }
            } else {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 80.dp)
                ) {
                    items(pageCount) { index ->
                        ContinuousPdfPage(
                            index = index,
                            viewModel = viewModel,
                            backgroundMode = backgroundMode,
                            containerWidthDp = containerWidthDp,
                            containerHeightDp = containerHeightDp,
                            isVertical = false,
                            onSingleTap = onSingleTap
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContinuousPdfPage(
    index: Int,
    viewModel: PdfReaderViewModel,
    backgroundMode: BackgroundMode,
    containerWidthDp: androidx.compose.ui.unit.Dp,
    containerHeightDp: androidx.compose.ui.unit.Dp,
    isVertical: Boolean,
    onSingleTap: () -> Unit
) {
    var pageRatio by remember(index) { mutableFloatStateOf(viewModel.getCachedPageRatio(index) ?: 0.707f) }

    LaunchedEffect(index) {
        val size = viewModel.getPageSize(index)
        if (size != null && size.second != 0) {
            pageRatio = size.first.toFloat() / size.second.toFloat()
        }
    }

    val pageModifier = if (isVertical) {
        Modifier
            .fillMaxWidth()
            .height(containerWidthDp / pageRatio)
    } else {
        Modifier
            .fillMaxHeight()
            .width(containerHeightDp * pageRatio)
    }

    Box(
        modifier = pageModifier,
        contentAlignment = Alignment.Center
    ) {
        var imageModel by remember { mutableStateOf<Any?>(null) }

        val targetWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            if (isVertical) containerWidthDp.roundToPx() else (containerHeightDp * pageRatio).roundToPx()
        }

        LaunchedEffect(index, targetWidthPx, backgroundMode) {
            if (targetWidthPx > 0) {
                imageModel = viewModel.getPageBitmap(index, targetWidthPx)
            }
        }

        val colorFilter = when (backgroundMode) {
            BackgroundMode.Invert -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f,   0f
                ))
            )
            BackgroundMode.EyeComfort -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    0.988f, 0f,  0f, 0f, 0f,
                    0f, 0.913f, 0f, 0f, 0f,
                    0f,  0f, 0.823f, 0f, 0f,
                    0f,  0f,  0f, 1f, 0f
                ))
            )
            BackgroundMode.Paper -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    0.957f, 0f,  0f, 0f, 0f,
                    0f, 0.925f, 0f, 0f, 0f,
                    0f,  0f, 0.847f, 0f, 0f,
                    0f,  0f,  0f, 1f, 0f
                ))
            )
            else -> null
        }

        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Page ",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (backgroundMode == BackgroundMode.Invert) Color.Black else Color.White)
            )
        }
    }
}
