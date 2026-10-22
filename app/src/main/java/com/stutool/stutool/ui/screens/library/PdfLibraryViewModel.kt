package com.stutool.stutool.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.data.local.RecentDocxRepository
import com.stutool.stutool.data.local.RecentPdfRepository
import com.stutool.stutool.data.local.RecentTxtRepository
import com.stutool.stutool.data.local.RecentXlsxRepository
import com.stutool.stutool.domain.model.RecentDocxFile
import com.stutool.stutool.domain.model.RecentPdf
import com.stutool.stutool.domain.model.RecentTxtFile
import com.stutool.stutool.domain.model.RecentXlsxFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecentPdfRepository(application)
    private val txtRepository = RecentTxtRepository(application)
    private val docxRepository = RecentDocxRepository(application)
    private val xlsxRepository = RecentXlsxRepository(application)
    
    private val _recentPdfs = MutableStateFlow<List<RecentPdf>>(emptyList())
    val recentPdfs: StateFlow<List<RecentPdf>> = _recentPdfs.asStateFlow()

    private val _recentTxts = MutableStateFlow<List<RecentTxtFile>>(emptyList())
    val recentTxts: StateFlow<List<RecentTxtFile>> = _recentTxts.asStateFlow()

    private val _recentDocxs = MutableStateFlow<List<RecentDocxFile>>(emptyList())
    val recentDocxs: StateFlow<List<RecentDocxFile>> = _recentDocxs.asStateFlow()

    private val _recentXlsxs = MutableStateFlow<List<RecentXlsxFile>>(emptyList())
    val recentXlsxs: StateFlow<List<RecentXlsxFile>> = _recentXlsxs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadRecentPdfs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            // Run migration first on IO thread
            repository.checkMigration()

            // Load and emit immediately for faster UX
            _recentPdfs.value = repository.getRecentPdfs()
            _recentTxts.value = txtRepository.getRecentTxts()
            _recentDocxs.value = docxRepository.getRecentDocxs()
            _recentXlsxs.value = xlsxRepository.getRecentXlsxs()
            _isLoading.value = false

            // Prune entries whose files are gone (dead URIs + missing local copies).
            // This runs in the background and only updates if something was removed.
            if (repository.pruneInaccessibleEntries()) {
                _recentPdfs.value = repository.getRecentPdfs()
            }
            if (txtRepository.pruneInaccessibleEntries()) {
                _recentTxts.value = txtRepository.getRecentTxts()
            }
            if (docxRepository.pruneInaccessibleEntries()) {
                _recentDocxs.value = docxRepository.getRecentDocxs()
            }
            if (xlsxRepository.pruneInaccessibleEntries()) {
                _recentXlsxs.value = xlsxRepository.getRecentXlsxs()
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

    fun addRecentTxt(uri: String, name: String, size: Long, localCopyPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val txt = RecentTxtFile(
                uri = uri,
                name = name,
                size = size,
                lastOpened = System.currentTimeMillis(),
                localCopyPath = localCopyPath
            )
            txtRepository.saveRecentTxt(txt)
            loadRecentPdfs()
        }
    }

    fun addRecentDocx(uri: String, name: String, size: Long, localCopyPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val docx = RecentDocxFile(
                uri = uri,
                name = name,
                size = size,
                lastOpened = System.currentTimeMillis(),
                localCopyPath = localCopyPath
            )
            docxRepository.saveRecentDocx(docx)
            loadRecentPdfs()
        }
    }

    fun addRecentXlsx(uri: String, name: String, size: Long, localCopyPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val xlsx = RecentXlsxFile(
                uri = uri,
                name = name,
                size = size,
                lastOpened = System.currentTimeMillis(),
                localCopyPath = localCopyPath
            )
            xlsxRepository.saveRecentXlsx(xlsx)
            loadRecentPdfs()
        }
    }

    fun deleteRecentPdf(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRecentPdf(uri)
            loadRecentPdfs()
        }
    }

    fun deleteRecentTxt(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            txtRepository.deleteRecentTxt(uri)
            loadRecentPdfs()
        }
    }

    fun deleteRecentDocx(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            docxRepository.deleteRecentDocx(uri)
            loadRecentPdfs()
        }
    }

    fun deleteRecentXlsx(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            xlsxRepository.deleteRecentXlsx(uri)
            loadRecentPdfs()
        }
    }

    fun clearAllRecentPdfs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllRecentPdfs()
            txtRepository.clearAllRecentTxts()
            docxRepository.clearAllRecentDocxs()
            xlsxRepository.clearAllRecentXlsxs()
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
