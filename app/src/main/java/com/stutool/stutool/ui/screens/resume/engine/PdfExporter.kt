package com.stutool.stutool.ui.screens.resume.engine

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stutool.stutool.ui.screens.resume.models.ResumeData
import com.stutool.stutool.ui.screens.resume.models.formatForExport
import com.stutool.stutool.ui.screens.resume.templates.RenderResume
import com.stutool.stutool.ui.screens.resume.utils.PaginationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    /**
     * Generates a PDF into a temporary cache file and returns the File.
     * Does NOT save to public Documents. Callers decide what to do with it.
     */
    suspend fun generatePdfToCache(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        savedStateOwner: SavedStateRegistryOwner,
        resumeData: ResumeData,
        template: String,
        fileName: String
    ): File = withContext(Dispatchers.Main) {
        val formattedData = resumeData.formatForExport()
        val pdfWidthPx = PaginationUtils.PDF_WIDTH_PX
        val pdfHeightPx = PaginationUtils.PDF_HEIGHT_PX

        // Attach a ComposeView to the Activity's window decor so it gets
        // a proper windowRecomposer, then measure/draw off-screen.
        val activity = context as? Activity
            ?: throw Exception("PDF export requires an Activity context")

        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        // CRITICAL: Convert raw pixel width to proper dp.
        // RenderResume scales density as: currentDensity * (pageWidth.value / 595).
        // If we pass raw pixels (2480) as dp, the density gets multiplied by
        // deviceDensity twice, making the layout ~3x too wide for the MeasureSpec,
        // causing text to wrap excessively and content to spill to page 2.
        val deviceDensity = context.resources.displayMetrics.density
        val pdfWidthDp = (pdfWidthPx.toFloat() / deviceDensity).dp
        // Minimum page height in dp so fillMaxHeight() in templates
        // (e.g. Modern sidebar) stretches to at least one full A4 page,
        // matching how the preview sets defaultMinSize(minHeight = singlePageHeight).
        val pdfPageHeightDp = pdfWidthDp * PaginationUtils.A4_RATIO

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)

            setContent {
                Box(
                    modifier = Modifier
                        .width(pdfWidthDp)
                        .defaultMinSize(minHeight = pdfPageHeightDp)
                        .height(IntrinsicSize.Max)
                ) {
                    RenderResume(
                        data = formattedData,
                        template = template,
                        pageWidth = pdfWidthDp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Add off-screen (invisible) to get a valid window token & recomposer
        composeView.visibility = View.INVISIBLE
        composeView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        rootView.addView(composeView)

        // Wait one frame for composition to attach
        kotlinx.coroutines.delay(200)

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(pdfWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val measuredHeight = composeView.measuredHeight
        composeView.layout(0, 0, pdfWidthPx, measuredHeight)

        // Wait for draw to settle
        kotlinx.coroutines.delay(100)

        val cacheFile = withContext(Dispatchers.IO) {
            val pdfDoc = PdfDocument()

            var pageIndex = 0
            var currentStartY = 0f

            while (currentStartY < measuredHeight) {
                val pageInfo = PdfDocument.PageInfo.Builder(pdfWidthPx, pdfHeightPx, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Header injection for multi-page documents
                if (pageIndex > 0) {
                    val headerHeight = PaginationUtils.getHeaderHeightPx(pdfWidthPx.toFloat())

                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        style = android.graphics.Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, pdfWidthPx.toFloat(), headerHeight, bgPaint)

                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 65f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    }

                    val displayName = formattedData.personal.name.ifBlank { "Resume" }
                    val headerText = "$displayName • Continued"

                    val bounds = android.graphics.Rect()
                    textPaint.getTextBounds(headerText, 0, headerText.length, bounds)
                    canvas.drawText(headerText, pdfWidthPx / 2f, (headerHeight / 2f) + (bounds.height() / 2f), textPaint)
                }

                canvas.save()
                val dY = PaginationUtils.getCanvasTranslationY(pageIndex, pdfWidthPx.toFloat())
                canvas.translate(0f, dY)
                composeView.draw(canvas)
                canvas.restore()

                pdfDoc.finishPage(page)

                pageIndex++
                currentStartY = PaginationUtils.getSourceBoundsForPage(pageIndex, pdfWidthPx.toFloat()).first
            }

            // Write to cache directory
            val cacheDir = File(context.cacheDir, "resume_pdf")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            file
        }

        // Clean up: remove the off-screen view
        rootView.removeView(composeView)

        cacheFile
    }

    /**
     * Save a cached PDF to the public Documents/StuTool folder.
     */
    fun savePdfToDevice(context: Context, cacheFile: File, fileName: String): Uri? {
        var outputStream: java.io.OutputStream? = null
        var finalUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/StuTool"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                ) ?: throw Exception("Failed to create MediaStore record")
                finalUri = uri
                outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw Exception("Failed to open output stream")
            } else {
                val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                val outputFile = File(outputDir, fileName)
                finalUri = Uri.fromFile(outputFile)
                outputStream = FileOutputStream(outputFile)
            }
            cacheFile.inputStream().use { it.copyTo(outputStream!!) }
        } finally {
            outputStream?.close()
        }
        return finalUri
    }

    /**
     * Get a sharable content URI for the cached file via FileProvider.
     */
    fun getShareableUri(context: Context, cacheFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    }

    /**
     * Launch the system share sheet for the PDF.
     */
    fun sharePdf(context: Context, cacheFile: File, fileName: String) {
        val uri = getShareableUri(context, cacheFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName.removeSuffix(".pdf"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Resume PDF"))
    }

    /**
     * Open the PDF using the device's default viewer.
     */
    fun viewPdf(context: Context, cacheFile: File) {
        val uri = getShareableUri(context, cacheFile)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(viewIntent)
    }
}
