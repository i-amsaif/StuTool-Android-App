package com.stutool.stutool.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Centralised SharedPreferences helper for app-wide settings.
 * Stores theme preference and user-chosen save location.
 */
class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // â”€â”€ Theme â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // false = light, true = dark, null = system default
    private val _themeFlow = MutableStateFlow(getThemePreference())
    val themeFlow: StateFlow<Boolean?> = _themeFlow

    fun getThemePreference(): Boolean? {
        if (!prefs.contains(KEY_IS_DARK_MODE)) return null
        return prefs.getBoolean(KEY_IS_DARK_MODE, false)
    }

    fun setThemePreference(isDarkMode: Boolean?) {
        val editor = prefs.edit()
        if (isDarkMode == null) {
            editor.remove(KEY_IS_DARK_MODE)
        } else {
            editor.putBoolean(KEY_IS_DARK_MODE, isDarkMode)
        }
        editor.apply()
        _themeFlow.value = isDarkMode
    }

    // â”€â”€ Onboarding â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun isOnboardingComplete(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    fun isPdfGuideComplete(): Boolean =
        prefs.getBoolean(KEY_PDF_GUIDE_COMPLETE, false)

    fun setPdfGuideComplete() {
        prefs.edit().putBoolean(KEY_PDF_GUIDE_COMPLETE, true).apply()
    }

    // â”€â”€ Save Location â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _saveLocationFlow = MutableStateFlow(getSaveLocation())
    val saveLocationFlow: StateFlow<String?> = _saveLocationFlow

    /**
     * Returns the user-chosen custom save location URI, or null if using default.
     */
    fun getSaveLocation(): String? = prefs.getString(KEY_SAVE_LOCATION, null)

    fun setSaveLocation(uriString: String?) {
        prefs.edit().apply {
            if (uriString == null) remove(KEY_SAVE_LOCATION)
            else putString(KEY_SAVE_LOCATION, uriString)
        }.apply()
        _saveLocationFlow.value = uriString
    }

    // â”€â”€ PDF Viewer Settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getReadingDirection(): Boolean {
        // true = Vertical, false = Horizontal
        return prefs.getBoolean(KEY_PDF_READING_DIRECTION, true)
    }

    fun setReadingDirection(isVertical: Boolean) {
        prefs.edit().putBoolean(KEY_PDF_READING_DIRECTION, isVertical).apply()
    }

    fun getContinuousView(): Boolean {
        return prefs.getBoolean(KEY_CONTINUOUS_VIEW, false)
    }

    fun setContinuousView(isContinuous: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_VIEW, isContinuous).apply()
    }

    companion object {
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
        private const val KEY_SAVE_LOCATION = "save_location"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_PDF_GUIDE_COMPLETE = "pdf_guide_complete"
        private const val KEY_PDF_READING_DIRECTION = "pdf_reading_direction"
        private const val KEY_CONTINUOUS_VIEW = "pdf_continuous_view"

        /** Default output folder path: Documents/StuTool */
        val DEFAULT_SAVE_DIR: File
            get() = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "StuTool"
            )

        /** Human-readable default path */
        const val DEFAULT_DISPLAY_PATH = "Documents/StuTool"

        /**
         * Ensures the default StuTool folder exists in Documents.
         * Call once from Application or Activity onCreate.
         */
        fun ensureDefaultFolder() {
            val dir = DEFAULT_SAVE_DIR
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        @Volatile
        private var INSTANCE: ThemePreferences? = null

        fun getInstance(context: Context): ThemePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
