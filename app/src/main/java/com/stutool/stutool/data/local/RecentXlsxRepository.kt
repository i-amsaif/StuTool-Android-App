package com.stutool.stutool.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.stutool.stutool.domain.model.RecentXlsxFile
import java.io.File

/**
 * Isolated repository for recently opened Excel (.xlsx) workbooks.
 * Uses a dedicated SharedPreferences file ("xlsx_recent_prefs") so that
 * [RecentPdfRepository], [RecentTxtRepository], and [RecentDocxRepository]
 * remain 100% untouched.
 */
class RecentXlsxRepository(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "xlsx_recent_prefs"
        private const val KEY_RECENT_XLSXS = "recent_xlsxs"
        const val MAX_RECENT = 50

        fun parseListFromSerialized(serialized: String?): List<RecentXlsxFile> {
            if (serialized.isNullOrBlank()) return emptyList()
            val list = mutableListOf<RecentXlsxFile>()
            serialized.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        list.add(RecentXlsxFile.fromEncodedRecord(line))
                    } catch (_: Exception) {
                        // Skip malformed entry without failing the whole list
                    }
                }
            }
            return list.sortedByDescending { it.lastOpened }
        }

        fun serializeList(list: List<RecentXlsxFile>): String {
            return list.joinToString("\n") { it.toEncodedRecord() }
        }

        fun deduplicateAndInsert(
            currentList: List<RecentXlsxFile>,
            xlsx: RecentXlsxFile,
            maxEntries: Int = MAX_RECENT
        ): List<RecentXlsxFile> {
            val mutable = currentList.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.fileIdentity == xlsx.fileIdentity }
            if (existingIndex != -1) {
                val existing = mutable.removeAt(existingIndex)
                mutable.add(
                    0,
                    xlsx.copy(
                        localCopyPath = xlsx.localCopyPath ?: existing.localCopyPath,
                        fileIdentity = xlsx.fileIdentity
                    )
                )
            } else {
                mutable.removeAll { it.uri == xlsx.uri }
                mutable.add(0, xlsx)
            }
            return mutable.sortedByDescending { it.lastOpened }.take(maxEntries)
        }
    }

    fun getRecentXlsxs(): List<RecentXlsxFile> {
        val raw = prefs.getString(KEY_RECENT_XLSXS, null)
        return parseListFromSerialized(raw)
    }

    fun saveRecentXlsx(xlsx: RecentXlsxFile) {
        val updated = deduplicateAndInsert(getRecentXlsxs(), xlsx, MAX_RECENT)
        saveList(updated)
    }

    fun deleteRecentXlsx(uri: String) {
        val currentList = getRecentXlsxs().toMutableList()
        currentList.removeAll { it.uri == uri }
        saveList(currentList)
    }

    fun clearAllRecentXlsxs() {
        saveList(emptyList())
    }

    fun pruneInaccessibleEntries(): Boolean {
        val currentList = getRecentXlsxs().toMutableList()
        val sizeBefore = currentList.size
        currentList.removeAll { xlsx -> !isAccessible(xlsx) }
        return if (currentList.size < sizeBefore) {
            saveList(currentList)
            true
        } else {
            false
        }
    }

    private fun isAccessible(xlsx: RecentXlsxFile): Boolean {
        xlsx.localCopyPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() > 0L) return true
        }
        val uri = Uri.parse(xlsx.uri)
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return false)
            return file.exists() && file.length() > 0L
        }
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveList(list: List<RecentXlsxFile>) {
        prefs.edit().putString(KEY_RECENT_XLSXS, serializeList(list)).apply()
    }
}
