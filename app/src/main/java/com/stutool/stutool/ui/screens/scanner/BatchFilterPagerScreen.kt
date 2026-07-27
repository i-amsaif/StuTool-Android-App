package com.stutool.stutool.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.stutool.stutool.ui.screens.scanner.models.ScanPage
import com.stutool.stutool.ui.screens.scanner.utils.ScanFilter
import com.stutool.stutool.ui.theme.ScannerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// BatchFilterPagerScreen
//
// Immersive professional filter workflow. Image-first hierarchy.
// No logic processing modifications, presentation only.
// ─────────────────────────────────────────────────────────────────────────────

private val OVERRIDE_BADGE = Color(0xFFFF6F00)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BatchFilterPagerScreen(
    navController: NavController,
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val pages by viewModel.batchPages.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
    val applyFilterToAll by viewModel.applyFilterToAll.collectAsState()
    val livePreviewBitmap by viewModel.livePreviewBitmap.collectAsState()
    val isPreviewProcessing by viewModel.isPreviewProcessing.collectAsState()
    val thumbnailLoadingIds by viewModel.thumbnailLoadingIds.collectAsState()
    val restoredPageIndex by viewModel.currentFilterPageIndex.collectAsState()
    val showExitDialog by viewModel.showExitDialog.collectAsState()
    val scannedPages by viewModel.scannedPages.collectAsState()

    val batchPages = remember(pages, activeBatch) {
        val pageIds = activeBatch?.pageIds ?: emptyList()
        pages.filter { it.id in pageIds }
    }

    val isIntentionalExit by viewModel.isIntentionalScannerExit.collectAsState()
    val isTransitioning by viewModel.isTransitioningBetweenScannerStages.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setTransitioning(false)
    }

    val totalPages = scannedPages.size + (activeBatch?.pageIds?.size ?: 0)
    LaunchedEffect(totalPages) {
        if (totalPages == 0 && !isIntentionalExit && !isTransitioning) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    BackHandler {
        viewModel.requestScannerExit {
            viewModel.clearFilterPagerState()
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
    
    if (!isIntentionalExit && !isTransitioning && batchPages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(ScannerTheme.colors.scannerBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Session Corrupted", color = ScannerTheme.colors.scannerTextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No pages found in the active batch.", color = Color.LightGray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { 
                    viewModel.clearSession(context)
                    navController.navigate("home") { popUpTo(0) }
                }) {
                    Text("Discard Session & Return Home")
                }
            }
        }
        return
    }

    val pageCount = batchPages.size

    val pagerState = rememberPagerState(
        initialPage = restoredPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    ) { pageCount }

    val filmstripState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val currentPage = remember(pagerState.currentPage, batchPages) {
        batchPages.getOrNull(pagerState.currentPage)
    }

    var selectedFilter by remember { mutableStateOf(currentPage?.transform?.filter ?: ScanFilter.ENHANCED) }
    var filterStrength by remember { mutableFloatStateOf(currentPage?.transform?.filterStrength ?: 1.0f) }

    LaunchedEffect(pagerState.currentPage) {
        val page = batchPages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        selectedFilter = page.transform.filter
        filterStrength = page.transform.filterStrength
        viewModel.setFilterPageIndex(pagerState.currentPage)
        filmstripState.animateScrollToItem(
            index = pagerState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            scrollOffset = -80
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearFilterPagerState()
        }
    }

    ScannerRoot(
        topBar = {},
        bottomBar = {}
    ) {
        Scaffold(
            containerColor = Color.Transparent,
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
                                shape = RoundedCornerShape(ScannerTokens.CardCornerRadius)
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.requestScannerExit {
                                    viewModel.clearFilterPagerState()
                                    navController.popBackStack()
                                }
                            },
                            modifier = Modifier.size(ScannerTokens.MinTouchTarget)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(24.dp))
                        }

                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            val title by viewModel.documentTitle.collectAsState()
                            Text(
                                text = if (title.isNotBlank()) title else "Filter Documents",
                                color = ScannerTheme.colors.scannerTextPrimary,
                                fontSize = ScannerTokens.ToolbarTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${pagerState.currentPage + 1} of $pageCount",
                                color = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.6f),
                                fontSize = ScannerTokens.CaptionText
                            )
                        }

                        // Crop action
                        IconButton(
                            onClick = { navController.navigate("filter_crop") },
                            modifier = Modifier
                                .clip(RoundedCornerShape(ScannerTokens.ChipCornerRadius))
                                .background(ScannerTheme.colors.scannerOverlaySurface)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Crop, "Crop", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        }
                        
                        Spacer(Modifier.width(8.dp))

                        // Apply-to-all toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(ScannerTokens.ChipCornerRadius))
                                .background(if (applyFilterToAll) ScannerTheme.colors.scannerActionPrimary.copy(alpha = 0.2f) else ScannerTheme.colors.scannerOverlaySurface)
                                .clickable { viewModel.setApplyFilterToAll(!applyFilterToAll) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                if (applyFilterToAll) Icons.Default.SelectAll else Icons.Default.CropSquare,
                                null,
                                tint = if (applyFilterToAll) ScannerTheme.colors.scannerActionPrimary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "All",
                                color = if (applyFilterToAll) ScannerTheme.colors.scannerActionPrimary else Color.White.copy(alpha = 0.7f),
                                fontSize = ScannerTokens.LabelText,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        TextButton(
                            onClick = {
                                viewModel.setTransitioning(true)
                                viewModel.clearFilterPagerState()
                                viewModel.finalizeProcessedPages(
                                    onSuccess = {
                                        navController.navigate("document_review") {
                                            popUpTo("batch_filter_pager") { inclusive = true }
                                        }
                                    },
                                    onError = {
                                        viewModel.setTransitioning(false)
                                        android.widget.Toast.makeText(context, "Error: Document pages empty", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = ScannerTheme.colors.scannerActionPrimary)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, fontSize = ScannerTokens.ButtonText)
                        }
                    }
                }
            },
            bottomBar = {
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
                            .padding(vertical = 10.dp)
                    ) {
                        // --- FILMSTRIP ---
                        LazyRow(
                            state = filmstripState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(batchPages, key = { _, page -> page.id }) { idx, page ->
                                val isActive = idx == pagerState.currentPage
                                val isLoading = page.id in thumbnailLoadingIds
                                
                                val borderAlpha by animateFloatAsState(targetValue = if (isActive) 1f else 0f, animationSpec = tween(ScannerTokens.FastDuration), label = "borderAlpha")
                                val scale by animateFloatAsState(targetValue = if (isActive) 1.05f else 1f, animationSpec = tween(ScannerTokens.FastDuration), label = "scale")

                                Box(
                                    modifier = Modifier
                                        .size(50.dp, 66.dp)
                                        .graphicsLayer { scaleX = scale; scaleY = scale }
                                        .clip(RoundedCornerShape(ScannerTokens.CardCornerRadiusSmall))
                                        .border(
                                            width = 2.dp,
                                            color = ScannerTheme.colors.scannerActionPrimary.copy(alpha = borderAlpha),
                                            shape = RoundedCornerShape(ScannerTokens.CardCornerRadiusSmall)
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(idx)
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(page.thumbnailCacheUri ?: page.originalUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = ScannerTheme.colors.scannerTextPrimary,
                                                strokeWidth = 1.5.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (page.transform.hasFilterOverride) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(8.dp)
                                                .background(OVERRIDE_BADGE, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))

                        // --- FILTER RAIL ---
                        FilterSelectionRow(
                            selectedFilter = selectedFilter,
                            onFilterSelected = { filter ->
                                selectedFilter = filter
                                viewModel.applyFilterForPager(
                                    context, filter, filterStrength, applyFilterToAll
                                )
                            }
                        )

                        // --- INTENSITY SLIDER ---
                        AnimatedVisibility(
                            visible = selectedFilter != ScanFilter.ORIGINAL,
                            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Intensity",
                                    color = ScannerTheme.colors.scannerTextSecondary,
                                    fontSize = ScannerTokens.CaptionText,
                                    maxLines = 1,
                                    modifier = Modifier.widthIn(min = ScannerTokens.IntensityLabelMinWidth)
                                )
                                Slider(
                                    value = filterStrength,
                                    onValueChange = { newVal ->
                                        filterStrength = newVal
                                        viewModel.applyFilterForPager(
                                            context, selectedFilter, newVal, applyFilterToAll
                                        )
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ScannerTheme.colors.scannerActionPrimary,
                                        activeTrackColor = ScannerTheme.colors.scannerActionPrimary,
                                        inactiveTrackColor = ScannerTheme.colors.scannerOverlaySurface
                                    )
                                )
                                Text(
                                    "${(filterStrength * 100).toInt()}%",
                                    color = ScannerTheme.colors.scannerTextSecondary,
                                    fontSize = ScannerTokens.CaptionText,
                                    maxLines = 1,
                                    modifier = Modifier.widthIn(min = ScannerTokens.PercentageLabelMinWidth),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // --- VIEWPORT (Content Layer) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScannerTheme.colors.scannerBackground),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 8.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    beyondViewportPageCount = 1,
                    key = { batchPages.getOrNull(it)?.id ?: it }
                ) { pageIdx ->
                    val page = batchPages.getOrNull(pageIdx)
                    
                    if (page == null) {
                        PageErrorPlaceholder(onRetry = {
                            val p = batchPages.getOrNull(pagerState.currentPage) ?: return@PageErrorPlaceholder
                            viewModel.generateLowResPagePreview(context, p, selectedFilter, filterStrength, System.currentTimeMillis())
                        })
                    } else {
                        key(page.id) {
                            val isCurrentPage = pageIdx == pagerState.currentPage
                            val displayUri = page.previewCacheUri ?: page.thumbnailCacheUri
                            val currentBitmap = if (isCurrentPage) livePreviewBitmap else null
        
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                // G4: Elegant subtle loading without aggressive crossfades
                                Crossfade(
                                    targetState = currentBitmap != null,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "PreviewCrossfade"
                                ) { hasLiveBitmap ->
                                    if (hasLiveBitmap && currentBitmap != null) {
                                        Image(
                                            bitmap = currentBitmap.asImageBitmap(),
                                            contentDescription = "Page ${pageIdx + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                        )
                                    } else if (displayUri == null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(ScannerTheme.colors.scannerBackground, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.5f), strokeWidth = 2.dp)
                                        }
                                    } else {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(displayUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Page ${pageIdx + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                            loading = {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.5f), strokeWidth = 2.dp)
                                                }
                                            },
                                            error = {
                                                PageErrorPlaceholder(onRetry = {
                                                    viewModel.generateLowResPagePreview(context, page, selectedFilter, filterStrength, System.currentTimeMillis())
                                                })
                                            }
                                        )
                                    }
                                }

                                if (page.transform.hasFilterOverride) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .background(OVERRIDE_BADGE.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Edit, null, tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(10.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Custom", color = ScannerTheme.colors.scannerTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                
                                // Subtle loading indicator that doesn't black out the screen
                                if (isCurrentPage && isPreviewProcessing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = ScannerTheme.colors.scannerTextPrimary, strokeWidth = 3.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageErrorPlaceholder(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C), RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.BrokenImage,
            contentDescription = null,
            tint = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.3f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Preview unavailable", color = ScannerTheme.colors.scannerTextPrimary.copy(alpha = 0.4f), fontSize = ScannerTokens.LabelText)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("Retry", color = ScannerTheme.colors.scannerActionPrimary, fontWeight = FontWeight.Medium)
        }
    }
}
