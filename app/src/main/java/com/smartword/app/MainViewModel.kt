package com.smartword.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartword.app.data.DocumentRepository
import com.smartword.app.data.FailureKind
import com.smartword.app.data.LoadFailure
import com.smartword.app.data.RecentFiles
import com.smartword.docx.model.DocxDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connects the screens to [AppModel] and runs file work off the main thread. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val model = AppModel()
    private val repository = DocumentRepository(
        application.contentResolver,
        application.getString(R.string.unknown_file_name),
    )
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val state: StateFlow<UiState> get() = model.state

    init {
        model.recentsLoaded(RecentFiles.parse(prefs.getString(KEY_RECENTS, null)))
    }

    /** Opens the document at [uri]. [persist] keeps access to it for the recent files list. */
    fun open(uri: Uri, persist: Boolean) {
        model.loadingStarted()
        Log.i(TAG, "opening a document (scheme=${uri.scheme}, persist=$persist)")
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { runCatching { repository.read(uri, persist) } }
            val loaded = result.getOrNull()
            if (loaded != null) {
                model.opened(uri.toString(), loaded.name, loaded.document, System.currentTimeMillis())
                val shown = model.state.value.open
                Log.i(
                    TAG,
                    "opened '${loaded.name}' in ${System.currentTimeMillis() - started} ms: " +
                        "${shown?.blocks?.size ?: 0} blocks, ${loaded.document.plainText.length} characters of text",
                )
                saveRecents()
            } else {
                val failure = result.exceptionOrNull()
                Log.e(TAG, "could not open the document", failure)
                val kind = LoadFailure.classify(failure ?: IllegalStateException())
                if (kind == FailureKind.NOT_FOUND || kind == FailureKind.NO_ACCESS) {
                    model.forgotten(uri.toString())
                    saveRecents()
                }
                model.failed(kind, null)
            }
        }
    }

    fun createNew() {
        val application = getApplication<Application>()
        model.created(DocxDocument.create(rtl = true), application.getString(R.string.untitled))
    }

    fun close() = model.closed()

    fun dismissError() = model.errorDismissed()

    private fun saveRecents() {
        prefs.edit().putString(KEY_RECENTS, model.recentsToStore().serialize()).apply()
    }

    private companion object {
        const val TAG = "SmartWord"
        const val PREFS = "smart_word"
        const val KEY_RECENTS = "recent_files"
    }
}
