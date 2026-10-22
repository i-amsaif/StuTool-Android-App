package com.stutool.stutool.data.local

import android.content.Context
import android.content.SharedPreferences
import com.stutool.stutool.domain.ocr.OcrMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistence for OCR user preferences.
 * Ensures strict offline-first operation:
 * - New installations default to [OcrMode.OFFLINE_ONLY].
 * - Existing installations without an explicit saved preference default to [OcrMode.OFFLINE_ONLY].
 * - Explicit user selections (e.g. opting into Cloud OCR) are preserved.
 * - Tracks whether the user has seen and acknowledged the Cloud OCR data disclosure dialog.
 */
class OcrPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "ocr_prefs"
        const val KEY_OCR_MODE = "saved_ocr_mode"
        const val KEY_CLOUD_OPT_IN_ACKNOWLEDGED = "cloud_opt_in_acknowledged"
    }

    private val _modeFlow = MutableStateFlow(getOcrMode())
    val modeFlow: StateFlow<OcrMode> = _modeFlow.asStateFlow()

    /**
     * Retrieves the persisted OCR mode.
     * Guaranteed backward-compatible:
     * If no preference has been saved (new install or upgrade from v1.1),
     * strictly returns [OcrMode.DEFAULT] ([OcrMode.OFFLINE_ONLY]).
     * If the user previously made an explicit selection, that selection is preserved.
     */
    fun getOcrMode(): OcrMode {
        val code = prefs.getString(KEY_OCR_MODE, null) ?: return OcrMode.DEFAULT
        return OcrMode.fromCode(code)
    }

    /**
     * Persists an explicit user choice for OCR mode.
     */
    fun setOcrMode(mode: OcrMode) {
        prefs.edit().putString(KEY_OCR_MODE, mode.code).apply()
        _modeFlow.value = mode
    }

    /**
     * Returns true if user has already acknowledged the Cloud OCR notice.
     */
    fun isCloudOptInAcknowledged(): Boolean {
        return prefs.getBoolean(KEY_CLOUD_OPT_IN_ACKNOWLEDGED, false)
    }

    /**
     * Records that the user has seen and accepted the Cloud OCR disclosure.
     */
    fun setCloudOptInAcknowledged(acknowledged: Boolean = true) {
        prefs.edit().putBoolean(KEY_CLOUD_OPT_IN_ACKNOWLEDGED, acknowledged).apply()
    }
}
