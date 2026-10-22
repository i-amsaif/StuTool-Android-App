package com.stutool.stutool.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.stutool.stutool.domain.model.RecentTxtFile
import java.io.File

/**
 * Isolated repository for recently opened plain-text (.txt) files.
 * Uses a dedicated SharedPreferences file ("txt_recent_prefs") so that
 * [RecentPdfRepository] ("pdf_prefs") and its schema remain 100% untouched.
 */
class RecentTxtRepository(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "txt_recent_prefs"
        private const val KEY_RECENT_TXTS = "recent_txts"
        const val MAX_RECENT = 50

        fun parseListFromJson(serialized: String?): List<RecentTxtFile> {
            if (serialized.isNullOrBlank()) return emptyList()
            val list = mutableListOf<RecentTxtFile>()
            serialized.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        list.add(RecentTxtFile.fromEncodedRecord(line))
                    } catch (_: Exception) {
                        // Skip malformed entry without failing the whole list
                    }
                }
            }
            return list.sortedByDescending { it.lastOpened }
        }

        fun serializeListToJson(list: List<RecentTxtFile>): String {
            return list.joinToString("\n") { it.toEncodedRecord() }
        }

        fun deduplicateAndInsert(
            currentList: List<RecentTxtFile>,
            txt: RecentTxtFile,
            maxEntries: Int = MAX_RECENT
        ): List<RecentTxtFile> {
            val mutable = currentList.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.fileIdentity == txt.fileIdentity }
            if (existingIndex != -1) {
                val existing = mutable.removeAt(existingIndex)
                mutable.add(
                    0,
                    txt.copy(
                        localCopyPath = txt.localCopyPath ?: existing.localCopyPath,
                        fileIdentity = txt.fileIdentity
                    )
                )
            } else {
                mutable.removeAll { it.uri == txt.uri }
                mutable.add(0, txt)
            }
            return mutable.sortedByDescending { it.lastOpened }.take(maxEntries)
        }
    }

    fun getRecentTxts(): List<RecentTxtFile> {
        val jsonString = prefs.getString(KEY_RECENT_TXTS, null)
        return parseListFromJson(jsonString)
    }

    fun saveRecentTxt(txt: RecentTxtFile) {
        val updated = deduplicateAndInsert(getRecentTxts(), txt, MAX_RECENT)
        saveList(updated)
    }

    fun deleteRecentTxt(uri: String) {
        val currentList = getRecentTxts().toMutableList()
        currentList.removeAll { it.uri == uri }
        saveList(currentList)
    }

    fun clearAllRecentTxts() {
        saveList(emptyList())
    }

    fun pruneInaccessibleEntries(): Boolean {
        val currentList = getRecentTxts().toMutableList()
        val sizeBefore = currentList.size
        currentList.removeAll { txt -> !isAccessible(txt) }
        return if (currentList.size < sizeBefore) {
            saveList(currentList)
            true
        } else {
            false
        }
    }

    private fun isAccessible(txt: RecentTxtFile): Boolean {
        txt.localCopyPath?.let { path ->
            val file = File(path)
            if (file.exists()) return true
        }
        val uri = Uri.parse(txt.uri)
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return false)
            return file.exists()
        }
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveList(list: List<RecentTxtFile>) {
        prefs.edit().putString(KEY_RECENT_TXTS, serializeListToJson(list)).apply()
    }
}
