package com.stutool.stutool.ui.screens.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stutool.stutool.ui.screens.scanner.utils.ScanFilter
import com.stutool.stutool.ui.theme.ScannerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePageFilterEditorScreen(
    navController: NavController,
    viewModel: ScannerViewModel,
    pageId: String
) {
    val context = LocalContext.current
    val pages by viewModel.scannedPages.collectAsState()
    val page = pages.find { it.id == pageId }
    
    if (page == null) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
        return
    }

    val isIntentionalExit by viewModel.isIntentionalScannerExit.collectAsState()
    val isTransitioning by viewModel.isTransitioningBetweenScannerStages.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setTransitioning(false)
    }

    val scannedPages by viewModel.scannedPages.collectAsState()
    LaunchedEffect(scannedPages.size) {
        if (scannedPages.isEmpty() && !isIntentionalExit && !isTransitioning) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    var selectedFilter by remember { mutableStateOf(page.transform.filter) }
    var filterStrength by remember { mutableFloatStateOf(page.transform.filterStrength) }
    var isSaving by remember { mutableStateOf(false) }

    val livePreview by viewModel.livePreviewBitmap.collectAsState()
    val isPreviewProcessing by viewModel.isPreviewProcessing.collectAsState()

    LaunchedEffect(selectedFilter, filterStrength) {
        viewModel.generateLowResPagePreview(
            context = context,
            page = page,
            filter = selectedFilter,
            strength = filterStrength,
            token = System.currentTimeMillis(),
            isSinglePageMode = true
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelSinglePageFilterPreview(page.id)
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
                // --- TOP FLOATING SYSTEM ---
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
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.size(ScannerTokens.MinTouchTarget)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Cancel", tint = ScannerTheme.colors.scannerTextPrimary, modifier = Modifier.size(24.dp))
                        }

                        Text(
                            text = "Edit Filter Override",
                            color = ScannerTheme.colors.scannerTextPrimary,
                            fontSize = ScannerTokens.ToolbarTitle,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                    }
                }
            },
            bottomBar = {
                // --- BOTTOM FLOATING SYSTEM ---
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
                        // --- FILTER RAIL ---
                        FilterSelectionRow(
                            selectedFilter = selectedFilter,
                            onFilterSelected = { filter ->
                                selectedFilter = filter
                            }
                        )

                        // --- INTENSITY SLIDER ---
                        AnimatedVisibility(
                            visible = selectedFilter != ScanFilter.ORIGINAL,
                            enter = fadeIn(tween(ScannerTokens.FastDuration)) + expandVertically(tween(ScannerTokens.FastDuration)),
                            exit = fadeOut(tween(ScannerTokens.FastDuration)) + shrinkVertically(tween(ScannerTokens.FastDuration))
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
                                    onValueChange = { filterStrength = it },
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

                        // --- SAVE ACTION ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    isSaving = true
                                    viewModel.setSinglePageFilterOverride(context, pageId, selectedFilter, filterStrength)
                                    navController.popBackStack()
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius)
                            ) {
                                Text("Save Override", fontWeight = FontWeight.SemiBold, fontSize = ScannerTokens.ButtonText)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScannerTheme.colors.scannerBackground)
            ) {
                // --- 1. VIEWPORT ---
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (livePreview != null) {
                        Image(
                            bitmap = livePreview!!.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize()
                                .padding(
                                    top = innerPadding.calculateTopPadding() + 8.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(page.previewCacheUri ?: page.croppedCacheUri ?: page.originalUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize()
                                .padding(
                                    top = innerPadding.calculateTopPadding() + 8.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (isPreviewProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(Color.Black.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ScannerTheme.colors.scannerTextPrimary, strokeWidth = 3.dp)
                        }
                    }

                    if (isSaving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(Color.Black.copy(alpha = 0.3f)),
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
