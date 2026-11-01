package com.smartword.app.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.smartword.docx.model.DocxDocument
import java.io.FileNotFoundException

/** Reads documents from the files the user picked, through the Storage Access Framework. */
class DocumentRepository(private val resolver: ContentResolver, private val fallbackName: String) {

    class Loaded(val name: String, val document: DocxDocument)

    /**
     * Reads the document at [uri]. With [persist] the app keeps access to the file after a restart,
     * so that it can be opened again from the recent files list.
     * Throws the engine's exceptions for unusable files and [FileNotFoundException] if it is gone.
     */
    fun read(uri: Uri, persist: Boolean): Loaded {
        if (persist) keepAccess(uri)
        val name = displayName(uri) ?: uri.lastPathSegment ?: fallbackName
        val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        val document = stream.use { DocxDocument.open(it) }
        return Loaded(name, document)
    }

    private fun keepAccess(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider does not offer persistable access; the file still opens now.
        }
    }

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    } catch (_: RuntimeException) {
        null
    }
}
