package com.stutool.stutool.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.stutool.stutool.ui.screens.scanner.models.CropMode
import com.stutool.stutool.ui.theme.ScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterCropScreen(
    navController: NavController,
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val pages by viewModel.batchPages.collectAsState()
    val restoredPageIndex by viewModel.currentFilterPageIndex.collectAsState()

    val page = remember(pages, restoredPageIndex) {
        pages.getOrNull(restoredPageIndex)
    }

    if (page == null) {
        // Fallback if page disappeared
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    var currentQuad by remember { mutableStateOf(page.transform.effectiveCropQuad() ?: com.stutool.stutool.ui.screens.scanner.utils.Quad.defaultForImage(100, 100)) }
    val currentRotation by remember(page) { mutableIntStateOf(page.transform.effectiveRotation()) } // For FilterCrop we don't allow rotation edits directly here, just crop
    
    var isSaving by remember { mutableStateOf(false) }

    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(page.originalUri) {
        displayBitmap?.recycle()
        displayBitmap = null
        loadError = false

        try {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(page.originalUri)
                val fullBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (fullBitmap != null && !fullBitmap.isRecycled && fullBitmap.width > 0 && fullBitmap.height > 0) {
                    imageWidth = fullBitmap.width
                    imageHeight = fullBitmap.height

                    // Downscale for display
                    val displayScale = 1200f / maxOf(fullBitmap.width, fullBitmap.height)
                    if (displayScale < 1f) {
                        displayBitmap = Bitmap.createScaledBitmap(
                            fullBitmap,
                            (fullBitmap.width * displayScale).toInt(),
                            (fullBitmap.height * displayScale).toInt(),
                            true
                        )
                        fullBitmap.recycle()
                    } else {
                        displayBitmap = fullBitmap
                    }
                    
                    // If Quad was totally undefined, we must set a default before drawing
                    if (page.transform.cropQuad == null) {
                        currentQuad = com.stutool.stutool.ui.screens.scanner.utils.Quad.defaultForImage(imageWidth, imageHeight)
                    }
                } else {
                    loadError = true
                }
            }
        } catch (e: Exception) {
            loadError = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            displayBitmap?.recycle()
            displayBitmap = null
        }
    }

    Scaffold(
        containerColor = ScannerTheme.colors.scannerBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = ScannerTokens.viewportPadding,
                        vertical = ScannerTokens.floatingToolbarPadding
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = ScannerTheme.colors.floatingSurfaceColor.copy(alpha = ScannerTokens.OverlayAlpha),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(ScannerTokens.CardCornerRadius)
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.ArrowBack, "Cancel", tint = ScannerTheme.colors.scannerTextPrimary)
                    }

                    Text(
                        text = "Edit Crop",
                        color = ScannerTheme.colors.scannerTextPrimary,
                        fontSize = ScannerTokens.ToolbarTitle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )

                    TextButton(
                        onClick = {
                            isSaving = true
                            viewModel.updateSinglePageCrop(
                                context = context,
                                pageId = page.id,
                                newQuad = currentQuad,
                                newRotation = currentRotation,
                                onComplete = {
                                    navController.popBackStack()
                                }
                            )
                        },
                        enabled = !isSaving && displayBitmap != null
                    ) {
                        Text("Save", color = ScannerTheme.colors.scannerActionPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ScannerTheme.colors.scannerBackground)
        ) {
            val bmp = displayBitmap

            if (loadError) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load image", color = Color.Gray)
                }
            } else if (bmp == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ScannerTheme.colors.scannerActionPrimary)
                }
            } else {
                SharedCropEditor(
                    bitmap = bmp,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    rotation = currentRotation,
                    cropMode = CropMode.PERSPECTIVE,
                    quad = currentQuad,
                    isActivePage = true,
                    onQuadUpdated = { currentQuad = it }
                )
            }

            // Saving overlay
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ScannerTheme.colors.scannerActionPrimary)
                        Spacer(Modifier.height(16.dp))
                        Text("Regenerating preview...", color = ScannerTheme.colors.scannerTextPrimary)
                    }
                }
            }
        }
    }
}
