package com.stutool.stutool.ui.screens.scanner.utils

import android.graphics.PointF
import com.stutool.stutool.ui.screens.scanner.models.CropHandle
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Represents a quadrilateral defined by 4 corner points in image coordinate space.
 * Points should be ordered: topLeft → topRight → bottomRight → bottomLeft (clockwise).
 */
data class Quad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    /**
     * Returns the estimated output width based on the longer of the top/bottom edges.
     * Returns at least 100f to prevent zero-size bitmaps.
     */
    fun estimatedWidth(): Float {
        val topWidth = distance(topLeft, topRight)
        val bottomWidth = distance(bottomLeft, bottomRight)
        return maxOf(topWidth, bottomWidth).coerceAtLeast(100f)
    }

    /**
     * Returns the estimated output height based on the longer of the left/right edges.
     * Returns at least 100f to prevent zero-size bitmaps.
     */
    fun estimatedHeight(): Float {
        val leftHeight = distance(topLeft, bottomLeft)
        val rightHeight = distance(topRight, bottomRight)
        return maxOf(leftHeight, rightHeight).coerceAtLeast(100f)
    }

    /**
     * Returns true if all four corners contain valid (finite, non-NaN) coordinates.
     */
    fun isValid(): Boolean {
        return allPoints().all { p ->
            p.x.isFinite() && p.y.isFinite()
        }
    }

    /**
     * Returns true if the quad forms a convex polygon with non-trivial area.
     */
    fun isConvex(): Boolean {
        val pts = allPoints()
        if (pts.any { !it.x.isFinite() || !it.y.isFinite() }) return false

        // Cross-product sign check for convexity
        var positive = 0
        var negative = 0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross > 0) positive++ else if (cross < 0) negative++
        }
        return positive == 0 || negative == 0
    }

    /**
     * Strict boundary and geometry validation during crop dragging.
     * Prevents inverted quads, crossing lines, and collapsed edges.
     */
    fun isPerspectiveGeometryValid(imageWidth: Int, imageHeight: Int, minSize: Float): Boolean {
        try {
            val pts = allPoints()
            val tolerance = 0.5f // Float precision tolerance
            
            // 0. Sanitize coordinates
            if (pts.any { !it.x.isFinite() || !it.y.isFinite() || it.x.isNaN() || it.y.isNaN() }) {
                return false
            }

            // 1. Hard Boundary Constraints: must be fully inside bounds
            if (pts.any { it.x < -tolerance || it.y < -tolerance || 
                          it.x > imageWidth + tolerance || it.y > imageHeight + tolerance }) {
                return false
            }

            // 2. Convexity: no crossed lines or inverted quads
            if (!isConvex()) return false
            
            // 3. Minimum dimensions: prevent collapsing edges
            if (distance(topLeft, topRight) < minSize) return false
            if (distance(topRight, bottomRight) < minSize) return false
            if (distance(bottomRight, bottomLeft) < minSize) return false
            if (distance(bottomLeft, topLeft) < minSize) return false
            
            // 4. Area: Prevent zero or negative area
            val quadArea = area()
            if (!quadArea.isFinite() || quadArea.isNaN() || quadArea < 10f) return false
            
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Strict boundary and geometry validation for Rectangle mode.
     * Enforces axis-alignment, min size, and viewport containment.
     */
    fun isRectangleGeometryValid(imageWidth: Int, imageHeight: Int, minSize: Float): Boolean {
        try {
            val pts = allPoints()
            val tolerance = 0.5f

            if (pts.any { !it.x.isFinite() || !it.y.isFinite() || it.x.isNaN() || it.y.isNaN() }) {
                return false
            }

            if (pts.any { it.x < -tolerance || it.y < -tolerance || 
                          it.x > imageWidth + tolerance || it.y > imageHeight + tolerance }) {
                return false
            }

            // Axis alignment
            if (abs(topLeft.y - topRight.y) > tolerance) return false
            if (abs(bottomLeft.y - bottomRight.y) > tolerance) return false
            if (abs(topLeft.x - bottomLeft.x) > tolerance) return false
            if (abs(topRight.x - bottomRight.x) > tolerance) return false

            // Width/Height checks
            val w = topRight.x - topLeft.x
            val h = bottomLeft.y - topLeft.y
            if (w < minSize || h < minSize) return false

            val quadArea = area()
            if (!quadArea.isFinite() || quadArea.isNaN() || quadArea < 10f) return false

            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Semantic handle mappings
     */
    fun getPoint(handle: CropHandle): PointF {
        return when (handle) {
            CropHandle.TOP_LEFT -> topLeft
            CropHandle.TOP_RIGHT -> topRight
            CropHandle.BOTTOM_RIGHT -> bottomRight
            CropHandle.BOTTOM_LEFT -> bottomLeft
            CropHandle.TOP -> PointF((topLeft.x + topRight.x) / 2f, (topLeft.y + topRight.y) / 2f)
            CropHandle.RIGHT -> PointF((topRight.x + bottomRight.x) / 2f, (topRight.y + bottomRight.y) / 2f)
            CropHandle.BOTTOM -> PointF((bottomLeft.x + bottomRight.x) / 2f, (bottomLeft.y + bottomRight.y) / 2f)
            CropHandle.LEFT -> PointF((topLeft.x + bottomLeft.x) / 2f, (topLeft.y + bottomLeft.y) / 2f)
        }
    }

    fun getEdgeCorners(handle: CropHandle): Pair<CropHandle, CropHandle> {
        return when (handle) {
            CropHandle.TOP -> Pair(CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT)
            CropHandle.RIGHT -> Pair(CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT)
            CropHandle.BOTTOM -> Pair(CropHandle.BOTTOM_RIGHT, CropHandle.BOTTOM_LEFT)
            CropHandle.LEFT -> Pair(CropHandle.BOTTOM_LEFT, CropHandle.TOP_LEFT)
            else -> Pair(CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT) // Should not happen
        }
    }

    fun updateCorner(handle: CropHandle, point: PointF): Quad {
        return when (handle) {
            CropHandle.TOP_LEFT -> copy(topLeft = point)
            CropHandle.TOP_RIGHT -> copy(topRight = point)
            CropHandle.BOTTOM_RIGHT -> copy(bottomRight = point)
            CropHandle.BOTTOM_LEFT -> copy(bottomLeft = point)
            else -> this
        }
    }

    fun updateCornerRectangular(handle: CropHandle, point: PointF, minSize: Float): Quad {
        return when (handle) {
            CropHandle.TOP_LEFT -> {
                val x = minOf(point.x, topRight.x - minSize)
                val y = minOf(point.y, bottomLeft.y - minSize)
                copy(
                    topLeft = PointF(x, y),
                    bottomLeft = PointF(x, bottomLeft.y),
                    topRight = PointF(topRight.x, y)
                )
            }
            CropHandle.TOP_RIGHT -> {
                val x = maxOf(point.x, topLeft.x + minSize)
                val y = minOf(point.y, bottomRight.y - minSize)
                copy(
                    topRight = PointF(x, y),
                    bottomRight = PointF(x, bottomRight.y),
                    topLeft = PointF(topLeft.x, y)
                )
            }
            CropHandle.BOTTOM_RIGHT -> {
                val x = maxOf(point.x, bottomLeft.x + minSize)
                val y = maxOf(point.y, topRight.y + minSize)
                copy(
                    bottomRight = PointF(x, y),
                    topRight = PointF(x, topRight.y),
                    bottomLeft = PointF(bottomLeft.x, y)
                )
            }
            CropHandle.BOTTOM_LEFT -> {
                val x = minOf(point.x, bottomRight.x - minSize)
                val y = maxOf(point.y, topLeft.y + minSize)
                copy(
                    bottomLeft = PointF(x, y),
                    topLeft = PointF(x, topLeft.y),
                    bottomRight = PointF(bottomRight.x, y)
                )
            }
            else -> this
        }
    }

    fun updateEdgeRectangular(handle: CropHandle, pointA: PointF, pointB: PointF, minSize: Float): Quad {
        return when (handle) {
            CropHandle.TOP -> {
                val y = minOf(pointA.y, bottomLeft.y - minSize)
                copy(topLeft = PointF(topLeft.x, y), topRight = PointF(topRight.x, y))
            }
            CropHandle.RIGHT -> {
                val x = maxOf(pointA.x, topLeft.x + minSize)
                copy(topRight = PointF(x, topRight.y), bottomRight = PointF(x, bottomRight.y))
            }
            CropHandle.BOTTOM -> {
                val y = maxOf(pointB.y, topLeft.y + minSize)
                copy(bottomLeft = PointF(bottomLeft.x, y), bottomRight = PointF(bottomRight.x, y))
            }
            CropHandle.LEFT -> {
                val x = minOf(pointA.x, topRight.x - minSize)
                copy(topLeft = PointF(x, topLeft.y), bottomLeft = PointF(x, bottomLeft.y))
            }
            else -> this
        }
    }

    /**
     * Snaps this quad to its axis-aligned bounding rectangle.
     * Used when switching from Perspective to Rectangle crop mode.
     */
    fun snapToRectangle(): Quad {
        val pts = allPoints()
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        return Quad(
            topLeft = PointF(minX, minY),
            topRight = PointF(maxX, minY),
            bottomRight = PointF(maxX, maxY),
            bottomLeft = PointF(minX, maxY)
        )
    }

    /**
     * Returns the area of this quad using the shoelace formula.
     */
    fun area(): Float {
        val pts = allPoints()
        var area = 0f
        for (i in pts.indices) {
            val j = (i + 1) % 4
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return abs(area) / 2f
    }

    /**
     * Returns a copy of this quad with all points clamped within image bounds.
     */
    fun clampToImage(imageWidth: Int, imageHeight: Int): Quad {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        fun clamp(p: PointF) = PointF(
            p.x.coerceIn(0f, w),
            p.y.coerceIn(0f, h)
        )
        return Quad(
            clamp(topLeft), clamp(topRight),
            clamp(bottomRight), clamp(bottomLeft)
        )
    }

    fun sanitize(imageWidth: Int, imageHeight: Int): Quad {
        if (!isValid()) return defaultForImage(imageWidth, imageHeight)
        val clamped = clampToImage(imageWidth, imageHeight)
        if (clamped.area() < 100f || !clamped.isConvex()) return defaultForImage(imageWidth, imageHeight)
        return clamped
    }

    /**
     * Rotates the quad by the specified degrees (90, 180, 270) clockwise.
     * Note: [imageWidth] and [imageHeight] must be the dimensions of the image
     * BEFORE the rotation is applied.
     */
    fun rotate(degrees: Int, imageWidth: Int, imageHeight: Int): Quad {
        val normalizedDegrees = ((degrees % 360) + 360) % 360
        if (normalizedDegrees == 0) return this

        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()

        fun rotatePoint(p: PointF): PointF {
            return when (normalizedDegrees) {
                90 -> PointF(h - p.y, p.x)
                180 -> PointF(w - p.x, h - p.y)
                270 -> PointF(p.y, w - p.x)
                else -> PointF(p.x, p.y)
            }
        }

        val pTL = rotatePoint(topLeft)
        val pTR = rotatePoint(topRight)
        val pBR = rotatePoint(bottomRight)
        val pBL = rotatePoint(bottomLeft)

        return when (normalizedDegrees) {
            90 -> Quad(topLeft = pBL, topRight = pTL, bottomRight = pTR, bottomLeft = pBR)
            180 -> Quad(topLeft = pBR, topRight = pBL, bottomRight = pTL, bottomLeft = pTR)
            270 -> Quad(topLeft = pTR, topRight = pBR, bottomRight = pBL, bottomLeft = pTL)
            else -> this
        }
    }

    private fun allPoints() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        fun distance(a: PointF, b: PointF): Float {
            val dx = b.x - a.x
            val dy = b.y - a.y
            return sqrt(dx * dx + dy * dy)
        }

        /**
         * Creates a default quad at 2% inset from image edges.
         * Reduced from 5% to allow near-edge cropping while preserving handle accessibility.
         */
        fun defaultForImage(width: Int, height: Int): Quad {
            val insetX = 0f
            val insetY = 0f
            return Quad(
                topLeft = PointF(insetX, insetY),
                topRight = PointF(width - insetX, insetY),
                bottomRight = PointF(width - insetX, height - insetY),
                bottomLeft = PointF(insetX, height - insetY)
            )
        }
    }
}
