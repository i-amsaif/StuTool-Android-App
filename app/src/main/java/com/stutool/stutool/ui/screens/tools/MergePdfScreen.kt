package com.stutool.stutool.ui.screens.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalDensity
import com.stutool.stutool.ui.theme.*
import com.stutool.stutool.ui.components.FullScreenProcessingView
import com.stutool.stutool.ui.components.ProcessingState
import com.stutool.stutool.ui.components.FloatingBottomActionBar

// ── Step Indicator Data ──
private enum class MergeStep(val label: String, val icon: ImageVector) {
    SELECT("Select", Icons.Outlined.UploadFile),
    REVIEW("Review", Icons.Outlined.Checklist),
    MERGE("Merge", Icons.Filled.CallMerge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    navController: NavController,
    viewModel: MergePdfViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val isMerging by viewModel.isMerging.collectAsState()
    val mergeSuccess by viewModel.mergeSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()

    val showProcessingUi = isMerging || mergeSuccess != null || processingError != null

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
        }
    }

    // Determine current step
    val currentStep = when {
        mergeSuccess != null -> MergeStep.MERGE
        selectedFiles.isNotEmpty() -> MergeStep.REVIEW
        else -> MergeStep.SELECT
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CallMerge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Merge PDF", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                FloatingBottomActionBar(
                    show = currentStep == MergeStep.REVIEW,
                    onClick = { viewModel.mergePdfs(context) },
                    isEnabled = selectedFiles.size >= 2 && !isMerging,
                    isLoading = isMerging,
                    loadingText = "Merging...",
                    icon = Icons.Filled.CallMerge,
                    text = "Merge ${selectedFiles.size} PDFs"
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // ── Step Indicator ──
                StepIndicator(currentStep = currentStep)

                Spacer(modifier = Modifier.height(28.dp))

                // ── Main Content ──
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + slideInVertically { it / 4 } togetherWith
                                fadeOut(animationSpec = tween(200)) + slideOutVertically { -it / 4 }
                    },
                    label = "merge_content"
                ) { step ->
                    when (step) {
                        MergeStep.SELECT -> {
                            UploadPlaceholder(onClick = { launcher.launch(arrayOf("application/pdf")) })
                        }
                        MergeStep.REVIEW -> {
                            FileListSection(
                                files = selectedFiles,
                                context = context,
                                onRemove = { viewModel.removeFile(it.id) },
                                onAddMore = { launcher.launch(arrayOf("application/pdf")) },
                                onReorder = { id, toIndex -> viewModel.moveFile(id, toIndex) }
                            )
                        }
                        MergeStep.MERGE -> {
                            // Handled by FullScreenProcessingView
                        }
                    }
                }
                }

            // ── Snackbar ──
            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK", color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    errorMessage?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
            } // Close inner Box
        }
        // end Scaffold content lambda

        AnimatedVisibility(
            visible = showProcessingUi,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            val processingState = when {
                processingError != null -> ProcessingState.Error(message = processingError!!)
                mergeSuccess != null -> ProcessingState.Success(
                    title = "PDF Merged Successfully!",
                    subtitle = "Your merged PDF has been saved to Documents/StuTool",
                    primaryActionLabel = "Open in Viewer",
                    secondaryActionLabel = "Share PDF",
                    tertiaryActionLabel = "Start Over"
                )
                else -> ProcessingState.Processing(progress = -1f, message = statusMessage)
            }
            
            FullScreenProcessingView(
                state = processingState,
                onCancel = { viewModel.cancelProcessing() },
                onSuccessPrimaryAction = {
                    val uri = mergeSuccess
                    if (uri != null) {
                        val encodedUri = android.util.Base64.encodeToString(
                            uri.toString().toByteArray(),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("reader_screen/$encodedUri")
                    }
                },
                onSuccessSecondaryAction = {
                    val uri = mergeSuccess
                    if (uri != null) {
                        val shareUri = if (uri.scheme == "file") {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "com.stutool.stutool.fileprovider",
                                java.io.File(uri.path ?: "")
                            )
                        } else {
                            uri
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Merged PDF"))
                    }
                },
                onSuccessTertiaryAction = { viewModel.resetState() },
                onErrorRetry = {
                    viewModel.clearProcessingError()
                    viewModel.mergePdfs(context)
                },
                onErrorCancel = { viewModel.clearProcessingError() }
            )
        }
    } // Close outer Box
}

// ═══════════════════════════════════════════════
// ── Step Indicator ──
// ═══════════════════════════════════════════════

@Composable
private fun StepIndicator(currentStep: MergeStep) {
    val steps = MergeStep.entries

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { index, step ->
            val isActive = step.ordinal <= currentStep.ordinal
            val isCurrent = step == currentStep

            StepDot(
                step = step,
                isActive = isActive,
                isCurrent = isCurrent
            )

            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (steps[index + 1].ordinal <= currentStep.ordinal)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                )
            }
        }
    }
}

@Composable
private fun StepDot(step: MergeStep, isActive: Boolean, isCurrent: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "step_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = step.label,
                tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = step.label,
            fontSize = 11.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════
// ── Upload Placeholder ──
// ═══════════════════════════════════════════════

@Composable
fun UploadPlaceholder(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "upload_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Dashed border upload zone
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // Dark background card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(20.dp)
                    )
            )

            // Dashed border overlay
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                drawRoundRect(
                    color = primaryColor.copy(alpha = pulseAlpha),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
                    )
                )
            }

            // Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                // Upload icon container
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Select PDF Files",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Select two or more PDFs to merge them into one document",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // CTA Button
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select PDF Files", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // How it works section
        HowItWorksSection()
    }
}

// ═══════════════════════════════════════════════
// ── How It Works Section ──
// ═══════════════════════════════════════════════

@Composable
private fun HowItWorksSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "How it works",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        HowItWorksItem(
            number = "1",
            title = "Select Files",
            description = "Choose two or more PDF files from your device",
            icon = Icons.Outlined.UploadFile
        )
        HowItWorksItem(
            number = "2",
            title = "Review & Reorder",
            description = "Review your files and remove any unwanted ones",
            icon = Icons.Outlined.Checklist
        )
        HowItWorksItem(
            number = "3",
            title = "Merge & Save",
            description = "Tap merge and your combined PDF is saved instantly",
            icon = Icons.Outlined.Done
        )
    }
}

@Composable
private fun HowItWorksItem(number: String, title: String, description: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Number badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }

        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ═══════════════════════════════════════════════
// ── File List Section ──
// ═══════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListSection(
    files: List<SelectedPdf>,
    context: Context,
    onRemove: (SelectedPdf) -> Unit,
    onAddMore: () -> Unit,
    onReorder: (String, Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val spacingPx = with(density) { 10.dp.toPx() }
    
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var draggedItemInitialIndex by remember { mutableStateOf(-1) }
    var currentDraggedIndex by remember { mutableStateOf(-1) }
    var absoluteDragOffset by remember { mutableStateOf(0f) }
    var itemHeights by remember { mutableStateOf(mapOf<String, Int>()) }
    var autoScrollSpeed by remember { mutableStateOf(0f) }

    val checkReorder = {
        if (draggedItemKey != null) {
            val itemHeight = itemHeights[draggedItemKey] ?: 0
            val itemAndSpacing = itemHeight + spacingPx
            if (itemAndSpacing > 0) {
                val offsetIndex = (absoluteDragOffset / itemAndSpacing).roundToInt()
                val targetIndex = (draggedItemInitialIndex + offsetIndex).coerceIn(0, files.size - 1)

                if (targetIndex != currentDraggedIndex && currentDraggedIndex != -1) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReorder(draggedItemKey!!, targetIndex)
                    currentDraggedIndex = targetIndex
                }
            }
        }
    }

    // Auto-scroll logic
    LaunchedEffect(autoScrollSpeed) {
        if (autoScrollSpeed != 0f) {
            while (autoScrollSpeed != 0f) {
                val scrolled = lazyListState.scrollBy(autoScrollSpeed)
                absoluteDragOffset += scrolled
                checkReorder()
                delay(16)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${files.size} files selected",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    if (files.size >= 2) "Ready to merge" else "Add at least ${2 - files.size} more",
                    color = if (files.size >= 2) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(
                onClick = onAddMore,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add more", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File items in a LazyColumn
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp), // Limit height to avoid covering entire screen
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = files,
                key = { it.id }
            ) { pdf ->
                val isDragging = draggedItemKey == pdf.id
                val index = files.indexOf(pdf)
                
                val placementModifier = if (isDragging) {
                    Modifier
                } else {
                    Modifier.animateItemPlacement()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(placementModifier)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                val itemHeight = itemHeights[pdf.id] ?: 0
                                val itemAndSpacing = itemHeight + spacingPx
                                val listDelta = (currentDraggedIndex - draggedItemInitialIndex) * itemAndSpacing
                                translationY = absoluteDragOffset - listDelta
                                
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = 8.dp.toPx()
                                alpha = 0.95f
                            }
                        }
                ) {
                    FileItem(
                        uri = pdf.uri,
                        context = context,
                        index = index + 1,
                        onRemove = { onRemove(pdf) },
                        dragHandle = {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .pointerInput(pdf.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedItemKey = pdf.id
                                                draggedItemInitialIndex = files.indexOf(pdf)
                                                currentDraggedIndex = draggedItemInitialIndex
                                                absoluteDragOffset = 0f
                                            },
                                            onDragEnd = {
                                                draggedItemKey = null
                                                autoScrollSpeed = 0f
                                            },
                                            onDragCancel = {
                                                draggedItemKey = null
                                                autoScrollSpeed = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                absoluteDragOffset += dragAmount.y
                                                
                                                checkReorder()

                                                val layoutInfo = lazyListState.layoutInfo
                                                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                                
                                                val itemInfo = layoutInfo.visibleItemsInfo.find { it.key == draggedItemKey }
                                                if (itemInfo != null) {
                                                    val itemHeight = itemHeights[pdf.id] ?: 0
                                                    val itemAndSpacing = itemHeight + spacingPx
                                                    val listDelta = (currentDraggedIndex - draggedItemInitialIndex) * itemAndSpacing
                                                    val visualTop = itemInfo.offset + (absoluteDragOffset - listDelta)
                                                    val visualCenter = visualTop + itemHeight / 2f
                                                    
                                                    val edgeSize = 150f
                                                    val maxScrollSpeed = 25f

                                                    autoScrollSpeed = when {
                                                        visualCenter < edgeSize -> {
                                                            -((edgeSize - visualCenter) / edgeSize).coerceIn(0f, 1f) * maxScrollSpeed
                                                        }
                                                        visualCenter > viewportHeight - edgeSize -> {
                                                            ((visualCenter - (viewportHeight - edgeSize)) / edgeSize).coerceIn(0f, 1f) * maxScrollSpeed
                                                        }
                                                        else -> 0f
                                                    }
                                                }
                                            }
                                        )
                                    }
                            )
                        },
                        onLayout = { height ->
                            val updatedMap = itemHeights.toMutableMap()
                            updatedMap[pdf.id] = height
                            itemHeights = updatedMap
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Info card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Files will be merged in the order shown above. Saved to Documents/StuTool.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

    }
}

// ═══════════════════════════════════════════════
// ── File Item ──
// ═══════════════════════════════════════════════

@Composable
fun FileItem(
    uri: Uri, 
    context: Context, 
    index: Int, 
    onRemove: () -> Unit,
    dragHandle: @Composable (() -> Unit)? = null,
    onLayout: (Int) -> Unit = {}
) {
    val fileName = remember(uri) { getFileName(context, uri) }
    val fileSize = remember(uri) { getFileSize(context, uri) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onLayout(it.size.height) }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle if provided
        dragHandle?.invoke()
        
        Spacer(modifier = Modifier.width(4.dp))

        // Index number badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$index",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // File icon
        Icon(
            Icons.Filled.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // File info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (fileSize.isNotEmpty()) {
                Text(
                    text = fileSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Outlined.RemoveCircleOutline,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Removed inline SuccessView

// ═══════════════════════════════════════════════
// ── Utility Functions ──
// ═══════════════════════════════════════════════

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = it.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Unknown file"
}

fun getFileSize(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    val sizeBytes = it.getLong(index)
                    return formatFileSize(sizeBytes)
                }
            }
        }
    }
    return ""
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

fun sharePdf(context: Context, uri: Uri) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Merged PDF"))
}
