package com.stutool.stutool.ui.screens.resume.utils

import java.util.Locale

object FormatUtils {
    /**
     * Formats names into Title Case (e.g., "john doe" -> "John Doe").
     */
    fun formatName(input: String): String {
        return input.split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) "" else {
                word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
        }
    }

    /**
     * Capitalizes the first letter of sentences while strictly preserving all whitespaces.
     */
    fun formatSentence(input: String): String {
        if (input.isEmpty()) return input

        val result = StringBuilder()
        var capitalizeNext = true
        
        for (char in input) {
            when {
                char.isWhitespace() -> {
                    result.append(char)
                }
                char == '.' || char == '?' || char == '!' || char == '\n' -> {
                    result.append(char)
                    capitalizeNext = true
                }
                capitalizeNext && char.isLetter() -> {
                    result.append(char.titlecase(Locale.ROOT))
                    capitalizeNext = false
                }
                else -> {
                    result.append(char)
                    if (char.isLetterOrDigit()) capitalizeNext = false
                }
            }
        }
        return result.toString()
    }

    /**
     * Filters phone strings safely while retaining spaces and + for country codes.
     */
    fun formatPhoneNumber(input: String): String {
        return input.filter { it.isDigit() || it == '+' || it == ' ' }
    }
}
