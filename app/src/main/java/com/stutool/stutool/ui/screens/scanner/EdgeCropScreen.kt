package com.stutool.stutool.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import com.stutool.stutool.ui.screens.scanner.models.CropHandle
import com.stutool.stutool.ui.screens.scanner.utils.CropConstraintEngine
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.stutool.stutool.ui.theme.ScannerTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.stutool.stutool.ui.screens.scanner.utils.DetectionResult
import com.stutool.stutool.ui.screens.scanner.utils.EdgeDetection
import com.stutool.stutool.ui.screens.scanner.utils.PerspectiveTransform
import com.stutool.stutool.ui.screens.scanner.utils.Quad
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeCropScreen(
    navController: NavController,
    viewModel: ScannerViewModel
) {
    val showExitDialog by viewModel.showExitDialog.collectAsState()
    
    val currentImageUri by viewModel.currentImageUri.collectAsState()
    val context = LocalContext.current
    val activeRotation by viewModel.activeRotation.collectAsState()
    val activeQuad by viewModel.activeQuad.collectAsState()
    val cropMode by viewModel.cropMode.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()
    val inQueue = activeBatch != null && currentQueueIndex >= 0

    // G2: For routing single page edits out of the queue
    val editingPageIndex by viewModel.editingPageIndex.collectAsState()
    val scannedPages by viewModel.scannedPages.collectAsState()
    val editingPageId by viewModel.editingPageId.collectAsState()

    val isIntentionalExit by viewModel.isIntentionalScannerExit.collectAsState()
    val isTransitioning by viewModel.isTransitioningBetweenScannerStages.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setTransitioning(false)
    }

    // G4.1 & G4.2: Empty state guard — if all pages are gone, exit to home
    val totalPages = scannedPages.size + (activeBatch?.pageIds?.size ?: 0)
    LaunchedEffect(totalPages) {
        if (totalPages == 0 && !isIntentionalExit && !isTransitioning) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    // G4.1: Intercept back press
    BackHandler {
        if (editingPageId.isNotEmpty()) {
            viewModel.clearEditingPageId()
            navController.popBackStack()
        } else {
            viewModel.requestScannerExit {
                navController.popBackStack()
            }
        }
    }

    ScannerExitDialog(
        showDialog = showExitDialog,
        onConfirm = {
            viewModel.confirmScannerExit(context) {
                navController.popBackStack("home", inclusive = false)
            }
        },
        onDismiss = { viewModel.dismissExitDialog() }
    )

    // Image dimensions (original)
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }

    // Fullscreen expand trigger
    var expandTrigger by remember { mutableIntStateOf(0) }

    // Quad corners in IMAGE coordinates removed to prevent duplicate state
    // We now rely solely on activeQuad from ScannerViewModel

    // Loading states
    var isDetecting by remember { mutableStateOf(true) }
    var isApplyingCrop by remember { mutableStateOf(false) }
    var detectionConfidence by remember { mutableFloatStateOf(0f) }
    // G2: Error state for bitmap load failure — enables retry UI
    var loadError by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    // Delete page confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Delete page confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Page?", fontWeight = FontWeight.Bold) },
            text = { Text("This page will be permanently removed from the scan queue.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCurrentCropPage(
                        context = context,
                        onNavigate = {
                            // Safe navigation: pop current edge_crop, then navigate fresh
                            // This avoids stacking duplicate edge_crop destinations
                            navController.popBackStack("edge_crop", inclusive = true)
                            navController.navigate("edge_crop")
                        },
                        onEmpty = {
                            // Last page deleted — return to app home
                            viewModel.setTransitioning(true)
                            navController.popBackStack("home", inclusive = false)
                        }
                    )
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bitmap for display (loaded once, downscaled for UI)
    var displayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Wait for image to be fully processed (EXIF corrected) before loading
    val imageReady by viewModel.imageReady.collectAsState()

    // Run edge detection ONLY after imageReady = true (EXIF correction complete)
    LaunchedEffect(currentImageUri, imageReady, retryTrigger) {
        val uri = currentImageUri ?: return@LaunchedEffect
        // For gallery imports, imageReady may not be set — allow those through
        // For camera captures, wait until EXIF correction is done
        if (!imageReady && uri.scheme == "file") return@LaunchedEffect

        // G2: Urgent memory fix. Clear previous state immediately.
        displayBitmap?.recycle()
        displayBitmap = null
        viewModel.setActiveQuad(null)
        detectionConfidence = 0f
        loadError = false

        isDetecting = true

        try {
            withContext(Dispatchers.IO) {
                android.util.Log.d("EdgeCropScreen", "[G2 Crop] Loading bitmap for URI: $uri")
                
                // G2: Strict timeout for bitmap decode
                val fullBitmap = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bmp
                }

                // G2: Bitmap validation with error state
                if (fullBitmap != null && !fullBitmap.isRecycled && fullBitmap.width > 0 && fullBitmap.height > 0) {
                    imageWidth = fullBitmap.width
                    imageHeight = fullBitmap.height
                    android.util.Log.d("EdgeCropScreen", "[G2 Crop] Bitmap loaded: ${imageWidth}x${imageHeight}")

                    // Downscale for display (max 1200px for UI performance)
                    val displayScale = 1200f / maxOf(fullBitmap.width, fullBitmap.height)
                    if (displayScale < 1f) {
                        displayBitmap = Bitmap.createScaledBitmap(
                            fullBitmap,
                            (fullBitmap.width * displayScale).toInt(),
                            (fullBitmap.height * displayScale).toInt(),
                            true
                        )
                    } else {
                        displayBitmap = fullBitmap
                    }

                    // G2: Immediate Fallback Quad assignment BEFORE detection
                    if (activeQuad != null) {
                        viewModel.setActiveQuad(activeQuad)
                        detectionConfidence = 1f
                    } else {
                        // G2: Strict Timeout for Edge Detection (3s)
                        val result = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                            EdgeDetection.detectWithConfidence(fullBitmap)
                        }

                        if (result != null) {
                            // PIPELINE: detect -> sanitize -> validate -> commit
                            val safeInsetRatio = 0.02f // 2% inset — reduced for near-edge cropping while preserving handle accessibility
                            val insetW = imageWidth * safeInsetRatio
                            val insetH = imageHeight * safeInsetRatio
                            
                            val rawQuad = result.quad ?: Quad.defaultForImage(imageWidth, imageHeight)
                            // Enforce safe boundaries before sanitization
                            val clampedQuad = Quad(
                                PointF(rawQuad.topLeft.x.coerceIn(insetW, imageWidth - insetW), rawQuad.topLeft.y.coerceIn(insetH, imageHeight - insetH)),
                                PointF(rawQuad.topRight.x.coerceIn(insetW, imageWidth - insetW), rawQuad.topRight.y.coerceIn(insetH, imageHeight - insetH)),
                                PointF(rawQuad.bottomRight.x.coerceIn(insetW, imageWidth - insetW), rawQuad.bottomRight.y.coerceIn(insetH, imageHeight - insetH)),
                                PointF(rawQuad.bottomLeft.x.coerceIn(insetW, imageWidth - insetW), rawQuad.bottomLeft.y.coerceIn(insetH, imageHeight - insetH))
                            )
                            
                            val sanitizedQuad = clampedQuad.sanitize(imageWidth, imageHeight)
                            val minSize = minOf(imageWidth, imageHeight) * 0.05f
                            if (sanitizedQuad.isPerspectiveGeometryValid(imageWidth, imageHeight, minSize)) {
                                viewModel.setActiveQuad(sanitizedQuad) // COMMIT IMMEDIATELY
                                detectionConfidence = result.confidence
                                android.util.Log.d("EdgeCropScreen", "[G3.3 Crop] Edge detection successful & valid, committed to viewModel.")
                            } else {
                                val defaultQuad = Quad.defaultForImage(imageWidth, imageHeight)
                                viewModel.setActiveQuad(defaultQuad) // COMMIT IMMEDIATELY
                                detectionConfidence = 0f
                                android.util.Log.w("EdgeCropScreen", "[G3.3 Crop] Edge detection valid but sanitized geometry failed. Committed fallback.")
                            }
                        } else {
                            // G2: Detection timed out - hide loader, keep fallback quad, show non-blocking Toast
                            val defaultQuad = Quad.defaultForImage(imageWidth, imageHeight)
                            viewModel.setActiveQuad(defaultQuad) // COMMIT IMMEDIATELY
                            detectionConfidence = 0f
                            android.util.Log.w("EdgeCropScreen", "[G3.3 Crop] Edge detection timed out. Committed fallback.")
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Auto detection unavailable", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // Recycle full if we created a display copy
                    if (displayScale < 1f) fullBitmap.recycle()
                } else {
                    android.util.Log.e("EdgeCropScreen", "[G2 Crop] Invalid bitmap decoded or decode timed out")
                    loadError = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EdgeCropScreen", "[G2 Crop] Detection pipeline failed", e)
            loadError = true
            if (imageWidth > 0 && imageHeight > 0 && activeQuad == null) {
                val fallback = Quad.defaultForImage(imageWidth, imageHeight)
                viewModel.setActiveQuad(fallback)
            }
        } finally {
            // G2: Failsafe loader shutdown — ALWAYS runs
            isDetecting = false
            android.util.Log.d("EdgeCropScreen", "[G2 Crop] Detection state reset to false, loadError=$loadError")
        }
    }

    // Safe cleanup when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            displayBitmap?.recycle()
            displayBitmap = null
        }
    }



    val density = LocalDensity.current
    ScannerRoot(
        topBar = {},
        bottomBar = {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
        // --- 1. VIEWPORT (Lowest Layer) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                
                .background(ScannerTheme.colors.scannerBackground),
            contentAlignment = Alignment.Center
        ) {
            // Image + overlay container
            // Asymmetric padding: top/bottom must clear the floating toolbar/action overlays
            // so that crop handles remain accessible for all aspect ratios (including 9:16)
            // Top clearance: statusBar + floatingToolbarPadding + viewportPadding + title row + crop tools ≈ 140dp
            // Bottom clearance: navigationBar + bottomDockPadding + action row ≈ 100dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 130.dp, bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = displayBitmap

                // G2: Error fallback container — retry or skip when bitmap load fails
                if (loadError && bmp == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Failed to load image",
                            color = ScannerTheme.colors.scannerTextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    android.util.Log.d("EdgeCropScreen", "[G2 Crop] User triggered retry")
                                    retryTrigger++
                                },
                                shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius)
                            ) {
                                Text("Retry", color = ScannerTheme.colors.scannerTextPrimary)
                            }
                            Button(
                                onClick = {
                                    android.util.Log.d("EdgeCropScreen", "[G2 Crop] User skipped failed page")
                                    viewModel.setActiveQuad(null)
                                    if (inQueue) {
                                        viewModel.advanceQueue(context) { next ->
                                            navController.navigate(next) {
                                                popUpTo("edge_crop") { inclusive = true }
                                            }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                },
                                shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius)
                            ) {
                                Text("Skip Page")
                            }
                        }
                    }
                } else if (bmp != null) {
                    val currentQuad = activeQuad
                    if (currentQuad != null) {
                        android.util.Log.e("CropTrace_LOAD", "[C] EdgeCropScreen before SharedCropEditor")
                        android.util.Log.e("CropTrace_LOAD", "editingPageId: $editingPageId")
                        android.util.Log.e("CropTrace_LOAD", "activeQuad: $activeQuad")
                        android.util.Log.e("CropTrace_LOAD", "currentQuad passed to SharedCropEditor: $currentQuad")
                        SharedCropEditor(
                            bitmap = bmp,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            rotation = activeRotation,
                            cropMode = cropMode,
                            quad = currentQuad,
                            onQuadUpdated = { newQuad -> viewModel.setActiveQuad(newQuad) },
                            expandTrigger = expandTrigger,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Loading overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDetecting,
                    enter = androidx.compose.animation.fadeIn(tween(ScannerTokens.MediumDuration)),
                    exit = androidx.compose.animation.fadeOut(tween(ScannerTokens.FastDuration))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ScannerTheme.colors.scannerCropOverlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ScannerTheme.colors.scannerTextPrimary, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Detecting edges...", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 14.sp)
                        }
                    }
                }

                // Confidence hint (only after detection completes)
                if (!isDetecting && detectionConfidence < 0.8f && activeQuad != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(Color(0xCC333333), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (detectionConfidence < 0.5f)
                                "⚠ Low confidence — adjust edges manually"
                            else
                                "ℹ Edges may need fine-tuning",
                            color = if (detectionConfidence < 0.5f) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Crop processing overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isApplyingCrop,
                    enter = androidx.compose.animation.fadeIn(tween(ScannerTokens.FastDuration)),
                    exit = androidx.compose.animation.fadeOut(tween(ScannerTokens.MediumDuration))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ScannerTheme.colors.scannerCropOverlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ScannerTheme.colors.scannerTextPrimary, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Applying crop...", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // --- 2. TOP FLOATING SYSTEM ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = ScannerTokens.viewportPadding,
                    vertical = ScannerTokens.floatingToolbarPadding
                )
        ) {
            val density = LocalDensity.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = ScannerTheme.colors.floatingSurfaceColor.copy(alpha = ScannerTokens.OverlayAlpha),
                        shape = RoundedCornerShape(ScannerTokens.CardCornerRadius)
                    )
            ) {
                // ── Row 1: Title bar ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back
                    IconButton(
                        onClick = {
                            if (editingPageId.isNotEmpty()) {
                                viewModel.clearEditingPageId()
                                navController.popBackStack()
                            } else {
                                viewModel.requestScannerExit {
                                    navController.popBackStack()
                                }
                            }
                        },
                        modifier = Modifier.size(ScannerTokens.MinTouchTarget)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Title
                    Text(
                        text = if (inQueue) {
                            "Crop page ${currentQueueIndex + 1} of ${activeBatch?.pageIds?.size ?: 0}"
                        } else {
                            "Adjust Edges"
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = ScannerTheme.colors.scannerTextPrimary,
                        fontSize = ScannerTokens.ToolbarTitle,
                        modifier = Modifier.weight(1f)
                    )
                    // Delete (queue mode only)
                    if (inQueue) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = ScannerTokens.MinTouchTarget, minHeight = ScannerTokens.MinTouchTarget)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDeleteDialog = true }
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Delete", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                // ── Row 2: Crop tools strip ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScannerTokens.CropToolbarHorizontalPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Crop Mode Selector — subtle rounded container
                    Surface(
                        onClick = {
                            val newMode = if (cropMode == com.stutool.stutool.ui.screens.scanner.models.CropMode.PERSPECTIVE) {
                                com.stutool.stutool.ui.screens.scanner.models.CropMode.RECTANGULAR
                            } else {
                                com.stutool.stutool.ui.screens.scanner.models.CropMode.PERSPECTIVE
                            }
                            viewModel.setCropMode(newMode)
                        },
                        shape = RoundedCornerShape(ScannerTokens.ChipCornerRadius),
                        color = ScannerTheme.colors.chipSelectedBackground,
                        modifier = Modifier.height(ScannerTokens.DenseTouchTarget)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp), tint = ScannerTheme.colors.scannerActionPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (cropMode == com.stutool.stutool.ui.screens.scanner.models.CropMode.RECTANGULAR) "Rectangle" else "Perspective",
                                fontWeight = FontWeight.Medium,
                                fontSize = ScannerTokens.LabelText,
                                color = ScannerTheme.colors.scannerActionPrimary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(ScannerTokens.CropToolbarIconSpacing)) {
                        // Fullscreen Expand
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = ScannerTokens.MinTouchTarget, minHeight = ScannerTokens.MinTouchTarget)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    expandTrigger++
                                }
                                .padding(4.dp)
                        ) {
                            Icon(ScannerTokens.OpenInFull, contentDescription = "Expand Crop", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(ScannerTokens.CropToolbarIconSize))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Full", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 10.sp, maxLines = 1)
                        }
                        // Rotate Left
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = ScannerTokens.MinTouchTarget, minHeight = ScannerTokens.MinTouchTarget)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    android.util.Log.d("EdgeCropScreen", "Rotate: left, current=${activeRotation}")
                                    viewModel.rotateActivePage(clockwise = false)
                                }
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(ScannerTokens.CropToolbarIconSize))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Left", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 10.sp, maxLines = 1)
                        }
                        // Rotate Right
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = ScannerTokens.MinTouchTarget, minHeight = ScannerTokens.MinTouchTarget)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    android.util.Log.d("EdgeCropScreen", "Rotate: right, current=${activeRotation}")
                                    viewModel.rotateActivePage(clockwise = true)
                                }
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(ScannerTokens.CropToolbarIconSize))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Right", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // --- 3. BOTTOM FLOATING SYSTEM ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = ScannerTokens.bottomDockPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScannerTokens.viewportPadding)
                    .background(
                        color = ScannerTheme.colors.floatingSurfaceColor.copy(alpha = ScannerTokens.OverlayAlpha),
                        shape = RoundedCornerShape(ScannerTokens.CardCornerRadius)
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Crop
                TextButton(
                    onClick = {
                        viewModel.setActiveQuad(null)
                        if (inQueue) {
                            viewModel.saveCurrentQueuePageEdits()
                            viewModel.advanceQueue(context) { next ->
                                viewModel.setTransitioning(true)
                                navController.navigate(next) {
                                    popUpTo("edge_crop") { inclusive = true }
                                }
                            }
                        } else {
                            if (editingPageId.isNotEmpty()) {
                                viewModel.clearEditingPageId()
                                navController.popBackStack()
                            } else {
                                navController.popBackStack()
                            }
                        }
                    },
                    // G2: Skip Crop enabled as long as quad exists — never trap user during detection
                    enabled = !isApplyingCrop && activeQuad != null
                ) {
                    Text("Skip Crop", color = ScannerTheme.colors.scannerTextSecondary, fontSize = ScannerTokens.ButtonText, fontWeight = FontWeight.SemiBold)
                }

                // Apply perspective crop / Next in queue
                Button(
                    onClick = {
                        if (activeQuad == null || currentImageUri == null) return@Button
                        if (isApplyingCrop) return@Button
                        isApplyingCrop = true
                        
                        // We no longer need to sync quad to viewModel.activeQuad because activeQuad is already the source of truth
                        
                        if (inQueue) {
                            viewModel.saveCurrentQueuePageEdits()
                            viewModel.advanceQueue(context) { next ->
                                viewModel.setTransitioning(true)
                                navController.navigate(next) {
                                    popUpTo("edge_crop") { inclusive = true }
                                }
                            }
                        } else {
                            if (editingPageId.isNotEmpty()) {
                                viewModel.updateSinglePageCrop(
                                    context = context,
                                    pageId = editingPageId,
                                    newQuad = activeQuad!!,
                                    newRotation = activeRotation,
                                    onComplete = {
                                        isApplyingCrop = false
                                        viewModel.clearEditingPageId()
                                        navController.popBackStack()
                                    }
                                )
                            } else {
                                navController.popBackStack()
                            }
                        }
                    },
                    enabled = !isApplyingCrop && activeQuad != null,
                    shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius), // Premium pill shape
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (inQueue && currentQueueIndex < (activeBatch?.pageIds?.size ?: 0) - 1) "Next" else "Done",
                        fontWeight = FontWeight.Bold,
                        fontSize = ScannerTokens.ButtonText
                    )
                }
            }
        }
    }
}
}
