package com.stutool.stutool.ui.screens.scanner

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.stutool.stutool.ui.theme.ScannerTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stutool.stutool.ui.components.ReorderableAutoScrollEffect
import com.stutool.stutool.ui.components.ReorderableItem
import com.stutool.stutool.ui.components.reorderHandle
import com.stutool.stutool.ui.components.reorderableItem
import com.stutool.stutool.ui.components.rememberReorderableState
import com.stutool.stutool.ui.screens.scanner.models.*

private data class PageItem(
    override val id: String,
    val page: ScanPage,
    val index: Int
) : ReorderableItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReviewScreen(
    navController: NavController,
    viewModel: ScannerViewModel
) {
    val pages by viewModel.scannedPages.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val documentTitle by viewModel.documentTitle.collectAsState()
    val context = LocalContext.current
    var showExportOptions by remember { mutableStateOf(false) }
    var showExportSuccess by remember { mutableStateOf(false) }
    var showExportError by remember { mutableStateOf(false) }
    var exportErrorMessage by remember { mutableStateOf("") }
    var exportSuccessUri by remember { mutableStateOf<Uri?>(null) }
    
    // G1: STRICT REVIEW GATE
    LaunchedEffect(viewModel.isQueueActive, pages) {
        val queueActive = viewModel.isQueueActive
        val hasUnprocessed = pages.any {
            it.state == com.stutool.stutool.ui.screens.scanner.models.ScanPageState.IDLE ||
            it.state == com.stutool.stutool.ui.screens.scanner.models.ScanPageState.CROPPED
        }

        if (queueActive || hasUnprocessed) {
            if (hasUnprocessed && !queueActive) {
                viewModel.startAddMoreQueue(
                    context = context,
                    onProceedToCrop = {
                        navController.navigate("edge_crop") {
                            popUpTo("document_review") { inclusive = false }
                        }
                    },
                    onProceedToCropReview = {
                        navController.navigate("batch_crop_review") {
                            popUpTo("document_review") { inclusive = false }
                        }
                    },
                    onProceedToFilter = {
                        navController.navigate("batch_filter_pager") {
                            popUpTo("document_review") { inclusive = false }
                        }
                    }
                )
            } else if (queueActive) {
                val stage = viewModel.activeBatch.value?.stage
                val route = if (stage == com.stutool.stutool.ui.screens.scanner.models.QueueStage.CROP) {
                    "edge_crop"
                } else {
                    "batch_filter_pager"
                }
                navController.navigate(route) {
                    popUpTo("document_review") { inclusive = true }
                }
            }
        } else if (pages.isEmpty()) {
            viewModel.restoreSession(context)
        }
    }
    
    var exportFormat by remember { mutableStateOf(ExportType.PDF) }
    var pageSize by remember { mutableStateOf("A4") }
    var colorMode by remember { mutableStateOf("Color") }
    var marginEnabled by remember { mutableStateOf(false) }

    val showExitDialog by viewModel.showExitDialog.collectAsState()
    val isIntentionalExit by viewModel.isIntentionalScannerExit.collectAsState()
    val isTransitioning by viewModel.isTransitioningBetweenScannerStages.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setTransitioning(false)
    }

    LaunchedEffect(pages.size) {
        if (pages.isEmpty() && !isIntentionalExit && !isTransitioning) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    BackHandler {
        viewModel.requestScannerExit {
            navController.popBackStack("home", inclusive = false)
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

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableState(lazyListState, itemSpacing = 8.dp)

    val visiblePages = remember(pages) { pages.filter { !it.isNew } }
    val reorderableItems = remember(pages) {
        visiblePages.mapIndexed { index, page ->
            PageItem(id = page.id, page = page, index = pages.indexOf(page))
        }
    }

    val onReorder: (String, Int) -> Unit = { key, targetIndex ->
        // CRITICAL: Read directly from the ViewModel's live StateFlow value.
        // Do NOT use `reorderableItems` here — it is only refreshed during
        // recomposition, and multiple swaps can fire between recompositions
        // during a fast continuous drag, causing stale index lookups.
        val livePages = viewModel.scannedPages.value
        val fromAbsIndex = livePages.indexOfFirst { it.id == key }
        val liveVisible = livePages.filter { !it.isNew }
        val toAbsIndex = if (targetIndex in liveVisible.indices) {
            livePages.indexOf(liveVisible[targetIndex])
        } else -1
        if (fromAbsIndex >= 0 && toAbsIndex >= 0 && fromAbsIndex != toAbsIndex) {
            viewModel.reorderPages(fromAbsIndex, toAbsIndex)
        }
    }

    ReorderableAutoScrollEffect(reorderState, reorderableItems, onReorder)

    val editingPageIndex by viewModel.editingPageIndex.collectAsState()
    LaunchedEffect(editingPageIndex) {
        if (editingPageIndex >= 0) {
            val visibleIdx = visiblePages.indexOfFirst { pages.indexOf(it) == editingPageIndex }
            if (visibleIdx >= 0) {
                lazyListState.animateScrollToItem(visibleIdx)
            }
            viewModel.setEditingPageIndex(-1)
        }
    }

    // Add the Iteration 3 UI overlay
    com.stutool.stutool.ui.components.CropDecisionOverlay(viewModel = viewModel)

    ScannerRoot(
        topBar = {
            // Clean, minimal custom TopBar to reduce top margin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScannerTheme.colors.scannerBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.requestScannerExit {
                        navController.popBackStack("home", inclusive = false)
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ScannerTheme.colors.scannerTextPrimary)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = if (documentTitle.isNotBlank()) documentTitle else "Scanned Document",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = ScannerTokens.ToolbarTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ScannerTheme.colors.scannerTextPrimary
                    )
                    Text(
                        text = "${visiblePages.size} page${if (visiblePages.size != 1) "s" else ""}${if (viewModel.sessionDirty) " \u2022 Unsaved" else ""}",
                        fontSize = ScannerTokens.CaptionText,
                        color = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.6f)
                    )
                }
                
                var showRenameDialog by remember { mutableStateOf(false) }
                val isExporting = exportState !is ExportState.Idle && exportState !is ExportState.Success && exportState !is ExportState.Failed
                IconButton(onClick = { showRenameDialog = true }, enabled = !isExporting) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = if (!isExporting) ScannerTheme.colors.scannerTextPrimary else Color.Gray)
                }
                if (showRenameDialog) {
                    RenameDialog(
                        currentTitle = documentTitle,
                        onDismiss = { showRenameDialog = false },
                        onConfirm = { newTitle ->
                            viewModel.documentTitle.value = newTitle
                            showRenameDialog = false
                        }
                    )
                }
            }
        },
        bottomBar = {
            // Anchored, premium compact dock
            Surface(
                color = ScannerTheme.colors.scannerBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isExporting = exportState !is ExportState.Idle && exportState !is ExportState.Success && exportState !is ExportState.Failed
                    val isQueueActive = viewModel.isQueueActive
                    val isProcessingIncomplete = pages.any { it.state == ScanPageState.IDLE || it.state == ScanPageState.CROPPED || it.thumbnailCacheUri == null } || isQueueActive
                    val isLocked = isExporting || isProcessingIncomplete
                    
                    var lastActionTime by remember { mutableLongStateOf(0L) }
                    fun canPerformAction(): Boolean {
                        val now = System.currentTimeMillis()
                        if (now - lastActionTime < 500) return false
                        lastActionTime = now
                        return true
                    }

                    OutlinedButton(
                        onClick = {
                            if (canPerformAction()) {
                                navController.navigate("scanner_camera")
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !isLocked,
                        shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Page", fontWeight = FontWeight.SemiBold, fontSize = ScannerTokens.ButtonText)
                    }

                    Button(
                        onClick = {
                            if (!canPerformAction()) return@Button
                            if (pages.isEmpty()) {
                                Toast.makeText(context, "No pages to export", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            showExportOptions = true
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = pages.isNotEmpty() && !isLocked,
                        shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScannerTheme.colors.scannerActionPrimary
                        )
                    ) {
                        Text("Export", fontWeight = FontWeight.Bold, fontSize = ScannerTokens.ButtonText)
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScannerTheme.colors.scannerBackground)
        ) {
            if (pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pages yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                val isExporting = exportState !is ExportState.Idle && exportState !is ExportState.Success && exportState !is ExportState.Failed
                val isQueueActive = viewModel.isQueueActive
                val isProcessingIncomplete = pages.any { it.state == ScanPageState.IDLE || it.state == ScanPageState.CROPPED || it.thumbnailCacheUri == null } || isQueueActive
                val isLocked = isExporting || isProcessingIncomplete
                
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 120.dp,
                        start = ScannerTokens.ListContentPaddingHorizontal,
                        end = ScannerTokens.ListContentPaddingHorizontal
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = reorderableItems,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        // Only animate placement for items that are not actively being dragged.
                        val isDragging = reorderState.isDraggingItem(item.id)
                        val animationModifier = if (isDragging) Modifier else Modifier.animateItem()

                        CompactPageCard(
                            item = item,
                            pageNumber = if (reorderState.isDraggingItem(item.id)) {
                                reorderState.draggedItemInitialIndex + 1
                            } else if (reorderState.isDragging) {
                                // If dragging ANY item, hold original index for all items.
                                // We can find the original index from the viewmodel/unmodified list.
                                // However, reorderState.items might not have original indices.
                                // Wait, the items list itself IS mutating, so its index is changing.
                                // Instead of recalculating, maybe we should use the item.index from the viewmodel?
                                // Let's check what 'item.index' contains. Is it the original index?
                                item.index + 1
                            } else {
                                index + 1
                            },
                            reorderState = reorderState,
                            allItems = reorderableItems,
                            isLocked = isLocked,
                            onReorder = onReorder,
                            onDelete = { viewModel.removePage(context, item.index) },
                            onEdit = {
                                android.util.Log.e("CropTrace_LOAD", "[A] DocumentReviewScreen Crop icon pressed")
                                android.util.Log.e("CropTrace_LOAD", "page.id: ${item.page.id}")
                                android.util.Log.e("CropTrace_LOAD", "page.transform.cropQuad: ${item.page.transform.cropQuad}")
                                android.util.Log.e("CropTrace_LOAD", "page.transform.effectiveCropQuad: ${item.page.transform.effectiveCropQuad()}")
                                viewModel.prepareForEdit(item.index)
                                navController.navigate("edge_crop")
                            },
                            onFilter = {
                                viewModel.prepareForEdit(item.index)
                                navController.navigate("single_page_filter_editor/${item.page.id}")
                            },
                            modifier = animationModifier
                        )
                    }
                }
            }

            // --- EXPORT OPTIONS ---
            if (showExportOptions) {
                val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    sheetState = sheetState,
                    onDismissRequest = { showExportOptions = false },
                    containerColor = ScannerTheme.colors.scannerBackground,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text("Export Document", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ScannerTheme.colors.scannerTextPrimary)
                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Format", fontWeight = FontWeight.Medium, fontSize = ScannerTokens.CaptionText, color = ScannerTheme.colors.scannerTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExportChip(selected = exportFormat == ExportType.PDF, label = "PDF", onClick = { exportFormat = ExportType.PDF })
                            ExportChip(selected = exportFormat == ExportType.JPG, label = "Image", onClick = { exportFormat = ExportType.JPG })
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        if (exportFormat == ExportType.PDF) {
                            Text("Page Size", fontWeight = FontWeight.Medium, fontSize = ScannerTokens.CaptionText, color = ScannerTheme.colors.scannerTextSecondary)
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExportChip(selected = pageSize == "A4", label = "A4", onClick = { pageSize = "A4" })
                                ExportChip(selected = pageSize == "Letter", label = "Letter", onClick = { pageSize = "Letter" })
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Button(
                            onClick = {
                                showExportOptions = false
                                val params = mapOf(
                                    "pageSize" to pageSize,
                                    "isGrayscale" to (colorMode == "Grayscale"),
                                    "marginEnabled" to marginEnabled
                                )
                                viewModel.startExport(context, exportFormat, params)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = ScannerTheme.colors.scannerActionPrimary)
                        ) {
                            Text("Create Document", fontWeight = FontWeight.Bold, fontSize = ScannerTokens.ButtonText)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // --- EXPORT PROGRESS (Stable, Localized) ---
            AnimatedVisibility(
                visible = exportState is ExportState.Preparing || exportState is ExportState.Exporting,
                enter = fadeIn(tween(ScannerTokens.FastDuration)),
                exit = fadeOut(tween(ScannerTokens.FastDuration)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                Surface(
                    color = ScannerTheme.colors.scannerBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp),
                            color = ScannerTheme.colors.scannerActionPrimary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Exporting Document", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val statusText = when (val s = exportState) {
                                is ExportState.Preparing -> "Preparing files..."
                                is ExportState.Exporting -> "Processing page ${s.pageIndex} of ${pages.size}"
                                else -> ""
                            }
                            Text(statusText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            LaunchedEffect(exportState) {
                when (val state = exportState) {
                    is ExportState.Success -> {
                        exportSuccessUri = state.uri
                        showExportSuccess = true
                    }
                    is ExportState.Failed -> {
                        exportErrorMessage = state.error.message ?: "Unknown error"
                        showExportError = true
                    }
                    else -> {}
                }
            }

            // --- EXPORT SUCCESS ---
            if (showExportSuccess && exportSuccessUri != null) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showExportSuccess = false
                        viewModel.resetExportState()
                    },
                    containerColor = ScannerTheme.colors.scannerBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Export Successful", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Your document is ready.",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        val mimeType = viewModel.getMimeTypeForExport(exportFormat)

                        Button(
                            onClick = {
                                try {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(exportSuccessUri, mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(viewIntent, "Open Document"))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = ScannerTheme.colors.scannerActionPrimary)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open File", fontWeight = FontWeight.Bold, fontSize = ScannerTokens.ButtonText)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = mimeType
                                        putExtra(Intent.EXTRA_STREAM, exportSuccessUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share", fontWeight = FontWeight.SemiBold, fontSize = ScannerTokens.ButtonText)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                showExportSuccess = false
                                viewModel.clearSession(context)
                                viewModel.resetExportState()
                                navController.popBackStack("home", inclusive = false)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // --- EXPORT ERROR ---
            if (showExportError) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showExportError = false
                        viewModel.resetExportState()
                    },
                    containerColor = ScannerTheme.colors.scannerBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Export Failed", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            exportErrorMessage,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                showExportError = false
                                viewModel.retryExport()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                showExportError = false
                                viewModel.resetExportState()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ScannerTokens.ChipCornerRadius))
            .background(
                if (selected) ScannerTheme.colors.scannerActionPrimary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color = if (selected) ScannerTheme.colors.scannerActionPrimary else Color.Transparent,
                shape = RoundedCornerShape(ScannerTokens.ChipCornerRadius)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (selected) ScannerTheme.colors.scannerActionPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CompactPageCard(
    item: PageItem,
    pageNumber: Int,
    reorderState: com.stutool.stutool.ui.components.ReorderableState,
    allItems: List<PageItem>,
    isLocked: Boolean,
    onReorder: (String, Int) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRestoring = item.page.thumbnailCacheUri == null
    val isProcessing = item.page.state == ScanPageState.IDLE || item.page.state == ScanPageState.CROPPED || isRestoring

    // G4: Removed shadow and heavy card background in favor of a clean, lightweight layout
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .reorderableItem(reorderState, item.id)
            .background(Color.Transparent)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Handle
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Reorder",
            tint = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier
                .run {
                    if (!isLocked) reorderHandle(reorderState, item.id, allItems, onReorder) else this
                }
                .padding(end = 12.dp)
        )

        // Thumbnail with Clean Framing
        val thumbnailUri = item.page.thumbnailCacheUri 
            ?: item.page.croppedCacheUri 
            ?: item.page.originalUri
        Box(
            modifier = Modifier
                .size(72.dp, 96.dp)
                .clip(RoundedCornerShape(ScannerTokens.CardCornerRadiusSmall))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(ScannerTokens.CardCornerRadiusSmall))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Page $pageNumber",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text Info (Premium spacing and clarity)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = "Page $pageNumber",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Processing state indicator
                val stateColor = when {
                    isProcessing -> Color(0xFFFFA726)
                    item.page.state == ScanPageState.FILTERED -> Color(0xFF4CAF50)
                    item.page.state == ScanPageState.EXPORTED -> Color(0xFF42A5F5)
                    item.page.state == ScanPageState.CROPPED -> Color(0xFFFFEB3B)
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(stateColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRestoring) "Restoring..." else if (isProcessing) "Processing..." else item.page.transform.filter.label,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onFilter,
                enabled = !isLocked && !isProcessing,
                modifier = Modifier
                    .size(ScannerTokens.DenseTouchTarget)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = "Filter", tint = if (!isLocked && !isProcessing) MaterialTheme.colorScheme.onSurface else Color.Gray, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onEdit,
                enabled = !isLocked && !isProcessing,
                modifier = Modifier
                    .size(ScannerTokens.DenseTouchTarget)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(Icons.Default.Crop, contentDescription = "Edit", tint = if (!isLocked && !isProcessing) MaterialTheme.colorScheme.onSurface else Color.Gray, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onDelete,
                enabled = !isLocked && !isProcessing,
                modifier = Modifier
                    .size(ScannerTokens.DenseTouchTarget)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (!isLocked && !isProcessing) MaterialTheme.colorScheme.error else Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun RenameDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Document", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(title) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = ScannerTheme.colors.scannerBackground
    )
}
