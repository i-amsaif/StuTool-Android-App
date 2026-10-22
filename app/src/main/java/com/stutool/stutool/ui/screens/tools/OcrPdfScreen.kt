package com.stutool.stutool.ui.screens.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.stutool.stutool.domain.ocr.OcrLanguage
import com.stutool.stutool.ui.components.*
import java.io.File

private enum class OcrStep(val index: Int) {
    SELECT(0),
    OPTIONS(1),
    RESULT(2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPdfScreen(
    navController: NavController,
    viewModel: OcrPdfViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedUri.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val fileSizeBytes by viewModel.fileSizeBytes.collectAsState()
    val isImage by viewModel.isImage.collectAsState()
    val imageDimensions by viewModel.imageDimensions.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val engineUsed by viewModel.engineUsed.collectAsState()
    val isLoadingInfo by viewModel.isLoadingInfo.collectAsState()
    val isConverting by viewModel.isConverting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val outputUri by viewModel.outputUri.collectAsState()
    val outputTxtUri by viewModel.outputTxtUri.collectAsState()
    val outputDocxUri by viewModel.outputDocxUri.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()
    val outputFileSizeBytes by viewModel.outputFileSizeBytes.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val processingError by viewModel.processingError.collectAsState()
    val showCloudOptInDialog by viewModel.showCloudOptInDialog.collectAsState()
    val forceOfflineOnly by viewModel.forceOfflineOnly.collectAsState()
    val showConsentDialog by viewModel.showConsentDialog.collectAsState()

    val currentStep = when {
        extractedText.isNotBlank() || outputUri != null -> OcrStep.RESULT
        selectedUri != null -> OcrStep.OPTIONS
        else -> OcrStep.SELECT
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setInput(uri)
        }
    }

    val onNavigateBack = {
        if (isConverting) {
            viewModel.cancelConversion()
        }
        navController.popBackStack()
    }

    val showProcessingOverlay = isConverting || processingError != null

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
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "OCR Document",
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
                    show = currentStep == OcrStep.OPTIONS && !isConverting,
                    onClick = { viewModel.startConversion() },
                    isEnabled = !isLoadingInfo && !isConverting,
                    isLoading = isConverting,
                    loadingText = "Running OCR...",
                    icon = Icons.Default.DocumentScanner,
                    text = "Extract Text"
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
                    label = "ocr_step_content"
                ) { step ->
                    when (step) {
                        OcrStep.SELECT -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PdfToolUploadCard(
                                    title = "Select PDF or Image",
                                    subtitle = "Extract text from scanned PDFs, photos, and documents in Kannada & English",
                                    buttonText = "Select File",
                                    onClick = {
                                        filePickerLauncher.launch(
                                            arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                Text(
                                    text = "Supported Features",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                PdfToolFeatureItem(
                                    number = "1",
                                    title = "PDF & Image Input",
                                    description = "Accepts PDF documents and standard image formats (PNG, JPEG, WebP)",
                                    icon = Icons.Outlined.UploadFile
                                )
                                PdfToolFeatureItem(
                                    number = "2",
                                    title = "Smart Hybrid & Offline OCR",
                                    description = "Fast OCR.space Cloud when online + 100% private Tesseract 5 offline fallback",
                                    icon = Icons.Outlined.DocumentScanner
                                )
                                PdfToolFeatureItem(
                                    number = "3",
                                    title = "Kannada, English & Hindi",
                                    description = "High-accuracy script recognition supporting Indian languages and mixed documents",
                                    icon = Icons.Outlined.Translate
                                )
                                PdfToolFeatureItem(
                                    number = "4",
                                    title = "Multiple Export Formats",
                                    description = "Copy text, or export to TXT, Word (DOCX), and Searchable PDF with Unicode fonts",
                                    icon = Icons.Outlined.FindInPage
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
                                        text = "Smart Auto & Offline options protect your privacy. Zero data leaves your device in Offline mode.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        OcrStep.OPTIONS -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PdfSelectedFileCard(
                                    fileName = fileName ?: "Selected Document",
                                    pageCount = if (isImage) 1 else pageCount,
                                    fileSizeBytes = fileSizeBytes,
                                    isLoading = isLoadingInfo,
                                    onChangeFile = {
                                        filePickerLauncher.launch(
                                            arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")
                                        )
                                    }
                                )

                                if (isImage && imageDimensions != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Image Dimensions: ${imageDimensions!!.first} × ${imageDimensions!!.second} px",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "Recognition Language",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Auto-detect",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Default",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Automatically recognizes Kannada, English & Hindi without manual setup",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Subtle On-Device Only Option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Process on-device only",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Keep all text recognition strictly local on this device without using an internet connection.",
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = forceOfflineOnly,
                                        onCheckedChange = { viewModel.setForceOfflineOnly(it) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Output notice
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
                                            text = if (isImage) "Image Text Extraction" else "Smart PDF Processing",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isImage) {
                                                "Recognizes characters and lines directly from the image. You can copy the text, or export it to TXT and Word (DOCX)."
                                            } else {
                                                "Extracts text across all pages. Genuine digital text is preserved, while scanned pages are processed with OCR. You can copy text or export to TXT, DOCX, and Searchable PDF."
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(90.dp))
                            }
                        }

                        OcrStep.RESULT -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Success summary card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "OCR Completed Successfully",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        val wordCount = extractedText.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                                        Text(
                                            text = "$wordCount words extracted • ${selectedLanguage.displayName}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Processed via ${engineUsed ?: selectedMode.displayName}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Extracted Text Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Extracted Text",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )

                                        Row {
                                            TextButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("OCR Extracted Text", extractedText)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Copy All", fontSize = 13.sp)
                                            }

                                            TextButton(
                                                onClick = {
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, extractedText)
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "Share Extracted Text"))
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Share", fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 140.dp, max = 280.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(12.dp)
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = extractedText.ifBlank { "No text recognized." },
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.verticalScroll(rememberScrollState())
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Export Options",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Export TXT
                                OutlinedButton(
                                    onClick = {
                                        viewModel.exportToTxt { uri ->
                                            shareFile(context, uri, "text/plain", "Share TXT File")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export as Text File (.txt)", fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Export DOCX
                                OutlinedButton(
                                    onClick = {
                                        viewModel.exportToDocx { uri ->
                                            shareFile(
                                                context,
                                                uri,
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                "Share Word Document"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export as Word Document (.docx)", fontWeight = FontWeight.SemiBold)
                                }

                                if (!isImage && outputUri != null) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            outputUri?.let { uri ->
                                                val encodedUri = Base64.encodeToString(
                                                    uri.toString().toByteArray(),
                                                    Base64.URL_SAFE or Base64.NO_WRAP
                                                )
                                                navController.navigate("reader_screen/$encodedUri")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Searchable PDF in Viewer", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = {
                                            outputUri?.let { uri ->
                                                shareFile(context, uri, "application/pdf", "Share Searchable PDF")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Share Searchable PDF", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                TextButton(
                                    onClick = { viewModel.reset() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Process Another File", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(30.dp))
                            }
                        }
                    }
                }
            }
        }

        // Processing & Errors Overlay
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
                    subtitle = if (!isImage && pageCount > 0) "Recognizing characters on pages" else "Extracting text"
                )
                processingError != null -> ProcessingState.Error(
                    title = "OCR Failed",
                    message = processingError!!,
                    retryLabel = when {
                        selectedMode == com.stutool.stutool.domain.ocr.OcrMode.CLOUD_ONLY -> "Try Offline OCR"
                        selectedMode == com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY && fileSizeBytes <= 1048576L && (isImage || pageCount <= 3) -> "Try Cloud OCR"
                        else -> "Try Again"
                    },
                    cancelLabel = "Back"
                )
                else -> ProcessingState.Idle
            }

            FullScreenProcessingView(
                state = processingState,
                onCancel = { viewModel.cancelConversion() },
                onSuccessPrimaryAction = {},
                onErrorRetry = {
                    if (selectedMode == com.stutool.stutool.domain.ocr.OcrMode.CLOUD_ONLY) {
                        viewModel.setMode(com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY)
                        viewModel.clearProcessingError()
                        viewModel.startConversion()
                    } else if (selectedMode == com.stutool.stutool.domain.ocr.OcrMode.OFFLINE_ONLY && fileSizeBytes <= 1048576L && (isImage || pageCount <= 3)) {
                        viewModel.clearProcessingError()
                        viewModel.requestCloudRetry()
                    } else {
                        viewModel.clearProcessingError()
                        viewModel.startConversion()
                    }
                },
                onErrorCancel = {
                    viewModel.clearProcessingError()
                }
            )
        }
    }

    if (showConsentDialog) {
        com.stutool.stutool.ui.components.OcrCloudConsentDialog(
            onProceedOnline = { viewModel.onConsentProceedOnline() },
            onProcessOffline = { viewModel.onConsentProcessOffline() },
            onDismiss = { viewModel.onConsentDismissed() }
        )
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

private fun shareFile(context: Context, uri: Uri, mimeType: String, title: String) {
    val file = File(uri.path ?: "")
    val shareUri = if (file.exists()) {
        try {
            FileProvider.getUriForFile(context, "com.stutool.stutool.fileprovider", file)
        } catch (_: Exception) { uri }
    } else uri

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
