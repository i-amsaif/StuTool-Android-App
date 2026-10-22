package com.stutool.stutool.data.remote.feedback

import android.content.Context
import android.os.Build
import com.stutool.stutool.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class FeedbackSubmissionResult {
    data object Success : FeedbackSubmissionResult()
    data class RateLimited(val remainingMinutes: Long) : FeedbackSubmissionResult()
    data class Error(val message: String) : FeedbackSubmissionResult()
}

/**
 * Handles Web3Forms feedback submission, local cooldowns, and error mapping.
 */
class FeedbackRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "feedback_prefs"
        private const val KEY_LAST_SUBMISSION = "last_submission_timestamp"
        private const val WEB3FORMS_URL = "https://api.web3forms.com/submit"
        
        // Cooldown period per app install to prevent quota exhaustion
        const val COOLDOWN_DURATION_MS = 60 * 60 * 1000L // 1 hour
    }

    /**
     * Returns remaining cooldown in minutes, or 0 if user is eligible to submit.
     */
    fun getRemainingCooldownMinutes(): Long {
        val lastSubmission = prefs.getLong(KEY_LAST_SUBMISSION, 0L)
        if (lastSubmission == 0L) return 0L
        val elapsed = System.currentTimeMillis() - lastSubmission
        val remainingMs = COOLDOWN_DURATION_MS - elapsed
        return if (remainingMs > 0) (remainingMs / 60000L) + 1 else 0L
    }

    fun recordSubmission() {
        prefs.edit().putLong(KEY_LAST_SUBMISSION, System.currentTimeMillis()).apply()
    }

    suspend fun submitFeedback(
        rating: Int,
        comment: String
    ): FeedbackSubmissionResult = withContext(Dispatchers.IO) {
        val cooldownMinutes = getRemainingCooldownMinutes()
        if (cooldownMinutes > 0) {
            return@withContext FeedbackSubmissionResult.RateLimited(cooldownMinutes)
        }

        val accessKey = BuildConfig.WEB3FORMS_ACCESS_KEY
        if (accessKey.isBlank() || accessKey == "PLACEHOLDER") {
            return@withContext FeedbackSubmissionResult.Error(
                "Feedback service is currently unconfigured. Please try again later."
            )
        }

        val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
        val device = Build.MODEL ?: "Unknown"
        val manufacturer = Build.MANUFACTURER ?: "Unknown"

        val jsonBody = JSONObject().apply {
            put("access_key", accessKey)
            put("subject", "StuTool App Feedback (${rating}★)")
            put("from_name", "StuTool Android App")
            put("rating", "$rating / 5 (${"★".repeat(rating)}${"☆".repeat(5 - rating)})")
            put("message", comment.ifBlank { "No additional comments provided." })
            put("app_version", appVersion)
            put("android_version", androidVersion)
            put("device", device)
            put("manufacturer", manufacturer)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(WEB3FORMS_URL)
            .addHeader("Accept", "application/json")
            .addHeader("Origin", "https://stutool.com")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                try {
                    val json = JSONObject(responseBody)
                    val success = json.optBoolean("success", false)
                    if (success) {
                        recordSubmission()
                        return@withContext FeedbackSubmissionResult.Success
                    } else {
                        val serverMsg = json.optString("message", "Submission rejected by service.")
                        return@withContext FeedbackSubmissionResult.Error(serverMsg)
                    }
                } catch (e: Exception) {
                    if (!response.isSuccessful) {
                        return@withContext FeedbackSubmissionResult.Error(
                            "Service returned an error (${response.code})."
                        )
                    }
                    return@withContext FeedbackSubmissionResult.Error("Invalid response format.")
                }
            } else {
                if (!response.isSuccessful) {
                    return@withContext FeedbackSubmissionResult.Error(
                        "Service returned an error (${response.code})."
                    )
                }
                return@withContext FeedbackSubmissionResult.Error("Empty response received. Please try again later.")
            }
        } catch (e: UnknownHostException) {
            FeedbackSubmissionResult.Error("No internet connection. Please check your network and try again.")
        } catch (e: SocketTimeoutException) {
            FeedbackSubmissionResult.Error("Request timed out. Please check your connection and try again.")
        } catch (e: IOException) {
            FeedbackSubmissionResult.Error("Network error. Please try again later.")
        } catch (e: Exception) {
            FeedbackSubmissionResult.Error("Unable to submit feedback. Please try again later.")
        }
    }
}
