package com.stutool.stutool.ui.screens.tools

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.TableChart
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

private enum class PdfToExcelStep(val index: Int) {
    SELECT(0),
    OPTIONS(1),
    RESULT(2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToExcelScreen(
    navController: NavController,
    viewModel: PdfToExcelViewModel = viewModel()
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
        outputUri != null -> PdfToExcelStep.RESULT
        selectedUri != null -> PdfToExcelStep.OPTIONS
        else -> PdfToExcelStep.SELECT
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
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "PDF to Excel",
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
                    show = currentStep == PdfToExcelStep.OPTIONS && !isConverting,
                    onClick = { viewModel.startConversion() },
                    isEnabled = !isLoadingInfo && !isConverting,
                    isLoading = isConverting,
                    loadingText = "Extracting...",
                    icon = Icons.Default.TableChart,
                    text = "Convert to Excel"
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
                    label = "pdf_to_excel_step_content"
                ) { step ->
                    when (step) {
                        PdfToExcelStep.SELECT -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PdfToolUploadCard(
                                    title = "Select PDF Document",
                                    subtitle = "Extract text and tabular columns into an editable Microsoft Excel (.xlsx) spreadsheet",
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
                                    description = "Choose any PDF file containing tables, lists, or structured text",
                                    icon = Icons.Outlined.UploadFile
                                )
                                PdfToolFeatureItem(
                                    number = "2",
                                    title = "Spatial Table Alignment",
                                    description = "Intelligently groups text positions into distinct spreadsheet rows and columns",
                                    icon = Icons.Outlined.GridOn
                                )
                                PdfToolFeatureItem(
                                    number = "3",
                                    title = "Ready for Excel & Sheets",
                                    description = "Downloads a native OpenXML .xlsx file compatible with Excel and Google Sheets",
                                    icon = Icons.Outlined.TableChart
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

                        PdfToExcelStep.OPTIONS -> {
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
                                            text = "OpenXML Spreadsheet Document",
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
                                            text = ".XLSX",
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
                                            text = "Table Extraction Guidelines",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Text is sorted by vertical line coordinates and grouped into columns based on horizontal alignment. Complex nested merged cells or scanned PDFs without selectable text may require OCR first.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(90.dp)) // Padding for bottom floating bar
                            }
                        }

                        PdfToExcelStep.RESULT -> {
                            // Handled by FullScreenProcessingView overlay
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
                    subtitle = if (pageCount > 0) "Reading document structure" else null
                )
                processingError != null -> ProcessingState.Error(
                    title = "Conversion Failed",
                    message = processingError!!,
                    retryLabel = "Try Again",
                    cancelLabel = "Back"
                )
                outputUri != null -> {
                    val outName = outputUri?.path?.let { File(it).name } ?: "spreadsheet.xlsx"
                    val sizeFormatted = formatToolFileSize(outputFileSizeBytes)
                    ProcessingState.Success(
                        title = "Spreadsheet Generated!",
                        subtitle = "$outName • $sizeFormatted\nSaved and ready to view or share",
                        primaryActionLabel = "Open Spreadsheet",
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
                        navController.navigate("xlsx_reader/$encodedUri")
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
                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Spreadsheet"))
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
