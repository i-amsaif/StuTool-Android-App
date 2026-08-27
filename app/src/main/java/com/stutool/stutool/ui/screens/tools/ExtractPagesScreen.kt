package com.stutool.stutool.ui.screens.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

private enum class ExtractStep(val label: String, val icon: ImageVector) {
    SELECT("Select", Icons.Outlined.UploadFile),
    EDIT("Edit", Icons.Outlined.Edit),
    EXTRACT("Extract", Icons.Filled.Description)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractPagesScreen(
    navController: NavController,
    viewModel: ExtractPagesViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedFileUri.collectAsState()
    val successUri by viewModel.successUri.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val pageInput by viewModel.pageInput.collectAsState()
    val selectedPages by viewModel.selectedPages.collectAsState()
    
    val isExtracting by viewModel.isExtracting.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadFile(it, context) }
    }

    val currentStep = when {
        successUri != null -> ExtractStep.EXTRACT
        selectedUri != null -> ExtractStep.EDIT
        else -> ExtractStep.SELECT
    }

    val showProcessingUi = isExtracting || successUri != null || processingError != null

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Extract Pages", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
            val hasSelection = selectedPages.isNotEmpty() || pageInput.isNotBlank()
            FloatingBottomActionBar(
                show = currentStep == ExtractStep.EDIT,
                onClick = { viewModel.extractPages(context) },
                isEnabled = hasSelection && !isLoading,
                isLoading = isLoading,
                loadingText = "Extracting...",
                icon = Icons.Filled.Description,
                text = if (selectedPages.isNotEmpty()) "Extract ${selectedPages.size} Pages" else "Extract Pages"
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
                ExtractStepIndicator(currentStep = currentStep)

                Spacer(modifier = Modifier.height(28.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + slideInVertically { it / 4 } togetherWith
                                fadeOut(animationSpec = tween(200)) + slideOutVertically { -it / 4 }
                    },
                    label = "extract_content"
                ) { step ->
                    when (step) {
                        ExtractStep.SELECT -> {
                            ExtractUploadPlaceholder(onClick = { launcher.launch(arrayOf("application/pdf")) })
                        }
                        ExtractStep.EDIT -> {
                            ExtractEditSection(
                                viewModel = viewModel,
                                context = context,
                                onExtract = { viewModel.extractPages(context) },
                                onChangeFile = { launcher.launch(arrayOf("application/pdf")) }
                            )
                        }
                        ExtractStep.EXTRACT -> {
                            // Handled by FullScreenProcessingView now
                        }
                    }
                }
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
                    errorMessage?.let { errorMsg ->
                        Text(errorMsg, color = MaterialTheme.colorScheme.inverseOnSurface)
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
                successUri != null -> ProcessingState.Success(
                    title = "Pages Extracted!",
                    subtitle = "Your extracted PDF has been saved to Documents/StuTool",
                    primaryActionLabel = "Open",
                    secondaryActionLabel = "Share",
                    tertiaryActionLabel = "Start Over"
                )
                processingError != null -> ProcessingState.Error(
                    message = processingError!!
                )
                else -> ProcessingState.Processing(
                    progress = -1f,
                    message = statusMessage
                )
            }

            FullScreenProcessingView(
                state = processingState,
                onCancel = { viewModel.cancelProcessing() },
                onSuccessPrimaryAction = {
                    successUri?.let { uri ->
                        val encodedUri = android.util.Base64.encodeToString(
                            uri.toString().toByteArray(),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("reader_screen/$encodedUri")
                    }
                },
                onSuccessSecondaryAction = {
                    successUri?.let { uri ->
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
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Extracted PDF"))
                    }
                },
                onSuccessTertiaryAction = { viewModel.resetState() },
                onErrorRetry = {
                    viewModel.clearProcessingError()
                    viewModel.extractPages(context)
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
private fun ExtractStepIndicator(currentStep: ExtractStep) {
    val steps = ExtractStep.entries

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

            ExtractStepDot(
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
private fun ExtractStepDot(step: ExtractStep, isActive: Boolean, isCurrent: Boolean) {
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
fun ExtractUploadPlaceholder(onClick: () -> Unit) {
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
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
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Select PDF File",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Select a PDF to extract specific pages",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

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
                    Text("Select PDF File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        ExtractHowItWorksSection()
    }
}

@Composable
private fun ExtractHowItWorksSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "How it works",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        ExtractHowItWorksItem(
            number = "1",
            title = "Select File",
            description = "Choose a PDF file from your device",
            icon = Icons.Outlined.UploadFile
        )
        ExtractHowItWorksItem(
            number = "2",
            title = "Choose Pages",
            description = "Select pages visually or type ranges (e.g., 1,3-5,8)",
            icon = Icons.Outlined.Edit
        )
        ExtractHowItWorksItem(
            number = "3",
            title = "Extract & Save",
            description = "Tap extract and your new PDF is saved instantly",
            icon = Icons.Outlined.Done
        )
    }
}

@Composable
private fun ExtractHowItWorksItem(number: String, title: String, description: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
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
// ── Edit Section (Enhanced with Visual Mode) ──
// ═══════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractEditSection(
    viewModel: ExtractPagesViewModel,
    context: Context,
    onExtract: () -> Unit,
    onChangeFile: () -> Unit
) {
    val originalFileName by viewModel.originalFileName.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val pageInput by viewModel.pageInput.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedPages by viewModel.selectedPages.collectAsState()
    val inputError by viewModel.inputError.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── File Info Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Selected File",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            TextButton(
                onClick = onChangeFile,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Change", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Selected File Display ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = originalFileName ?: "Unknown",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isLoading && totalPages == 0) "Loading..." else "$totalPages pages",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Mode Toggle (Manual / Visual) ──
        Text(
            "Pages to Extract",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeToggleTab(
                label = "Manual Input",
                icon = Icons.Outlined.Edit,
                isSelected = selectionMode == PageSelectionMode.MANUAL,
                onClick = { viewModel.setSelectionMode(PageSelectionMode.MANUAL) },
                modifier = Modifier.weight(1f)
            )
            ModeToggleTab(
                label = "Visual Selection",
                icon = Icons.Outlined.GridView,
                isSelected = selectionMode == PageSelectionMode.VISUAL,
                onClick = { viewModel.setSelectionMode(PageSelectionMode.VISUAL) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Mode Content ──
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(150))
            },
            label = "mode_switch"
        ) { mode ->
            when (mode) {
                PageSelectionMode.MANUAL -> {
                    ManualInputSection(
                        pageInput = pageInput,
                        inputError = inputError,
                        onInputChange = { viewModel.updatePageInput(it) },
                        selectedCount = selectedPages.size
                    )
                }
                PageSelectionMode.VISUAL -> {
                    VisualSelectionSection(
                        viewModel = viewModel,
                        totalPages = totalPages,
                        selectedPages = selectedPages
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Info Card ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Extracted pages will be saved as a new PDF to Documents/StuTool.",
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── Mode Toggle Tab ──
// ═══════════════════════════════════════════════

@Composable
private fun ModeToggleTab(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ── Manual Input Section ──
// ═══════════════════════════════════════════════

@Composable
private fun ManualInputSection(
    pageInput: String,
    inputError: String?,
    onInputChange: (String) -> Unit,
    selectedCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Enter page numbers or ranges",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(
            "e.g., 1,3-5,8",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = pageInput,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter pages here...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
            isError = inputError != null,
            supportingText = {
                if (inputError != null) {
                    Text(inputError, color = Color(0xFFEF4444), fontSize = 12.sp)
                } else if (selectedCount > 0) {
                    Text("$selectedCount pages selected", color = SuccessColor, fontSize = 12.sp)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = Color(0xFFEF4444),
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ═══════════════════════════════════════════════
// ── Visual Selection Section ──
// ═══════════════════════════════════════════════

@Composable
private fun VisualSelectionSection(
    viewModel: ExtractPagesViewModel,
    totalPages: Int,
    selectedPages: Set<Int>
) {
    val gridState = rememberLazyGridState()
    val thumbnailVersion by viewModel.thumbnailVersion.collectAsState()
    val loadingPages by viewModel.loadingPages.collectAsState()

    // Trigger thumbnail loads based on visible grid items
    LaunchedEffect(gridState.layoutInfo.visibleItemsInfo, thumbnailVersion) {
        val visibleIndices = gridState.layoutInfo.visibleItemsInfo.map { it.index }
        for (index in visibleIndices) {
            val pageNum = index + 1
            if (pageNum <= totalPages) {
                viewModel.requestThumbnailLoad(pageNum)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Selection Status Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (selectedPages.isEmpty()) "Tap pages to select"
                else "${selectedPages.size} of $totalPages pages selected",
                color = if (selectedPages.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = { viewModel.selectAllPages() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Select All", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (selectedPages.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearPageSelection() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Thumbnail Grid (fixed height — NOT nested inside another scroll) ──
        val gridHeight = when {
            totalPages <= 3 -> 200.dp
            totalPages <= 6 -> 360.dp
            totalPages <= 12 -> 500.dp
            else -> 480.dp
        }

        // Force recompose when thumbnails load
        @Suppress("UNUSED_VARIABLE")
        val version = thumbnailVersion

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clip(RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = totalPages,
                key = { it }
            ) { index ->
                val pageNum = index + 1
                val isSelected = pageNum in selectedPages
                val isLoadingThis = pageNum in loadingPages
                val thumbnail = viewModel.getThumbnail(pageNum)

                PageThumbnailItem(
                    pageNumber = pageNum,
                    bitmap = thumbnail,
                    isSelected = isSelected,
                    isLoading = isLoadingThis,
                    onClick = { viewModel.togglePageSelection(pageNum) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── Page Thumbnail Item ──
// ═══════════════════════════════════════════════

@Composable
private fun PageThumbnailItem(
    pageNumber: Int,
    bitmap: Bitmap?,
    isSelected: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "thumbnail_scale"
    )

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .aspectRatio(0.707f) // A4 portrait ratio (210/297)
            .scale(scaleAnim)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail or placeholder
        if (bitmap != null && !bitmap.isRecycled) {
            val alpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(300),
                label = "thumbnail_fade"
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageNumber",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha },
                contentScale = ContentScale.Fit
            )
        } else if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            // Placeholder
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            )
            // Check badge (top right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Page number badge (bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                "$pageNumber",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

