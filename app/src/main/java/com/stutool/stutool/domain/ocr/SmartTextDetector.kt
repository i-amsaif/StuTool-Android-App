package com.stutool.stutool.domain.ocr

object SmartTextDetector {

    /**
     * Determines whether text extracted via PDFTextStripper represents a genuine,
     * high-quality digital text layer rather than a blank page, scanner artifact,
     * or corrupted OCR layer from previous upstream tools.
     */
    fun isAuthenticNativeText(pageText: String?): Boolean {
        if (pageText.isNullOrBlank()) return false
        val clean = pageText.trim()
        if (clean.length < 15) return false

        // Check for common scanner artifact / replacement character corruption
        val replacementCount = clean.count { it == '\uFFFD' || it == '\u0000' }
        if (replacementCount.toFloat() / clean.length.toFloat() > 0.08f) {
            return false // Heavily corrupted text layer
        }

        // Count meaningful characters (letters, digits, Indic scripts)
        var meaningfulCount = 0
        for (c in clean) {
            if (Character.isLetterOrDigit(c) || isIndicUnicode(c)) {
                meaningfulCount++
            }
        }

        val meaningfulRatio = meaningfulCount.toFloat() / clean.length.toFloat()
        if (meaningfulRatio < 0.40f) {
            // Mostly punctuation, dashes, or whitespace artifacts
            return false
        }

        // Tokenize into words
        val words = clean.split("\\s+".toRegex()).filter { it.length >= 2 }
        if (words.size < 3) {
            return false
        }

        // Check for repetitive noise (e.g. "a a a a a a a a" or "1 1 1 1 1")
        val uniqueWords = words.toSet()
        if (words.size >= 8 && uniqueWords.size.toFloat() / words.size.toFloat() < 0.25f) {
            return false
        }

        return true
    }

    private fun isIndicUnicode(c: Char): Boolean {
        val code = c.code
        return (code in 0x0900..0x097F) || // Devanagari
               (code in 0x0C80..0x0CFF) || // Kannada
               (code in 0x0B80..0x0BFF) || // Tamil
               (code in 0x0C00..0x0C7F) || // Telugu
               (code in 0x0D00..0x0D7F) || // Malayalam
               (code in 0x0980..0x09FF)    // Bengali
    }
}
