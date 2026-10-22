package com.stutool.stutool.ui.screens.viewer

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxReaderScreen(
    navController: NavController,
    uriString: String,
    viewModel: DocxReaderViewModel = viewModel()
) {
    val documentName by viewModel.documentName.collectAsState()
    val fileSizeBytes by viewModel.fileSizeBytes.collectAsState()
    val blocks by viewModel.blocks.collectAsState()
    val isEmptyDocument by viewModel.isEmptyDocument.collectAsState()
    val isTruncated by viewModel.isTruncated.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(uriString) {
        viewModel.openFromEncodedUri(uriString)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelLoading()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = documentName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val sizeText = remember(fileSizeBytes) { formatDocxFileSize(fileSizeBytes) }
                        val metaText = if (sizeText.isNotEmpty()) {
                            "DOCX • $sizeText"
                        } else {
                            "DOCX Document"
                        }
                        Text(
                            text = metaText,
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
                        enabled = fontSizeSp > DocxReaderViewModel.MIN_FONT_SIZE_SP
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextDecrease,
                            contentDescription = "Decrease Font Size"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.increaseFontSize() },
                        enabled = fontSizeSp < DocxReaderViewModel.MAX_FONT_SIZE_SP
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
                            text = "Loading Word document...",
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
                            text = "Cannot Open Word Document",
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

                isEmptyDocument -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Empty Word Document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This .docx file does not contain any readable text or tables.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isTruncated) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
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
                                        text = "Large document safeguard: displaying the first ${blocks.size} sections.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        SelectionContainer(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    items = blocks,
                                    key = { index, _ -> index }
                                ) { _, block ->
                                    when (block) {
                                        is DocxBlock.Paragraph -> {
                                            DocxParagraphView(
                                                paragraph = block,
                                                baseFontSizeSp = fontSizeSp
                                            )
                                        }
                                        is DocxBlock.Table -> {
                                            DocxTableView(
                                                table = block,
                                                baseFontSizeSp = fontSizeSp
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

@Composable
private fun DocxParagraphView(
    paragraph: DocxBlock.Paragraph,
    baseFontSizeSp: Int
) {
    val annotated = remember(paragraph.runs, paragraph.headingLevel, paragraph.isTitle) {
        buildParagraphAnnotatedString(paragraph)
    }

    val effectiveFontSizeSp = when {
        paragraph.isTitle -> baseFontSizeSp + 10
        paragraph.isSubtitle -> baseFontSizeSp + 4
        paragraph.headingLevel == 1 -> baseFontSizeSp + 7
        paragraph.headingLevel == 2 -> baseFontSizeSp + 5
        paragraph.headingLevel == 3 -> baseFontSizeSp + 3
        paragraph.headingLevel == 4 -> baseFontSizeSp + 2
        paragraph.headingLevel == 5 || paragraph.headingLevel == 6 -> baseFontSizeSp + 1
        else -> baseFontSizeSp
    }

    val isHeading = paragraph.isTitle || paragraph.headingLevel != null
    val topPadding = if (isHeading) 8.dp else 0.dp
    val bottomPadding = if (isHeading) 2.dp else 0.dp
    val startIndent = (paragraph.indentLevel.coerceIn(0, 6) * 18).dp

    val textColor = if (paragraph.isSubtitle) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    val defaultWeight = if (isHeading && !paragraph.isSubtitle) {
        FontWeight.Bold
    } else {
        FontWeight.Normal
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent, top = topPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.Top
    ) {
        if (paragraph.listPrefix != null) {
            Text(
                text = paragraph.listPrefix,
                fontSize = effectiveFontSizeSp.sp,
                lineHeight = (effectiveFontSizeSp + 7).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .widthIn(min = 24.dp)
                    .padding(end = 6.dp)
            )
        }
        Text(
            text = annotated,
            fontSize = effectiveFontSizeSp.sp,
            lineHeight = (effectiveFontSizeSp + 7).sp,
            fontWeight = defaultWeight,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DocxTableView(
    table: DocxBlock.Table,
    baseFontSizeSp: Int
) {
    val maxColumns = remember(table.rows) {
        table.rows.maxOfOrNull { row -> row.cells.sumOf { it.gridSpan.coerceAtLeast(1) } }?.coerceAtLeast(1) ?: 1
    }
    val horizontalScroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val cellFontSizeSp = (baseFontSizeSp - 1).coerceAtLeast(11)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        val useHorizontalScroll = maxColumns > 2
        val columnModifier = if (useHorizontalScroll) {
            Modifier.horizontalScroll(horizontalScroll)
        } else {
            Modifier.fillMaxWidth()
        }

        Column(modifier = columnModifier) {
            table.rows.forEachIndexed { rowIndex, row ->
                val isHeaderRow = row.isHeader || (rowIndex == 0 && table.rows.size > 1)
                Row(
                    modifier = Modifier
                        .then(if (useHorizontalScroll) Modifier else Modifier.fillMaxWidth())
                        .background(if (isHeaderRow) headerBg else MaterialTheme.colorScheme.surface)
                        .border(width = 0.5.dp, color = borderColor),
                    verticalAlignment = Alignment.Top
                ) {
                    row.cells.forEach { cell ->
                        val span = cell.gridSpan.coerceAtLeast(1)
                        val cellModifier = if (useHorizontalScroll) {
                            Modifier
                                .width((150 * span).dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        } else {
                            Modifier
                                .weight(span.toFloat())
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        }
                        Column(
                            modifier = cellModifier,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (cell.paragraphs.isEmpty()) {
                                Text(
                                    text = " ",
                                    fontSize = cellFontSizeSp.sp
                                )
                            } else {
                                cell.paragraphs.forEach { cellPara ->
                                    val cellAnnotated = remember(cellPara.runs) {
                                        buildParagraphAnnotatedString(cellPara)
                                    }
                                    Text(
                                        text = cellAnnotated,
                                        fontSize = cellFontSizeSp.sp,
                                        lineHeight = (cellFontSizeSp + 5).sp,
                                        fontWeight = if (isHeaderRow) FontWeight.SemiBold else FontWeight.Normal,
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

private fun buildParagraphAnnotatedString(paragraph: DocxBlock.Paragraph): AnnotatedString {
    return buildAnnotatedString {
        for (run in paragraph.runs) {
            val decorations = buildList {
                if (run.isUnderline) add(TextDecoration.Underline)
                if (run.isStrike) add(TextDecoration.LineThrough)
            }
            val combinedDecoration = if (decorations.isEmpty()) {
                TextDecoration.None
            } else {
                TextDecoration.combine(decorations)
            }
            val spanStyle = SpanStyle(
                fontWeight = if (run.isBold) FontWeight.Bold else null,
                fontStyle = if (run.isItalic) FontStyle.Italic else null,
                textDecoration = combinedDecoration
            )
            withStyle(spanStyle) {
                append(run.text)
            }
        }
    }
}

private fun formatDocxFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
