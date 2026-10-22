package com.stutool.stutool.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XlsxReaderScreen(
    navController: NavController,
    uriString: String,
    viewModel: XlsxReaderViewModel = viewModel()
) {
    val workbookName by viewModel.workbookName.collectAsState()
    val fileSizeBytes by viewModel.fileSizeBytes.collectAsState()
    val sheets by viewModel.sheets.collectAsState()
    val selectedSheetIndex by viewModel.selectedSheetIndex.collectAsState()
    val isEmptyWorkbook by viewModel.isEmptyWorkbook.collectAsState()
    val isTruncated by viewModel.isTruncated.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()

    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(uriString) {
        viewModel.openFromEncodedUri(uriString)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelLoading()
        }
    }

    // Reset vertical and horizontal scroll positions when switching worksheets
    LaunchedEffect(selectedSheetIndex) {
        listState.scrollToItem(0)
        horizontalScrollState.scrollTo(0)
    }

    val activeSheet = sheets.getOrNull(selectedSheetIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = workbookName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val sizeText = remember(fileSizeBytes) { formatXlsxFileSize(fileSizeBytes) }
                        val sheetCountText = if (sheets.isNotEmpty()) {
                            "${sheets.size} ${if (sheets.size == 1) "sheet" else "sheets"}"
                        } else {
                            ""
                        }
                        val metaParts = listOfNotNull(
                            "XLSX",
                            sizeText.takeIf { it.isNotEmpty() },
                            sheetCountText.takeIf { it.isNotEmpty() }
                        )
                        Text(
                            text = metaParts.joinToString(" • "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelLoading()
                        if (!navController.popBackStack()) {
                            navController.navigate(com.stutool.stutool.navigation.Screen.Viewer.route) {
                                launchSingleTop = true
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.decreaseFontSize() },
                        enabled = fontSizeSp > XlsxReaderViewModel.MIN_FONT_SIZE_SP
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextDecrease,
                            contentDescription = "Decrease Font Size"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.increaseFontSize() },
                        enabled = fontSizeSp < XlsxReaderViewModel.MAX_FONT_SIZE_SP
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextIncrease,
                            contentDescription = "Increase Font Size"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading spreadsheet...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cannot Open Spreadsheet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                if (!navController.popBackStack()) {
                                    navController.navigate(com.stutool.stutool.navigation.Screen.Viewer.route) {
                                        launchSingleTop = true
                                    }
                                }
                            }) {
                                Text("Go Back")
                            }
                            Button(onClick = { viewModel.retry() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                isEmptyWorkbook || sheets.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Empty Spreadsheet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This .xlsx workbook does not contain any populated cells.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sheet tabs bar
                        if (sheets.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedSheetIndex.coerceIn(0, sheets.lastIndex),
                                edgePadding = 12.dp,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                sheets.forEachIndexed { index, sheet ->
                                    Tab(
                                        selected = index == selectedSheetIndex,
                                        onClick = { viewModel.selectSheet(index) },
                                        text = {
                                            Text(
                                                text = sheet.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (index == selectedSheetIndex) {
                                                    FontWeight.SemiBold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        } else if (activeSheet != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeSheet.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "• ${activeSheet.rows.size} rows × ${activeSheet.columnCount} cols",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (isTruncated || activeSheet?.isTruncated == true) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Large spreadsheet safeguard: displaying bounded rows/columns to preserve memory.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        if (activeSheet == null || activeSheet.isEmpty) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "This worksheet (\"${activeSheet?.name ?: ""}\") is empty.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val columnWidths = remember(activeSheet, fontSizeSp) {
                                computeColumnWidths(activeSheet, fontSizeSp)
                            }
                            val rowNumberColWidth = 48.dp
                            val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            val headerBg = MaterialTheme.colorScheme.surfaceVariant
                            val evenRowBg = MaterialTheme.colorScheme.surface
                            val oddRowBg = MaterialTheme.colorScheme.surfaceContainerLow

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Sticky column header row (A, B, C, ...)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(headerBg)
                                        .border(0.5.dp, borderColor),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(rowNumberColWidth)
                                            .border(0.5.dp, borderColor)
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#",
                                            fontSize = (fontSizeSp - 1).coerceAtLeast(10).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(horizontalScrollState)
                                    ) {
                                        for (colIdx in 0 until activeSheet.columnCount) {
                                            Box(
                                                modifier = Modifier
                                                    .width(columnWidths[colIdx])
                                                    .border(0.5.dp, borderColor)
                                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = XlsxDocumentLoader.columnIndexToLabel(colIdx),
                                                    fontSize = (fontSizeSp - 1).coerceAtLeast(10).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // Scrollable grid rows
                                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        itemsIndexed(
                                            items = activeSheet.rows,
                                            key = { idx, row -> "${selectedSheetIndex}-${row.rowNumber}-$idx" }
                                        ) { rowIdx, row ->
                                            val rowBg = if (rowIdx % 2 == 0) evenRowBg else oddRowBg
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(rowBg),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                // Pinned left row number cell
                                                Box(
                                                    modifier = Modifier
                                                        .width(rowNumberColWidth)
                                                        .background(headerBg.copy(alpha = 0.65f))
                                                        .border(0.5.dp, borderColor)
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = row.rowNumber.toString(),
                                                        fontSize = (fontSizeSp - 2).coerceAtLeast(10).sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }

                                                // Horizontally scrolled data cells
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .horizontalScroll(horizontalScrollState)
                                                ) {
                                                    for (colIdx in 0 until activeSheet.columnCount) {
                                                        val cellText = row.cells.getOrNull(colIdx).orEmpty()
                                                        Box(
                                                            modifier = Modifier
                                                                .width(columnWidths[colIdx])
                                                                .border(0.5.dp, borderColor)
                                                                .padding(horizontal = 8.dp, vertical = 7.dp)
                                                        ) {
                                                            Text(
                                                                text = cellText.ifEmpty { " " },
                                                                fontSize = fontSizeSp.sp,
                                                                lineHeight = (fontSizeSp + 5).sp,
                                                                fontWeight = if (rowIdx == 0 && activeSheet.rows.size > 1) {
                                                                    FontWeight.SemiBold
                                                                } else {
                                                                    FontWeight.Normal
                                                                },
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
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
                }
            }
        }
    }
}

private fun computeColumnWidths(sheet: XlsxSheet, fontSizeSp: Int): List<Dp> {
    if (sheet.columnCount <= 0) return emptyList()
    val sampleRows = sheet.rows.take(60)
    val charPixelFactor = (fontSizeSp * 0.62f).coerceAtLeast(7f)

    return List(sheet.columnCount) { colIdx ->
        var maxChars = 4
        for (row in sampleRows) {
            val cell = row.cells.getOrNull(colIdx).orEmpty()
            val longestLine = cell.lineSequence().maxOfOrNull { it.length } ?: 0
            if (longestLine > maxChars) {
                maxChars = longestLine.coerceAtMost(36)
            }
        }
        val estimatedDp = (maxChars * charPixelFactor + 24f).toInt().coerceIn(88, 240)
        estimatedDp.dp
    }
}

private fun formatXlsxFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
