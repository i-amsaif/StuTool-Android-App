package com.stutool.stutool.ui.screens.scanner.utils

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import com.stutool.stutool.ui.screens.scanner.DisplayMapping
import com.stutool.stutool.ui.screens.scanner.imageToScreen
import com.stutool.stutool.ui.screens.scanner.screenToImage
import com.stutool.stutool.ui.screens.scanner.models.CropHandle
import com.stutool.stutool.ui.screens.scanner.models.CropMode
import kotlin.math.abs

/**
 * Shared constraint engine for both Perspective and Rectangle crop modes.
 * Ensures strict geometry bounds and safe viewport edge clamping.
 */
object CropConstraintEngine {

    /**
     * Given an offset, clamps it strictly within the logical safe bounds of the viewport.
     */
    fun clampToViewport(
        screenPos: Offset,
        mapping: DisplayMapping,
        safeInsetPx: Float
    ): Offset {
        val safeMinX = mapping.offsetX + safeInsetPx
        val safeMaxX = mapping.offsetX + mapping.displayWidth - safeInsetPx
        val safeMinY = mapping.offsetY + safeInsetPx
        val safeMaxY = mapping.offsetY + mapping.displayHeight - safeInsetPx

        val effMinX = minOf(safeMinX, safeMaxX)
        val effMaxX = maxOf(safeMinX, safeMaxX)
        val effMinY = minOf(safeMinY, safeMaxY)
        val effMaxY = maxOf(safeMinY, safeMaxY)

        return Offset(
            screenPos.x.coerceIn(effMinX, effMaxX),
            screenPos.y.coerceIn(effMinY, effMaxY)
        )
    }

    /**
     * Computes maximum safe translation across X and Y for an edge (two corners moving together).
     */
    private fun getSafeEdgeDelta(
        dragAmount: Offset,
        screenA: Offset,
        screenB: Offset,
        mapping: DisplayMapping,
        safeInsetPx: Float
    ): Offset {
        val safeMinX = mapping.offsetX + safeInsetPx
        val safeMaxX = mapping.offsetX + mapping.displayWidth - safeInsetPx
        val safeMinY = mapping.offsetY + safeInsetPx
        val safeMaxY = mapping.offsetY + mapping.displayHeight - safeInsetPx

        val minDx = maxOf(safeMinX - screenA.x, safeMinX - screenB.x)
        val maxDx = minOf(safeMaxX - screenA.x, safeMaxX - screenB.x)
        val minDy = maxOf(safeMinY - screenA.y, safeMinY - screenB.y)
        val maxDy = minOf(safeMaxY - screenA.y, safeMaxY - screenB.y)

        val safeDx = if (minDx <= maxDx) dragAmount.x.coerceIn(minDx, maxDx) else 0f
        val safeDy = if (minDy <= maxDy) dragAmount.y.coerceIn(minDy, maxDy) else 0f

        return Offset(safeDx, safeDy)
    }

    /**
     * Primary drag orchestrator. Modifies the quad securely.
     */
    fun onDrag(
        quad: Quad,
        handle: CropHandle,
        dragAmount: Offset,
        mapping: DisplayMapping,
        imageWidth: Int,
        imageHeight: Int,
        cropMode: CropMode,
        safeInsetPx: Float
    ): Quad {
        val minSize = minOf(imageWidth, imageHeight) * 0.05f

        return if (handle.isCorner) {
            handleCornerDrag(quad, handle, dragAmount, mapping, imageWidth, imageHeight, cropMode, safeInsetPx, minSize)
        } else {
            handleEdgeDrag(quad, handle, dragAmount, mapping, imageWidth, imageHeight, cropMode, safeInsetPx, minSize)
        }
    }

    private fun handleCornerDrag(
        quad: Quad,
        handle: CropHandle,
        dragAmount: Offset,
        mapping: DisplayMapping,
        imageWidth: Int,
        imageHeight: Int,
        cropMode: CropMode,
        safeInsetPx: Float,
        minSize: Float
    ): Quad {
        val currentImagePoint = quad.getPoint(handle)
        val currentScreen = imageToScreen(currentImagePoint, mapping)
        
        val newScreenRaw = Offset(currentScreen.x + dragAmount.x, currentScreen.y + dragAmount.y)
        val clampedScreen = clampToViewport(newScreenRaw, mapping, safeInsetPx)
        val targetImagePoint = screenToImage(clampedScreen, mapping, imageWidth, imageHeight)

        if (cropMode == CropMode.RECTANGULAR) {
            // Live Constraint: Apply Rectangle Rules
            val newQuad = quad.updateCornerRectangular(handle, targetImagePoint, minSize)
            return if (newQuad.isRectangleGeometryValid(imageWidth, imageHeight, minSize)) newQuad else quad
        } else {
            // Perspective Drag with Soft-Wall
            val tempQuad = quad.updateCorner(handle, targetImagePoint)
            if (tempQuad.isPerspectiveGeometryValid(imageWidth, imageHeight, minSize)) {
                return tempQuad
            }
            
            // Soft-wall X axis
            val tempQuadX = quad.updateCorner(handle, PointF(targetImagePoint.x, currentImagePoint.y))
            if (tempQuadX.isPerspectiveGeometryValid(imageWidth, imageHeight, minSize)) {
                return tempQuadX
            }

            // Soft-wall Y axis
            val tempQuadY = quad.updateCorner(handle, PointF(currentImagePoint.x, targetImagePoint.y))
            if (tempQuadY.isPerspectiveGeometryValid(imageWidth, imageHeight, minSize)) {
                return tempQuadY
            }

            return quad // Reject if completely blocked
        }
    }

    private fun handleEdgeDrag(
        quad: Quad,
        handle: CropHandle,
        dragAmount: Offset,
        mapping: DisplayMapping,
        imageWidth: Int,
        imageHeight: Int,
        cropMode: CropMode,
        safeInsetPx: Float,
        minSize: Float
    ): Quad {
        val (cornerA, cornerB) = quad.getEdgeCorners(handle)
        val imgA = quad.getPoint(cornerA)
        val imgB = quad.getPoint(cornerB)

        val screenA = imageToScreen(imgA, mapping)
        val screenB = imageToScreen(imgB, mapping)

        val safeDelta = getSafeEdgeDelta(dragAmount, screenA, screenB, mapping, safeInsetPx)
        
        val newImgA = screenToImage(Offset(screenA.x + safeDelta.x, screenA.y + safeDelta.y), mapping, imageWidth, imageHeight)
        val newImgB = screenToImage(Offset(screenB.x + safeDelta.x, screenB.y + safeDelta.y), mapping, imageWidth, imageHeight)

        if (cropMode == CropMode.RECTANGULAR) {
            // Live Constraint: Rectangle Edges only translate along their perpendicular axis
            val newQuad = quad.updateEdgeRectangular(handle, newImgA, newImgB, minSize)
            return if (newQuad.isRectangleGeometryValid(imageWidth, imageHeight, minSize)) newQuad else quad
        } else {
            // Perspective Edge Drag
            val tempQuad = quad.updateCorner(cornerA, newImgA).updateCorner(cornerB, newImgB)
            if (tempQuad.isPerspectiveGeometryValid(imageWidth, imageHeight, minSize)) {
                return tempQuad
            }

            // Soft-wall X axis
            val tempQuadX = quad.updateCorner(cornerA, PointF(newImgA.x, imgA.y)).updateCorner(cornerB, PointF(newImgB.x, imgB.y))
            if (tempQuadX.isPerspectiveGeometryValid(imageWidth, imageHeight, minSize)) {
                return tempQuadX
            }

            // Soft-wall Y axis
            val tempQuadY = quad.updateCorner(cornerA, PointF(imgA.x, newImgA.y)).updateCorner(cornerB, PointF(imgB.x, newImgB.y))
            if (tempQuadY.isPerspectiveGeometryValid(imageWidth, imageHeight, minSize)) {
                return tempQuadY
            }

            return quad
        }
    }
}
