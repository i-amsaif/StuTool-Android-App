package com.stutool.stutool.ui.screens

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.stutool.stutool.navigation.Screen
import com.stutool.stutool.ui.components.ToolCard
import com.stutool.stutool.ui.theme.LocalGlassTokens

@Composable
fun ToolsScreen(navController: NavController) {
    val tools = listOf(
        Tool(
            icon = Icons.Default.JoinFull,
            title = "Merge PDF",
            description = "Combine multiple PDF files into one single document."
        ),
        Tool(
            icon = Icons.Default.Image,
            title = "Image to PDF",
            description = "Adjust orientation and margins. Convert images to PDFs."
        ),
        Tool(
            icon = Icons.Default.Layers,
            title = "Multiple Pages per Sheet",
            description = "Print multiple pages on a single sheet of paper."
        ),
        Tool(
            icon = Icons.Default.Description,
            title = "Extract Pages",
            description = "Extract specific pages from your PDF into a new one."
        ),
        Tool(
            icon = Icons.Default.Compress,
            title = "Compress PDF",
            description = "Significantly reduce your PDF file size locally."
        ),
        Tool(
            icon = Icons.Default.CropFree,
            title = "Document Scanner",
            description = "Scan physical documents using your camera or import from gallery."
        )
    )

    val glass = LocalGlassTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(glass.backgroundBrush)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(top = 40.dp, bottom = 100.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Section (Full Width)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                append("PDF ")
                            }
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)) {
                                append("Tools")
                            }
                        },
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth(0.85f)) {
                        Text(
                            text = "Select a tool below to quickly edit, merge, or convert your PDF files directly on your device.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Tools Grid
            items(tools) { tool ->
                ToolCard(
                    icon = tool.icon,
                    title = tool.title,
                    description = tool.description,
                    onClick = when (tool.title) {
                        "Merge PDF" -> { { navController.navigate(Screen.MergePdf.route) } }
                        "Image to PDF" -> { { navController.navigate(Screen.ImageToPdf.route) } }
                        "Multiple Pages per Sheet" -> { { navController.navigate(Screen.PagesPerSheet.route) } }
                        "Extract Pages" -> { { navController.navigate(Screen.ExtractPages.route) } }
                        "Compress PDF" -> { { navController.navigate(Screen.CompressPdf.route) } }
                        "Document Scanner" -> { { navController.navigate("scanner_camera") } }
                        else -> null
                    }
                )
            }
        }
    }
}

data class Tool(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String
)
