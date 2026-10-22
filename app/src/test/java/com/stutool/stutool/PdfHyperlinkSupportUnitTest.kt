package com.stutool.stutool

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.ui.screens.viewer.NormalizedRect
import com.stutool.stutool.ui.screens.viewer.PdfHyperlinkDialog
import com.stutool.stutool.ui.screens.viewer.PdfHyperlinkSupport
import com.stutool.stutool.ui.screens.viewer.PdfLinkAnnotation
import com.stutool.stutool.ui.screens.viewer.PdfReaderViewModel
import com.stutool.stutool.ui.theme.StuToolTheme
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfHyperlinkSupportUnitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun urlValidation_acceptsSupportedSchemesAndRejectsUnsafeOrMalformedUrls() {
        // Standard HTTPS and HTTP URLs
        val https = PdfHyperlinkSupport.validateAndNormalizePdfUrl("https://www.stutool.in/tools?ref=pdf#top")
        assertNotNull(https)
        assertEquals("https://www.stutool.in/tools?ref=pdf#top", https?.resolvedUrl)
        assertEquals("https", https?.scheme)

        val uppercaseHttp = PdfHyperlinkSupport.validateAndNormalizePdfUrl("  HTTP://example.org/PaperCase  ")
        assertNotNull(uppercaseHttp)
        assertEquals("http://example.org/PaperCase", uppercaseHttp?.resolvedUrl)
        assertEquals("http", uppercaseHttp?.scheme)

        // Bare www. URL normalized to https://
        val bareWww = PdfHyperlinkSupport.validateAndNormalizePdfUrl("www.stutool.in/resume")
        assertNotNull(bareWww)
        assertEquals("https://www.stutool.in/resume", bareWww?.resolvedUrl)
        assertEquals("https", bareWww?.scheme)

        // Supported mailto and tel schemes
        val mailto = PdfHyperlinkSupport.validateAndNormalizePdfUrl("mailto:contact@stutool.in")
        assertNotNull(mailto)
        assertEquals("mailto:contact@stutool.in", mailto?.resolvedUrl)
        assertEquals("mailto", mailto?.scheme)

        val tel = PdfHyperlinkSupport.validateAndNormalizePdfUrl("tel:+91-9876543210")
        assertNotNull(tel)
        assertEquals("tel:+91-9876543210", tel?.resolvedUrl)
        assertEquals("tel", tel?.scheme)

        // Unsafe / unsupported schemes must be rejected
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("javascript:alert(1)"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("JAVASCRIPT:void(0)"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("file:///etc/passwd"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("content://com.stutool.provider/doc.pdf"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("data:text/html,<script>alert(1)</script>"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("vbscript:msgbox(1)"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("ftp://files.example.com/archive.zip"))

        // Malformed or empty inputs must be rejected
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl(null))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl(""))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("   "))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("https://"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("http:///missing-host"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("https://..badhost.com"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("https://example.com/bad\nurl"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("mailto:invalid-email"))
        assertNull(PdfHyperlinkSupport.validateAndNormalizePdfUrl("tel:no-digits"))
    }

    @Test
    fun coordinateMapping_convertsPdfRectAcrossRotationsAndCropBoxOffsets() {
        // Unrotated (0 deg) page: 600 x 800, rect [60, 640, 240, 720] (near top-left in PDF bottom-up coords)
        val rot0 = PdfHyperlinkSupport.mapPdfRectToNormalizedPageRect(
            rectLeft = 60f,
            rectBottom = 640f,
            rectRight = 240f,
            rectTop = 720f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 600f,
            cropHeight = 800f,
            rotationDegrees = 0
        )
        assertNotNull(rot0)
        assertEquals(0.10f, rot0!!.left, 0.0001f)
        assertEquals(0.10f, rot0.top, 0.0001f)
        assertEquals(0.40f, rot0.right, 0.0001f)
        assertEquals(0.20f, rot0.bottom, 0.0001f)

        // 90 deg CW rotated page: cropWidth=600, cropHeight=800
        // left = ry0 / 800 = 0.80, top = rx0 / 600 = 0.10, right = ry1 / 800 = 0.90, bottom = rx1 / 600 = 0.40
        val rot90 = PdfHyperlinkSupport.mapPdfRectToNormalizedPageRect(
            rectLeft = 60f,
            rectBottom = 640f,
            rectRight = 240f,
            rectTop = 720f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 600f,
            cropHeight = 800f,
            rotationDegrees = 90
        )
        assertNotNull(rot90)
        assertEquals(0.80f, rot90!!.left, 0.0001f)
        assertEquals(0.10f, rot90.top, 0.0001f)
        assertEquals(0.90f, rot90.right, 0.0001f)
        assertEquals(0.40f, rot90.bottom, 0.0001f)

        // 180 deg rotated page:
        // left = (600 - 240)/600 = 0.60, top = 640/800 = 0.80, right = (600 - 60)/600 = 0.90, bottom = 720/800 = 0.90
        val rot180 = PdfHyperlinkSupport.mapPdfRectToNormalizedPageRect(
            rectLeft = 60f,
            rectBottom = 640f,
            rectRight = 240f,
            rectTop = 720f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 600f,
            cropHeight = 800f,
            rotationDegrees = 180
        )
        assertNotNull(rot180)
        assertEquals(0.60f, rot180!!.left, 0.0001f)
        assertEquals(0.80f, rot180.top, 0.0001f)
        assertEquals(0.90f, rot180.right, 0.0001f)
        assertEquals(0.90f, rot180.bottom, 0.0001f)

        // 270 deg CW rotated page:
        // left = (800 - 720)/800 = 0.10, top = (600 - 240)/600 = 0.60, right = (800 - 640)/800 = 0.20, bottom = (600 - 60)/600 = 0.90
        val rot270 = PdfHyperlinkSupport.mapPdfRectToNormalizedPageRect(
            rectLeft = 60f,
            rectBottom = 640f,
            rectRight = 240f,
            rectTop = 720f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 600f,
            cropHeight = 800f,
            rotationDegrees = 270
        )
        assertNotNull(rot270)
        assertEquals(0.10f, rot270!!.left, 0.0001f)
        assertEquals(0.60f, rot270.top, 0.0001f)
        assertEquals(0.20f, rot270.right, 0.0001f)
        assertEquals(0.90f, rot270.bottom, 0.0001f)

        // Non-zero cropBox offset: cropLeft=50, cropBottom=100
        val withCropOffset = PdfHyperlinkSupport.mapPdfRectToNormalizedPageRect(
            rectLeft = 110f,
            rectBottom = 740f,
            rectRight = 290f,
            rectTop = 820f,
            cropLeft = 50f,
            cropBottom = 100f,
            cropWidth = 600f,
            cropHeight = 800f,
            rotationDegrees = 0
        )
        assertNotNull(withCropOffset)
        assertEquals(0.10f, withCropOffset!!.left, 0.0001f)
        assertEquals(0.10f, withCropOffset.top, 0.0001f)
        assertEquals(0.40f, withCropOffset.right, 0.0001f)
        assertEquals(0.20f, withCropOffset.bottom, 0.0001f)
    }

    @Test
    fun touchCoordinateMapping_handlesZoomPanLetterboxingAndContinuousScroll() {
        val viewerWidth = 1000f
        val viewerHeight = 2000f
        val actualWidth = 1000f
        val actualHeight = 1400f // centered vertically: top margin 300px, bottom margin 300px

        // 1. Unzoomed tap in the top letterbox margin (y = 150) -> outside page (null)
        val outsideTap = PdfHyperlinkSupport.mapZoomablePageTapToNormalizedPoint(
            tapX = 500f,
            tapY = 150f,
            viewerWidth = viewerWidth,
            viewerHeight = viewerHeight,
            actualWidth = actualWidth,
            actualHeight = actualHeight,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f
        )
        assertNull(outsideTap)

        // 2. Zoomed (scale = 2.5f) and panned (offsetX = 120f, offsetY = -180f)
        // Suppose a link is at normalized (u = 0.25f, v = 0.70f)
        val targetU = 0.25f
        val targetV = 0.70f
        val scale = 2.5f
        val offsetX = 120f
        val offsetY = -180f

        val expectedScreenX = viewerWidth / 2f + ((targetU - 0.5f) * actualWidth) * scale + offsetX
        val expectedScreenY = viewerHeight / 2f + ((targetV - 0.5f) * actualHeight) * scale + offsetY

        val mappedZoomTap = PdfHyperlinkSupport.mapZoomablePageTapToNormalizedPoint(
            tapX = expectedScreenX,
            tapY = expectedScreenY,
            viewerWidth = viewerWidth,
            viewerHeight = viewerHeight,
            actualWidth = actualWidth,
            actualHeight = actualHeight,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY
        )
        assertNotNull(mappedZoomTap)
        assertEquals(targetU, mappedZoomTap!!.first, 0.0001f)
        assertEquals(targetV, mappedZoomTap.second, 0.0001f)

        // 3. Continuous mode tap mapping with top contentPadding (viewportStartOffset = -160)
        val viewportStartOffset = -160
        val itemOffset = 0 // item starts right after contentPadding, so at y = 160 in Box
        val itemSize = 1400
        val contScale = 2.0f
        val contOffsetX = -80f
        val contOffsetY = 100f

        // Unzoomed viewport point corresponding to u=0.3f, v=0.5f on this item:
        val unzoomedViewportX = 0.3f * viewerWidth
        val unzoomedViewportY = (itemOffset - viewportStartOffset) + 0.5f * itemSize
        val screenTapX = viewerWidth / 2f + (unzoomedViewportX - viewerWidth / 2f) * contScale + contOffsetX
        val screenTapY = viewerHeight / 2f + (unzoomedViewportY - viewerHeight / 2f) * contScale + contOffsetY

        val mappedContTap = PdfHyperlinkSupport.mapContinuousViewportTapToNormalizedPoint(
            tapX = screenTapX,
            tapY = screenTapY,
            viewerWidth = viewerWidth,
            viewerHeight = viewerHeight,
            scale = contScale,
            offsetX = contOffsetX,
            offsetY = contOffsetY,
            isVertical = true,
            viewportStartOffset = viewportStartOffset,
            itemOffset = itemOffset,
            itemSize = itemSize
        )
        assertNotNull(mappedContTap)
        assertEquals(0.3f, mappedContTap!!.first, 0.0001f)
        assertEquals(0.5f, mappedContTap.second, 0.0001f)
    }

    @Test
    fun extractPageHyperlinks_extractsValidLinksAndIgnoresUnsafeOrNonExternalAnnotations() {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(600f, 800f))
            doc.addPage(page)

            // 1. Valid HTTPS link annotation
            val validLink = PDAnnotationLink().apply {
                rectangle = PDRectangle(60f, 640f, 180f, 40f) // [60, 640, 240, 680]
                action = PDActionURI().apply {
                    uri = "https://www.stutool.in/docs"
                }
            }

            // 2. Unsafe javascript link annotation (must be ignored)
            val unsafeLink = PDAnnotationLink().apply {
                rectangle = PDRectangle(60f, 500f, 180f, 40f)
                action = PDActionURI().apply {
                    uri = "javascript:evil()"
                }
            }

            // 3. Internal GoTo link annotation without external URI (must be ignored)
            val internalLink = PDAnnotationLink().apply {
                rectangle = PDRectangle(60f, 400f, 180f, 40f)
                action = PDActionGoTo()
            }

            page.annotations = listOf(validLink, unsafeLink, internalLink)

            val extracted = PdfHyperlinkSupport.extractPageHyperlinks(page, pageIndex = 2)
            assertEquals(1, extracted.size)

            val link = extracted.first()
            assertEquals(2, link.pageIndex)
            assertEquals("https://www.stutool.in/docs", link.resolvedUrl)
            assertEquals(0.10f, link.normalizedBounds.left, 0.001f)
            assertEquals(0.15f, link.normalizedBounds.top, 0.001f)
            assertEquals(0.40f, link.normalizedBounds.right, 0.001f)
            assertEquals(0.20f, link.normalizedBounds.bottom, 0.001f)

            // Hit-test inside the valid link
            val hit = PdfHyperlinkSupport.findHitHyperlink(extracted, normalizedX = 0.25f, normalizedY = 0.175f)
            assertNotNull(hit)
            assertEquals("https://www.stutool.in/docs", hit?.resolvedUrl)

            // Hit-test where the unsafe link was placed -> null
            val missUnsafe = PdfHyperlinkSupport.findHitHyperlink(extracted, normalizedX = 0.25f, normalizedY = 0.35f)
            assertNull(missUnsafe)
        }
    }

    @Test
    fun pdfHyperlinkDialog_displaysFullUrlAndExecutesOpenCopyAndDismissActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testUrl = "https://www.stutool.in/very/long/path/to/document/resource?query=1234567890&mode=viewer"

        var openedUrl: String? = null
        var copiedUrl: String? = null
        var dismissed = false

        composeTestRule.setContent {
            StuToolTheme {
                PdfHyperlinkDialog(
                    url = testUrl,
                    onOpenLink = { openedUrl = it },
                    onCopyLink = {
                        copiedUrl = it
                        PdfHyperlinkSupport.copyUrlToClipboard(context, it)
                    },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("pdf_hyperlink_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_hyperlink_url_text").assertTextEquals(testUrl)

        // Test Copy Link action + system clipboard verification
        composeTestRule.onNodeWithTag("pdf_hyperlink_copy_button").performClick()
        assertEquals(testUrl, copiedUrl)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(testUrl, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Test Open Link action
        composeTestRule.onNodeWithTag("pdf_hyperlink_open_button").performClick()
        assertEquals(testUrl, openedUrl)

        // Test Cancel / Dismiss action
        composeTestRule.onNodeWithTag("pdf_hyperlink_cancel_button").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun viewModel_retainsViewerZoomAndPanStateAcrossExternalLinkReturn() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = PdfReaderViewModel(app)

        assertEquals(1f, viewModel.viewerScale.value, 0.0001f)
        assertEquals(0f, viewModel.viewerOffsetX.value, 0.0001f)
        assertEquals(0f, viewModel.viewerOffsetY.value, 0.0001f)

        // Simulate user zooming and panning on a page before tapping Open Link
        viewModel.updateViewerTransform(scale = 2.75f, offsetX = 145f, offsetY = -92f)
        viewModel.updateContinuousTransform(scale = 2.25f, offsetX = -60f, offsetY = 210f)

        // Verify state is retained on the ViewModel when returning from external browser
        assertEquals(2.75f, viewModel.viewerScale.value, 0.0001f)
        assertEquals(145f, viewModel.viewerOffsetX.value, 0.0001f)
        assertEquals(-92f, viewModel.viewerOffsetY.value, 0.0001f)
        assertEquals(2.25f, viewModel.continuousScale.value, 0.0001f)
        assertEquals(-60f, viewModel.continuousOffsetX.value, 0.0001f)
        assertEquals(210f, viewModel.continuousOffsetY.value, 0.0001f)
    }
}
