package com.stutool.stutool.tutorial

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.stutool.stutool.BuildConfig

/**
 * Version-aware persistence manager for in-app video tutorials.
 *
 * Rules:
 * 1. If no version has been recorded for the feature, show its tutorial automatically.
 * 2. If the recorded version differs from current app version code, show its tutorial automatically.
 * 3. If already completed or intentionally dismissed for current version, do not show automatically.
 * 4. Manual replay is always allowed independently of stored version.
 * 5. State is preserved across app restarts and process recreation.
 * 6. Each feature is tracked independently.
 */
class TutorialPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Resolves the application's actual current version code.
     */
    fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE.toLong()
        }
    }

    /**
     * Retrieves the last version code during which the tutorial was completed or dismissed.
     * Returns -1L if not yet recorded.
     */
    fun getSeenVersion(type: TutorialType): Long {
        return prefs.getLong(type.prefKey, -1L)
    }

    /**
     * Checks whether the user has explicitly and permanently declined automatic tutorial popups
     * for the given feature by selecting "No Thanks".
     */
    fun isTutorialDeclined(type: TutorialType): Boolean {
        return prefs.getBoolean(type.declinePrefKey, false)
    }

    /**
     * Records or updates whether the user has permanently declined automatic tutorial popups
     * for the given feature.
     */
    fun setTutorialDeclined(type: TutorialType, declined: Boolean = true) {
        prefs.edit().putBoolean(type.declinePrefKey, declined).apply()
    }

    /**
     * Checks whether the tutorial opt-in prompt should be automatically presented.
     *
     * Rules:
     * 1. If the user permanently declined ("No Thanks"), NEVER show automatic prompt (overrides app updates).
     * 2. If no version has been recorded or recorded version differs from targetVersionCode, show prompt.
     * 3. If already completed/handled for targetVersionCode, do not show.
     */
    fun shouldShowTutorialPrompt(
        type: TutorialType,
        targetVersionCode: Long = getCurrentVersionCode()
    ): Boolean {
        if (isTutorialDeclined(type)) {
            return false
        }
        val seenVersion = getSeenVersion(type)
        return seenVersion != targetVersionCode
    }

    /**
     * Backwards-compatible alias for shouldShowTutorialPrompt.
     */
    fun shouldShowTutorial(
        type: TutorialType,
        targetVersionCode: Long = getCurrentVersionCode()
    ): Boolean = shouldShowTutorialPrompt(type, targetVersionCode)

    /**
     * Records that the user has completed, skipped, or closed the tutorial for the given version.
     */
    fun markTutorialSeen(
        type: TutorialType,
        versionCode: Long = getCurrentVersionCode()
    ) {
        prefs.edit().putLong(type.prefKey, versionCode).apply()
    }

    /**
     * Resets tutorial status for a specific feature (for manual reset or testing).
     */
    fun resetTutorial(type: TutorialType) {
        prefs.edit()
            .remove(type.prefKey)
            .remove(type.declinePrefKey)
            .apply()
    }

    /**
     * Resets tutorial status for all features.
     */
    fun resetAllTutorials() {
        val editor = prefs.edit()
        for (type in TutorialType.entries) {
            editor.remove(type.prefKey)
            editor.remove(type.declinePrefKey)
        }
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "stutool_tutorial_prefs"
    }
}
