package com.stutool.stutool.tutorial

/**
 * Authoritative tutorial video definitions and prompt metadata for StuTool features.
 * URLs are hosted remotely on Cloudinary.
 */
enum class TutorialType(
    val videoUrl: String,
    val prefKey: String,
    val declinePrefKey: String,
    val title: String,
    val dialogTitle: String,
    val dialogDescription: String
) {
    PDF_VIEWER(
        videoUrl = "https://res.cloudinary.com/pw8fglbu/video/upload/v1792313970/stutool_viewer.mp4",
        prefKey = "pdf_viewer_tutorial_seen_version",
        declinePrefKey = "pdf_viewer_tutorial_auto_declined",
        title = "PDF Viewer Tutorial",
        dialogTitle = "Explore PDF Viewer",
        dialogDescription = "Learn how to navigate documents, search text, customize your reading experience, and more."
    ),
    RESUME_BUILDER(
        videoUrl = "https://res.cloudinary.com/pw8fglbu/video/upload/v1792313881/stutool_resumebuilder.mp4",
        prefKey = "resume_builder_tutorial_seen_version",
        declinePrefKey = "resume_builder_tutorial_auto_declined",
        title = "Resume Builder Tutorial",
        dialogTitle = "Create Your Resume",
        dialogDescription = "Take a quick tour of resume creation, AI objective enhancement, templates, preview, and PDF export."
    ),
    DOCUMENT_SCANNER(
        videoUrl = "https://res.cloudinary.com/pw8fglbu/video/upload/v1792313868/stutool_docscanner.mp4",
        prefKey = "document_scanner_tutorial_seen_version",
        declinePrefKey = "document_scanner_tutorial_auto_declined",
        title = "Document Scanner Tutorial",
        dialogTitle = "Get Started with Document Scanner",
        dialogDescription = "Learn how to capture, adjust, and manage scanned documents."
    );

    companion object {
        fun fromPrefKey(key: String): TutorialType? =
            entries.firstOrNull { it.prefKey == key || it.declinePrefKey == key }
    }
}
