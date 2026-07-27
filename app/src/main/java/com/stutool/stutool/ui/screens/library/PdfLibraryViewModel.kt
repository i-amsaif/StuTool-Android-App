package com.stutool.stutool.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.data.local.RecentPdfRepository
import com.stutool.stutool.domain.model.RecentPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecentPdfRepository(application)
    
    private val _recentPdfs = MutableStateFlow<List<RecentPdf>>(emptyList())
    val recentPdfs: StateFlow<List<RecentPdf>> = _recentPdfs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadRecentPdfs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            // Run migration first on IO thread
            repository.checkMigration()

            // Load and emit immediately for faster UX
            _recentPdfs.value = repository.getRecentPdfs()
            _isLoading.value = false

            // Prune entries whose files are gone (dead URIs + missing local copies).
            // This runs in the background and only updates if something was removed.
            if (repository.pruneInaccessibleEntries()) {
                _recentPdfs.value = repository.getRecentPdfs()
            }
        }
    }

    fun addRecentPdf(uri: String, name: String, size: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val pdf = RecentPdf(
                uri = uri,
                name = name,
                size = size,
                lastOpened = System.currentTimeMillis()
            )
            repository.saveRecentPdf(pdf)
            loadRecentPdfs()

            // Asynchronously extract page count without blocking viewer load
            try {
                val parsedUri = android.net.Uri.parse(uri)
                getApplication<Application>().contentResolver.openFileDescriptor(parsedUri, "r")?.use { fd ->
                    val renderer = android.graphics.pdf.PdfRenderer(fd)
                    val count = renderer.pageCount
                    renderer.close()
                    repository.updatePageCount(uri, count)
                    loadRecentPdfs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteRecentPdf(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRecentPdf(uri)
            loadRecentPdfs()
        }
    }

    fun clearAllRecentPdfs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllRecentPdfs()
            loadRecentPdfs()
        }
    }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(uri)
            loadRecentPdfs()
        }
    }

    fun updateLocalCopyPath(uri: String, localCopyPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLocalCopyPath(uri, localCopyPath)
            loadRecentPdfs()
        }
    }
}
