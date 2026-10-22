package com.stutool.stutool.ui.screens.tools

import android.content.Intent
import android.net.Uri
import android.util.Base64
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
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
import java.io.File

private enum class PdfToWordStep(val index: Int) {
    SELECT(0),
    OPTIONS(1),
    RESULT(2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToWordScreen(
    navController: NavController,
    viewModel: PdfToWordViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedUri.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val fileSizeBytes by viewModel.fileSizeBytes.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val isLoadingInfo by viewModel.isLoadingInfo.collectAsState()
    val isConverting by viewModel.isConverting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val outputUri by viewModel.outputUri.collectAsState()
    val outputFileSizeBytes by viewModel.outputFileSizeBytes.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()

    val currentStep = when {
        outputUri != null -> PdfToWordStep.RESULT
        selectedUri != null -> PdfToWordStep.OPTIONS
        else -> PdfToWordStep.SELECT
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
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

    val showProcessingOverlay = isConverting || outputUri != null || processingError != null

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
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "PDF to Word",
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
                    show = currentStep == PdfToWordStep.OPTIONS && !isConverting,
                    onClick = { viewModel.startConversion() },
                    isEnabled = !isLoadingInfo && !isConverting,
                    isLoading = isConverting,
                    loadingText = "Converting...",
                    icon = Icons.Default.Description,
                    text = "Convert to Word"
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
                    label = "pdf_to_word_step_content"
                ) { step ->
                    when (step) {
                        PdfToWordStep.SELECT -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PdfToolUploadCard(
                                    title = "Select PDF Document",
                                    subtitle = "Extract text from digital or scanned PDFs into an editable Microsoft Word (.docx) document",
                                    buttonText = "Select PDF File",
                                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }
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
                                    title = "Select PDF",
                                    description = "Choose any standard PDF document from your phone storage",
                                    icon = Icons.Outlined.UploadFile
                                )
                                PdfToolFeatureItem(
                                    number = "2",
                                    title = "Digital & OCR Extraction",
                                    description = "Extracts native text instantly, falling back to ML Kit OCR for scanned pages",
                                    icon = Icons.Outlined.DocumentScanner
                                )
                                PdfToolFeatureItem(
                                    number = "3",
                                    title = "Editable Word Document",
                                    description = "Outputs a clean OpenXML (.docx) file ready to open, edit, and share",
                                    icon = Icons.Outlined.Description
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Privacy reassurance banner
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
                                        text = "100% Private & Offline. No data ever leaves your device.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        PdfToWordStep.OPTIONS -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PdfSelectedFileCard(
                                    fileName = fileName ?: "Selected Document",
                                    pageCount = pageCount,
                                    fileSizeBytes = fileSizeBytes,
                                    isLoading = isLoadingInfo,
                                    onChangeFile = { filePickerLauncher.launch(arrayOf("application/pdf")) }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "Export Settings",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Format Card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Output Format",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Microsoft Word Document",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = ".DOCX",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Information Card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Formatting & Layout Information",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Extracts body text with clean paragraph structure. Non-standard page geometry, decorative drawings, and background watermarks are omitted to produce clean, editable documents.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(90.dp))
                            }
                        }

                        PdfToWordStep.RESULT -> {
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
                    subtitle = if (pageCount > 0) "Extracting text content" else null
                )
                processingError != null -> ProcessingState.Error(
                    title = "Conversion Failed",
                    message = processingError!!,
                    retryLabel = "Try Again",
                    cancelLabel = "Back"
                )
                outputUri != null -> {
                    val outName = outputUri?.path?.let { File(it).name } ?: "document.docx"
                    val sizeFormatted = formatToolFileSize(outputFileSizeBytes)
                    ProcessingState.Success(
                        title = "Word Document Created!",
                        subtitle = "$outName • $sizeFormatted\nSaved and ready to view or share",
                        primaryActionLabel = "Open Document",
                        secondaryActionLabel = "Share",
                        tertiaryActionLabel = "Convert Another"
                    )
                }
                else -> ProcessingState.Idle
            }

            FullScreenProcessingView(
                state = processingState,
                onCancel = { viewModel.cancelConversion() },
                onSuccessPrimaryAction = {
                    outputUri?.let { uri ->
                        val encodedUri = Base64.encodeToString(
                            uri.toString().toByteArray(),
                            Base64.URL_SAFE or Base64.NO_WRAP
                        )
                        navController.navigate("docx_reader/$encodedUri")
                    }
                },
                onSuccessSecondaryAction = {
                    outputUri?.let { uri ->
                        val file = File(uri.path ?: "")
                        val shareUri = if (file.exists()) {
                            FileProvider.getUriForFile(
                                context,
                                "com.stutool.stutool.fileprovider",
                                file
                            )
                        } else uri

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Word Document"))
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
