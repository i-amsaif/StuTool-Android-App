package com.stutool.stutool.ui.screens.tools

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stutool.stutool.ui.components.*
import com.stutool.stutool.ui.components.FloatingBottomActionBar
import com.stutool.stutool.ui.theme.*
import com.stutool.stutool.ui.components.FullScreenProcessingView
import com.stutool.stutool.ui.components.ProcessingState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PagesPerSheetScreen(
    navController: NavController,
    viewModel: PagesPerSheetViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val pagesPerSheet by viewModel.pagesPerSheet.collectAsState()
    val sheetSize by viewModel.sheetSize.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val readingDirection by viewModel.readingDirection.collectAsState()
    val outerMargins by viewModel.outerMargins.collectAsState()
    val innerSpacing by viewModel.innerSpacing.collectAsState()
    val drawBorder by viewModel.drawBorder.collectAsState()
    val layoutExpanded by viewModel.layoutExpanded.collectAsState()
    val spacingExpanded by viewModel.spacingExpanded.collectAsState()
    val orderExpanded by viewModel.orderExpanded.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val resultUri by viewModel.resultUri.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()

    val showProcessingUi = isProcessing || resultUri != null || processingError != null

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
        }
    }

    // LazyListState for the single root LazyColumn
    val lazyListState = rememberLazyListState()

    // Shared reordering state — only meaningful when files exist
    val reorderState = rememberReorderableState(
        lazyListState = lazyListState,
        itemSpacing = 10.dp
    )

    // Frame-based auto-scroll effect
    if (selectedFiles.isNotEmpty()) {
        ReorderableAutoScrollEffect(
            state = reorderState,
            items = selectedFiles,
            onReorder = { id, toIndex -> viewModel.moveFile(id, toIndex) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Multiple Pages per Sheet",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
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
        bottomBar = {
            FloatingBottomActionBar(
                show = selectedFiles.isNotEmpty(),
                onClick = { viewModel.generatePdf(context) },
                isEnabled = selectedFiles.isNotEmpty() && !isProcessing,
                isLoading = isProcessing,
                loadingText = "Processing...",
                icon = Icons.Filled.Description,
                text = "Generate PDF"
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
            // ── Main Editor (single LazyColumn architecture) ──
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)
                ) {
                    // ── Header ──
                    item(key = "__header") {
                        PPSHeaderCard()
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // ── Upload / File List Header ──
                    if (selectedFiles.isEmpty()) {
                        item(key = "__upload_placeholder") {
                            PPSUploadPlaceholder(
                                onClick = { launcher.launch(arrayOf("application/pdf", "image/*")) }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    } else {
                        // File list header row
                        item(key = "__file_list_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${selectedFiles.size} file${if (selectedFiles.size != 1) "s" else ""} selected",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                TextButton(
                                    onClick = { launcher.launch(arrayOf("application/pdf", "image/*")) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add more", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // ── Reorderable File Items ──
                        items(
                            items = selectedFiles,
                            key = { it.id }
                        ) { file ->
                            val index = selectedFiles.indexOf(file)
                            val isDragging = reorderState.isDraggingItem(file.id)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isDragging) Modifier else Modifier.animateItemPlacement())
                                    .reorderableItem(reorderState, file.id)
                            ) {
                                PPSFileItem(
                                    file = file,
                                    context = context,
                                    index = index + 1,
                                    onRemove = { viewModel.removeFile(file.id) },
                                    reorderState = reorderState,
                                    items = selectedFiles,
                                    onReorder = { id, toIndex -> viewModel.moveFile(id, toIndex) }
                                )
                            }
                        }

                        item(key = "__file_list_spacer") {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    // ── Layout Settings (Collapsible) ──
                    item(key = "__layout_settings") {
                        PPSCollapsibleSection(
                            title = "Layout",
                            icon = Icons.Outlined.GridView,
                            isExpanded = layoutExpanded,
                            onToggle = { viewModel.toggleLayoutExpanded() },
                            summary = "${pagesPerSheet.label} · ${orientation.name.lowercase().replaceFirstChar { it.uppercase() }}"
                        ) {
                            PPSLayoutContent(
                                pagesPerSheet = pagesPerSheet,
                                onPagesPerSheetChange = { viewModel.setPagesPerSheet(it) },
                                sheetSize = sheetSize,
                                onSheetSizeChange = { viewModel.setSheetSize(it) },
                                orientation = orientation,
                                onOrientationChange = { viewModel.setOrientation(it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ── Spacing Settings (Collapsible) ──
                    item(key = "__spacing_settings") {
                        PPSCollapsibleSection(
                            title = "Spacing",
                            icon = Icons.Outlined.SpaceDashboard,
                            isExpanded = spacingExpanded,
                            onToggle = { viewModel.toggleSpacingExpanded() },
                            summary = "Inner: ${innerSpacing.label} · Margins: ${outerMargins.top}pt"
                        ) {
                            PPSSpacingContent(
                                outerMargins = outerMargins,
                                onTopChange = { viewModel.setOuterMarginTop(it) },
                                onBottomChange = { viewModel.setOuterMarginBottom(it) },
                                onLeftChange = { viewModel.setOuterMarginLeft(it) },
                                onRightChange = { viewModel.setOuterMarginRight(it) },
                                innerSpacing = innerSpacing,
                                onInnerSpacingChange = { viewModel.setInnerSpacing(it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ── Order Settings (Collapsible) ──
                    item(key = "__order_settings") {
                        PPSCollapsibleSection(
                            title = "Order & Style",
                            icon = Icons.Outlined.Tune,
                            isExpanded = orderExpanded,
                            onToggle = { viewModel.toggleOrderExpanded() },
                            summary = "${readingDirection.label} · Border: ${if (drawBorder) "ON" else "OFF"}"
                        ) {
                            PPSOrderContent(
                                readingDirection = readingDirection,
                                onReadingDirectionChange = { viewModel.setReadingDirection(it) },
                                drawBorder = drawBorder,
                                onDrawBorderChange = { viewModel.setDrawBorder(it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }


                }
            // ── Snackbar ──
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
            val processingState = when {
                isProcessing -> ProcessingState.Preparing(statusMessage)
                processingError != null -> ProcessingState.Error(message = processingError!!)
                resultUri != null -> ProcessingState.Success(
                    title = "PDF Generated Successfully!",
                    subtitle = "Your PDF has been saved to Documents/StuTool",
                    primaryActionLabel = "Open",
                    secondaryActionLabel = "Share",
                    tertiaryActionLabel = "Start Over"
                )
                else -> ProcessingState.Idle
            }
            FullScreenProcessingView(
                state = processingState,
                onCancel = { viewModel.cancelProcessing() },
                onSuccessPrimaryAction = {
                    resultUri?.let { uri ->
                        val encodedUri = android.util.Base64.encodeToString(
                            uri.toString().toByteArray(),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("reader_screen/$encodedUri")
                    }
                },
                onSuccessSecondaryAction = {
                    resultUri?.let { uri ->
                        val shareUri = if (uri.scheme == "file") {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "com.stutool.stutool.fileprovider",
                                java.io.File(uri.path ?: "")
                            )
                        } else uri
                        sharePdf(context, shareUri)
                    }
                },
                onSuccessTertiaryAction = { viewModel.resetState() },
                onErrorRetry = {
                    viewModel.clearProcessingError()
                    viewModel.generatePdf(context)
                },
                onErrorCancel = { viewModel.clearProcessingError() }
            )
        }
    } // Close outer Box
}

// ═══════════════════════════════════════════════
// ── Reusable Collapsible Section ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSCollapsibleSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    summary: String = "",
    content: @Composable () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "pps_arrow_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .animateContentSize(animationSpec = tween(300))
    ) {
        // Header (always visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    if (!isExpanded && summary.isNotEmpty()) {
                        Text(
                            summary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
        }

        // Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                content()
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── Header Card ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSHeaderCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Multiple Pages per Sheet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Select PDFs and images to arrange multiple pages onto a single sheet.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── Upload Placeholder ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSUploadPlaceholder(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pps_upload_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pps_pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(20.dp)
                )
        )

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
                "Select PDF & Image Files",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Select PDFs and images to arrange multiple pages per sheet.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── File Item (with drag handle) ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSFileItem(
    file: SelectedFile,
    context: Context,
    index: Int,
    onRemove: () -> Unit,
    reorderState: ReorderableState,
    items: List<SelectedFile>,
    onReorder: (String, Int) -> Unit
) {
    val fileName = remember(file.uri) { getFileName(context, file.uri) }
    val fileSize = remember(file.uri) { getFileSize(context, file.uri) }
    val mimeType = remember(file.uri) { context.contentResolver.getType(file.uri) ?: "" }
    val isImage = mimeType.startsWith("image/")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 8.dp)
                .reorderHandle(
                    state = reorderState,
                    key = file.id,
                    items = items,
                    onReorder = onReorder
                )
        )

        // Index badge
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

        // File type icon
        Icon(
            imageVector = if (isImage) Icons.Filled.Image else Icons.Filled.PictureAsPdf,
            contentDescription = null,
            tint = if (isImage) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
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
            Row {
                if (fileSize.isNotEmpty()) {
                    Text(text = fileSize, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                if (isImage) {
                    if (fileSize.isNotEmpty()) {
                        Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Text("1 page", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
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
// ── Layout Section Content ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSLayoutContent(
    pagesPerSheet: PagesPerSheetOption,
    onPagesPerSheetChange: (PagesPerSheetOption) -> Unit,
    sheetSize: SheetSize,
    onSheetSizeChange: (SheetSize) -> Unit,
    orientation: SheetOrientation,
    onOrientationChange: (SheetOrientation) -> Unit
) {
    // Pages per sheet
    Text("Pages per Sheet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PagesPerSheetOption.entries.take(3).forEach { opt ->
                PPSToggleButton(
                    label = opt.label,
                    isSelected = pagesPerSheet == opt,
                    onClick = { onPagesPerSheetChange(opt) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PagesPerSheetOption.entries.drop(3).forEach { opt ->
                PPSToggleButton(
                    label = opt.label,
                    isSelected = pagesPerSheet == opt,
                    onClick = { onPagesPerSheetChange(opt) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Sheet size
    Text("Sheet Size", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(10.dp))
    PPSSheetSizeDropdown(selectedSize = sheetSize, onSizeChange = onSheetSizeChange)

    Spacer(modifier = Modifier.height(20.dp))

    // Orientation
    Text("Sheet Orientation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PPSToggleButton(
            label = "Portrait",
            isSelected = orientation == SheetOrientation.PORTRAIT,
            onClick = { onOrientationChange(SheetOrientation.PORTRAIT) },
            modifier = Modifier.weight(1f)
        )
        PPSToggleButton(
            label = "Landscape",
            isSelected = orientation == SheetOrientation.LANDSCAPE,
            onClick = { onOrientationChange(SheetOrientation.LANDSCAPE) },
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════
// ── Spacing Section Content ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSSpacingContent(
    outerMargins: OuterMargins,
    onTopChange: (Int) -> Unit,
    onBottomChange: (Int) -> Unit,
    onLeftChange: (Int) -> Unit,
    onRightChange: (Int) -> Unit,
    innerSpacing: InnerSpacingOption,
    onInnerSpacingChange: (InnerSpacingOption) -> Unit
) {
    // Outer margins
    Text("Outer Margins (pts)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarginField("Top", outerMargins.top, onTopChange, Modifier.weight(1f))
        MarginField("Bottom", outerMargins.bottom, onBottomChange, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarginField("Left", outerMargins.left, onLeftChange, Modifier.weight(1f))
        MarginField("Right", outerMargins.right, onRightChange, Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Inner spacing
    Text("Inner Spacing", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InnerSpacingOption.entries.forEach { opt ->
            PPSToggleButton(
                label = opt.label,
                isSelected = innerSpacing == opt,
                onClick = { onInnerSpacingChange(opt) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── Order Section Content ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSOrderContent(
    readingDirection: ReadingDirection,
    onReadingDirectionChange: (ReadingDirection) -> Unit,
    drawBorder: Boolean,
    onDrawBorderChange: (Boolean) -> Unit
) {
    // Reading direction
    Text("Reading Direction", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReadingDirection.entries.forEach { dir ->
            PPSToggleButton(
                label = dir.label,
                isSelected = readingDirection == dir,
                onClick = { onReadingDirectionChange(dir) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Draw border
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Switch(
            checked = drawBorder,
            onCheckedChange = onDrawBorderChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "Draw border around pages",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════
// ── Margin Field ──
// ═══════════════════════════════════════════════

@Composable
private fun MarginField(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                newText.toIntOrNull()?.let { onChange(it.coerceIn(0, 100)) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ═══════════════════════════════════════════════
// ── Shared UI Components ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSToggleButton(
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
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PPSSheetSizeDropdown(
    selectedSize: SheetSize,
    onSizeChange: (SheetSize) -> Unit
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
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
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
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            SheetSize.entries.forEach { size ->
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
// ── Generate Button ──
// ═══════════════════════════════════════════════

@Composable
private fun PPSGenerateButton(
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(50),
        enabled = enabled
    ) {
        if (isProcessing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Processing...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

