package com.stutool.stutool.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.stutool.stutool.domain.model.RecentPdf
import org.json.JSONArray

class RecentPdfRepository(private val appContext: Context) {
    private val prefs: SharedPreferences = appContext.getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_RECENT_PDFS = "recent_pdfs"
        private const val KEY_MIGRATION_DONE = "dedup_migration_v1"
        private const val MAX_RECENT = 50
    }

    fun checkMigration() {
        if (!prefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            migrateAndDeduplicate()
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Public API
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getRecentPdfs(): List<RecentPdf> {
        val jsonString = prefs.getString(KEY_RECENT_PDFS, null) ?: return emptyList()
        val list = mutableListOf<RecentPdf>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    list.add(RecentPdf.fromJson(jsonObject))
                } catch (itemEx: Exception) {
                    android.util.Log.w("RecentPdfRepo", "Skipping corrupted recent PDF entry at index $i", itemEx)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val sortedList = list.sortedWith(
            compareByDescending<RecentPdf> { it.isFavorite }
                .thenByDescending { it.lastOpened }
        )
        android.util.Log.d("PDF_DEBUG", "getRecentPdfs returned ${sortedList.size} items")
        return sortedList
    }

    /**
     * Saves a recent PDF, deduplicating by [RecentPdf.fileIdentity].
     *
     * If an entry with the same identity already exists:
     *   – its timestamp is bumped to the new value
     *   – the URI is updated to the latest one (which has the freshest permission)
     *   – isFavorite and localCopyPath are preserved from the existing entry
     *   – no duplicate is created
     */
    fun saveRecentPdf(pdf: RecentPdf) {
        android.util.Log.d("PDF_DEBUG", "saveRecentPdf called for URI: ${pdf.uri}")
        android.util.Log.d("PDF_DEBUG", "saveRecentPdf fileIdentity: ${pdf.fileIdentity}")
        android.util.Log.d("PDF_DEBUG", "saveRecentPdf localCopyPath: ${pdf.localCopyPath}")
        
        val currentList = getRecentPdfs().toMutableList()

        // Find existing entry by stable identity (name+size fingerprint)
        val existingIndex = currentList.indexOfFirst { it.fileIdentity == pdf.fileIdentity }

        if (existingIndex != -1) {
            val existing = currentList.removeAt(existingIndex)
            // Merge: keep favourite/localCopy from old, take URI & timestamp from new
            currentList.add(0, pdf.copy(
                isFavorite = existing.isFavorite,
                localCopyPath = pdf.localCopyPath ?: existing.localCopyPath,
                fileIdentity = pdf.fileIdentity
            ))
        } else {
            // Also check by exact URI as a secondary dedup (covers edge cases
            // where name/size couldn't be resolved on a previous save).
            currentList.removeAll { it.uri == pdf.uri }
            currentList.add(0, pdf)
        }

        saveList(currentList.take(MAX_RECENT))
        android.util.Log.d("PDF_DEBUG", "saveRecentPdf succeeded")
    }

    fun deleteRecentPdf(uri: String) {
        val currentList = getRecentPdfs().toMutableList()
        currentList.removeAll { it.uri == uri }
        saveList(currentList)
    }

    fun toggleFavorite(uri: String) {
        val currentList = getRecentPdfs().toMutableList()
        val index = currentList.indexOfFirst { it.uri == uri }
        if (index != -1) {
            val item = currentList[index]
            currentList[index] = item.copy(isFavorite = !item.isFavorite)
            saveList(currentList)
        }
    }

    fun renamePdf(uri: String, newName: String) {
        val currentList = getRecentPdfs().toMutableList()
        val index = currentList.indexOfFirst { it.uri == uri }
        if (index != -1) {
            val item = currentList[index]
            currentList[index] = item.copy(name = newName)
            saveList(currentList)
        }
    }

    fun updateLocalCopyPath(uri: String, localCopyPath: String) {
        val currentList = getRecentPdfs().toMutableList()
        val index = currentList.indexOfFirst { it.uri == uri }
        if (index != -1) {
            val item = currentList[index]
            currentList[index] = item.copy(localCopyPath = localCopyPath)
            saveList(currentList)
        }
    }

    fun updatePageCount(uri: String, count: Int) {
        val currentList = getRecentPdfs().toMutableList()
        val index = currentList.indexOfFirst { it.uri == uri }
        if (index != -1) {
            val item = currentList[index]
            currentList[index] = item.copy(pageCount = count)
            saveList(currentList)
        }
    }

    fun clearAllRecentPdfs() {
        saveList(emptyList())
    }

    /**
     * Removes entries whose file is no longer accessible — neither via the
     * stored URI nor via a local internal copy. Called by the ViewModel on load.
     * Returns `true` if any entries were removed.
     */
    fun pruneInaccessibleEntries(): Boolean {
        android.util.Log.d("PDF_DEBUG", "pruneInaccessibleEntries called")
        val currentList = getRecentPdfs().toMutableList()
        val sizeBefore = currentList.size

        currentList.removeAll { pdf ->
            val accessible = isAccessible(pdf)
            if (!accessible) {
                android.util.Log.d("PDF_DEBUG", "pruning entry: uri=${pdf.uri}, fileIdentity=${pdf.fileIdentity}, localCopyPath=${pdf.localCopyPath}")
            }
            !accessible
        }

        return if (currentList.size < sizeBefore) {
            saveList(currentList)
            android.util.Log.d("PDF_DEBUG", "pruneInaccessibleEntries deleted ${sizeBefore - currentList.size} entries")
            true
        } else {
            android.util.Log.d("PDF_DEBUG", "pruneInaccessibleEntries deleted 0 entries")
            false
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Internal helpers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Checks whether a recent entry can still be opened from at least one path.
     */
    private fun isAccessible(pdf: RecentPdf): Boolean {
        // 1. Check local copy first (fastest, no IPC)
        pdf.localCopyPath?.let { path ->
            val file = java.io.File(path)
            if (file.exists() && file.length() > 0L) return true
        }
        // 2. Check the stored URI
        val uri = Uri.parse(pdf.uri)
        if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: return false)
            return file.exists()
        }
        // content:// — probe with openFileDescriptor
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * One-time migration: collapses any duplicate entries that accumulated
     * under the old URI-only deduplication model. Keeps the most recently
     * opened entry per fileIdentity and preserves favourites/local copies.
     */
    private fun migrateAndDeduplicate() {
        val currentList = getRecentPdfs().toMutableList()
        if (currentList.isEmpty()) return

        // Force-recompute identity for every entry so stale stored keys
        // (e.g. ones that included ".pdf") are normalized uniformly.
        val normalized = currentList.map { pdf ->
            val freshIdentity = "${pdf.name.lowercase().trim().removeSuffix(".pdf")}|${pdf.size}"
            pdf.copy(fileIdentity = freshIdentity)
        }

        val deduplicated = mutableListOf<RecentPdf>()
        val seen = mutableSetOf<String>()

        // List is already sorted by lastOpened desc within groups — take first
        for (pdf in normalized) {
            if (pdf.fileIdentity in seen) {
                // Merge: find the already-kept entry and preserve metadata
                val keptIndex = deduplicated.indexOfFirst { it.fileIdentity == pdf.fileIdentity }
                if (keptIndex != -1) {
                    val kept = deduplicated[keptIndex]
                    deduplicated[keptIndex] = kept.copy(
                        isFavorite = kept.isFavorite || pdf.isFavorite,
                        localCopyPath = kept.localCopyPath ?: pdf.localCopyPath
                    )
                }
                continue // skip duplicate
            }
            seen.add(pdf.fileIdentity)
            deduplicated.add(pdf)
        }

        if (deduplicated.size < currentList.size) {
            saveList(deduplicated)
        }
    }

    private fun saveList(list: List<RecentPdf>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_RECENT_PDFS, jsonArray.toString()).apply()
    }
}
