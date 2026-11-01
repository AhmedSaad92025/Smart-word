package com.smartword.app

import com.smartword.app.data.FailureKind
import com.smartword.app.data.FileNames
import com.smartword.app.data.RecentFile
import com.smartword.app.data.RecentFiles
import com.smartword.app.preview.PreviewBlock
import com.smartword.app.preview.PreviewBuilder
import com.smartword.docx.model.DocxDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A document that is open on screen. [uri] is null for a new document that was not saved yet. */
class OpenDocument(val uri: String?, val name: String, val document: DocxDocument) {
    val blocks: List<PreviewBlock> = PreviewBuilder.build(document)
}

/** A failure to show to the user. [name] is the file it concerns, when known. */
data class UiError(val kind: FailureKind, val name: String?)

data class UiState(
    val recents: List<RecentFile> = emptyList(),
    /** The open document; null shows the home screen. */
    val open: OpenDocument? = null,
    val loading: Boolean = false,
    val error: UiError? = null,
)

/**
 * What the app shows and how it changes. Plain Kotlin without Android, so every transition
 * is tested on the JVM; the view model feeds it the results of file operations.
 */
class AppModel {

    private var recentFiles = RecentFiles()
    private val mutable = MutableStateFlow(UiState())
    val state: StateFlow<UiState> get() = mutable

    /** Replaces the recent files, for example with the stored list at startup. */
    fun recentsLoaded(files: RecentFiles) {
        recentFiles = files
        mutable.value = mutable.value.copy(recents = files.items)
    }

    fun loadingStarted() {
        mutable.value = mutable.value.copy(loading = true, error = null)
    }

    /** A document was read from [uri]; it opens and moves to the top of the recent files. */
    fun opened(uri: String, name: String, document: DocxDocument, nowMillis: Long) {
        val title = FileNames.title(name)
        recentFiles = recentFiles.with(RecentFile(uri, name, nowMillis))
        mutable.value = mutable.value.copy(
            recents = recentFiles.items,
            open = OpenDocument(uri, title, document),
            loading = false,
            error = null,
        )
    }

    /** A new, empty document that has no file yet. */
    fun created(document: DocxDocument, title: String) {
        mutable.value = mutable.value.copy(open = OpenDocument(null, title, document), loading = false, error = null)
    }

    fun failed(kind: FailureKind, name: String?) {
        mutable.value = mutable.value.copy(loading = false, error = UiError(kind, name))
    }

    fun errorDismissed() {
        mutable.value = mutable.value.copy(error = null)
    }

    /** Leaves the open document and returns to the home screen. */
    fun closed() {
        mutable.value = mutable.value.copy(open = null)
    }

    /** Removes a file from the recent list, for example when it no longer exists. */
    fun forgotten(uri: String) {
        recentFiles = recentFiles.without(uri)
        mutable.value = mutable.value.copy(recents = recentFiles.items)
    }

    /** The recent files as they should be stored. */
    fun recentsToStore(): RecentFiles = recentFiles
}
