package com.stutool.stutool.ui.screens.library

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.stutool.stutool.domain.model.RecentDocxFile
import com.stutool.stutool.domain.model.RecentPdf
import com.stutool.stutool.domain.model.RecentTxtFile
import com.stutool.stutool.domain.model.RecentXlsxFile
import com.stutool.stutool.ui.theme.LocalGlassTokens
import com.valentinilk.shimmer.shimmer
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.TableChart
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers

private val SUPPORTED_PICKER_MIME_TYPES = arrayOf(
    "application/pdf",
    "text/plain",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel"
)

private sealed interface RecentLibraryItem {
    val lastOpened: Long
    val isFavorite: Boolean

    data class PdfItem(val pdf: RecentPdf) : RecentLibraryItem {
        override val lastOpened: Long get() = pdf.lastOpened
        override val isFavorite: Boolean get() = pdf.isFavorite
    }

    data class TxtItem(val txt: RecentTxtFile) : RecentLibraryItem {
        override val lastOpened: Long get() = txt.lastOpened
        override val isFavorite: Boolean get() = false
    }

    data class DocxItem(val docx: RecentDocxFile) : RecentLibraryItem {
        override val lastOpened: Long get() = docx.lastOpened
        override val isFavorite: Boolean get() = false
    }

    data class XlsxItem(val xlsx: RecentXlsxFile) : RecentLibraryItem {
        override val lastOpened: Long get() = xlsx.lastOpened
        override val isFavorite: Boolean get() = false
    }
}

@Composable
fun PdfLibraryScreen(
    navController: NavController,
    viewModel: PdfLibraryViewModel = viewModel()
) {
    val recentPdfs by viewModel.recentPdfs.collectAsState()
    val recentTxts by viewModel.recentTxts.collectAsState()
    val recentDocxs by viewModel.recentDocxs.collectAsState()
    val recentXlsxs by viewModel.recentXlsxs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val glass = LocalGlassTokens.current
    var permissionErrorDialog by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val tutorialPrefs = remember { com.stutool.stutool.tutorial.TutorialPreferences(context) }
    var showOptInDialog by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var sessionPromptHandled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!sessionPromptHandled && tutorialPrefs.shouldShowTutorialPrompt(com.stutool.stutool.tutorial.TutorialType.PDF_VIEWER)) {
            showOptInDialog = true
        }
    }

    val recentItems = remember(recentPdfs, recentTxts, recentDocxs, recentXlsxs) {
        buildList {
            recentPdfs.forEach { add(RecentLibraryItem.PdfItem(it)) }
            recentTxts.forEach { add(RecentLibraryItem.TxtItem(it)) }
            recentDocxs.forEach { add(RecentLibraryItem.DocxItem(it)) }
            recentXlsxs.forEach { add(RecentLibraryItem.XlsxItem(it)) }
        }.sortedWith(
            compareByDescending<RecentLibraryItem> { it.isFavorite }
                .thenByDescending { it.lastOpened }
        )
    }

    // Refresh history when screen recomposes / resurfaces
    LaunchedEffect(Unit) {
        viewModel.loadRecentPdfs()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Ensure permission persists
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            var name = "Document"
            var size = 0L
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
            
            val candidateFileName = if (name != "Document") {
                name
            } else {
                it.lastPathSegment ?: name
            }
            // Strip extension for display consistency
            val displayName = name.substringBeforeLast(".").takeIf { s -> s.isNotBlank() } ?: name
            val encodedUri = android.util.Base64.encodeToString(
                it.toString().toByteArray(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            )
            val mimeType = context.contentResolver.getType(it)

            val isXlsxFile = candidateFileName.endsWith(".xlsx", ignoreCase = true) ||
                mimeType.equals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    ignoreCase = true
                )
            val isLegacyXlsFile = (!isXlsxFile && candidateFileName.endsWith(".xls", ignoreCase = true)) ||
                (!isXlsxFile && mimeType.equals("application/vnd.ms-excel", ignoreCase = true))

            val isDocxFile = !isXlsxFile && !isLegacyXlsFile && (
                candidateFileName.endsWith(".docx", ignoreCase = true) ||
                    mimeType.equals(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ignoreCase = true
                    )
                )
            val isLegacyDocFile = (!isDocxFile && !isXlsxFile && !isLegacyXlsFile &&
                candidateFileName.endsWith(".doc", ignoreCase = true)) ||
                mimeType.equals("application/msword", ignoreCase = true)
            val isTxtFile = !isXlsxFile && !isLegacyXlsFile && !isDocxFile && !isLegacyDocFile && (
                (mimeType != null && mimeType.startsWith("text/", ignoreCase = true)) ||
                    candidateFileName.endsWith(".txt", ignoreCase = true)
                )

            when {
                isXlsxFile -> {
                    viewModel.addRecentXlsx(uri = it.toString(), name = displayName, size = size)
                    navController.navigate("xlsx_reader/$encodedUri")
                }
                isLegacyXlsFile -> {
                    permissionErrorDialog =
                        "Legacy Excel (.xls) binary spreadsheets are not supported.\n\n" +
                            "Please save or export this spreadsheet as a modern .xlsx file and try again."
                }
                isDocxFile -> {
                    viewModel.addRecentDocx(uri = it.toString(), name = displayName, size = size)
                    navController.navigate("docx_reader/$encodedUri")
                }
                isLegacyDocFile -> {
                    navController.navigate("docx_reader/$encodedUri")
                }
                isTxtFile -> {
                    viewModel.addRecentTxt(uri = it.toString(), name = displayName, size = size)
                    navController.navigate("txt_reader/$encodedUri")
                }
                else -> {
                    viewModel.addRecentPdf(uri = it.toString(), name = displayName, size = size)
                    navController.navigate("reader_screen/$encodedUri")
                }
            }
        }
    }

    // ── Permission error dialog ──
    permissionErrorDialog?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { permissionErrorDialog = null },
            title = { Text("Cannot Open File", color = MaterialTheme.colorScheme.onBackground) },
            text = { Text(errorMsg, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { permissionErrorDialog = null }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { launcher.launch(SUPPORTED_PICKER_MIME_TYPES) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 96.dp) 
            ) {
                Icon(Icons.Default.Add, contentDescription = "Open Document", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glass.backgroundBrush)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Hi Reader",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Files",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recentItems.isNotEmpty()) {
                    var showClearDialog by remember { mutableStateOf(false) }
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.primary)
                    }
                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text("Clear Recent History", color = MaterialTheme.colorScheme.onBackground) },
                            text = { Text("Are you sure you want to clear your recent files history? This will not delete the actual files.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            confirmButton = {
                                TextButton(onClick = { 
                                    viewModel.clearAllRecentPdfs()
                                    showClearDialog = false 
                                }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearDialog = false }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(5) {
                        RecentPdfSkeleton()
                    }
                }
            } else if (recentItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Recent Files",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Files you open will appear here",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { launcher.launch(SUPPORTED_PICKER_MIME_TYPES) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Document")
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recentItems) { item ->
                        when (item) {
                            is RecentLibraryItem.PdfItem -> {
                                val pdf = item.pdf
                                RecentPdfItem(
                                    pdf = pdf,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val resolvedUriString = resolveAccessibleUri(pdf, context)
                                    if (resolvedUriString != null) {
                                        viewModel.addRecentPdf(pdf.uri, pdf.name, pdf.size) // bump timestamp
                                        val encodedUri = android.util.Base64.encodeToString(
                                            resolvedUriString.toByteArray(),
                                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                                        )
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            navController.navigate("reader_screen/$encodedUri")
                                        }
                                    } else {
                                        viewModel.deleteRecentPdf(pdf.uri)
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            permissionErrorDialog =
                                                "\"${pdf.name}\" can no longer be accessed.\n\n" +
                                                "The file may have been moved, deleted, or the app that " +
                                                "shared it has revoked access. It has been removed from your recent files."
                                        }
                                    }
                                }
                            },
                            onRemove = {
                                viewModel.deleteRecentPdf(pdf.uri)
                            },
                            onShare = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val originalUri = Uri.parse(pdf.uri)
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        com.stutool.stutool.utils.PdfShareUtils.sharePdf(
                                            context = context,
                                            localCopyPath = pdf.localCopyPath,
                                            originalUri = originalUri,
                                            activeTempPdfFile = null,
                                            displayName = pdf.name
                                        )
                                    }
                                }
                            },
                            onPrint = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val originalUri = Uri.parse(pdf.uri)
                                    val printFile = com.stutool.stutool.utils.PdfShareUtils.resolveReadableFile(
                                        context = context,
                                        localCopyPath = pdf.localCopyPath,
                                        originalUri = originalUri,
                                        activeTempPdfFile = null,
                                        displayName = pdf.name,
                                        cacheSubDir = "print_cache"
                                    )
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        if (printFile == null || !printFile.exists()) {
                                            com.stutool.stutool.utils.PdfShareUtils.showError(
                                                context, "Cannot print: the document source is unavailable."
                                            )
                                            return@withContext
                                        }
                                        val printManager = context.getSystemService(
                                            android.content.Context.PRINT_SERVICE
                                        ) as? android.print.PrintManager ?: return@withContext
                                        val jobName = com.stutool.stutool.utils.PdfShareUtils
                                            .sanitizeFilename(pdf.name).removeSuffix(".pdf")
                                        val adapter = object : android.print.PrintDocumentAdapter() {
                                            override fun onLayout(
                                                old: android.print.PrintAttributes?,
                                                new: android.print.PrintAttributes?,
                                                cancel: android.os.CancellationSignal?,
                                                cb: LayoutResultCallback?,
                                                extras: android.os.Bundle?
                                            ) {
                                                if (cancel?.isCanceled == true) { cb?.onLayoutCancelled(); return }
                                                cb?.onLayoutFinished(
                                                    android.print.PrintDocumentInfo.Builder(jobName)
                                                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                                        .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                                                        .build(), true
                                                )
                                            }
                                            override fun onWrite(
                                                pages: Array<out android.print.PageRange>?,
                                                dest: android.os.ParcelFileDescriptor?,
                                                cancel: android.os.CancellationSignal?,
                                                cb: WriteResultCallback?
                                            ) {
                                                if (cancel?.isCanceled == true) { cb?.onWriteCancelled(); return }
                                                try {
                                                    java.io.FileInputStream(printFile).use { i ->
                                                        android.os.ParcelFileDescriptor.AutoCloseOutputStream(dest).use { o -> i.copyTo(o) }
                                                    }
                                                    cb?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                                                } catch (e: Exception) {
                                                    cb?.onWriteFailed(e.message)
                                                }
                                            }
                                        }
                                        printManager.print(jobName, adapter, null)
                                    }
                                }
                            }
                                )
                            }
                            is RecentLibraryItem.TxtItem -> {
                                val txt = item.txt
                                RecentTxtItem(
                                    txt = txt,
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val resolvedUriString = resolveAccessibleTxtUri(txt, context)
                                            if (resolvedUriString != null) {
                                                viewModel.addRecentTxt(txt.uri, txt.name, txt.size, txt.localCopyPath)
                                                val encodedUri = android.util.Base64.encodeToString(
                                                    resolvedUriString.toByteArray(),
                                                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                                                )
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    navController.navigate("txt_reader/$encodedUri")
                                                }
                                            } else {
                                                viewModel.deleteRecentTxt(txt.uri)
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    permissionErrorDialog =
                                                        "\"${txt.name}\" can no longer be accessed.\n\n" +
                                                        "The file may have been moved, deleted, or the app that " +
                                                        "shared it has revoked access. It has been removed from your recent files."
                                                }
                                            }
                                        }
                                    },
                                    onRemove = {
                                        viewModel.deleteRecentTxt(txt.uri)
                                    }
                                )
                            }
                            is RecentLibraryItem.DocxItem -> {
                                val docx = item.docx
                                RecentDocxItem(
                                    docx = docx,
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val resolvedUriString = resolveAccessibleDocxUri(docx, context)
                                            if (resolvedUriString != null) {
                                                viewModel.addRecentDocx(docx.uri, docx.name, docx.size, docx.localCopyPath)
                                                val encodedUri = android.util.Base64.encodeToString(
                                                    resolvedUriString.toByteArray(),
                                                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                                                )
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    navController.navigate("docx_reader/$encodedUri")
                                                }
                                            } else {
                                                viewModel.deleteRecentDocx(docx.uri)
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    permissionErrorDialog =
                                                        "\"${docx.name}\" can no longer be accessed.\n\n" +
                                                        "The file may have been moved, deleted, or the app that " +
                                                        "shared it has revoked access. It has been removed from your recent files."
                                                }
                                            }
                                        }
                                    },
                                    onRemove = {
                                        viewModel.deleteRecentDocx(docx.uri)
                                    }
                                )
                            }
                            is RecentLibraryItem.XlsxItem -> {
                                val xlsx = item.xlsx
                                RecentXlsxItem(
                                    xlsx = xlsx,
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val resolvedUriString = resolveAccessibleXlsxUri(xlsx, context)
                                            if (resolvedUriString != null) {
                                                viewModel.addRecentXlsx(xlsx.uri, xlsx.name, xlsx.size, xlsx.localCopyPath)
                                                val encodedUri = android.util.Base64.encodeToString(
                                                    resolvedUriString.toByteArray(),
                                                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                                                )
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    navController.navigate("xlsx_reader/$encodedUri")
                                                }
                                            } else {
                                                viewModel.deleteRecentXlsx(xlsx.uri)
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    permissionErrorDialog =
                                                        "\"${xlsx.name}\" can no longer be accessed.\n\n" +
                                                        "The file may have been moved, deleted, or the app that " +
                                                        "shared it has revoked access. It has been removed from your recent files."
                                                }
                                            }
                                        }
                                    },
                                    onRemove = {
                                        viewModel.deleteRecentXlsx(xlsx.uri)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOptInDialog) {
        com.stutool.stutool.tutorial.TutorialOptInDialog(
            tutorialType = com.stutool.stutool.tutorial.TutorialType.PDF_VIEWER,
            onWatchTutorial = {
                showOptInDialog = false
                sessionPromptHandled = true
                showPlayer = true
            },
            onNoThanks = {
                showOptInDialog = false
                sessionPromptHandled = true
                tutorialPrefs.setTutorialDeclined(com.stutool.stutool.tutorial.TutorialType.PDF_VIEWER, true)
            },
            onDismissRequest = {
                showOptInDialog = false
                sessionPromptHandled = true
            }
        )
    }

    if (showPlayer) {
        com.stutool.stutool.tutorial.TutorialPlayerDialog(
            tutorialType = com.stutool.stutool.tutorial.TutorialType.PDF_VIEWER,
            onDismiss = { completedOrDismissedByUser ->
                if (completedOrDismissedByUser) {
                    tutorialPrefs.markTutorialSeen(com.stutool.stutool.tutorial.TutorialType.PDF_VIEWER)
                }
                showPlayer = false
            }
        )
    }
}

private fun resolveAccessibleXlsxUri(xlsx: RecentXlsxFile, context: android.content.Context): String? {
    val uri = Uri.parse(xlsx.uri)
    if (uri.scheme == "file") {
        val file = java.io.File(uri.path ?: return null)
        return if (file.exists() && file.length() > 0L) xlsx.uri else fallbackToLocalXlsxCopy(xlsx)
    }
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.close()
        xlsx.uri
    } catch (e: SecurityException) {
        fallbackToLocalXlsxCopy(xlsx)
    } catch (e: Exception) {
        fallbackToLocalXlsxCopy(xlsx)
    }
}

private fun fallbackToLocalXlsxCopy(xlsx: RecentXlsxFile): String? {
    val path = xlsx.localCopyPath ?: return null
    val file = java.io.File(path)
    return if (file.exists() && file.length() > 0L) {
        Uri.fromFile(file).toString()
    } else null
}

@Composable
fun RecentXlsxItem(
    xlsx: RecentXlsxFile,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val glass = LocalGlassTokens.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TableChart,
                contentDescription = null,
                tint = Color(0xFF107C41),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = xlsx.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF107C41).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "XLSX",
                            color = Color(0xFF107C41),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatSize(xlsx.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(xlsx.lastOpened),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .widthIn(min = 200.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Open",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onClick() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove from Recent",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onRemove() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun resolveAccessibleDocxUri(docx: RecentDocxFile, context: android.content.Context): String? {
    val uri = Uri.parse(docx.uri)
    if (uri.scheme == "file") {
        val file = java.io.File(uri.path ?: return null)
        return if (file.exists() && file.length() > 0L) docx.uri else fallbackToLocalDocxCopy(docx)
    }
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.close()
        docx.uri
    } catch (e: SecurityException) {
        fallbackToLocalDocxCopy(docx)
    } catch (e: Exception) {
        fallbackToLocalDocxCopy(docx)
    }
}

private fun fallbackToLocalDocxCopy(docx: RecentDocxFile): String? {
    val path = docx.localCopyPath ?: return null
    val file = java.io.File(path)
    return if (file.exists() && file.length() > 0L) {
        Uri.fromFile(file).toString()
    } else null
}

private fun resolveAccessibleTxtUri(txt: RecentTxtFile, context: android.content.Context): String? {
    val uri = Uri.parse(txt.uri)
    if (uri.scheme == "file") {
        val file = java.io.File(uri.path ?: return null)
        return if (file.exists()) txt.uri else fallbackToLocalTxtCopy(txt)
    }
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.close()
        txt.uri
    } catch (e: SecurityException) {
        fallbackToLocalTxtCopy(txt)
    } catch (e: Exception) {
        fallbackToLocalTxtCopy(txt)
    }
}

private fun fallbackToLocalTxtCopy(txt: RecentTxtFile): String? {
    val path = txt.localCopyPath ?: return null
    val file = java.io.File(path)
    return if (file.exists()) {
        Uri.fromFile(file).toString()
    } else null
}

@Composable
fun RecentDocxItem(
    docx: RecentDocxFile,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val glass = LocalGlassTokens.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = docx.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "DOCX",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatSize(docx.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(docx.lastOpened),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .widthIn(min = 200.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Open",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onClick() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove from Recent",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onRemove() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Returns the best accessible URI string for [pdf]:
 * 1. The original URI if it is still readable.
 * 2. A file:// URI from the local internal copy if one exists.
 * 3. null if neither is reachable.
 */
private fun resolveAccessibleUri(pdf: RecentPdf, context: android.content.Context): String? {
    val uri = Uri.parse(pdf.uri)
    // For file:// URIs just check existence directly
    if (uri.scheme == "file") {
        val file = java.io.File(uri.path ?: return null)
        return if (file.exists()) pdf.uri else fallbackToLocalCopy(pdf)
    }
    // For content:// URIs, attempt to open a descriptor — SecurityException = permission gone
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.close()
        pdf.uri // original still accessible
    } catch (e: SecurityException) {
        fallbackToLocalCopy(pdf)
    } catch (e: Exception) {
        fallbackToLocalCopy(pdf)
    }
}

private fun fallbackToLocalCopy(pdf: RecentPdf): String? {
    val path = pdf.localCopyPath ?: return null
    val file = java.io.File(path)
    return if (file.exists() && file.length() > 0L) {
        // Return as file:// URI so PdfReaderScreen can decode it uniformly
        Uri.fromFile(file).toString()
    } else null
}

@Composable
fun RecentTxtItem(
    txt: RecentTxtFile,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val glass = LocalGlassTokens.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = txt.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "TXT",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatSize(txt.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(txt.lastOpened),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .widthIn(min = 200.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Open",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onClick() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove from Recent",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onRemove() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentPdfItem(
    pdf: RecentPdf,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit
) {
    val glass = LocalGlassTokens.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatSize(pdf.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(pdf.lastOpened),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (pdf.pageCount != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${pdf.pageCount} pages",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (pdf.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .widthIn(min = 200.dp)
                ) {
                    // â”€â”€ Open â”€â”€
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Open",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onClick() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                    // â”€â”€ Share â”€â”€
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Share",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onShare() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                    // â”€â”€ Print â”€â”€
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Print",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onPrint() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                    // â”€â”€ Divider before destructive action â”€â”€
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    // â”€â”€ Remove from Recent (destructive) â”€â”€
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove from Recent",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showMenu = false; onRemove() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(Date(timestamp))
}

@Composable
fun RecentPdfSkeleton() {
    val glass = LocalGlassTokens.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}
