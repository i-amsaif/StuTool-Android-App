package com.stutool.stutool.data.remote.ai

interface AiProvider {
    suspend fun enhance(text: String): Result<String>
}
