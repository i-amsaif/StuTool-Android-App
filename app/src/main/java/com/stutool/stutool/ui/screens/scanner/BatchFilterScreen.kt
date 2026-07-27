package com.stutool.stutool.ui.screens.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.activity.compose.BackHandler
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stutool.stutool.ui.screens.scanner.utils.ScanFilter
import com.stutool.stutool.ui.theme.ScannerTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchFilterScreen(
    navController: NavController,
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val pages by viewModel.batchPages.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
    val scannedPages by viewModel.scannedPages.collectAsState()
    val showExitDialog by viewModel.showExitDialog.collectAsState()

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
    
    val batchPages = pages.filter { it.id in (activeBatch?.pageIds ?: emptyList()) }
    
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val filterStrength by viewModel.filterStrength.collectAsState()
    
    // Local slider state for debounce
    var localStrength by remember { mutableFloatStateOf(filterStrength) }
    
    LaunchedEffect(filterStrength) {
        localStrength = filterStrength
    }
    
    LaunchedEffect(localStrength) {
        delay(120)
        if (localStrength != filterStrength) {
            viewModel.applyFilterToBatch(context, selectedFilter, localStrength)
        }
    }
    
    var isSaving by remember { mutableStateOf(false) }

    ScannerRoot(
        topBar = {},
        bottomBar = {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScannerTheme.colors.scannerBackground)
        ) {
            // --- 1. VIEWPORT (Grid) ---
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = ScannerTokens.floatingToolbarPadding + 64.dp,
                    bottom = ScannerTokens.bottomDockPadding + 200.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(batchPages) { page ->
                    BatchThumbnailItem(
                        page = page,
                        onClick = {
                            navController.navigate("single_page_filter_editor/${page.id}")
                        }
                    )
                }
            }

            // --- 2. TOP FLOATING SYSTEM ---
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
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
                    IconButton(onClick = {
                        viewModel.requestScannerExit {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ScannerTheme.colors.scannerTextPrimary)
                    }

                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = "Filter Documents",
                            color = ScannerTheme.colors.scannerTextPrimary,
                            fontSize = ScannerTokens.ToolbarTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${batchPages.size} pages",
                            color = ScannerTheme.colors.scannerTextSecondary,
                            fontSize = ScannerTokens.CaptionText
                        )
                    }
                }
            }

            // --- 3. BOTTOM FLOATING SYSTEM ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
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
                            viewModel.applyFilterToBatch(context, filter, localStrength)
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
                                modifier = Modifier.width(60.dp)
                            )
                            Slider(
                                value = localStrength,
                                onValueChange = { localStrength = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = ScannerTheme.colors.scannerActionPrimary,
                                    activeTrackColor = ScannerTheme.colors.scannerActionPrimary,
                                    inactiveTrackColor = ScannerTheme.colors.scannerOverlaySurface
                                )
                            )
                            Text(
                                "${(localStrength * 100).toInt()}%",
                                color = ScannerTheme.colors.scannerTextSecondary,
                                fontSize = ScannerTokens.CaptionText,
                                modifier = Modifier.width(38.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }

                    // --- CONTINUE ACTION ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                isSaving = true
                                viewModel.finalizeProcessedPages(
                                    onSuccess = {
                                        navController.navigate("document_review") {
                                            popUpTo("batch_filter") { inclusive = true }
                                        }
                                    },
                                    onError = {
                                        isSaving = false
                                        android.widget.Toast.makeText(context, "Error: Document pages empty", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(ScannerTokens.ButtonCornerRadius)
                        ) {
                            Text("Continue to Review", fontWeight = FontWeight.SemiBold, fontSize = ScannerTokens.ButtonText)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchThumbnailItem(
    page: com.stutool.stutool.ui.screens.scanner.models.ScanPage,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(ScannerTokens.CardCornerRadius))
            .background(ScannerTheme.colors.scannerOverlaySurface)
            .clickable { onClick() }
    ) {
        if (page.thumbnailCacheUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(page.thumbnailCacheUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Filter Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(ScannerTokens.ChipCornerRadius))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                page.transform.filter.label,
                color = ScannerTheme.colors.scannerTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Override Indicator
        if (page.transform.hasFilterOverride) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xFFFF6F00), CircleShape)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Override",
                    tint = ScannerTheme.colors.scannerTextPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
