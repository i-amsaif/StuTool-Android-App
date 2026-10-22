package com.stutool.stutool.ui.screens.viewer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import java.net.URI
import java.util.Locale
import kotlin.math.hypot

/**
 * Normalized rectangle in `[0f, 1f]` page coordinates where `(0f, 0f)` is the
 * rendered top-left corner and `(1f, 1f)` is the rendered bottom-right corner
 * (after page rotation has been applied).
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height

    fun contains(u: Float, v: Float, toleranceU: Float = 0f, toleranceV: Float = 0f): Boolean {
        return u >= (left - toleranceU) &&
            u <= (right + toleranceU) &&
            v >= (top - toleranceV) &&
            v <= (bottom + toleranceV)
    }

    fun distanceSquaredToCenter(u: Float, v: Float): Float {
        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f
        val dx = u - cx
        val dy = v - cy
        return dx * dx + dy * dy
    }
}

/**
 * Represents a validated, safe hyperlink destination extracted from a PDF annotation.
 */
data class ValidatedPdfUrl(
    val originalUrl: String,
    val resolvedUrl: String,
    val scheme: String
)

/**
 * Represents an interactive hyperlink annotation on a specific PDF page.
 */
data class PdfLinkAnnotation(
    val pageIndex: Int,
    val rawUrl: String,
    val resolvedUrl: String,
    val normalizedBounds: NormalizedRect
)

object PdfHyperlinkSupport {

    private val BLOCKED_SCHEMES = setOf(
        "javascript",
        "vbscript",
        "data",
        "file",
        "content",
        "intent",
        "about",
        "blob",
        "ftp",
        "jar",
        "ws",
        "wss",
        "chrome",
        "market"
    )

    private val ALLOWED_SCHEMES = setOf(
        "https",
        "http",
        "mailto",
        "tel"
    )

    /**
     * Validates and normalizes a raw URI string from a PDF annotation.
     *
     * - Supports `https://`, `http://`, `mailto:`, and `tel:` schemes.
     * - Normalizes bare `www.example.com` strings to `https://www.example.com`.
     * - Strictly rejects `javascript:`, `file:`, `content:`, `data:`, `intent:`,
     *   malformed URLs, control characters, and unsupported schemes.
     */
    fun validateAndNormalizePdfUrl(rawUrl: String?): ValidatedPdfUrl? {
        if (rawUrl == null) return null

        // Strip leading/trailing whitespace and ASCII control characters
        val trimmed = rawUrl.trim { it <= ' ' }
        if (trimmed.isEmpty()) return null

        // Reject embedded control characters or newlines/tabs
        if (trimmed.any { it.code < 0x20 || it.code == 0x7F }) {
            return null
        }

        // Normalize bare "www." links to https://
        val candidate = if (
            trimmed.startsWith("www.", ignoreCase = true) &&
            !trimmed.contains("://") &&
            !trimmed.contains(":")
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val colonIndex = candidate.indexOf(':')
        if (colonIndex <= 0) return null

        val scheme = candidate.substring(0, colonIndex).lowercase(Locale.US)
        if (scheme in BLOCKED_SCHEMES || scheme !in ALLOWED_SCHEMES) {
            return null
        }

        val schemeSpecific = candidate.substring(colonIndex + 1)
        if (schemeSpecific.isBlank()) return null

        return when (scheme) {
            "http", "https" -> {
                if (!schemeSpecific.startsWith("//")) return null
                val afterSlashes = schemeSpecific.removePrefix("//")
                if (afterSlashes.isBlank()) return null

                val hostEnd = afterSlashes.indexOfAny(charArrayOf('/', '?', '#'))
                val authority = if (hostEnd >= 0) afterSlashes.substring(0, hostEnd) else afterSlashes
                val host = authority.substringAfter('@').substringBefore(':')
                if (host.isBlank() || host.startsWith(".") || host.endsWith(".") || host.contains("..")) {
                    return null
                }
                // Reject whitespace anywhere in the host
                if (host.any { it.isWhitespace() }) return null

                // Normalize scheme prefix to lowercase while preserving path/query case
                val normalized = "$scheme:${candidate.substring(colonIndex + 1)}"
                ValidatedPdfUrl(
                    originalUrl = trimmed,
                    resolvedUrl = normalized,
                    scheme = scheme
                )
            }

            "mailto" -> {
                if (schemeSpecific.startsWith("//")) return null
                val addressPart = schemeSpecific.substringBefore('?').trim()
                if (addressPart.isBlank() || !addressPart.contains('@')) return null
                val localPart = addressPart.substringBefore('@')
                val domainPart = addressPart.substringAfter('@')
                if (localPart.isBlank() || domainPart.isBlank() || !domainPart.contains('.')) {
                    return null
                }
                ValidatedPdfUrl(
                    originalUrl = trimmed,
                    resolvedUrl = "mailto:${candidate.substring(colonIndex + 1)}",
                    scheme = "mailto"
                )
            }

            "tel" -> {
                if (schemeSpecific.startsWith("//")) return null
                val phonePart = schemeSpecific.substringBefore('?').trim()
                if (phonePart.none { it.isDigit() }) return null
                ValidatedPdfUrl(
                    originalUrl = trimmed,
                    resolvedUrl = "tel:$phonePart",
                    scheme = "tel"
                )
            }

            else -> null
        }
    }

    /**
     * Converts a PDF annotation rectangle `[rectLeft, rectBottom, rectRight, rectTop]`
     * in unrotated PDF user space into a top-left origin [NormalizedRect] (`[0f..1f]`)
     * on the rendered page, accounting for cropBox offset and page rotation (`0`, `90`, `180`, `270`).
     */
    fun mapPdfRectToNormalizedPageRect(
        rectLeft: Float,
        rectBottom: Float,
        rectRight: Float,
        rectTop: Float,
        cropLeft: Float,
        cropBottom: Float,
        cropWidth: Float,
        cropHeight: Float,
        rotationDegrees: Int
    ): NormalizedRect? {
        if (cropWidth <= 0f || cropHeight <= 0f) return null
        if (rectLeft.isNaN() || rectBottom.isNaN() || rectRight.isNaN() || rectTop.isNaN()) return null

        val minX = minOf(rectLeft, rectRight)
        val maxX = maxOf(rectLeft, rectRight)
        val minY = minOf(rectBottom, rectTop)
        val maxY = maxOf(rectBottom, rectTop)

        if (maxX <= minX || maxY <= minY) return null

        val rx0 = minX - cropLeft
        val rx1 = maxX - cropLeft
        val ry0 = minY - cropBottom
        val ry1 = maxY - cropBottom

        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360

        val (rawLeft, rawTop, rawRight, rawBottom) = when (normalizedRotation) {
            90 -> floatArrayOf(
                ry0 / cropHeight,
                rx0 / cropWidth,
                ry1 / cropHeight,
                rx1 / cropWidth
            )
            180 -> floatArrayOf(
                (cropWidth - rx1) / cropWidth,
                ry0 / cropHeight,
                (cropWidth - rx0) / cropWidth,
                ry1 / cropHeight
            )
            270 -> floatArrayOf(
                (cropHeight - ry1) / cropHeight,
                (cropWidth - rx1) / cropWidth,
                (cropHeight - ry0) / cropHeight,
                (cropWidth - rx0) / cropWidth
            )
            else -> floatArrayOf(
                rx0 / cropWidth,
                (cropHeight - ry1) / cropHeight,
                rx1 / cropWidth,
                (cropHeight - ry0) / cropHeight
            )
        }

        val clampedLeft = rawLeft.coerceIn(0f, 1f)
        val clampedTop = rawTop.coerceIn(0f, 1f)
        val clampedRight = rawRight.coerceIn(0f, 1f)
        val clampedBottom = rawBottom.coerceIn(0f, 1f)

        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return null

        return NormalizedRect(
            left = clampedLeft,
            top = clampedTop,
            right = clampedRight,
            bottom = clampedBottom
        )
    }

    /**
     * Maps a screen-space tap inside `ZoomablePdfPage` (`0..viewerWidth`, `0..viewerHeight`)
     * to normalized rendered page coordinates `(u, v)` in `[0f..1f]`, accounting for
     * fitted page size (`actualWidth x actualHeight`), zoom `scale`, and pan `(offsetX, offsetY)`.
     *
     * Returns `null` if the tap landed outside the rendered page bounds.
     */
    fun mapZoomablePageTapToNormalizedPoint(
        tapX: Float,
        tapY: Float,
        viewerWidth: Float,
        viewerHeight: Float,
        actualWidth: Float,
        actualHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Float, Float>? {
        if (viewerWidth <= 0f || viewerHeight <= 0f ||
            actualWidth <= 0f || actualHeight <= 0f ||
            scale <= 0f
        ) {
            return null
        }

        val localX = (tapX - viewerWidth / 2f - offsetX) / scale
        val localY = (tapY - viewerHeight / 2f - offsetY) / scale

        val u = localX / actualWidth + 0.5f
        val v = localY / actualHeight + 0.5f

        if (u.isNaN() || v.isNaN() || u !in 0f..1f || v !in 0f..1f) {
            return null
        }
        return u to v
    }

    /**
     * Maps a screen-space tap inside `ContinuousPdfViewer` (`0..viewerWidth`, `0..viewerHeight`)
     * to normalized rendered page coordinates `(u, v)` in `[0f..1f]` for a visible list item.
     */
    fun mapContinuousViewportTapToNormalizedPoint(
        tapX: Float,
        tapY: Float,
        viewerWidth: Float,
        viewerHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        isVertical: Boolean,
        viewportStartOffset: Int,
        itemOffset: Int,
        itemSize: Int
    ): Pair<Float, Float>? {
        if (viewerWidth <= 0f || viewerHeight <= 0f || scale <= 0f || itemSize <= 0) {
            return null
        }

        val viewportX = (tapX - viewerWidth / 2f - offsetX) / scale + viewerWidth / 2f
        val viewportY = (tapY - viewerHeight / 2f - offsetY) / scale + viewerHeight / 2f

        val itemStartInBox = (itemOffset - viewportStartOffset).toFloat()

        val u: Float
        val v: Float
        if (isVertical) {
            u = viewportX / viewerWidth
            v = (viewportY - itemStartInBox) / itemSize.toFloat()
        } else {
            u = (viewportX - itemStartInBox) / itemSize.toFloat()
            v = viewportY / viewerHeight
        }

        if (u.isNaN() || v.isNaN() || u !in 0f..1f || v !in 0f..1f) {
            return null
        }
        return u to v
    }

    /**
     * Finds the hyperlink annotation hit by `(normalizedX, normalizedY)` on a page.
     * Checks exact containment first; if none matches, checks with a small touch tolerance.
     */
    fun findHitHyperlink(
        links: List<PdfLinkAnnotation>,
        normalizedX: Float,
        normalizedY: Float,
        toleranceU: Float = 0.006f,
        toleranceV: Float = 0.005f
    ): PdfLinkAnnotation? {
        if (links.isEmpty()) return null
        if (normalizedX !in 0f..1f || normalizedY !in 0f..1f) return null

        val exactMatches = links.filter { it.normalizedBounds.contains(normalizedX, normalizedY) }
        if (exactMatches.isNotEmpty()) {
            return exactMatches.minByOrNull { it.normalizedBounds.area }
        }

        val tolerantMatches = links.filter {
            it.normalizedBounds.contains(normalizedX, normalizedY, toleranceU, toleranceV)
        }
        if (tolerantMatches.isNotEmpty()) {
            return tolerantMatches.minByOrNull {
                it.normalizedBounds.distanceSquaredToCenter(normalizedX, normalizedY)
            }
        }

        return null
    }

    /**
     * Extracts all valid, actionable external hyperlink annotations from a PDFBox [PDPage].
     */
    fun extractPageHyperlinks(page: PDPage, pageIndex: Int): List<PdfLinkAnnotation> {
        val cropBox = page.cropBox ?: page.mediaBox ?: return emptyList()
        val cropLeft = cropBox.lowerLeftX
        val cropBottom = cropBox.lowerLeftY
        val cropWidth = cropBox.width
        val cropHeight = cropBox.height
        if (cropWidth <= 0f || cropHeight <= 0f) return emptyList()

        val rotation = page.rotation
        val annotations = try {
            page.annotations
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val results = mutableListOf<PdfLinkAnnotation>()

        for (annotation in annotations) {
            val linkAnnotation = annotation as? PDAnnotationLink ?: continue

            val rawUri = extractRawUriFromAnnotation(linkAnnotation) ?: continue
            val validated = validateAndNormalizePdfUrl(rawUri) ?: continue

            var addedFromQuads = false
            val quadPoints = try {
                linkAnnotation.quadPoints
            } catch (_: Exception) {
                null
            }

            if (quadPoints != null && quadPoints.size >= 8 && quadPoints.size % 8 == 0) {
                var i = 0
                while (i + 7 < quadPoints.size) {
                    val x1 = quadPoints[i]
                    val y1 = quadPoints[i + 1]
                    val x2 = quadPoints[i + 2]
                    val y2 = quadPoints[i + 3]
                    val x3 = quadPoints[i + 4]
                    val y3 = quadPoints[i + 5]
                    val x4 = quadPoints[i + 6]
                    val y4 = quadPoints[i + 7]

                    val qLeft = minOf(x1, x2, x3, x4)
                    val qRight = maxOf(x1, x2, x3, x4)
                    val qBottom = minOf(y1, y2, y3, y4)
                    val qTop = maxOf(y1, y2, y3, y4)

                    val normalizedRect = mapPdfRectToNormalizedPageRect(
                        rectLeft = qLeft,
                        rectBottom = qBottom,
                        rectRight = qRight,
                        rectTop = qTop,
                        cropLeft = cropLeft,
                        cropBottom = cropBottom,
                        cropWidth = cropWidth,
                        cropHeight = cropHeight,
                        rotationDegrees = rotation
                    )
                    if (normalizedRect != null) {
                        results.add(
                            PdfLinkAnnotation(
                                pageIndex = pageIndex,
                                rawUrl = validated.originalUrl,
                                resolvedUrl = validated.resolvedUrl,
                                normalizedBounds = normalizedRect
                            )
                        )
                        addedFromQuads = true
                    }
                    i += 8
                }
            }

            if (!addedFromQuads) {
                val rect = try {
                    linkAnnotation.rectangle
                } catch (_: Exception) {
                    null
                } ?: continue

                val normalizedRect = mapPdfRectToNormalizedPageRect(
                    rectLeft = rect.lowerLeftX,
                    rectBottom = rect.lowerLeftY,
                    rectRight = rect.upperRightX,
                    rectTop = rect.upperRightY,
                    cropLeft = cropLeft,
                    cropBottom = cropBottom,
                    cropWidth = cropWidth,
                    cropHeight = cropHeight,
                    rotationDegrees = rotation
                )
                if (normalizedRect != null) {
                    results.add(
                        PdfLinkAnnotation(
                            pageIndex = pageIndex,
                            rawUrl = validated.originalUrl,
                            resolvedUrl = validated.resolvedUrl,
                            normalizedBounds = normalizedRect
                        )
                    )
                }
            }
        }

        return results
    }

    private fun extractRawUriFromAnnotation(linkAnnotation: PDAnnotationLink): String? {
        try {
            val action = linkAnnotation.action
            if (action is PDActionURI) {
                val uri = action.uri
                if (!uri.isNullOrBlank()) return uri
            }
        } catch (_: Exception) {
        }

        try {
            val cosObj = linkAnnotation.cosObject
            val actionDict = cosObj.getDictionaryObject(COSName.A) as? COSDictionary
            val dictUri = actionDict?.getString(COSName.URI) ?: cosObj.getString(COSName.URI)
            if (!dictUri.isNullOrBlank()) return dictUri
        } catch (_: Exception) {
        }

        return null
    }

    /**
     * Copies [url] to the system clipboard and returns `true` on success.
     */
    fun copyUrlToClipboard(context: Context, url: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            val clip = ClipData.newPlainText("PDF Link", url)
            clipboard.setPrimaryClip(clip)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Safely launches an external browser or handler app for [url] after validating its scheme.
     * Returns `true` if the external activity was started, `false` otherwise.
     */
    fun openExternalUrl(context: Context, url: String): Boolean {
        val validated = validateAndNormalizePdfUrl(url) ?: return false
        return try {
            val parsedUri = Uri.parse(validated.resolvedUrl)
            val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                if (validated.scheme == "http" || validated.scheme == "https") {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Native popup dialog shown when a user taps an embedded hyperlink in the PDF viewer.
 * Displays the complete destination URL in a selectable, scrollable container and provides
 * **Open Link**, **Copy Link**, and dismissal actions.
 */
@Composable
fun PdfHyperlinkDialog(
    url: String,
    onOpenLink: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pdf_hyperlink_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "External Link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("pdf_hyperlink_dismiss_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss link popup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Destination URL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 180.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ) {
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = url,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_hyperlink_url_text")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onCopyLink(url) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_hyperlink_copy_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy Link",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = { onOpenLink(url) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_hyperlink_open_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Link",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("pdf_hyperlink_cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
