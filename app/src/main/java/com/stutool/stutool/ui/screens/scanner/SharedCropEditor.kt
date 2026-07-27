package com.stutool.stutool.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stutool.stutool.R
import com.stutool.stutool.ui.screens.scanner.models.CropMode
import com.stutool.stutool.ui.screens.scanner.utils.CropConstraintEngine
import com.stutool.stutool.ui.screens.scanner.models.CropHandle
import com.stutool.stutool.ui.screens.scanner.utils.Quad
import com.stutool.stutool.ui.theme.ScannerTheme
import com.stutool.stutool.ui.screens.scanner.ScannerTokens

data class DisplayMapping(
    val offsetX: Float,
    val offsetY: Float,
    val displayWidth: Float,
    val displayHeight: Float,
    val scaleX: Float,
    val scaleY: Float
)

fun calculateDisplayMapping(viewSize: IntSize, imgW: Int, imgH: Int): DisplayMapping {
    val viewW = viewSize.width.toFloat()
    val viewH = viewSize.height.toFloat()
    val imageAspect = imgW.toFloat() / imgH
    val viewAspect = viewW / viewH

    val (displayW, displayH) = if (imageAspect > viewAspect) {
        viewW to (viewW / imageAspect)
    } else {
        (viewH * imageAspect) to viewH
    }

    return DisplayMapping(
        offsetX = (viewW - displayW) / 2f,
        offsetY = (viewH - displayH) / 2f,
        displayWidth = displayW,
        displayHeight = displayH,
        scaleX = displayW / imgW,
        scaleY = displayH / imgH
    )
}

fun imageToScreen(point: PointF, mapping: DisplayMapping): Offset {
    return Offset(
        x = mapping.offsetX + point.x * mapping.scaleX,
        y = mapping.offsetY + point.y * mapping.scaleY
    )
}

fun screenToImage(offset: Offset, mapping: DisplayMapping, imgW: Int, imgH: Int): PointF {
    return PointF(
        ((offset.x - mapping.offsetX) / mapping.scaleX).coerceIn(0f, imgW.toFloat()),
        ((offset.y - mapping.offsetY) / mapping.scaleY).coerceIn(0f, imgH.toFloat())
    )
}

@Composable
fun SharedCropEditor(
    bitmap: Bitmap,
    imageWidth: Int,
    imageHeight: Int,
    rotation: Int,
    cropMode: CropMode,
    quad: Quad,
    isActivePage: Boolean = true,
    onQuadUpdated: (Quad) -> Unit,
    expandTrigger: Int = 0,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val isRotated90 = rotation == 90 || rotation == 270
    val logicalImgW = if (isRotated90) imageHeight else imageWidth
    val logicalImgH = if (isRotated90) imageWidth else imageHeight

    LaunchedEffect(quad) {
        android.util.Log.e("CropTrace_LOAD", "[D] SharedCropEditor first composition / quad update")
        android.util.Log.e("CropTrace_LOAD", "received quad: $quad")
    }

    // Snap quad to rectangle when switching to RECTANGULAR mode (local to this editor)
    LaunchedEffect(cropMode) {
        if (cropMode == CropMode.RECTANGULAR) {
            val snapped = quad.snapToRectangle()
            if (snapped != quad) {
                onQuadUpdated(snapped)
            }
        }
    }

    // Handle full screen expand
    LaunchedEffect(expandTrigger) {
        if (expandTrigger > 0 && viewSize.width > 0) {
            val mapping = calculateDisplayMapping(viewSize, logicalImgW, logicalImgH)
            
            val safeInsetPx = with(density) { 6.dp.toPx() }
            val safeMinX = mapping.offsetX + safeInsetPx
            val safeMaxX = mapping.offsetX + mapping.displayWidth - safeInsetPx
            val safeMinY = mapping.offsetY + safeInsetPx
            val safeMaxY = mapping.offsetY + mapping.displayHeight - safeInsetPx
            
            val effMinX = minOf(safeMinX, safeMaxX)
            val effMaxX = maxOf(safeMinX, safeMaxX)
            val effMinY = minOf(safeMinY, safeMaxY)
            val effMaxY = maxOf(safeMinY, safeMaxY)
            
            val tl = screenToImage(Offset(effMinX, effMinY), mapping, logicalImgW, logicalImgH)
            val tr = screenToImage(Offset(effMaxX, effMinY), mapping, logicalImgW, logicalImgH)
            val br = screenToImage(Offset(effMaxX, effMaxY), mapping, logicalImgW, logicalImgH)
            val bl = screenToImage(Offset(effMinX, effMaxY), mapping, logicalImgW, logicalImgH)
            
            val newLogicalQuad = Quad(tl, tr, br, bl)
            val originalQuad = newLogicalQuad.rotate((360 - rotation) % 360, logicalImgW, logicalImgH)
            
            onQuadUpdated(originalQuad)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { viewSize = it.size },
        contentAlignment = Alignment.Center
    ) {
        if (viewSize.width > 0) {
            val mapping = calculateDisplayMapping(viewSize, logicalImgW, logicalImgH)
            
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val unrotatedDisplayW = if (isRotated90) mapping.displayHeight else mapping.displayWidth
                    val unrotatedDisplayH = if (isRotated90) mapping.displayWidth else mapping.displayHeight
                    val viewCenterX = mapping.offsetX + mapping.displayWidth / 2f
                    val viewCenterY = mapping.offsetY + mapping.displayHeight / 2f
                    val dstLeft = viewCenterX - unrotatedDisplayW / 2f
                    val dstTop = viewCenterY - unrotatedDisplayH / 2f

                    withTransform({
                        rotate(rotation.toFloat(), Offset(viewCenterX, viewCenterY))
                    }) {
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            bitmap,
                            null,
                            android.graphics.RectF(
                                dstLeft, dstTop,
                                dstLeft + unrotatedDisplayW,
                                dstTop + unrotatedDisplayH
                            ),
                            null
                        )
                    }
                }
                
                val logicalQuad = remember(quad, rotation, imageWidth, imageHeight) {
                    quad.rotate(rotation, imageWidth, imageHeight)
                }
                
                if (isActivePage) {
                    QuadOverlay(
                        activeQuad = logicalQuad,
                        mapping = mapping,
                        imageWidth = logicalImgW,
                        imageHeight = logicalImgH,
                        bitmap = bitmap,
                        activeRotation = rotation,
                        cropMode = cropMode,
                        onQuadUpdated = { updatedLogicalQuad ->
                            // Rotate the logical quad back to base coordinates before saving
                            val baseQuad = updatedLogicalQuad.rotate(-rotation, logicalImgW, logicalImgH)
                            onQuadUpdated(baseQuad)
                        },
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd
                    )
                }
            }
        }
    }
}

@Composable
fun QuadOverlay(
    activeQuad: Quad,
    mapping: DisplayMapping,
    imageWidth: Int,
    imageHeight: Int,
    bitmap: android.graphics.Bitmap?,
    activeRotation: Int,
    cropMode: CropMode,
    onQuadUpdated: (Quad) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val currentActiveQuad by rememberUpdatedState(activeQuad)
    val currentMappingState by rememberUpdatedState(mapping)
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }

    val currentOnQuadUpdated by rememberUpdatedState(onQuadUpdated)
    
    val density = LocalDensity.current
    val touchRadiusPx = remember(density) { with(density) { 48.dp.toPx() } }
    
    val tlScreenRaw = imageToScreen(activeQuad.topLeft, mapping)
    val trScreenRaw = imageToScreen(activeQuad.topRight, mapping)
    val brScreenRaw = imageToScreen(activeQuad.bottomRight, mapping)
    val blScreenRaw = imageToScreen(activeQuad.bottomLeft, mapping)
    
    val animSpec = tween<Offset>(durationMillis = if (activeHandle != null) 0 else 300)
    
    val tlScreen by animateOffsetAsState(targetValue = tlScreenRaw, animationSpec = animSpec, label = "tlAnim")
    val trScreen by animateOffsetAsState(targetValue = trScreenRaw, animationSpec = animSpec, label = "trAnim")
    val brScreen by animateOffsetAsState(targetValue = brScreenRaw, animationSpec = animSpec, label = "brAnim")
    val blScreen by animateOffsetAsState(targetValue = blScreenRaw, animationSpec = animSpec, label = "blAnim")
    
    val topMidScreen = Offset((tlScreen.x + trScreen.x) / 2f, (tlScreen.y + trScreen.y) / 2f)
    val rightMidScreen = Offset((trScreen.x + brScreen.x) / 2f, (trScreen.y + brScreen.y) / 2f)
    val bottomMidScreen = Offset((brScreen.x + blScreen.x) / 2f, (brScreen.y + blScreen.y) / 2f)
    val leftMidScreen = Offset((blScreen.x + tlScreen.x) / 2f, (blScreen.y + tlScreen.y) / 2f)

    val accentColor = ScannerTheme.colors.scannerActionPrimary.copy(alpha = 0.8f)
    val activeColor = ScannerTheme.colors.scannerActionPrimary
    val fillColor = ScannerTheme.colors.scannerOverlaySurface.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageWidth, imageHeight, cropMode) {
                awaitPointerEventScope {
                    while (true) {
                        var down: androidx.compose.ui.input.pointer.PointerInputChange? = null
                        while (down == null) {
                            val event = awaitPointerEvent()
                            down = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                        }
                        val touch = down.position
                        
                        val currentMapping = currentMappingState
                        val tl = imageToScreen(currentActiveQuad.topLeft, currentMapping)
                        val tr = imageToScreen(currentActiveQuad.topRight, currentMapping)
                        val br = imageToScreen(currentActiveQuad.bottomRight, currentMapping)
                        val bl = imageToScreen(currentActiveQuad.bottomLeft, currentMapping)
                        
                        val topMid = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
                        val rightMid = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
                        val bottomMid = Offset((br.x + bl.x) / 2f, (br.y + bl.y) / 2f)
                        val leftMid = Offset((bl.x + tl.x) / 2f, (bl.y + tl.y) / 2f)

                        var closestDist = Float.MAX_VALUE
                        var closestHandle: CropHandle? = null
                        
                        fun checkDist(handle: CropHandle, sc: Offset) {
                            val dist = kotlin.math.sqrt(
                                ((touch.x - sc.x) * (touch.x - sc.x) + 
                                (touch.y - sc.y) * (touch.y - sc.y)).toDouble()
                            ).toFloat()
                            if (dist < touchRadiusPx && dist < closestDist) {
                                closestDist = dist
                                closestHandle = handle
                            }
                        }
                        
                        checkDist(CropHandle.TOP_LEFT, tl)
                        checkDist(CropHandle.TOP_RIGHT, tr)
                        checkDist(CropHandle.BOTTOM_RIGHT, br)
                        checkDist(CropHandle.BOTTOM_LEFT, bl)
                        
                        if (cropMode == CropMode.PERSPECTIVE) {
                            checkDist(CropHandle.TOP, topMid)
                            checkDist(CropHandle.RIGHT, rightMid)
                            checkDist(CropHandle.BOTTOM, bottomMid)
                            checkDist(CropHandle.LEFT, leftMid)
                        }

                        if (closestHandle != null) {
                            down.consume()
                            activeHandle = closestHandle
                            touchPosition = down.position
                            onDragStart()
                            
                            var isDragging = true
                            while (isDragging) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    isDragging = false
                                } else {
                                    change.consume()
                                    val dragAmount = change.position - change.previousPosition
                                    touchPosition = change.position
                                    
                                    val safeInsetPx = with(density) { 6.dp.toPx() }
                                    val dragMapping = currentMappingState
                                    
                                    val newQuad = CropConstraintEngine.onDrag(
                                        quad = currentActiveQuad,
                                        handle = activeHandle!!,
                                        dragAmount = dragAmount,
                                        mapping = dragMapping,
                                        imageWidth = imageWidth,
                                        imageHeight = imageHeight,
                                        cropMode = cropMode,
                                        safeInsetPx = safeInsetPx
                                    )
                                    currentOnQuadUpdated(newQuad)
                                }
                            }
                            activeHandle = null
                            touchPosition = null
                            onDragEnd()
                        }
                    }
                }
            }
    ) {
        val scannerTextPrimary = ScannerTheme.colors.scannerTextPrimary
        val scannerCropOverlay = ScannerTheme.colors.scannerCropOverlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(tlScreen.x, tlScreen.y)
                lineTo(trScreen.x, trScreen.y)
                lineTo(brScreen.x, brScreen.y)
                lineTo(blScreen.x, blScreen.y)
                close()
            }

            val dstLeft = mapping.offsetX
            val dstTop = mapping.offsetY
            val displayW = mapping.displayWidth
            val displayH = mapping.displayHeight

            clipPath(path = path, clipOp = ClipOp.Difference) {
                drawRect(
                    color = scannerCropOverlay,
                    topLeft = Offset(dstLeft, dstTop),
                    size = Size(displayW, displayH)
                )
            }

            drawPath(
                path,
                if (activeHandle != null) activeColor else accentColor,
                style = Stroke(width = if (activeHandle != null) 4f else 3f)
            )

            fun drawEdgeHandle(handle: CropHandle, sc: Offset) {
                val isActive = handle == activeHandle
                val color = if (isActive) activeColor else accentColor
                val size = if (isActive) 22.dp.toPx() else 14.dp.toPx()
                val rectLeft = sc.x - size / 2
                val rectTop = sc.y - size / 2
                
                drawRect(
                    color = Color.Black.copy(alpha = 0.4f),
                    topLeft = Offset(rectLeft - 2f, rectTop - 2f),
                    size = androidx.compose.ui.geometry.Size(size + 4f, size + 4f),
                    style = Stroke(width = if (isActive) 9f else 7f)
                )

                drawRect(
                    color = color,
                    topLeft = Offset(rectLeft, rectTop),
                    size = androidx.compose.ui.geometry.Size(size, size),
                    style = Stroke(width = if (isActive) 5f else 3f)
                )
                drawRect(
                    color = color.copy(alpha = if (isActive) 0.6f else 0.4f),
                    topLeft = Offset(rectLeft + 2f, rectTop + 2f),
                    size = androidx.compose.ui.geometry.Size(size - 4f, size - 4f)
                )
                drawRect(
                    color = scannerTextPrimary,
                    topLeft = Offset(sc.x - 3f, sc.y - 3f),
                    size = androidx.compose.ui.geometry.Size(6f, 6f)
                )
            }
            
            if (cropMode == CropMode.PERSPECTIVE) {
                drawEdgeHandle(CropHandle.TOP, topMidScreen)
                drawEdgeHandle(CropHandle.RIGHT, rightMidScreen)
                drawEdgeHandle(CropHandle.BOTTOM, bottomMidScreen)
                drawEdgeHandle(CropHandle.LEFT, leftMidScreen)
            }

            fun drawCornerHandle(handle: CropHandle, sc: Offset) {
                val isActive = handle == activeHandle
                val color = if (isActive) activeColor else accentColor
                val radius = if (isActive) 18.dp.toPx() else 14.dp.toPx()
                
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = radius + 2f,
                    center = sc,
                    style = Stroke(width = if (isActive) 9f else 7f)
                )

                drawCircle(
                    color = color,
                    radius = radius,
                    center = sc,
                    style = Stroke(width = if (isActive) 5f else 3f)
                )
                drawCircle(
                    color = color.copy(alpha = if (isActive) 0.6f else 0.4f),
                    radius = radius - 2f,
                    center = sc
                )
                drawCircle(
                    color = scannerTextPrimary,
                    radius = if (isActive) 6f else 4f,
                    center = sc
                )
            }
            
            drawCornerHandle(CropHandle.TOP_LEFT, tlScreen)
            drawCornerHandle(CropHandle.TOP_RIGHT, trScreen)
            drawCornerHandle(CropHandle.BOTTOM_RIGHT, brScreen)
            drawCornerHandle(CropHandle.BOTTOM_LEFT, blScreen)
            
            val activeNodeScreen = when (activeHandle) {
                CropHandle.TOP_LEFT -> tlScreen
                CropHandle.TOP_RIGHT -> trScreen
                CropHandle.BOTTOM_RIGHT -> brScreen
                CropHandle.BOTTOM_LEFT -> blScreen
                else -> null
            }
            
            if (activeNodeScreen != null && touchPosition != null && bitmap != null && cropMode == CropMode.PERSPECTIVE) {
                val tp = touchPosition!!
                val loupeRadius = 60.dp.toPx()
                val defaultLoupeOffset = 100.dp.toPx()
                
                val safeX = tp.x.coerceIn(loupeRadius, size.width - loupeRadius)
                
                val safeY = if (tp.y - defaultLoupeOffset - loupeRadius < 0) {
                    tp.y + defaultLoupeOffset
                } else {
                    tp.y - defaultLoupeOffset
                }
                
                val loupeCenter = Offset(safeX, safeY)
                
                drawCircle(
                    color = scannerTextPrimary,
                    radius = loupeRadius + 3.dp.toPx(),
                    center = loupeCenter
                )
                drawCircle(
                    color = accentColor,
                    radius = loupeRadius + 1.dp.toPx(),
                    center = loupeCenter
                )
                
                withTransform({
                    val clipPath = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(
                            left = loupeCenter.x - loupeRadius,
                            top = loupeCenter.y - loupeRadius,
                            right = loupeCenter.x + loupeRadius,
                            bottom = loupeCenter.y + loupeRadius
                        ))
                    }
                    clipPath(clipPath)
                    val zoom = 2.0f
                    translate(loupeCenter.x - activeNodeScreen.x, loupeCenter.y - activeNodeScreen.y)
                    scale(zoom, zoom, activeNodeScreen)
                }) {
                    drawRect(scannerCropOverlay)
                    
                    val isRotated90 = activeRotation == 90 || activeRotation == 270
                    val unrotatedDisplayW = if (isRotated90) mapping.displayHeight else mapping.displayWidth
                    val unrotatedDisplayH = if (isRotated90) mapping.displayWidth else mapping.displayHeight
                    val viewCenterX = mapping.offsetX + mapping.displayWidth / 2f
                    val viewCenterY = mapping.offsetY + mapping.displayHeight / 2f
                    val dstLeft = viewCenterX - unrotatedDisplayW / 2f
                    val dstTop = viewCenterY - unrotatedDisplayH / 2f

                    withTransform({
                        rotate(activeRotation.toFloat(), Offset(viewCenterX, viewCenterY))
                    }) {
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            bitmap,
                            null,
                            android.graphics.RectF(
                                dstLeft, dstTop,
                                dstLeft + unrotatedDisplayW,
                                dstTop + unrotatedDisplayH
                            ),
                            null
                        )
                    }
                }
                
                drawCircle(
                    color = scannerTextPrimary,
                    radius = 2.dp.toPx(),
                    center = loupeCenter
                )
                
                drawLine(
                    color = accentColor,
                    start = Offset(loupeCenter.x - 6.dp.toPx(), loupeCenter.y),
                    end = Offset(loupeCenter.x + 6.dp.toPx(), loupeCenter.y),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = accentColor,
                    start = Offset(loupeCenter.x, loupeCenter.y - 6.dp.toPx()),
                    end = Offset(loupeCenter.x, loupeCenter.y + 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}
