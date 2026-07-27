package com.stutool.stutool.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.stutool.stutool.ui.screens.scanner.models.CropMode
import com.stutool.stutool.ui.screens.scanner.models.ScanPage
import com.stutool.stutool.ui.screens.scanner.utils.Quad
import com.stutool.stutool.ui.theme.ScannerTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.onSizeChanged

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BatchCropReviewScreen(
    navController: NavController,
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val pages by viewModel.batchPages.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
    val cropReviewQuads by viewModel.cropReviewQuads.collectAsState()
    val cropReviewRotations by viewModel.cropReviewRotations.collectAsState()
    val cropMode by viewModel.cropMode.collectAsState()

    val batchPages = remember(pages, activeBatch) {
        val pageIds = activeBatch?.pageIds ?: emptyList()
        pages.filter { it.id in pageIds }
    }

    if (batchPages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(ScannerTheme.colors.scannerBackground))
        return
    }

    val pageCount = batchPages.size
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }
    val filmstripState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Sync filmstrip with pager (centered if possible)
    LaunchedEffect(pagerState.currentPage) {
        filmstripState.animateScrollToItem(
            index = maxOf(0, pagerState.currentPage - 2),
            scrollOffset = 0
        )
    }

    var isCommitting by remember { mutableStateOf(false) }
    var expandTriggers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showBackConfirmation by remember { mutableStateOf(false) }
    var isDraggingHandle by remember { mutableStateOf(false) }
    var toolbarHeightPx by remember { mutableIntStateOf(0) }

    androidx.activity.compose.BackHandler {
        showBackConfirmation = true
    }

    if (showBackConfirmation) {
        AlertDialog(
            onDismissRequest = { showBackConfirmation = false },
            title = { Text("Discard scan?") },
            text = { Text("You have unsaved review changes. Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = { 
                    showBackConfirmation = false
                    viewModel.clearSession(context)
                    navController.popBackStack("scanner_camera", inclusive = false)
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val density = LocalDensity.current
    // Convert measured toolbar height to dp, add spacing for content clearance
    val toolbarInsetDp = with(density) { toolbarHeightPx.toDp() } + 16.dp
    // Bottom dock clearance: filmstrip (~88dp) + bottom padding + page indicator
    val bottomInsetDp = 120.dp

    Box(modifier = Modifier.fillMaxSize().background(ScannerTheme.colors.scannerBackground)) {
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = toolbarInsetDp, bottom = bottomInsetDp),
            userScrollEnabled = !isCommitting && !isDraggingHandle,
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) { pageIndex ->
            val page = batchPages.getOrNull(pageIndex) ?: return@HorizontalPager
            
            CropReviewPage(
                page = page,
                viewModel = viewModel,
                cropReviewQuads = cropReviewQuads,
                cropReviewRotations = cropReviewRotations,
                cropMode = cropMode,
                isActivePage = pagerState.currentPage == pageIndex,
                expandTrigger = expandTriggers[page.id] ?: 0,
                onDragStart = { isDraggingHandle = true },
                onDragEnd = { isDraggingHandle = false }
            )
        }

        // --- TOP FLOATING SYSTEM ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = ScannerTokens.viewportPadding,
                    vertical = ScannerTokens.floatingToolbarPadding
                )
                .onSizeChanged { toolbarHeightPx = it.height }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = ScannerTheme.colors.floatingSurfaceColor.copy(alpha = ScannerTokens.OverlayAlpha),
                        shape = RoundedCornerShape(ScannerTokens.CardCornerRadius)
                    )
            ) {
                // Row 1: Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showBackConfirmation = true },
                        modifier = Modifier.size(ScannerTokens.MinTouchTarget)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Review Auto Crop", fontWeight = FontWeight.SemiBold, color = ScannerTheme.colors.scannerTextPrimary, fontSize = ScannerTokens.ToolbarTitle)
                        Text("${pagerState.currentPage + 1} of $pageCount", color = ScannerTheme.colors.scannerTextPrimary, fontSize = ScannerTokens.CaptionText, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            isCommitting = true
                            viewModel.commitBatchCropReviewEdits(context) {
                                navController.navigate("batch_filter_pager") {
                                    popUpTo("batch_crop_review") { inclusive = true }
                                }
                            }
                        },
                        enabled = !isCommitting
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = ScannerTokens.ButtonText, color = ScannerTheme.colors.scannerActionPrimary)
                    }
                }

                // Row 2: Crop tools strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScannerTokens.CropToolbarHorizontalPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Crop Mode Selector
                    Surface(
                        onClick = {
                            val newMode = if (cropMode == CropMode.PERSPECTIVE) CropMode.RECTANGULAR else CropMode.PERSPECTIVE
                            viewModel.setCropMode(newMode)
                        },
                        shape = RoundedCornerShape(ScannerTokens.ChipCornerRadius),
                        color = ScannerTheme.colors.chipSelectedBackground,
                        modifier = Modifier.height(ScannerTokens.DenseTouchTarget)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp), tint = ScannerTheme.colors.scannerActionPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (cropMode == CropMode.RECTANGULAR) "Rectangle" else "Perspective",
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
                                    val activePage = batchPages.getOrNull(pagerState.currentPage)
                                    if (activePage != null) {
                                        expandTriggers = expandTriggers.toMutableMap().apply {
                                            put(activePage.id, (get(activePage.id) ?: 0) + 1)
                                        }
                                    }
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
                                    val activePage = batchPages.getOrNull(pagerState.currentPage)
                                    if (activePage != null) {
                                        viewModel.updateCropReviewRotation(activePage.id, false)
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate Left", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(ScannerTokens.CropToolbarIconSize))
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
                                    val activePage = batchPages.getOrNull(pagerState.currentPage)
                                    if (activePage != null) {
                                        viewModel.updateCropReviewRotation(activePage.id, true)
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate Right", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(ScannerTokens.CropToolbarIconSize))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Right", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        
        // --- SWIPE HINTS ---
        if (pagerState.currentPage > 0) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Previous",
                tint = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(32.dp)
            )
        }
        if (pagerState.currentPage < pageCount - 1) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = "Next",
                tint = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(32.dp)
            )
        }
        
        // --- BOTTOM FLOATING SYSTEM ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
                    .padding(vertical = 10.dp)
            ) {
                LazyRow(
                    state = filmstripState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(batchPages, key = { _, p -> p.id }) { idx, p ->
                        val isActive = idx == pagerState.currentPage
                        val borderAlpha by animateFloatAsState(targetValue = if (isActive) 1f else 0f, label = "border")
                        val rotation = cropReviewRotations[p.id] ?: p.transform.effectiveRotation()
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.DarkGray)
                                .border(
                                    width = 2.dp,
                                    color = ScannerTheme.colors.scannerActionPrimary.copy(alpha = borderAlpha),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(idx)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = p.thumbnailCacheUri ?: p.originalUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = rotation.toFloat())
                            )
                            val conf = p.transform.edgeConfidence
                            val modified = cropReviewQuads.containsKey(p.id) || cropReviewRotations.containsKey(p.id)
                            val badgeColor = if (modified) Color.Blue else if (conf > 0.8f) Color.Green else Color.Red
                            Box(modifier = Modifier.size(8.dp).align(Alignment.TopEnd).padding(2.dp).clip(CircleShape).background(badgeColor))
                        }
                    }
                }
            }
        }
        
        if (isCommitting) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ScannerTheme.colors.scannerActionPrimary)
            }
        }
    }
}

@Composable
fun CropReviewPage(
    page: ScanPage,
    viewModel: ScannerViewModel,
    cropReviewQuads: Map<String, Quad>,
    cropReviewRotations: Map<String, Int>,
    cropMode: CropMode,
    isActivePage: Boolean,
    expandTrigger: Int,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val context = LocalContext.current
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageWidth by remember { mutableIntStateOf(1000) }
    var imageHeight by remember { mutableIntStateOf(1000) }
    var isLoading by remember { mutableStateOf(true) }

    // Load bitmap once when the composable first enters composition.
    // beyondViewportPageCount=1 keeps adjacent pages composed, so bitmaps stay alive.
    // Bitmap is only recycled when the composable leaves composition entirely (via DisposableEffect).
    LaunchedEffect(page.id) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(page.originalUri)
                val fullBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (fullBitmap != null) {
                    imageWidth = fullBitmap.width
                    imageHeight = fullBitmap.height
                    val displayScale = 1200f / maxOf(fullBitmap.width, fullBitmap.height)
                    if (displayScale < 1f) {
                        val scaled = Bitmap.createScaledBitmap(
                            fullBitmap,
                            (fullBitmap.width * displayScale).toInt(),
                            (fullBitmap.height * displayScale).toInt(),
                            true
                        )
                        if (scaled !== fullBitmap) fullBitmap.recycle()
                        displayBitmap = scaled
                    } else {
                        displayBitmap = fullBitmap
                    }
                }
            } catch (e: Exception) {
                // handle safely
            }
        }
        isLoading = false
    }

    // Clean up bitmap when the composable leaves composition
    DisposableEffect(page.id) {
        onDispose {
            displayBitmap?.recycle()
            displayBitmap = null
        }
    }

    val rotation = cropReviewRotations[page.id] ?: page.transform.effectiveRotation()
    val activeQuad = cropReviewQuads[page.id] ?: page.transform.effectiveCropQuad() ?: Quad.defaultForImage(imageWidth, imageHeight)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val bmp = displayBitmap
        if (bmp != null) {
            SharedCropEditor(
                bitmap = bmp,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                rotation = rotation,
                cropMode = cropMode,
                quad = activeQuad,
                isActivePage = isActivePage,
                onQuadUpdated = { newQuad -> viewModel.updateCropReviewQuad(page.id, newQuad) },
                expandTrigger = expandTrigger,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            // Lightweight loading placeholder — no AsyncImage, no dual-path rendering
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ScannerTheme.colors.scannerActionPrimary,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
