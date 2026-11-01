package com.smartword.app.data

/** Helpers for the names shown to the user. */
object FileNames {
    const val DOCX_EXTENSION = ".docx"
    const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** The name without the .docx extension (in any letter case), for titles and lists. */
    fun title(fileName: String): String {
        val trimmed = fileName.trim()
        return if (trimmed.endsWith(DOCX_EXTENSION, ignoreCase = true)) {
            trimmed.dropLast(DOCX_EXTENSION.length).ifBlank { trimmed }
        } else {
            trimmed
        }
    }

    /** A safe file name for saving: forbidden characters are replaced and the .docx extension is added once. */
    fun forSaving(title: String): String {
        val cleaned = title.map { if (it in FORBIDDEN || it.isISOControl()) '_' else it }
            .joinToString("").trim().trim('.').ifBlank { DEFAULT_TITLE }
        val base = if (cleaned.endsWith(DOCX_EXTENSION, ignoreCase = true)) cleaned.dropLast(DOCX_EXTENSION.length) else cleaned
        return base.ifBlank { DEFAULT_TITLE } + DOCX_EXTENSION
    }

    private const val DEFAULT_TITLE = "مستند جديد"
    private val FORBIDDEN = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
}
