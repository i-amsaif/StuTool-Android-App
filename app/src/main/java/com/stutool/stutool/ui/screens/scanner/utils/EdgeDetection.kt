package com.stutool.stutool.ui.screens.scanner.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detection result with confidence score.
 * confidence: 0.0–1.0
 *   > 0.8 → reliable, auto-apply
 *   0.5–0.8 → marginal, show with hint
 *   < 0.5 → fallback rectangle
 */
data class DetectionResult(
    val quad: Quad?,
    val confidence: Float,
    val fallbackUsed: Boolean
)

/**
 * Document edge detection engine with contour scoring and confidence.
 *
 * Pipeline (all on ~250–300px downscaled image):
 * 1. Grayscale
 * 2. Gaussian blur (3×3)
 * 3. Sobel gradient magnitude
 * 4. Morphological cleanup (dilate then erode)
 * 5. Adaptive threshold
 * 6. Border scan with contour scoring
 * 7. Quad validation (convexity, skew, area)
 * 8. Confidence calculation
 */
object EdgeDetection {

    private const val TARGET_SIZE = 350 // Downscale longest edge (300–500px range)

    // ── Public API: Bitmap ──────────────────────────────────────────────

    fun detectDocumentEdges(bitmap: Bitmap): Quad {
        val result = detectWithConfidence(bitmap)
        return result.quad ?: Quad.defaultForImage(bitmap.width, bitmap.height)
    }

    fun detectWithConfidence(bitmap: Bitmap): DetectionResult {
        val w = bitmap.width
        val h = bitmap.height
        val scale = TARGET_SIZE.toFloat() / max(w, h)

        val smallW = (w * scale).toInt().coerceAtLeast(10)
        val smallH = (h * scale).toInt().coerceAtLeast(10)

        android.util.Log.d("EdgeDetection", "detectWithConfidence: source=${w}x${h}, analysis=${smallW}x${smallH}")

        val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)

        try {
            val gray = toGrayscale(small, smallW, smallH)
            // Local contrast normalization to handle shadows/low-contrast
            val normalized = localContrastNormalize(gray, smallW, smallH)
            val result = runPipeline(normalized, smallW, smallH, scale, w, h)
            if (result != null) {
                // Apply safe outward expansion to prevent tight clipping
                val expandedQuad = expandQuadSafely(result.quad!!, w, h)
                android.util.Log.d("EdgeDetection", "Detection success: confidence=${result.confidence}, fallback=false")
                return DetectionResult(expandedQuad, result.confidence, false)
            }
        } catch (e: Exception) {
            android.util.Log.e("EdgeDetection", "Detection failed", e)
        } finally {
            small.recycle()
        }

        android.util.Log.d("EdgeDetection", "Detection fallback: using default rectangle")
        return DetectionResult(Quad.defaultForImage(w, h), 0f, true)
    }

    private fun expandQuadSafely(quad: Quad, w: Int, h: Int): Quad {
        // Expand outward by ~1.5% of max dimension to create safe margins
        val pad = max(w, h) * 0.015f
        
        // Find center
        val cx = (quad.topLeft.x + quad.topRight.x + quad.bottomRight.x + quad.bottomLeft.x) / 4f
        val cy = (quad.topLeft.y + quad.topRight.y + quad.bottomRight.y + quad.bottomLeft.y) / 4f

        fun pushOut(p: PointF): PointF {
            val vx = p.x - cx
            val vy = p.y - cy
            val dist = sqrt(vx * vx + vy * vy)
            if (dist < 1f) return p
            val scale = (dist + pad) / dist
            return PointF(cx + vx * scale, cy + vy * scale)
        }

        return Quad(
            pushOut(quad.topLeft),
            pushOut(quad.topRight),
            pushOut(quad.bottomRight),
            pushOut(quad.bottomLeft)
        ).sanitize(w, h)
    }

    // ── Public API: ImageProxy (real-time, Y-plane only) ────────────────

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun detectDocumentEdges(image: androidx.camera.core.ImageProxy): Quad {
        val w = image.width
        val h = image.height

        val targetSize = 200
        val scale = targetSize.toFloat() / max(w, h)
        val smallW = (w * scale).toInt().coerceAtLeast(10)
        val smallH = (h * scale).toInt().coerceAtLeast(10)

        val stepX = w.toFloat() / smallW
        val stepY = h.toFloat() / smallH

        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val gray = IntArray(smallW * smallH)

        try {
            for (sy in 0 until smallH) {
                val y = (sy * stepY).toInt().coerceAtMost(h - 1)
                for (sx in 0 until smallW) {
                    val x = (sx * stepX).toInt().coerceAtMost(w - 1)
                    val idx = y * rowStride + x * pixelStride
                    gray[sy * smallW + sx] = buffer.get(idx).toInt() and 0xFF
                }
            }

            val result = runPipeline(gray, smallW, smallH, scale, w, h)
            if (result != null && result.quad != null) {
                return expandQuadSafely(result.quad, w, h)
            }
        } catch (e: Exception) {
            android.util.Log.e("EdgeDetection", "ImageProxy detection failed", e)
        } finally {
            image.close()
        }

        return Quad.defaultForImage(w, h)
    }

    // ── Core Pipeline ───────────────────────────────────────────────────

    private fun runPipeline(
        gray: IntArray, smallW: Int, smallH: Int,
        scale: Float, origW: Int, origH: Int
    ): DetectionResult? {
        // Step 1: 5×5 Gaussian blur for stronger noise reduction
        val blurred = gaussianBlur5x5(gray, smallW, smallH)

        // Step 2: Sobel gradient magnitude
        val edges = sobelMagnitude(blurred, smallW, smallH)

        // Step 3: Morphological cleanup (dilate then erode to close edge gaps)
        val dilated = dilate3x3(edges, smallW, smallH)
        val cleaned = erode3x3(dilated, smallW, smallH)

        // Step 4: Adaptive threshold
        val threshold = computeAdaptiveThreshold(cleaned)

        // Step 5: Find document corners with scoring
        val scoredResult = findScoredCorners(cleaned, smallW, smallH, threshold) ?: return null

        val corners = scoredResult.first
        val confidence = scoredResult.second

        // Scale back to original coordinates
        val invScale = 1f / scale
        val quad = Quad(
            topLeft = PointF(corners[0].x * invScale, corners[0].y * invScale),
            topRight = PointF(corners[1].x * invScale, corners[1].y * invScale),
            bottomRight = PointF(corners[2].x * invScale, corners[2].y * invScale),
            bottomLeft = PointF(corners[3].x * invScale, corners[3].y * invScale)
        )

        // Final validation: ensure quad is within original image
        val safeQuad = quad.sanitize(origW, origH)

        return DetectionResult(safeQuad, confidence, false)
    }

    // ── Morphological Operations ────────────────────────────────────────

    private fun dilate3x3(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var maxVal = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = src[(y + dy) * w + (x + dx)]
                        if (v > maxVal) maxVal = v
                    }
                }
                out[y * w + x] = maxVal
            }
        }
        return out
    }

    private fun erode3x3(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var minVal = 255
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = src[(y + dy) * w + (x + dx)]
                        if (v < minVal) minVal = v
                    }
                }
                out[y * w + x] = minVal
            }
        }
        return out
    }

    // ── Corner Finding with Contour Scoring ─────────────────────────────

    private fun findScoredCorners(
        edges: IntArray, w: Int, h: Int, threshold: Int
    ): Pair<Array<PointF>, Float>? {
        val margin = (min(w, h) * 0.03f).toInt().coerceAtLeast(2)

        // Scan borders inward to find edge points
        val topPoints = scanBorderDown(edges, w, h, margin, threshold)
        val bottomPoints = scanBorderUp(edges, w, h, margin, threshold)
        val leftPoints = scanBorderRight(edges, w, h, margin, threshold)
        val rightPoints = scanBorderLeft(edges, w, h, margin, threshold)

        val minPoints = 3
        if (topPoints.size < minPoints || bottomPoints.size < minPoints ||
            leftPoints.size < minPoints || rightPoints.size < minPoints
        ) {
            return null
        }

        // Fit lines
        val topLine = fitLineMedian(topPoints) ?: return null
        val bottomLine = fitLineMedian(bottomPoints) ?: return null
        val leftLine = fitLineVerticalMedian(leftPoints) ?: return null
        val rightLine = fitLineVerticalMedian(rightPoints) ?: return null

        // Find 4 corner intersections
        val tl = intersectHV(topLine, leftLine) ?: return null
        val tr = intersectHV(topLine, rightLine) ?: return null
        val br = intersectHV(bottomLine, rightLine) ?: return null
        val bl = intersectHV(bottomLine, leftLine) ?: return null

        // Validate bounds
        val tolerance = max(w, h) * 0.15f
        for (p in arrayOf(tl, tr, br, bl)) {
            if (p.x < -tolerance || p.x > w + tolerance ||
                p.y < -tolerance || p.y > h + tolerance
            ) return null
            p.x = p.x.coerceIn(0f, w.toFloat())
            p.y = p.y.coerceIn(0f, h.toFloat())
        }

        val corners = arrayOf(tl, tr, br, bl)

        // ── Scoring ─────────────────────────────────────────────────
        val imageArea = w.toFloat() * h
        val area = quadArea(tl, tr, br, bl)

        // 1. Area score: best when covering 40-90% of image
        val areaRatio = area / imageArea
        if (areaRatio < 0.10f || areaRatio > 0.99f) return null
        val areaScore = when {
            areaRatio in 0.3f..0.9f -> 1f
            areaRatio < 0.3f -> areaRatio / 0.3f
            else -> (1f - areaRatio) / 0.1f
        }.coerceIn(0f, 1f)

        // 2. Rectangularity score: how close angles are to 90°
        val angles = computeAngles(corners)
        if (angles.any { it < 30f || it > 150f }) return null // Reject extreme skew
        val angleDeviation = angles.map { abs(it - 90f) }.average().toFloat()
        val rectScore = (1f - angleDeviation / 60f).coerceIn(0f, 1f)

        // 3. Convexity: reject self-intersecting
        if (!isConvex(corners)) return null

        // 4. Edge point consistency: how many points were found vs expected
        val expectedPoints = (w - 2 * margin) / 2
        val edgeConsistency = min(
            topPoints.size.toFloat() / expectedPoints,
            min(
                bottomPoints.size.toFloat() / expectedPoints,
                min(
                    leftPoints.size.toFloat() / (h - 2 * margin) * 2,
                    rightPoints.size.toFloat() / (h - 2 * margin) * 2
                )
            )
        ).coerceIn(0f, 1f)

        // 5. Aspect ratio score: prefer document-like proportions (A4 ~1:1.41, Letter ~1:1.29)
        val quadW = (Quad.distance(tl, tr) + Quad.distance(bl, br)) / 2f
        val quadH = (Quad.distance(tl, bl) + Quad.distance(tr, br)) / 2f
        val aspectRatio = if (quadW > 0f && quadH > 0f) max(quadW, quadH) / min(quadW, quadH) else 1f
        // Documents typically have aspect ratios between 1.0 and 2.0
        val aspectScore = when {
            aspectRatio in 1.0f..2.0f -> 1f
            aspectRatio in 2.0f..3.0f -> (3f - aspectRatio) / 1f
            else -> 0.3f
        }.coerceIn(0f, 1f)

        // Weighted confidence (edge continuity + rectangularity emphasized)
        val confidence = (areaScore * 0.20f + rectScore * 0.30f + edgeConsistency * 0.35f + aspectScore * 0.15f).coerceIn(0f, 1f)

        android.util.Log.d("EdgeDetection", "Scoring: area=${"%,.2f".format(areaRatio)} rect=${"%,.2f".format(rectScore)} edge=${"%,.2f".format(edgeConsistency)} aspect=${"%,.2f".format(aspectScore)} → confidence=${"%,.2f".format(confidence)}")

        return corners to confidence
    }

    // ── Border scanning helpers ─────────────────────────────────────────

    private fun scanBorderDown(edges: IntArray, w: Int, h: Int, margin: Int, threshold: Int): List<PointF> {
        val points = mutableListOf<PointF>()
        for (x in margin until w - margin step 2) {
            for (y in margin until h / 2) {
                if (edges[y * w + x] >= threshold) {
                    points.add(PointF(x.toFloat(), y.toFloat()))
                    break
                }
            }
        }
        return points
    }

    private fun scanBorderUp(edges: IntArray, w: Int, h: Int, margin: Int, threshold: Int): List<PointF> {
        val points = mutableListOf<PointF>()
        for (x in margin until w - margin step 2) {
            for (y in h - margin - 1 downTo h / 2) {
                if (edges[y * w + x] >= threshold) {
                    points.add(PointF(x.toFloat(), y.toFloat()))
                    break
                }
            }
        }
        return points
    }

    private fun scanBorderRight(edges: IntArray, w: Int, h: Int, margin: Int, threshold: Int): List<PointF> {
        val points = mutableListOf<PointF>()
        for (y in margin until h - margin step 2) {
            for (x in margin until w / 2) {
                if (edges[y * w + x] >= threshold) {
                    points.add(PointF(x.toFloat(), y.toFloat()))
                    break
                }
            }
        }
        return points
    }

    private fun scanBorderLeft(edges: IntArray, w: Int, h: Int, margin: Int, threshold: Int): List<PointF> {
        val points = mutableListOf<PointF>()
        for (y in margin until h - margin step 2) {
            for (x in w - margin - 1 downTo w / 2) {
                if (edges[y * w + x] >= threshold) {
                    points.add(PointF(x.toFloat(), y.toFloat()))
                    break
                }
            }
        }
        return points
    }

    // ── Angle computation ───────────────────────────────────────────────

    private fun computeAngles(corners: Array<PointF>): FloatArray {
        val angles = FloatArray(4)
        for (i in 0 until 4) {
            val prev = corners[(i + 3) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]

            val v1x = prev.x - curr.x
            val v1y = prev.y - curr.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = sqrt(v1x * v1x + v1y * v1y)
            val mag2 = sqrt(v2x * v2x + v2y * v2y)

            if (mag1 < 1e-6f || mag2 < 1e-6f) {
                angles[i] = 0f
            } else {
                val cosAngle = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
                angles[i] = Math.toDegrees(kotlin.math.acos(cosAngle).toDouble()).toFloat()
            }
        }
        return angles
    }

    // ── Convexity check ─────────────────────────────────────────────────

    private fun isConvex(corners: Array<PointF>): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            val c = corners[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) {
                if (sign != 0 && s != sign) return false
                sign = s
            }
        }
        return true
    }

    // ── Grayscale conversion ────────────────────────────────────────────

    private fun toGrayscale(bitmap: Bitmap, w: Int, h: Int): IntArray {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = ((Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000)
        }
        return gray
    }

    // ── 3×3 Gaussian blur ───────────────────────────────────────────────

    private fun gaussianBlur3x3(gray: IntArray, w: Int, h: Int): IntArray {
        val kernel = intArrayOf(1, 2, 1, 2, 4, 2, 1, 2, 1) // sum = 16
        val out = IntArray(w * h)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sum = 0
                var ki = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        sum += gray[(y + dy) * w + (x + dx)] * kernel[ki]
                        ki++
                    }
                }
                out[y * w + x] = sum / 16
            }
        }
        return out
    }

    // ── 5×5 Gaussian blur (stronger noise reduction) ────────────────────

    private fun gaussianBlur5x5(gray: IntArray, w: Int, h: Int): IntArray {
        // 5×5 Gaussian kernel, sum = 256
        val kernel = intArrayOf(
            1,  4,  6,  4, 1,
            4, 16, 24, 16, 4,
            6, 24, 36, 24, 6,
            4, 16, 24, 16, 4,
            1,  4,  6,  4, 1
        )
        val out = IntArray(w * h)
        // Copy border pixels unchanged
        for (i in gray.indices) out[i] = gray[i]

        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                var sum = 0
                var ki = 0
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        sum += gray[(y + dy) * w + (x + dx)] * kernel[ki]
                        ki++
                    }
                }
                out[y * w + x] = sum / 256
            }
        }
        return out
    }

    // ── Local contrast normalization (lightweight CLAHE-like) ───────────

    private fun localContrastNormalize(gray: IntArray, w: Int, h: Int): IntArray {
        // Block-based local normalization: stretch each tile's histogram
        val blockSize = 32
        val out = IntArray(w * h)
        for (i in gray.indices) out[i] = gray[i]

        for (by in 0 until h step blockSize) {
            for (bx in 0 until w step blockSize) {
                val endX = min(bx + blockSize, w)
                val endY = min(by + blockSize, h)

                // Find local min/max
                var localMin = 255
                var localMax = 0
                for (y in by until endY) {
                    for (x in bx until endX) {
                        val v = gray[y * w + x]
                        if (v < localMin) localMin = v
                        if (v > localMax) localMax = v
                    }
                }

                val range = localMax - localMin
                if (range < 20) continue // Skip flat regions (no useful contrast)

                // Stretch contrast within this block
                for (y in by until endY) {
                    for (x in bx until endX) {
                        val v = gray[y * w + x]
                        out[y * w + x] = ((v - localMin) * 255 / range).coerceIn(0, 255)
                    }
                }
            }
        }
        return out
    }

    // ── Sobel gradient magnitude ────────────────────────────────────────

    private fun sobelMagnitude(gray: IntArray, w: Int, h: Int): IntArray {
        val mag = IntArray(w * h)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x

                val gx = -gray[(y - 1) * w + (x - 1)] + gray[(y - 1) * w + (x + 1)] +
                        -2 * gray[y * w + (x - 1)] + 2 * gray[y * w + (x + 1)] +
                        -gray[(y + 1) * w + (x - 1)] + gray[(y + 1) * w + (x + 1)]

                val gy = -gray[(y - 1) * w + (x - 1)] - 2 * gray[(y - 1) * w + x] - gray[(y - 1) * w + (x + 1)] +
                        gray[(y + 1) * w + (x - 1)] + 2 * gray[(y + 1) * w + x] + gray[(y + 1) * w + (x + 1)]

                mag[idx] = (abs(gx) + abs(gy)).coerceAtMost(255)
            }
        }
        return mag
    }

    // ── Adaptive threshold (Otsu-like) ──────────────────────────────────

    private fun computeAdaptiveThreshold(edges: IntArray): Int {
        val histogram = IntArray(256)
        for (v in edges) {
            histogram[v.coerceIn(0, 255)]++
        }

        val totalPixels = edges.size
        
        // Primary pass: top 10% of edge magnitudes
        val targetCount10 = (totalPixels * 0.10).toInt()
        var count = 0
        for (t in 255 downTo 1) {
            count += histogram[t]
            if (count >= targetCount10) {
                // If threshold is too aggressive (>200), try softer pass for low-contrast docs
                if (t > 200) {
                    // Fallback pass: top 15%
                    var count15 = 0
                    for (t2 in 255 downTo 1) {
                        count15 += histogram[t2]
                        if (count15 >= (totalPixels * 0.15).toInt()) return t2
                    }
                }
                return t
            }
        }
        return 30
    }

    // ── Line fitting ────────────────────────────────────────────────────

    private data class HLine(val m: Float, val b: Float)

    private fun fitLineMedian(points: List<PointF>): HLine? {
        if (points.size < 3) return null
        val sorted = points.sortedBy { it.x }

        val slopes = mutableListOf<Float>()
        val step = (sorted.size / 5).coerceAtLeast(1)
        for (i in 0 until sorted.size - step step step) {
            val dx = sorted[i + step].x - sorted[i].x
            if (abs(dx) > 1f) {
                slopes.add((sorted[i + step].y - sorted[i].y) / dx)
            }
        }
        if (slopes.isEmpty()) return null

        slopes.sort()
        val m = slopes[slopes.size / 2]

        val intercepts = sorted.map { it.y - m * it.x }
        val sortedIntercepts = intercepts.sorted()
        val b = sortedIntercepts[sortedIntercepts.size / 2]

        return HLine(m, b)
    }

    private data class VLine(val m: Float, val b: Float)

    private fun fitLineVerticalMedian(points: List<PointF>): VLine? {
        if (points.size < 3) return null
        val sorted = points.sortedBy { it.y }

        val slopes = mutableListOf<Float>()
        val step = (sorted.size / 5).coerceAtLeast(1)
        for (i in 0 until sorted.size - step step step) {
            val dy = sorted[i + step].y - sorted[i].y
            if (abs(dy) > 1f) {
                slopes.add((sorted[i + step].x - sorted[i].x) / dy)
            }
        }
        if (slopes.isEmpty()) return null

        slopes.sort()
        val m = slopes[slopes.size / 2]

        val intercepts = sorted.map { it.x - m * it.y }
        val sortedIntercepts = intercepts.sorted()
        val b = sortedIntercepts[sortedIntercepts.size / 2]

        return VLine(m, b)
    }

    // ── Intersection ────────────────────────────────────────────────────

    private fun intersectHV(hLine: HLine, vLine: VLine): PointF? {
        val denom = 1f - hLine.m * vLine.m
        if (abs(denom) < 1e-6f) return null

        val y = (hLine.m * vLine.b + hLine.b) / denom
        val x = vLine.m * y + vLine.b
        return PointF(x, y)
    }

    // ── Quad area (shoelace) ────────────────────────────────────────────

    private fun quadArea(tl: PointF, tr: PointF, br: PointF, bl: PointF): Float {
        val pts = listOf(tl, tr, br, bl)
        var area = 0f
        for (i in pts.indices) {
            val j = (i + 1) % pts.size
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return abs(area) / 2f
    }
}
