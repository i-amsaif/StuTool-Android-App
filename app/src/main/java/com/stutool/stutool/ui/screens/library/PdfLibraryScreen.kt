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
import com.stutool.stutool.domain.model.RecentPdf
import com.stutool.stutool.ui.theme.LocalGlassTokens
import com.valentinilk.shimmer.shimmer
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun PdfLibraryScreen(
    navController: NavController,
    viewModel: PdfLibraryViewModel = viewModel()
) {
    val recentPdfs by viewModel.recentPdfs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val glass = LocalGlassTokens.current
    var permissionErrorDialog by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
            
            // Strip extension for display consistency (reader ViewModel also strips it)
            val displayName = name.substringBeforeLast(".").takeIf { it.isNotBlank() } ?: name
            
            viewModel.addRecentPdf(uri = it.toString(), name = displayName, size = size)
            // Navigate to reader securely using Base64
            val encodedUri = android.util.Base64.encodeToString(
                it.toString().toByteArray(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            )
            navController.navigate("reader_screen/$encodedUri")
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
                onClick = { launcher.launch(arrayOf("application/pdf")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(bottom = 96.dp) 
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add PDF", tint = MaterialTheme.colorScheme.onPrimary)
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
                if (recentPdfs.isNotEmpty()) {
                    var showClearDialog by remember { mutableStateOf(false) }
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.primary)
                    }
                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text("Clear Recent History", color = MaterialTheme.colorScheme.onBackground) },
                            text = { Text("Are you sure you want to clear your recent PDFs history? This will not delete the actual files.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
            } else if (recentPdfs.isEmpty()) {
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
                        text = "No Recent PDFs",
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
                        onClick = { launcher.launch(arrayOf("application/pdf")) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open PDF")
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recentPdfs) { pdf ->
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
                            }
                        )
                    }
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
fun RecentPdfItem(
    pdf: RecentPdf,
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
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Open", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Recent", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
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
