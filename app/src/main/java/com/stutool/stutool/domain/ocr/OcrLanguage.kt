package com.stutool.stutool.domain.ocr

enum class OcrLanguage(
    val code: String,
    val displayName: String,
    val tessLanguages: String,
    val isIndic: Boolean,
    val ocrSpaceLanguage: String
) {
    AUTO("auto", "Auto-detect", "eng+kan+hin", true, "auto"),
    KANNADA_ENGLISH("kan_eng", "Kannada + English", "kan+eng", true, "auto"),
    ENGLISH("eng", "English Only", "eng", false, "eng"),
    KANNADA("kan", "Kannada Only", "kan", true, "kan"),
    HINDI("hin", "Hindi Only", "hin", true, "hin");

    companion object {
        val DEFAULT = AUTO

        fun fromCode(code: String?): OcrLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEFAULT
        }
    }
}
