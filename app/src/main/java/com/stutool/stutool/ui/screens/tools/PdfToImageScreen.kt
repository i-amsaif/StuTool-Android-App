package com.stutool.stutool.ui.screens.tools

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stutool.stutool.ui.components.*
import kotlinx.coroutines.launch

private enum class PdfToImageStep(val index: Int) {
    SELECT(0),
    OPTIONS(1),
    RESULT(2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    navController: NavController,
    viewModel: PdfToImageViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedUri by viewModel.selectedUri.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val fileSizeBytes by viewModel.fileSizeBytes.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val isLoadingInfo by viewModel.isLoadingInfo.collectAsState()
    val format by viewModel.format.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val pageRange by viewModel.pageRange.collectAsState()
    val isConverting by viewModel.isConverting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val outputFiles by viewModel.outputFiles.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()

    val currentStep = when {
        outputFiles != null -> PdfToImageStep.RESULT
        selectedUri != null -> PdfToImageStep.OPTIONS
        else -> PdfToImageStep.SELECT
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setPdf(uri)
        }
    }

    val onNavigateBack = {
        if (isConverting) {
            viewModel.cancelConversion()
        }
        navController.popBackStack()
    }

    val showProcessingOverlay = isConverting || outputFiles != null || processingError != null

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "PDF to Image",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { onNavigateBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                FloatingBottomActionBar(
                    show = currentStep == PdfToImageStep.OPTIONS && !isConverting,
                    onClick = { viewModel.startConversion() },
                    isEnabled = !isLoadingInfo && !isConverting,
                    isLoading = isConverting,
                    loadingText = "Rendering...",
                    icon = Icons.Default.Image,
                    text = "Convert to Images"
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step Indicator
                PdfToolStepIndicator(currentStepIndex = currentStep.index)

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "pdf_to_image_step_content"
                ) { step ->
                    when (step) {
                        PdfToImageStep.SELECT -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PdfToolUploadCard(
                                    title = "Select PDF Document",
                                    subtitle = "Convert PDF pages into high-resolution JPEG or lossless PNG images",
                                    buttonText = "Select PDF File",
                                    onClick = { launcher.launch(arrayOf("application/pdf")) }
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                Text(
                                    text = "How it works",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                PdfToolFeatureItem(
                                    number = "1",
                                    title = "Select PDF File",
                                    description = "Pick any PDF document from your device",
                                    icon = Icons.Outlined.UploadFile
                                )
                                PdfToolFeatureItem(
                                    number = "2",
                                    title = "Format & Quality",
                                    description = "Choose JPEG or PNG output and specify optional page ranges",
                                    icon = Icons.Outlined.Tune
                                )
                                PdfToolFeatureItem(
                                    number = "3",
                                    title = "Gallery & ZIP Export",
                                    description = "Save all converted pages directly to your device gallery or share as ZIP",
                                    icon = Icons.Outlined.Collections
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "100% Private & Offline. High-resolution on-device rendering.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        PdfToImageStep.OPTIONS -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PdfSelectedFileCard(
                                    fileName = fileName ?: "Selected Document",
                                    pageCount = pageCount,
                                    fileSizeBytes = fileSizeBytes,
                                    isLoading = isLoadingInfo,
                                    onChangeFile = { launcher.launch(arrayOf("application/pdf")) }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "Conversion Options",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Options Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                        .padding(16.dp)
                                ) {
                                    // Format selector
                                    Text(
                                        text = "Image Format",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilterChip(
                                            selected = format == "JPEG",
                                            onClick = { viewModel.setFormat("JPEG") },
                                            label = { Text("JPEG (Smaller size)") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        FilterChip(
                                            selected = format == "PNG",
                                            onClick = { viewModel.setFormat("PNG") },
                                            label = { Text("PNG (Lossless)") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    // Quality selector
                                    Text(
                                        text = "Rendering Quality",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilterChip(
                                            selected = quality == "High",
                                            onClick = { viewModel.setQuality("High") },
                                            label = { Text("High (300 DPI)") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        FilterChip(
                                            selected = quality == "Standard",
                                            onClick = { viewModel.setQuality("Standard") },
                                            label = { Text("Standard (150 DPI)") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    // Page Range input
                                    Text(
                                        text = "Page Range (Optional)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "e.g. 1-3, 5, 8. Leave blank to convert all $pageCount pages.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = pageRange,
                                        onValueChange = { viewModel.setPageRange(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("All pages (1-$pageCount)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(90.dp))
                            }
                        }

                        PdfToImageStep.RESULT -> {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp))
                        }
                    }
                }
            }
        }

        // Full-Screen Processing & Results Overlay
        AnimatedVisibility(
            visible = showProcessingOverlay,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            val processingState = when {
                isConverting -> ProcessingState.Processing(
                    progress = progress,
                    message = statusMessage,
                    subtitle = if (outputFiles != null) "Finalizing images" else "Rendering page bitmaps"
                )
                processingError != null -> ProcessingState.Error(
                    title = "Conversion Failed",
                    message = processingError!!,
                    retryLabel = "Try Again",
                    cancelLabel = "Back"
                )
                outputFiles != null -> {
                    val count = outputFiles?.size ?: 0
                    ProcessingState.Success(
                        title = "Images Generated!",
                        subtitle = "Successfully rendered $count image(s) in $format format\nSave to gallery or export as ZIP archive",
                        primaryActionLabel = "Save to Gallery",
                        secondaryActionLabel = "Share as ZIP",
                        tertiaryActionLabel = "Convert Another"
                    )
                }
                else -> ProcessingState.Idle
            }

            FullScreenProcessingView(
                state = processingState,
                onCancel = { viewModel.cancelConversion() },
                onSuccessPrimaryAction = {
                    viewModel.saveToGallery { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                onSuccessSecondaryAction = {
                    coroutineScope.launch {
                        val zipFile = viewModel.createZipForSharing()
                        if (zipFile != null && zipFile.exists()) {
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "com.stutool.stutool.fileprovider",
                                zipFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Images ZIP"))
                        } else {
                            Toast.makeText(context, "Failed to create ZIP package", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSuccessTertiaryAction = {
                    viewModel.reset()
                },
                onErrorRetry = {
                    viewModel.clearProcessingError()
                    viewModel.startConversion()
                },
                onErrorCancel = {
                    viewModel.clearProcessingError()
                }
            )
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error", fontWeight = FontWeight.Bold) },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
