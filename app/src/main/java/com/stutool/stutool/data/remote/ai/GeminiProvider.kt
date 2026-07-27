package com.stutool.stutool.data.remote.ai

import com.stutool.stutool.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiProvider(private val client: OkHttpClient) : AiProvider {
    override suspend fun enhance(text: String): Result<String> {
        return try {
            val prompt = "Rewrite the following career objective into a professional resume-ready version.\n" +
                    "Rules:\n" +
                    "* Output ONLY the final improved text\n" +
                    "* No explanations\n" +
                    "* No headings\n" +
                    "* Keep it 2–3 lines\n" +
                    "* Make it concise and impactful\n" +
                    "* Keep meaning aligned with input\n" +
                    "Input:\n\"$text\""

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            
            val authKey = BuildConfig.GEMINI_API_KEY
            if (authKey == "PLACEHOLDER" || authKey.isBlank()) {
                return Result.failure(Exception("API Key is missing"))
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$authKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val root = JSONObject(responseBody)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val resultText = parts.getJSONObject(0).optString("text").trim()
                        Result.success(resultText)
                    } else {
                        Result.failure(Exception("Failed to parse parts"))
                    }
                } else {
                    Result.failure(Exception("Empty candidates from server"))
                }
            } else {
                val code = response.code
                if (code == 429 || code == 503) {
                    Result.failure(Exception("RATE_LIMIT_$code"))
                } else {
                    Result.failure(Exception("API failure: $code"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
