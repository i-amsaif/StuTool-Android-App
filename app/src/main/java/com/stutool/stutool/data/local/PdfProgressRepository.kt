package com.stutool.stutool.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class PdfProgressRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pdf_progress_prefs", Context.MODE_PRIVATE)

    // Progress
    fun saveProgress(pdfId: String, pageIndex: Int) {
        prefs.edit().putInt("progress_$pdfId", pageIndex).apply()
    }

    fun getProgress(pdfId: String): Int {
        return prefs.getInt("progress_$pdfId", 0)
    }

    // Bookmarks
    fun getBookmarks(pdfId: String): List<Int> {
        val jsonString = prefs.getString("bookmarks_$pdfId", null) ?: return emptyList()
        val list = mutableListOf<Int>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getInt(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val sortedList = list.sorted()
        android.util.Log.d("PDF_DEBUG", "getBookmarks for pdfId=$pdfId returned $sortedList")
        return sortedList
    }

    fun addBookmark(pdfId: String, pageIndex: Int) {
        android.util.Log.d("PDF_DEBUG", "addBookmark entered for pdfId=$pdfId, page=$pageIndex")
        val current = getBookmarks(pdfId).toMutableSet()
        if (current.add(pageIndex)) {
            val sortedList = current.sorted()
            saveBookmarks(pdfId, sortedList)
        }
    }

    fun removeBookmark(pdfId: String, pageIndex: Int) {
        val current = getBookmarks(pdfId).toMutableSet()
        if (current.remove(pageIndex)) {
            val sortedList = current.sorted()
            saveBookmarks(pdfId, sortedList)
        }
    }

    private fun saveBookmarks(pdfId: String, bookmarks: List<Int>) {
        android.util.Log.d("PDF_DEBUG", "saveBookmarks called for pdfId=$pdfId, bookmarks=$bookmarks")
        val jsonArray = JSONArray()
        bookmarks.forEach { jsonArray.put(it) }
        prefs.edit().putString("bookmarks_$pdfId", jsonArray.toString()).apply()
        android.util.Log.d("PDF_DEBUG", "saveBookmarks completed")
    }
}
