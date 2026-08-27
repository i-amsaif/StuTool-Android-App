package com.stutool.stutool.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfShareUtils {
    private const val TAG = "PdfShareUtils"
    private const val SHARE_CACHE_DIR = "share_cache"
    private const val FILE_PROVIDER_AUTHORITY = "com.stutool.stutool.fileprovider"

    /**
     * Shares a PDF safely by resolving the best available source and routing it through FileProvider.
     *
     * @param context the context
     * @param localCopyPath the path to the internal persisted copy (if it exists)
     * @param originalUri the original content URI (if it's an externally opened file)
     * @param activeTempPdfFile the decrypted temp file (if it's a password-protected PDF currently open)
     * @param displayName the original filename to display to the receiving app
     */
    fun sharePdf(
        context: Context,
        localCopyPath: String?,
        originalUri: Uri?,
        activeTempPdfFile: File?,
        displayName: String
    ) {
        try {
            val shareFile = prepareShareFile(context, localCopyPath, originalUri, activeTempPdfFile, displayName)

            if (shareFile == null || !shareFile.exists() || shareFile.length() == 0L) {
                showError(context, "Cannot share this file. The original document may have been deleted.")
                return
            }

            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, shareFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share PDF")
            // Required for some devices/Android versions to ensure the Chooser itself doesn't crash
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (shareIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                showError(context, "No application found to share PDFs.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share PDF", e)
            showError(context, "Failed to prepare PDF for sharing.")
        }
    }

    /**
     * Resolves the best available readable File for a PDF document.
     *
     * Priority:
     *  1. activeTempPdfFile  â€“ decrypted representation (protected PDFs)
     *  2. localCopyPath      â€“ persisted internal copy (filesDir/pdf_copies/)
     *  3. originalUri stream â€“ copy into the designated cache directory as a last resort
     *
     * @param cacheSubDir the specific subdirectory inside cacheDir to use for the fallback copy
     *                    (e.g. "share_cache" or "print_cache"). Kept isolated so Share and Print
     *                    files never interfere with each other.
     * @return A valid, readable File, or null if no source is accessible.
     */
    internal fun resolveReadableFile(
        context: Context,
        localCopyPath: String?,
        originalUri: Uri?,
        activeTempPdfFile: File?,
        displayName: String,
        cacheSubDir: String
    ): File? {
        val sanitizedName = sanitizeFilename(displayName)

        // 1. Decrypted temp file (password-protected PDF that is currently open)
        if (activeTempPdfFile != null && activeTempPdfFile.exists() && activeTempPdfFile.length() > 0L) {
            return copyFileToCache(context, activeTempPdfFile, sanitizedName, cacheSubDir)
        }

        // 2. Persisted internal copy (filesDir/pdf_copies/) â€“ share directly, no further copy needed
        //    FileProvider covers this path via the "pdf_copies" entry in file_paths.xml
        if (!localCopyPath.isNullOrBlank()) {
            val localFile = File(localCopyPath)
            if (localFile.exists() && localFile.length() > 0L) {
                return localFile
            }
        }

        // 3. Fallback: copy the original URI stream to avoid SecurityException when passing
        //    an external content:// URI to a third-party app/framework.
        if (originalUri != null) {
            return copyUriToCache(context, originalUri, sanitizedName, cacheSubDir)
        }

        return null
    }

    // â”€â”€ Share internals â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun prepareShareFile(
        context: Context,
        localCopyPath: String?,
        originalUri: Uri?,
        activeTempPdfFile: File?,
        displayName: String
    ): File? = resolveReadableFile(
        context, localCopyPath, originalUri, activeTempPdfFile, displayName, SHARE_CACHE_DIR
    )

    // â”€â”€ Cache helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun copyFileToCache(context: Context, sourceFile: File, sanitizedName: String, cacheSubDir: String): File? {
        return try {
            val dir = File(context.cacheDir, cacheSubDir).apply { mkdirs() }
            val destFile = File(dir, "${cacheSubDir.uppercase()}_${System.currentTimeMillis()}_$sanitizedName")
            sourceFile.inputStream().use { input ->
                BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file to $cacheSubDir", e)
            null
        }
    }

    private fun copyUriToCache(context: Context, sourceUri: Uri, sanitizedName: String, cacheSubDir: String): File? {
        return try {
            val dir = File(context.cacheDir, cacheSubDir).apply { mkdirs() }
            val destFile = File(dir, "${cacheSubDir.uppercase()}_${System.currentTimeMillis()}_$sanitizedName")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                    BufferedInputStream(input).copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying URI to $cacheSubDir", e)
            null
        }
    }

    // â”€â”€ Utilities â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @androidx.annotation.VisibleForTesting
    internal fun sanitizeFilename(name: String): String {
        // Replace any character that is not alphanumeric, dot, hyphen, or space with underscore
        var safeName = name.replace(Regex("[^a-zA-Z0-9.\\-_ ]"), "_")
        if (safeName.isBlank()) safeName = "Document"
        if (!safeName.lowercase().endsWith(".pdf")) {
            safeName += ".pdf"
        }
        return safeName
    }

    internal fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
