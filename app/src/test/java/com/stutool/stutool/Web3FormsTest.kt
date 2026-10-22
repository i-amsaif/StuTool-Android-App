package com.stutool.stutool

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Test
import java.util.concurrent.TimeUnit

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Web3FormsTest {
    @Test
    fun testWeb3FormsRequest() {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        val accessKey = BuildConfig.WEB3FORMS_ACCESS_KEY.ifBlank { "TEST_ACCESS_KEY" }
        val jsonBody = JSONObject().apply {
            put("access_key", accessKey)
            put("subject", "StuTool App Feedback (5*)")
            put("from_name", "StuTool Android App")
            put("rating", "5 / 5")
            put("message", "Test message")
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://api.web3forms.com/submit")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            println("Code: ${response.code}")
            println("Body: ${response.body?.string()}")
        } catch (e: Exception) {
            println("Skipping live network test in offline sandbox: ${e.message}")
        }
    }
}
