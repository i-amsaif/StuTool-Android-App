package com.stutool.stutool.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Isolated recent-file record for Word (.docx) documents.
 * Stored separately from [RecentPdf] and [RecentTxtFile] so existing PDF and TXT
 * history storage remain untouched.
 */
data class RecentDocxFile(
    val uri: String,
    val name: String,
    val size: Long,
    val lastOpened: Long,
    val localCopyPath: String? = null,
    val fileIdentity: String = computeIdentity(name, size)
) {
    fun toEncodedRecord(): String {
        val encUri = URLEncoder.encode(uri, "UTF-8")
        val encName = URLEncoder.encode(name, "UTF-8")
        val encCopy = localCopyPath?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
        val encId = URLEncoder.encode(fileIdentity, "UTF-8")
        return "$encUri\t$encName\t$size\t$lastOpened\t$encCopy\t$encId"
    }

    companion object {
        fun computeIdentity(name: String, size: Long): String {
            val normalizedName = name.lowercase().trim().removeSuffix(".docx")
            return "$normalizedName|$size"
        }

        fun fromEncodedRecord(record: String): RecentDocxFile {
            val parts = record.split('\t')
            if (parts.size < 4) {
                throw IllegalArgumentException("Malformed RecentDocxFile record")
            }
            val uri = URLDecoder.decode(parts[0], "UTF-8")
            val name = URLDecoder.decode(parts[1], "UTF-8")
            if (name.isBlank() || uri.isBlank()) {
                throw IllegalArgumentException("Missing or blank essential fields in RecentDocxFile")
            }
            val size = parts[2].toLongOrNull() ?: 0L
            val lastOpened = parts[3].toLongOrNull() ?: 0L
            val localCopyPath = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.let {
                URLDecoder.decode(it, "UTF-8")
            }
            val fileIdentity = parts.getOrNull(5)?.takeIf { it.isNotEmpty() }?.let {
                URLDecoder.decode(it, "UTF-8")
            } ?: computeIdentity(name, size)

            return RecentDocxFile(
                uri = uri,
                name = name,
                size = size,
                lastOpened = lastOpened,
                localCopyPath = localCopyPath,
                fileIdentity = fileIdentity
            )
        }
    }
}
