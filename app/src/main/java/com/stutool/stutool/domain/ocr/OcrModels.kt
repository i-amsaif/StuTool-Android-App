package com.stutool.stutool.domain.ocr

import android.graphics.Rect

enum class OcrEngineSource(val displayName: String) {
    LOCAL_TESSERACT("Tesseract 5 (On-Device)"),
    LOCAL_MLKIT("Google ML Kit (On-Device)"),
    CLOUD_OCR_SPACE("OCR.space Cloud API")
}

data class OcrWord(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float = 1.0f
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect,
    val words: List<OcrWord> = emptyList()
)

data class OcrBlock(
    val text: String,
    val boundingBox: Rect,
    val lines: List<OcrLine> = emptyList()
)

data class OcrPageResult(
    val text: String,
    val lines: List<OcrLine> = emptyList(),
    val blocks: List<OcrBlock> = emptyList(),
    val languageUsed: OcrLanguage = OcrLanguage.DEFAULT,
    val engineSource: OcrEngineSource = OcrEngineSource.LOCAL_TESSERACT,
    val processingTimeMs: Long = 0L
) {
    val isEmpty: Boolean
        get() = text.isBlank()

    val isCloudResult: Boolean
        get() = engineSource == OcrEngineSource.CLOUD_OCR_SPACE
}
