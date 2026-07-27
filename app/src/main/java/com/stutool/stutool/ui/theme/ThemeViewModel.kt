package com.stutool.stutool.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stutool.stutool.data.local.ThemePreferences
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val themePreferences = ThemePreferences.getInstance(application)

    val themeFlow: StateFlow<Boolean?> = themePreferences.themeFlow
    val saveLocationFlow: StateFlow<String?> = themePreferences.saveLocationFlow

    fun setDarkMode(isDark: Boolean?) {
        themePreferences.setThemePreference(isDark)
    }

    fun setSaveLocation(uriString: String?) {
        themePreferences.setSaveLocation(uriString)
    }
}

class ThemeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThemeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ThemeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
