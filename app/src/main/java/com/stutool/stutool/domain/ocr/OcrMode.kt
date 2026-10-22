package com.stutool.stutool.domain.ocr

enum class OcrMode(
    val code: String,
    val displayName: String,
    val description: String,
    val allowsCloud: Boolean
) {
    SMART_AUTO(
        code = "smart_auto",
        displayName = "Smart Auto",
        description = "Uses fast Cloud OCR when eligible & online. Automatically falls back to offline OCR.",
        allowsCloud = true
    ),
    CLOUD_ONLY(
        code = "cloud",
        displayName = "Cloud OCR",
        description = "High accuracy cloud processing via OCR.space. Requires internet and free-tier limits.",
        allowsCloud = true
    ),
    OFFLINE_ONLY(
        code = "offline",
        displayName = "Offline OCR",
        description = "100% private on-device processing via Tesseract 5 & ML Kit. No data leaves device.",
        allowsCloud = false
    );

    companion object {
        val DEFAULT = OFFLINE_ONLY

        fun fromCode(code: String?): OcrMode {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEFAULT
        }
    }
}
