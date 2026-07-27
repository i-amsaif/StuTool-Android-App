package com.stutool.stutool.domain.model

data class RecentPdf(
    val uri: String,
    val name: String,
    val size: Long,
    val lastOpened: Long,
    val isFavorite: Boolean = false,
    /** Absolute path to an internal-storage copy of this file, used when the
     *  original content:// URI permission has expired (e.g. files from WhatsApp). */
    val localCopyPath: String? = null,
    val pageCount: Int? = null,
    /**
     * Stable identity key for deduplication. Computed as "displayName|fileSize".
     * Two entries with the same fileIdentity are considered the same physical file
     * regardless of differing URIs (e.g. WhatsApp temp URIs vs file picker URIs).
     * Falls back to the raw URI string when name/size are unavailable.
     */
    val fileIdentity: String = "${name.lowercase().trim().removeSuffix(".pdf")}|$size"
) {
    fun toJson(): org.json.JSONObject {
        val json = org.json.JSONObject()
        json.put("uri", uri)
        json.put("name", name)
        json.put("size", size)
        json.put("lastOpened", lastOpened)
        json.put("isFavorite", isFavorite)
        if (localCopyPath != null) json.put("localCopyPath", localCopyPath)
        if (pageCount != null) json.put("pageCount", pageCount)
        json.put("fileIdentity", fileIdentity)
        return json
    }

    companion object {
        fun fromJson(json: org.json.JSONObject): RecentPdf {
            val name = json.getString("name")
            val size = json.getLong("size")
            return RecentPdf(
                uri = json.getString("uri"),
                name = name,
                size = size,
                lastOpened = json.getLong("lastOpened"),
                isFavorite = json.optBoolean("isFavorite", false),
                localCopyPath = if (json.has("localCopyPath")) json.getString("localCopyPath") else null,
                pageCount = if (json.has("pageCount")) json.getInt("pageCount") else null,
                // Re-derive identity on load if missing (backward compat with old entries)
                fileIdentity = json.optString("fileIdentity", "${name.lowercase().trim().removeSuffix(".pdf")}|$size")
            )
        }
    }
}
