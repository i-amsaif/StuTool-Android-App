package com.stutool.stutool.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.stutool.stutool.domain.model.RecentDocxFile
import java.io.File

/**
 * Isolated repository for recently opened Word (.docx) files.
 * Uses a dedicated SharedPreferences file ("docx_recent_prefs") so that
 * [RecentPdfRepository] ("pdf_prefs") and [RecentTxtRepository] ("txt_recent_prefs")
 * remain 100% untouched.
 */
class RecentDocxRepository(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "docx_recent_prefs"
        private const val KEY_RECENT_DOCXS = "recent_docxs"
        const val MAX_RECENT = 50

        fun parseListFromSerialized(serialized: String?): List<RecentDocxFile> {
            if (serialized.isNullOrBlank()) return emptyList()
            val list = mutableListOf<RecentDocxFile>()
            serialized.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        list.add(RecentDocxFile.fromEncodedRecord(line))
                    } catch (_: Exception) {
                        // Skip malformed entry without failing the whole list
                    }
                }
            }
            return list.sortedByDescending { it.lastOpened }
        }

        fun serializeList(list: List<RecentDocxFile>): String {
            return list.joinToString("\n") { it.toEncodedRecord() }
        }

        fun deduplicateAndInsert(
            currentList: List<RecentDocxFile>,
            docx: RecentDocxFile,
            maxEntries: Int = MAX_RECENT
        ): List<RecentDocxFile> {
            val mutable = currentList.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.fileIdentity == docx.fileIdentity }
            if (existingIndex != -1) {
                val existing = mutable.removeAt(existingIndex)
                mutable.add(
                    0,
                    docx.copy(
                        localCopyPath = docx.localCopyPath ?: existing.localCopyPath,
                        fileIdentity = docx.fileIdentity
                    )
                )
            } else {
                mutable.removeAll { it.uri == docx.uri }
                mutable.add(0, docx)
            }
            return mutable.sortedByDescending { it.lastOpened }.take(maxEntries)
        }
    }

    fun getRecentDocxs(): List<RecentDocxFile> {
        val raw = prefs.getString(KEY_RECENT_DOCXS, null)
        return parseListFromSerialized(raw)
    }

    fun saveRecentDocx(docx: RecentDocxFile) {
        val updated = deduplicateAndInsert(getRecentDocxs(), docx, MAX_RECENT)
        saveList(updated)
    }

    fun deleteRecentDocx(uri: String) {
        val currentList = getRecentDocxs().toMutableList()
        currentList.removeAll { it.uri == uri }
        saveList(currentList)
    }

    fun clearAllRecentDocxs() {
        saveList(emptyList())
    }

    fun pruneInaccessibleEntries(): Boolean {
        val currentList = getRecentDocxs().toMutableList()
        val sizeBefore = currentList.size
        currentList.removeAll { docx -> !isAccessible(docx) }
        return if (currentList.size < sizeBefore) {
            saveList(currentList)
            true
        } else {
            false
        }
    }

    private fun isAccessible(docx: RecentDocxFile): Boolean {
        docx.localCopyPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() > 0L) return true
        }
        val uri = Uri.parse(docx.uri)
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

    private fun saveList(list: List<RecentDocxFile>) {
        prefs.edit().putString(KEY_RECENT_DOCXS, serializeList(list)).apply()
    }
}
