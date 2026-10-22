package com.stutool.stutool.domain.ocr

open class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OcrNetworkException(message: String, cause: Throwable? = null) : OcrException(message, cause)

class OcrLimitExceededException(
    message: String,
    val isSizeLimit: Boolean = false,
    val isPageLimit: Boolean = false
) : OcrException(message)

class OcrApiException(
    val exitCode: Int,
    message: String,
    val details: String? = null
) : OcrException(message)

class OcrAuthException(message: String) : OcrException(message)

class OcrRateLimitException(message: String) : OcrException(message)
