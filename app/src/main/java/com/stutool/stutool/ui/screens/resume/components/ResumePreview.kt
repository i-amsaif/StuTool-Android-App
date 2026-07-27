package com.stutool.stutool.ui.screens.resume.components

import android.graphics.Picture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.stutool.stutool.ui.screens.resume.models.formatForExport
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import com.stutool.stutool.ui.screens.resume.templates.RenderResume
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.screens.resume.ResumeViewModel
import com.stutool.stutool.ui.screens.resume.templates.ResumeTemplateRenderer
import com.stutool.stutool.ui.screens.resume.utils.PaginationUtils



@Composable
fun ResumePreview(
    viewModel: ResumeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val data = remember(state.resumeData) { state.resumeData.formatForExport() }
    val template = state.selectedTemplate

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // --- Sticky PREVIEW header bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "PREVIEW",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "100%",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // --- Scrollable preview area ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 32.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val visualWidth = maxWidth
                val singlePageHeight = visualWidth * 1.414f
                
                Box(
                    modifier = Modifier
                        .width(visualWidth)
                        .defaultMinSize(minHeight = singlePageHeight)
                        .height(IntrinsicSize.Max)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(2.dp))
                        .background(Color.White)
                        .drawWithContent {
                            drawContent()

                            val totalHeight = size.height
                            var pageIndex = 0
                            
                            // Initialize currentY with the end of the first page bounds
                            var currentY = PaginationUtils.getSourceBoundsForPage(pageIndex, size.width).second

                            // Dashed line configuration
                            val dashPathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                            val lineColor = Color.LightGray.copy(alpha = 0.5f)
                            
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.DKGRAY
                                textSize = 30f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                            }

                            // Keep loop bounds safe 
                            while (currentY < totalHeight - 10f) {
                                // Draw dashed line across the full width
                                drawLine(
                                    color = lineColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, currentY),
                                    end = androidx.compose.ui.geometry.Offset(size.width, currentY),
                                    strokeWidth = 3f,
                                    pathEffect = dashPathEffect
                                )

                                // Draw "PAGE X" indicator block in the middle
                                val text = "PAGE ${pageIndex + 2}"
                                val textBounds = android.graphics.Rect()
                                textPaint.getTextBounds(text, 0, text.length, textBounds)
                                val cx = size.width / 2f
                                val cy = currentY

                                // Give the text a subtle background mask to erase the line beneath it
                                drawContext.canvas.nativeCanvas.drawRect(
                                    cx - textBounds.width() / 2f - 30f,
                                    cy - textBounds.height() / 2f - 15f,
                                    cx + textBounds.width() / 2f + 30f,
                                    cy + textBounds.height() / 2f + 15f,
                                    android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    text,
                                    cx,
                                    cy - textBounds.exactCenterY(),
                                    textPaint
                                )
                                
                                pageIndex++
                                currentY = PaginationUtils.getSourceBoundsForPage(pageIndex, size.width).second
                            }
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    RenderResume(
                        data = data,
                        template = template,
                        pageWidth = visualWidth,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
