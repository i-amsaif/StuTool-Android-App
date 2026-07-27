package com.stutool.stutool.ui.screens.tools

import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stutool.stutool.domain.engine.CompressionResult
import com.stutool.stutool.ui.components.FloatingBottomActionBar
import com.stutool.stutool.ui.components.FullScreenProcessingView
import com.stutool.stutool.ui.components.ProcessingState
import com.stutool.stutool.ui.theme.*

private enum class CompressStep(val label: String, val icon: ImageVector) {
    SELECT("Select", Icons.Outlined.UploadFile),
    OPTIONS("Options", Icons.Outlined.Build),
    COMPRESS("Compress", Icons.Filled.Compress)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressPdfScreen(
    navController: NavController,
    viewModel: CompressPdfViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedFileUri.collectAsState()
    val compressionResult by viewModel.compressionResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val isCompressing by viewModel.isCompressing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadFile(it, context) }
    }

    val currentStep = when {
        compressionResult != null -> CompressStep.COMPRESS
        selectedUri != null -> CompressStep.OPTIONS
        else -> CompressStep.SELECT
    }

    val showProcessingUi = isCompressing || compressionResult != null || processingError != null

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Compress,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Compress PDF", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                    show = selectedUri != null,
                    onClick = { viewModel.compressPdf(context) },
                    isEnabled = !isCompressing,
                    isLoading = isCompressing,
                    loadingText = "Compressing...",
                    icon = Icons.Filled.Compress,
                    text = "Compress PDF"
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
                    CompressStepIndicator(currentStep = currentStep)
    
                    Spacer(modifier = Modifier.height(28.dp))
    
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) + slideInVertically { it / 4 } togetherWith
                                    fadeOut(animationSpec = tween(200)) + slideOutVertically { -it / 4 }
                        },
                        label = "compress_content"
                    ) { step ->
                        when (step) {
                            CompressStep.SELECT -> {
                                CompressUploadPlaceholder(onClick = { launcher.launch(arrayOf("application/pdf")) })
                            }
                            CompressStep.OPTIONS -> {
                                CompressOptionsSection(
                                    viewModel = viewModel,
                                    onChangeFile = { launcher.launch(arrayOf("application/pdf")) }
                                )
                            }
                            CompressStep.COMPRESS -> {
                                // Content removed, handled by FullScreenProcessingView
                            }
                        }
                    }
                }
            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = Color(0xFF2D1515),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    errorMessage?.let { error ->
                        Text(error, color = Color(0xFFFFCDD2))
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
                isCompressing -> ProcessingState.Preparing(statusMessage)
                processingError != null -> ProcessingState.Error(message = processingError!!)
                compressionResult != null -> {
                    val result = compressionResult!!
                    val percentage = calculatePercentageSaved(result.originalSizeBytes, result.newSizeBytes)
                    val origStr = formatFileSize(result.originalSizeBytes)
                    val newStr = formatFileSize(result.newSizeBytes)
                    val reductionText = if (result.newSizeBytes < result.originalSizeBytes) {
                        "Reduced by $percentage%"
                    } else {
                        "No reduction possible"
                    }
                    ProcessingState.Success(
                        title = "PDF Compressed!",
                        subtitle = "$reductionText \u2022 Original: $origStr \u2192 New: $newStr\nSaved to Documents/StuTool",
                        primaryActionLabel = "Open in Viewer",
                        secondaryActionLabel = "Share PDF",
                        tertiaryActionLabel = "Start Over"
                    )
                }
                else -> ProcessingState.Idle
            }
            
            FullScreenProcessingView(
                state = processingState,
                onCancel = { viewModel.cancelProcessing() },
                onSuccessPrimaryAction = {
                    compressionResult?.let { result ->
                        val encodedUri = android.util.Base64.encodeToString(
                            result.uri.toString().toByteArray(),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                        navController.navigate("reader_screen/$encodedUri")
                    }
                },
                onSuccessSecondaryAction = {
                    compressionResult?.let { result ->
                        val shareUri = if (result.uri.scheme == "file") {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "com.stutool.stutool.fileprovider",
                                java.io.File(result.uri.path ?: "")
                            )
                        } else {
                            result.uri
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Compressed PDF"))
                    }
                },
                onSuccessTertiaryAction = { viewModel.resetState() },
                onErrorRetry = { 
                    viewModel.clearProcessingError()
                    viewModel.compressPdf(context)
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
private fun CompressStepIndicator(currentStep: CompressStep) {
    val steps = CompressStep.entries

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

            CompressStepDot(
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
private fun CompressStepDot(step: CompressStep, isActive: Boolean, isCurrent: Boolean) {
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
fun CompressUploadPlaceholder(onClick: () -> Unit) {
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
                    "Select a PDF to greatly reduce its physical file size",
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

        CompressHowItWorksSection()
    }
}

@Composable
private fun CompressHowItWorksSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "How it works",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        CompressHowItWorksItem(
            number = "1",
            title = "Select File",
            description = "Choose a heavy PDF file from your device",
            icon = Icons.Outlined.UploadFile
        )
        CompressHowItWorksItem(
            number = "2",
            title = "Select Quality",
            description = "Choose between High, Medium, or Low compression",
            icon = Icons.Outlined.Build
        )
        CompressHowItWorksItem(
            number = "3",
            title = "Compress & Save",
            description = "Tap compress and your light PDF is automatically saved",
            icon = Icons.Outlined.Done
        )
    }
}

@Composable
private fun CompressHowItWorksItem(number: String, title: String, description: String, icon: ImageVector) {
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
// ── Edit Section ──
// ═══════════════════════════════════════════════

@Composable
fun CompressOptionsSection(
    viewModel: CompressPdfViewModel,
    onChangeFile: () -> Unit
) {
    val originalFileName by viewModel.originalFileName.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val selectedLevel by viewModel.compressionLevel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Selected File", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

        // Selected File Display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
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

        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Compression Level", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Select how aggressively you want to reduce file size", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Compression Level Radio Cards
        CompressionLevel.entries.forEach { level ->
            val isSelected = selectedLevel == level
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.setCompressionLevel(level) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${level.title} Compression",
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    val note = when (level) {
                        CompressionLevel.LOW -> "Minimal size reduction. High quality images (80%)."
                        CompressionLevel.MEDIUM -> "Good size reduction. Balanced quality images (60%)."
                        CompressionLevel.HIGH -> "Maximum size reduction. Downscaled lower quality images (40%)."
                    }
                    Text(text = note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Warning: High compression noticeably degrades internal image dimensions & artifacting. Saved cleanly natively to Documents/StuTool.",
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}


private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun calculatePercentageSaved(original: Long, newSize: Long): Long {
    if (original == 0L || newSize >= original) return 0L
    val diff = original - newSize
    return (diff * 100L) / original
}
