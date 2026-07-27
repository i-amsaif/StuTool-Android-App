package com.stutool.stutool.ui.screens.resume

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.stutool.stutool.R
import com.stutool.stutool.ui.screens.resume.components.ResumeForm
import com.stutool.stutool.ui.screens.resume.components.ResumePreview
import com.stutool.stutool.ui.screens.resume.engine.PdfExporter
import kotlinx.coroutines.launch
import java.io.File

// Note: All colors below now use MaterialTheme.colorScheme at call-site

// Tab names mapped to indices for pager
private val TAB_NAMES = listOf("edit", "layout", "preview")
private fun tabToIndex(tab: String): Int = TAB_NAMES.indexOf(tab).coerceAtLeast(0)
private fun indexToTab(index: Int): String = TAB_NAMES.getOrElse(index) { "edit" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeBuilderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ResumeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateOwner = LocalSavedStateRegistryOwner.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    // Pager state: 3 pages (Form, Layout, Preview)
    val pagerState = rememberPagerState(initialPage = tabToIndex(state.activeTab)) { 3 }

    // Sync: when pager settles on a new page, update viewModel
    LaunchedEffect(pagerState.currentPage) {
        val newTab = indexToTab(pagerState.currentPage)
        if (state.activeTab != newTab) {
            viewModel.setActiveTab(newTab)
        }
    }

    // Sync: when viewModel activeTab changes (e.g. via nav button), scroll pager
    LaunchedEffect(state.activeTab) {
        val targetPage = tabToIndex(state.activeTab)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Bottom sheet state
    var showPdfSheet by remember { mutableStateOf(false) }
    var cachedPdfFile by remember { mutableStateOf<File?>(null) }
    var pdfFileName by remember { mutableStateOf("resume.pdf") }

    // Snackbar for feedback
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            // --- COMPACT TOP BAR ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Back Button (Left)
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Centered Static Image
                Image(
                    painter = painterResource(id = R.drawable.topbar),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .height(32.dp)
                        .align(Alignment.Center)
                )

                // Menu (Right)
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.saveStatus.isNotEmpty()) {
                        Text(
                            text = state.saveStatus,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                            .width(220.dp)
                    ) {
                        // Section header
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TEMPLATES",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Load Fresher
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Load Fresher",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                viewModel.loadFresherSample()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        )

                        // Load Experienced
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Load Experienced",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                viewModel.loadExperiencedSample()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.WorkHistory,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        )

                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // Reset Data
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Reset Data",
                                    color = Color(0xFFEF4444),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                viewModel.resetData()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(Color(0xFFEF4444).copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                }
            }

            // Hairline separator for visual hierarchy (adapts to light/dark mode)
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // --- MAIN CONTENT & FLOATING BOTTOM NAV ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Swipeable pager for Form / Layout / Preview
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1, // Keep adjacent pages alive
                    key = { it }
                ) { page ->
                    when (page) {
                        0 -> ResumeForm(viewModel)
                        1 -> TemplateSelector(
                            selectedTemplate = state.selectedTemplate,
                            onSelect = { viewModel.setSelectedTemplate(it) }
                        )
                        2 -> ResumePreview(
                            viewModel = viewModel
                        )
                    }
                }

                val resumeNavBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                SwipeableBottomNav(
                    pagerState = pagerState,
                    isExporting = isExporting,
                    onTabClick = { tab ->
                        scope.launch {
                            pagerState.animateScrollToPage(tabToIndex(tab))
                        }
                    },
                    onPdfClick = {
                        if (isExporting) return@SwipeableBottomNav
                        isExporting = true
                        scope.launch {
                            try {
                                val name = state.resumeData.personal.name.ifBlank { "resume" }
                                val fName = "${name.replace(" ", "_")}.pdf"
                                pdfFileName = fName
                                val file = PdfExporter.generatePdfToCache(
                                    context, lifecycleOwner, savedStateOwner,
                                    state.resumeData, state.selectedTemplate, fName
                                )
                                cachedPdfFile = file
                                showPdfSheet = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "PDF generation failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp + resumeNavBarPadding)
                )

                // Loading overlay
                if (isExporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Generating PDF…",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // --- Snackbar Host ---
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // --- PDF ACTIONS BOTTOM SHEET ---
        if (showPdfSheet) {
            PdfActionsSheet(
                fileName = pdfFileName,
                onDismiss = { showPdfSheet = false },
                onSave = {
                    cachedPdfFile?.let { file ->
                        scope.launch {
                            try {
                                PdfExporter.savePdfToDevice(context, file, pdfFileName)
                                showPdfSheet = false
                                snackbarHostState.showSnackbar(
                                    message = "Saved to Documents/StuTool/$pdfFileName",
                                    actionLabel = "OK",
                                    duration = SnackbarDuration.Short
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onView = {
                    cachedPdfFile?.let { file ->
                        try {
                            PdfExporter.viewPdf(context, file)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No PDF viewer found on device", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onShare = {
                    cachedPdfFile?.let { file ->
                        try {
                            PdfExporter.sharePdf(context, file, pdfFileName)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}

// ===========================================
// PDF Actions Bottom Sheet
// ===========================================

@Composable
fun PdfActionsSheet(
    fileName: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit
) {
    // Scrim behind sheet
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
    ) {
        // Sheet panel at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable(enabled = false) {} // Block scrim click-through
                .padding(top = 12.dp, bottom = 32.dp)
        ) {
            // Drag indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        "PDF Ready",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        fileName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            PdfActionButton(
                icon = Icons.Default.SaveAlt,
                title = "Save to Device",
                subtitle = "Documents/StuTool",
                iconBgColor = Color(0xFF10B981).copy(alpha = 0.15f),
                iconTint = Color(0xFF10B981),
                onClick = onSave
            )
            PdfActionButton(
                icon = Icons.Default.Visibility,
                title = "View PDF",
                subtitle = "Open in PDF viewer",
                iconBgColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                iconTint = Color(0xFF3B82F6),
                onClick = onView
            )
            PdfActionButton(
                icon = Icons.Default.Share,
                title = "Share PDF",
                subtitle = "Send via WhatsApp, Email, etc.",
                iconBgColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                iconTint = Color(0xFF8B5CF6),
                onClick = onShare
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel button
            Text(
                "Cancel",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun PdfActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconBgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(iconBgColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ===========================================
// Swipeable Bottom Navigation with Sliding Indicator
// ===========================================

@Composable
private fun SwipeableBottomNav(
    pagerState: androidx.compose.foundation.pager.PagerState,
    isExporting: Boolean,
    onTabClick: (String) -> Unit,
    onPdfClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        Triple(Icons.Default.Edit, "Form", "edit"),
        Triple(Icons.Default.Dashboard, "Layout", "layout"),
        Triple(Icons.Default.Visibility, "Preview", "preview")
    )
    // Total items including PDF button (non-swipeable)
    val totalSlots = 4

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 380.dp)
            .height(72.dp)
            .shadow(8.dp, RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(percent = 50))
    ) {
        val horizontalPadding = 6.dp
        val contentWidth = maxWidth - horizontalPadding * 2
        val itemWidth = contentWidth / totalSlots
        val itemInternalPadding = 4.dp // matches SwipeableNavItem's horizontal padding
        val indicatorWidth = itemWidth - itemInternalPadding * 2

        // Calculate continuously-sliding indicator offset
        val currentPage = pagerState.currentPage
        val offsetFraction = pagerState.currentPageOffsetFraction
        val indicatorOffset = horizontalPadding + itemInternalPadding +
                itemWidth * (currentPage + offsetFraction)

        // Sliding indicator pill behind the active tab
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
        )

        // Navigation items row
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, (icon, label, tab) ->
                // Compute smooth "selected-ness" factor: 1.0 when fully on this page, 0.0 otherwise
                val selectionFraction = (1f - kotlin.math.abs(
                    (currentPage + offsetFraction) - index
                ).coerceIn(0f, 1f))

                SwipeableNavItem(
                    icon = icon,
                    label = label,
                    selectionFraction = selectionFraction,
                    onClick = { onTabClick(tab) },
                    modifier = Modifier.weight(1f)
                )
            }

            // PDF button (not part of the pager, always unselected)
            SwipeableNavItem(
                icon = Icons.Default.Download,
                label = "PDF",
                selectionFraction = 0f,
                onClick = onPdfClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SwipeableNavItem(
    icon: ImageVector,
    label: String,
    selectionFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary, selectionFraction)
    val scale = 0.95f + 0.05f * selectionFraction

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Linear interpolation between two colors using a fraction [0..1]. */
private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f
    )
}

@Composable
fun TemplateSelector(selectedTemplate: String, onSelect: (String) -> Unit) {
    val templates = listOf(
        Triple("minimal", "Minimal", "Clean, simple, and traditional. Perfect for passing strict ATS systems."),
        Triple("modern", "Modern", "Two-column vibrant layout with a splash of color. Great for creatives."),
        Triple("classic", "Classic", "Professional serif typography designed exclusively for formal and executive roles."),
        Triple("persona", "Persona", "Multi-column layout featuring a custom professional profile picture.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Dashboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Choose Theme",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        templates.forEach { (id, title, desc) ->
            val isSelected = selectedTemplate == id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(id) }
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            desc,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
