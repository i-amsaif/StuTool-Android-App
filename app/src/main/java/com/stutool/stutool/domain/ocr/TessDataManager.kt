package com.stutool.stutool.domain.ocr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object TessDataManager {
    private const val TAG = "TessDataManager"
    private const val TESSDATA_DIR = "tessdata"
    private val copyMutex = Mutex()

    /**
     * Prepares and ensures the required .traineddata files exist in the app's internal filesDir/tessdata.
     * Returns the root data path (i.e. context.filesDir.absolutePath) required by TessBaseAPI.init().
     */
    suspend fun ensureTessData(context: Context, language: OcrLanguage): String = withContext(Dispatchers.IO) {
        copyMutex.withLock {
            val rootDir = context.filesDir
            val tessDir = File(rootDir, TESSDATA_DIR)
            if (!tessDir.exists()) {
                tessDir.mkdirs()
            }

            val requiredCodes = when (language) {
                OcrLanguage.AUTO -> listOf("eng", "kan", "hin")
                OcrLanguage.KANNADA_ENGLISH -> listOf("kan", "eng")
                OcrLanguage.KANNADA -> listOf("kan")
                OcrLanguage.ENGLISH -> listOf("eng")
                OcrLanguage.HINDI -> listOf("hin")
            }

            for (code in requiredCodes) {
                val fileName = "$code.traineddata"
                val targetFile = File(tessDir, fileName)
                
                // Copy if missing or empty
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    copyAssetToTarget(context, "$TESSDATA_DIR/$fileName", targetFile)
                }
            }

            rootDir.absolutePath
        }
    }

    private fun copyAssetToTarget(context: Context, assetPath: String, targetFile: File) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            var inputStream: InputStream? = null
            try {
                inputStream = context.assets.open(assetPath)
            } catch (e: Exception) {
                Log.w(TAG, "Asset not found: $assetPath (${e.message})")
                return
            }

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                val success = tempFile.renameTo(targetFile)
                if (!success) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                Log.i(TAG, "Successfully extracted $assetPath to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying asset $assetPath: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun isLanguageAvailable(context: Context, languageCode: String): Boolean {
        val tessDir = File(context.filesDir, TESSDATA_DIR)
        val file = File(tessDir, "$languageCode.traineddata")
        return file.exists() && file.length() > 100_000L
    }
}
