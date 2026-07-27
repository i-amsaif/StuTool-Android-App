package com.stutool.stutool.data.remote.ai

import com.stutool.stutool.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GroqProvider(private val client: OkHttpClient) : AiProvider {
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
                put("model", "mixtral-8x7b-32768")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            
            val authKey = BuildConfig.GROQ_API_KEY
            if (authKey == "PLACEHOLDER" || authKey.isBlank()) {
                return Result.failure(Exception("API Key is missing"))
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $authKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val root = JSONObject(responseBody)
                val choices = root.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    val content = message?.optString("content")
                    if (!content.isNullOrBlank()) {
                        Result.success(content.trim())
                    } else {
                        Result.failure(Exception("Empty content from server"))
                    }
                } else {
                    Result.failure(Exception("Empty choices from server"))
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
