package com.stutool.stutool.ui.screens.scanner

import android.Manifest
import android.net.Uri
import android.util.Log
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.stutool.stutool.ui.theme.LocalGlassTokens
import com.stutool.stutool.ui.theme.ScannerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    navController: NavController,
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current

    // Permission state
    var hasCameraPermission by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            navController.popBackStack()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            var activity: android.app.Activity? = context as? android.app.Activity
            var currentContext = context
            while (activity == null && currentContext is android.content.ContextWrapper) {
                currentContext = currentContext.baseContext
                activity = currentContext as? android.app.Activity
            }
            val shouldShowRationale = activity?.let { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: false
            if (!shouldShowRationale) {
                showPermissionSettingsDialog = true
            } else {
                // Permission denied - gracefully return to previous screen
                navController.popBackStack()
            }
        }
    }

    val scanMode by viewModel.scanMode.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
            // G3.1: Both SINGLE and BATCH now always go through the queue pipeline.
            // Single mode with 1 image uses enqueueGalleryImages which creates a 1-page batch,
            // routing correctly: Crop → Filter → Review via advanceQueue().
            viewModel.enqueueGalleryImages(
                context = context, 
                uris = uris,
                onProceedToCrop = {
                    navController.navigate("edge_crop") {
                        popUpTo("scanner_camera") { inclusive = false }
                    }
                },
                onProceedToCropReview = {
                    navController.navigate("batch_crop_review") {
                        popUpTo("scanner_camera") { inclusive = false }
                    }
                },
                onProceedToFilter = {
                    navController.navigate("batch_filter_pager") {
                        popUpTo("scanner_camera") { inclusive = false }
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.restoreSession(context)
    }

    val hasRecoveredSession by viewModel.hasRecoveredSession.collectAsState()
    val scannedPages by viewModel.scannedPages.collectAsState()
    val batchPages by viewModel.batchPages.collectAsState()
    val showExitDialog by viewModel.showExitDialog.collectAsState()

    // G4.1: Intercept back press on Camera screen if there are scanned/temp pages
    BackHandler(enabled = scannedPages.isNotEmpty() || batchPages.isNotEmpty()) {
        viewModel.requestScannerExit {
            navController.popBackStack()
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

    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPermissionSettingsDialog = false
                navController.popBackStack() 
            },
            title = { Text("Camera Permission Required") },
            text = { Text("Scanner needs camera access to capture documents. Please enable it in App Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionSettingsDialog = false
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    settingsLauncher.launch(intent)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPermissionSettingsDialog = false
                    navController.popBackStack() 
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraXViewfinder(
                viewModel = viewModel,
                navController = navController,
                onGalleryClick = { galleryLauncher.launch("image/*") }
            )
        } else {
            // Simple placeholder while asking for permission
            Box(modifier = Modifier.fillMaxSize().background(ScannerTheme.colors.scannerBackground))
        }

        // Session Recovery Bottom Sheet
        if (hasRecoveredSession) {
            val recoveredPageCount = viewModel.getRecoveredPageCount()
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.dismissRecoveredSessionPrompt(context)
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text("Recovered Session", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "You have $recoveredPageCount unsaved scanned page${if (recoveredPageCount != 1) "s" else ""}.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ScannerTokens.BottomActionSpacing)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.dismissRecoveredSessionPrompt(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius)
                        ) {
                            Text("Discard", color = MaterialTheme.colorScheme.error)
                        }
                        val coroutineScope = rememberCoroutineScope()
                        Button(
                            onClick = {
                                val destination = viewModel.getRestoreDestination()
                                viewModel.acceptRecoveredSession()
                                coroutineScope.launch {
                                    viewModel.awaitRestoreCompletion()
                                    navController.navigate(destination)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius)
                        ) {
                            Text("Continue")
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Auto Crop Loading Overlay (Top Z-Index)
        val isAutoCropping by viewModel.isAutoCropping.collectAsState()
        if (isAutoCropping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Block clicks
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ScannerTheme.colors.scannerBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = ScannerTheme.colors.scannerActionPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Auto-cropping pages...", 
                            fontWeight = FontWeight.SemiBold,
                            color = ScannerTheme.colors.scannerTextPrimary
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ── CameraX Viewfinder ──
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CameraXViewfinder(
    viewModel: ScannerViewModel,
    navController: NavController,
    onGalleryClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scannedPages by viewModel.scannedPages.collectAsState()
    val pendingScansCount by viewModel.pendingScansCount.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var isCapturing by remember { mutableStateOf(false) }

    // Ratios: use ScanRatio enum as single source of truth
    var currentRatio by remember { mutableStateOf(ScanRatio.RATIO_4_3) }

    // Flash Mode
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    // Grid State
    var showGrid by remember { mutableStateOf(false) }

    // Binding state for transitions
    var isRebinding by remember { mutableStateOf(false) }

    // Camera Preview Reference
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    // Focus state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusAlpha = remember { Animatable(0f) }
    val focusScale = remember { Animatable(1.5f) }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    var showFlash by remember { mutableStateOf(false) }

    // Function to trigger capture safely
    val triggerCapture = {
        if (!isCapturing) {
            isCapturing = true
            showFlash = true
            // Haptic feedback
            previewView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val outputFile = viewModel.createCaptureOutputFile(context)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        val captureRotation = previewView?.display?.rotation ?: Surface.ROTATION_0
                        viewModel.captureRatio = currentRatio
                        isCapturing = false

                        if (scanMode == ScanMode.SINGLE) {
                            viewModel.captureAndPrepare(context, outputFile, captureRotation) {
                                navController.navigate("edge_crop") {
                                    popUpTo("scanner_camera") { inclusive = false }
                                }
                            }
                        } else {
                            val accepted = viewModel.enqueueCapture(context, outputFile, captureRotation)
                            if (!accepted) {
                                android.widget.Toast.makeText(context, "Too many pending scans, please wait", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("ScannerScreen", "Capture failed", exception)
                        isCapturing = false
                    }
                }
            )
        }
    }

    LaunchedEffect(previewView, currentRatio) {
        val view = previewView ?: return@LaunchedEffect
        isRebinding = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Set target rotation so EXIF metadata is correct
                val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
                imageCapture.targetRotation = displayRotation

                val useCases = mutableListOf<androidx.camera.core.UseCase>(preview, imageCapture)

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )
                cameraControl = camera.cameraControl
                isRebinding = false
            } catch (e: Exception) {
                Log.e("ScannerScreen", "CameraX binding failed", e)
                isRebinding = false
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    // Add the Iteration 3 UI overlay
    com.stutool.stutool.ui.components.CropDecisionOverlay(viewModel = viewModel)

    val isGalleryImporting by viewModel.isGalleryImporting.collectAsState()
    if (isGalleryImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) {}, // Block clicks
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = ScannerTheme.colors.scannerBackground,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = ScannerTheme.colors.scannerActionPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Importing images...", 
                        fontWeight = FontWeight.SemiBold,
                        color = ScannerTheme.colors.scannerTextPrimary
                    )
                }
            }
        }
    }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ScannerTheme.colors.scannerBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
        // ── TOP BAR (Floating) ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = ScannerTokens.viewportPadding, vertical = ScannerTokens.floatingToolbarPadding)
                .clip(RoundedCornerShape(ScannerTokens.CardCornerRadius))
                .background(ScannerTheme.colors.floatingSurfaceColor.copy(alpha = ScannerTokens.OverlayAlpha))
                .padding(
                    horizontal = ScannerTokens.TopBarHorizontalPadding,
                    vertical = ScannerTokens.TopBarVerticalPadding
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Close Button
            IconButton(
                onClick = { 
                    viewModel.requestScannerExit { navController.popBackStack() }
                }, 
                modifier = Modifier.size(ScannerTokens.DenseTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Scanner",
                    tint = ScannerTheme.colors.scannerTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Right: Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(ScannerTokens.DenseTouchTarget)
                        .clip(CircleShape)
                        .background(if (showGrid) ScannerTheme.colors.scannerSurfaceVariant else Color.Transparent)
                        .clickable { showGrid = !showGrid },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Grid4x4,
                        contentDescription = "Toggle Grid",
                        tint = if (showGrid) ScannerTheme.colors.scannerActionPrimary else ScannerTheme.colors.scannerTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Ratio Toggle
                Box(
                    modifier = Modifier
                        .height(ScannerTokens.DenseTouchTarget)
                        .clip(RoundedCornerShape(ScannerTokens.DenseTouchTarget / 2))
                        .background(ScannerTheme.colors.scannerSurfaceVariant)
                        .clickable {
                            currentRatio = when (currentRatio) {
                                ScanRatio.RATIO_4_3 -> ScanRatio.RATIO_1_1
                                ScanRatio.RATIO_1_1 -> ScanRatio.RATIO_4_3
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Aspect Ratio",
                            tint = ScannerTheme.colors.scannerTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Crossfade(targetState = currentRatio.label, label = "ratioText") { text ->
                            Text(
                                text = text,
                                color = ScannerTheme.colors.scannerTextPrimary,
                                fontSize = ScannerTokens.Microcopy,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Flash Toggle
                Box(
                    modifier = Modifier
                        .size(ScannerTokens.DenseTouchTarget)
                        .clip(CircleShape)
                        .background(
                            if (flashMode != ImageCapture.FLASH_MODE_OFF) ScannerTheme.colors.scannerSurfaceVariant
                            else Color.Transparent
                        )
                        .clickable {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = flashMode, label = "flashIcon") { mode ->
                        val icon = when (mode) {
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            else -> Icons.Default.FlashOff
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Flash Mode",
                            tint = if (mode == ImageCapture.FLASH_MODE_ON) ScannerTheme.colors.scannerActionPrimary else ScannerTheme.colors.scannerTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Auto Capture Toggle removed

            }
        }

        },
        bottomBar = {
        // ── BOTTOM DOCK (Mode Toggle + Capture Controls) ───────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = ScannerTokens.bottomDockPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScannerTokens.viewportPadding)
                    .background(
                        color = ScannerTheme.colors.floatingSurfaceColor.copy(alpha = ScannerTokens.OverlayAlpha),
                        shape = RoundedCornerShape(ScannerTokens.CardCornerRadius)
                    )
                    .padding(vertical = 12.dp)
            ) {
                // ── MODE TOGGLE ──────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val modes = listOf(ScanMode.SINGLE to "Single", ScanMode.BATCH to "Batch")
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ScannerTokens.ButtonCornerRadius))
                            .background(ScannerTheme.colors.scannerOverlaySurface)
                            .padding(4.dp)
                    ) {
                        modes.forEach { (mode, label) ->
                            val selected = scanMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ScannerTokens.ButtonCornerRadius))
                                    .background(if (selected) ScannerTheme.colors.chipSelectedBackground else Color.Transparent)
                                    .clickable { viewModel.setScanMode(mode) }
                                    .defaultMinSize(minHeight = ScannerTokens.DenseTouchTarget)
                                    .padding(horizontal = 22.dp, vertical = 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) ScannerTheme.colors.scannerActionPrimary else ScannerTheme.colors.scannerTextSecondary,
                                    fontSize = ScannerTokens.CaptionText,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ScannerTokens.actionGroupSpacing))

                // ── CAPTURE CONTROLS ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
            // Gallery button (left)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onGalleryClick,
                    containerColor = ScannerTheme.colors.scannerSurfaceVariant,
                    contentColor = ScannerTheme.colors.scannerTextPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(ScannerTokens.GalleryFabSize),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Open Gallery",
                        modifier = Modifier.size(ScannerTokens.GalleryFabIconSize)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Gallery",
                    color = ScannerTheme.colors.scannerTextSecondary,
                    fontSize = ScannerTokens.Microcopy
                )
            }

            // Capture button (center) — compact shutter with press animation
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val shutterScale by animateFloatAsState(
                targetValue = if (isPressed) 0.85f else 1f,
                animationSpec = tween(ScannerTokens.FastDuration),
                label = "shutterScale"
            )

            Box(
                modifier = Modifier
                    .size(ScannerTokens.CaptureButtonOuterSize)
                    .graphicsLayer {
                        scaleX = shutterScale
                        scaleY = shutterScale
                    }
                    .clip(CircleShape)
                    .border(4.dp, ScannerTheme.colors.scannerActionPrimary, CircleShape)
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isCapturing && !isRebinding
                    ) {
                        triggerCapture()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(ScannerTokens.CaptureButtonInnerSize)
                        .clip(CircleShape)
                        .background(if (isRebinding) Color.LightGray else Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                )
            }

            // Right side: Thumbnail stack + session status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(ScannerTokens.ThumbnailSize)
            ) {
                if (scannedPages.isNotEmpty() || pendingScansCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(ScannerTokens.ThumbnailSize)
                            .clickable {
                                val hasIdlePages = scannedPages.any { it.state == com.stutool.stutool.ui.screens.scanner.models.ScanPageState.IDLE }
                                val isQueueActive = viewModel.isQueueActive
                                
                                if (isQueueActive) {
                                    // Resume active queue at correct stage
                                    val stage = viewModel.activeBatch.value?.stage
                                    if (stage == com.stutool.stutool.ui.screens.scanner.models.QueueStage.CROP) {
                                        navController.navigate("edge_crop")
                                    } else {
                                        navController.navigate("batch_filter_pager")
                                    }
                                } else if (hasIdlePages && pendingScansCount == 0) {
                                    // Start batch queue for all IDLE pages - NEVER bypass to review
                                    viewModel.startBatchCropQueue(
                                        context = context, 
                                        source = com.stutool.stutool.ui.screens.scanner.models.BatchSource.CAMERA,
                                        onProceedToCrop = { navController.navigate("edge_crop") },
                                        onProceedToCropReview = { navController.navigate("batch_crop_review") },
                                        onProceedToFilter = { navController.navigate("batch_filter_pager") }
                                    )
                                }
                                // If no IDLE pages and no active queue: stay on camera (user still capturing)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Stacked shadow cards
                        if (scannedPages.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(ScannerTokens.ThumbnailSize)
                                    .offset(x = 2.dp, y = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                        }
                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .size(ScannerTokens.ThumbnailSize)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            if (scannedPages.isNotEmpty()) {
                                val lastPage = scannedPages.last()
                                val thumbUri = lastPage.thumbnailCacheUri ?: lastPage.originalUri
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(thumbUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Recent Scan",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (pendingScansCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ScannerTheme.colors.scannerCropOverlay),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(ScannerTokens.BadgeSize)
                                    )
                                }
                            }
                        }

                        // Badge — use derivedStateOf to avoid full recomposition
                        val count by remember { derivedStateOf { scannedPages.size + pendingScansCount } }
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(ScannerTokens.BadgeSize)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = ScannerTokens.Microcopy,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(ScannerTokens.ThumbnailSize))
                }
            } // Ends Thumbnail Column
        } // Ends Capture Controls Row
            } // Ends inner Column
        } // Ends Bottom Dock Box
        }
    ) { innerPadding ->
        // ── CENTER: Camera Preview (aspect-ratio constrained) ───────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                    start = ScannerTokens.viewportPadding,
                    end = ScannerTokens.viewportPadding
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(currentRatio.displayAspect)
                    .clip(RoundedCornerShape(ScannerTokens.CardCornerRadius))
                    .pointerInput(previewView, cameraControl) {
                        detectTapGestures { offset ->
                            val view = previewView ?: return@detectTapGestures
                            val control = cameraControl ?: return@detectTapGestures

                            // Set focus ring state locally (isolated from queue/viewmodel)
                            focusPoint = offset
                            Log.d("ScannerScreen", "Focus: request at (${offset.x}, ${offset.y})")
                            
                            // Launch lightweight UI animations
                            coroutineScope.launch {
                                focusAlpha.snapTo(1f)
                                focusScale.snapTo(1.5f)
                                focusScale.animateTo(1f, tween(300))
                                kotlinx.coroutines.delay(1000)
                                focusAlpha.animateTo(0f, tween(300))
                                focusPoint = null
                            }

                            // CameraX focus request
                            val factory = view.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                                .build()

                            // Cancel previous metering to prevent queueing
                            control.cancelFocusAndMetering()
                            val result = control.startFocusAndMetering(action)
                            result.addListener({
                                try {
                                    val focusResult = result.get()
                                    Log.d("ScannerScreen", "Focus: metering completed, focused=${focusResult.isFocusSuccessful}")
                                } catch (e: Exception) {
                                    Log.w("ScannerScreen", "Focus: metering failed/cancelled", e)
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    }
            ) {
                // Camera PreviewView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this
                        }
                    }
                )

                // Grid Overlay (scoped to preview area only)
                if (showGrid) {
                    GridOverlay()
                }

                // Rebind overlay
                if (isRebinding) {
                    Box(modifier = Modifier.fillMaxSize().background(ScannerTheme.colors.scannerBackground))
                }

                // Auto-capture badges removed
                // Subtle processing indicator (non-blocking)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isCapturing,
                    enter = androidx.compose.animation.fadeIn(tween(ScannerTokens.FastDuration)),
                    exit = androidx.compose.animation.fadeOut(tween(ScannerTokens.FastDuration)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(ScannerTheme.colors.scannerCropOverlay, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                
                // White flash overlay for capture feedback
                if (showFlash) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(100)
                        showFlash = false
                    }
                }

                // Focus Ring UI (Lightweight render over everything)
                val currentFocusPoint = focusPoint
                if (currentFocusPoint != null && focusAlpha.value > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.White.copy(alpha = focusAlpha.value),
                            radius = 30.dp.toPx() * focusScale.value,
                            center = currentFocusPoint,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

    } // Ends Root Box
} // Ends ScannerScreen

// ═══════════════════════════════════════════════════════════════════════════════
// ── Grid Overlay ──
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 1.dp.toPx()
        val color = Color.White.copy(alpha = 0.25f)
        
        // Vertical lines
        drawLine(color, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), strokeWidth)
        drawLine(color, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), strokeWidth)
        
        // Horizontal lines
        drawLine(color, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), strokeWidth)
        drawLine(color, Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), strokeWidth)
    }
}

// End of file
