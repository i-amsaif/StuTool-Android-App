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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stutool.stutool.ui.components.FloatingBottomActionBar
import com.stutool.stutool.ui.components.FullScreenProcessingView
import com.stutool.stutool.ui.components.ProcessingState
import com.stutool.stutool.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    navController: NavController,
    viewModel: ImageToPdfViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedImages by viewModel.selectedImages.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val margin by viewModel.margin.collectAsState()
    val mergeEnabled by viewModel.mergeEnabled.collectAsState()
    val isSettingsExpanded by viewModel.isSettingsExpanded.collectAsState()
    val isConverting by viewModel.isConverting.collectAsState()
    val conversionResult by viewModel.conversionResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()

    val showProcessingUi = isConverting || conversionResult != null || processingError != null

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        bottomBar = {
            FloatingBottomActionBar(
                show = selectedImages.isNotEmpty(),
                onClick = { viewModel.convertToPdf(context) },
                isEnabled = selectedImages.isNotEmpty() && !isConverting,
                isLoading = isConverting,
                loadingText = "Converting...",
                icon = Icons.Filled.Description,
                text = "Convert Image to PDF"
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Image to PDF",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
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
                    // ── Header Card ──
                    ImageToPdfHeaderCard()

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Main Content ──
                    // Upload zone
                    ImageUploadZone(
                        selectedImages = selectedImages,
                        context = context,
                        onClick = { launcher.launch(arrayOf("image/jpeg", "image/png")) },
                        onRemove = { viewModel.removeImage(it) },
                        onReorder = { id, toIndex -> viewModel.moveImage(id, toIndex) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── PDF Settings ──
                    PdfSettingsCard(
                        orientation = orientation,
                        onOrientationChange = { viewModel.setOrientation(it) },
                        pageSize = pageSize,
                        onPageSizeChange = { viewModel.setPageSize(it) },
                        margin = margin,
                        onMarginChange = { viewModel.setMargin(it) },
                        mergeEnabled = mergeEnabled,
                        onMergeChange = { viewModel.setMergeEnabled(it) },
                        isExpanded = isSettingsExpanded,
                        onToggleExpand = { viewModel.toggleSettingsExpanded() }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
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
            val state = when {
                isConverting -> ProcessingState.Preparing(statusMessage)
                conversionResult != null -> {
                    val result = conversionResult!!
                    ProcessingState.Success(
                        title = if (result.isMerged) "PDF Created Successfully!" else "${result.uris.size} PDFs Created!",
                        subtitle = "Your PDF has been saved to Documents/StuTool"
                    )
                }
                processingError != null -> ProcessingState.Error(message = processingError!!)
                else -> ProcessingState.Idle
            }

            FullScreenProcessingView(
                state = state,
                onCancel = { viewModel.cancelProcessing() },
                onSuccessPrimaryAction = {
                    val uri = conversionResult!!.uris.first()
                    val encodedUri = android.util.Base64.encodeToString(
                        uri.toString().toByteArray(),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    navController.navigate("reader_screen/$encodedUri")
                },
                onSuccessSecondaryAction = {
                    val uri = conversionResult!!.uris.first()
                    val shareUri = if (uri.scheme == "file") {
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "com.stutool.stutool.fileprovider",
                            java.io.File(uri.path ?: "")
                        )
                    } else {
                        uri
                    }
                    sharePdf(context, shareUri)
                },
                onSuccessTertiaryAction = { viewModel.resetState() },
                onErrorRetry = {
                    viewModel.clearProcessingError()
                    viewModel.convertToPdf(context)
                },
                onErrorCancel = { viewModel.clearProcessingError() }
            )
        }
    } // Close outer Box
}

// ═══════════════════════════════════════════════
// ── Header Card ──
// ═══════════════════════════════════════════════

@Composable
private fun ImageToPdfHeaderCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red icon container
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Image to PDF",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Select JPG, PNG, or other image files to combine them into a single PDF document.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── Image Upload Zone ──
// ═══════════════════════════════════════════════

@Composable
private fun ImageUploadZone(
    selectedImages: List<SelectedImage>,
    context: Context,
    onClick: () -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (String, Int) -> Unit
) {
    if (selectedImages.isEmpty()) {
        // Empty state — dashed upload zone
        ImageUploadPlaceholder(onClick = onClick)
    } else {
        // Images selected — show list
        ImageListSection(
            images = selectedImages,
            context = context,
            onRemove = onRemove,
            onAddMore = onClick,
            onReorder = onReorder
        )
    }
}

@Composable
private fun ImageUploadPlaceholder(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "img_upload_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "img_pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Dark background
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
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Select or drop images here",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Add JPG or PNG files to convert them to a PDF.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── Image List Section ──
// ═══════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageListSection(
    images: List<SelectedImage>,
    context: Context,
    onRemove: (String) -> Unit,
    onAddMore: () -> Unit,
    onReorder: (String, Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val spacingPx = with(density) { 8.dp.toPx() }
    
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
                val targetIndex = (draggedItemInitialIndex + offsetIndex).coerceIn(0, images.size - 1)

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
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${images.size} image${if (images.size != 1) "s" else ""} selected",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "Ready to convert",
                    color = SuccessColor,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Image items in a LazyColumn for smooth reordering
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(
                items = images,
                key = { it.id }
            ) { item ->
                val isDragging = draggedItemKey == item.id
                val index = images.indexOf(item)
                
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
                                val itemHeight = itemHeights[item.id] ?: 0
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
                    ImageFileItem(
                        uri = item.uri,
                        context = context,
                        index = index + 1,
                        onRemove = { onRemove(item.id) },
                        dragHandle = {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .pointerInput(item.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedItemKey = item.id
                                                draggedItemInitialIndex = images.indexOf(item)
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
                                                    val itemHeight = itemHeights[item.id] ?: 0
                                                    val itemAndSpacing = itemHeight + spacingPx
                                                    val listDelta = (currentDraggedIndex - draggedItemInitialIndex) * itemAndSpacing
                                                    val visualTop = itemInfo.offset + (absoluteDragOffset - listDelta)
                                                    val visualCenter = visualTop + itemHeight / 2f
                                                    
                                                    val edgeSize = 120f
                                                    val maxScrollSpeed = 20f

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
                            updatedMap[item.id] = height
                            itemHeights = updatedMap
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageFileItem(
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
        
        if (dragHandle != null) {
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Index badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Image icon
        Icon(
            Icons.Filled.Image,
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
                Text(text = fileSize, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        // Remove button
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Outlined.RemoveCircleOutline,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── PDF Settings Card ──
// ═══════════════════════════════════════════════

@Composable
private fun PdfSettingsCard(
    orientation: PageOrientation,
    onOrientationChange: (PageOrientation) -> Unit,
    pageSize: PageSize,
    onPageSizeChange: (PageSize) -> Unit,
    margin: MarginOption,
    onMarginChange: (MarginOption) -> Unit,
    mergeEnabled: Boolean,
    onMergeChange: (Boolean) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .animateContentSize()
    ) {
        // Title Row (Always Visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "PDF Settings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Collapsible Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                // ── Page Orientation ──
                Text("Page Orientation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggleButton(
                        label = "Portrait",
                        isSelected = orientation == PageOrientation.PORTRAIT,
                        onClick = { onOrientationChange(PageOrientation.PORTRAIT) },
                        modifier = Modifier.weight(1f)
                    )
                    SettingsToggleButton(
                        label = "Landscape",
                        isSelected = orientation == PageOrientation.LANDSCAPE,
                        onClick = { onOrientationChange(PageOrientation.LANDSCAPE) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Page Size ──
                Text("Page Size", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(10.dp))
                PageSizeDropdown(
                    selectedSize = pageSize,
                    onSizeChange = onPageSizeChange
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Margin ──
                Text("Margin", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MarginOption.entries.forEach { opt ->
                        SettingsToggleButton(
                            label = opt.label,
                            isSelected = margin == opt,
                            onClick = { onMarginChange(opt) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Merge Toggle ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Switch(
                        checked = mergeEnabled,
                        onCheckedChange = onMergeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Merge all images into one PDF file",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSizeDropdown(
    selectedSize: PageSize,
    onSizeChange: (PageSize) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedSize.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            PageSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = {
                        Text(
                            size.label,
                            color = if (size == selectedSize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            fontWeight = if (size == selectedSize) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSizeChange(size)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── Convert Button ──
// ═══════════════════════════════════════════════

@Composable
private fun ConvertButton(
    enabled: Boolean,
    isConverting: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(50),
        enabled = enabled
    ) {
        if (isConverting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Converting...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Convert Image to PDF",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── End ──
// ═══════════════════════════════════════════════
