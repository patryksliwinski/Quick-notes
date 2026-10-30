package com.patryksliwinski.quicknotes.data

import android.content.Context
import android.content.Intent
import com.patryksliwinski.quicknotes.receiver.RecentNotesWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

enum class NoteError {
    CreateFailed,
    FolderUnavailable,
    StorageError,
    FolderPermissionLost,
}

class NoteRepository(
    private val context: Context,
    private val storeProvider: () -> Pair<NoteStore, Boolean> = { resolveNoteStore(context) },
) {

    private var store: NoteStore = InternalNoteStore(context.filesDir.resolve("notes"))
    private val _notes = MutableStateFlow<List<NoteMeta>>(emptyList())
    val notes: StateFlow<List<NoteMeta>> = _notes
    private val _folderRevoked = MutableStateFlow(false)
    val folderRevoked: StateFlow<Boolean> = _folderRevoked
    private val _storageName = MutableStateFlow("")
    val storageName: StateFlow<String> = _storageName
    private val _errorMessage = MutableStateFlow<NoteError?>(null)
    val errorMessage: StateFlow<NoteError?> = _errorMessage

    fun clearError() {
        _errorMessage.value = null
    }

    fun reportError(error: NoteError) {
        _errorMessage.value = error
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val (resolved, revoked) = storeProvider()
        store = resolved
        _folderRevoked.value = revoked
        _storageName.value = resolved.displayName()
        _notes.value = try {
            resolved.listNotes()
        } catch (e: Exception) {
            recover(resolved) { it.listNotes() } ?: emptyList()
        }
    }

    suspend fun createNote(): NoteMeta? = createNote("")

    suspend fun createNote(content: String): NoteMeta? = withContext(Dispatchers.IO) {
        val note = try {
            store.createNoteWithContent(content)
        } catch (e: Exception) {
            _errorMessage.value = NoteError.CreateFailed
            recover(store) { it.createNoteWithContent(content) }
        }
        if (note == null) return@withContext null
        _notes.value = listOf(note) + _notes.value
        requestWidgetRefresh()
        note
    }

    suspend fun restoreNote(meta: NoteMeta, content: String) = withContext(Dispatchers.IO) {
        try {
            val restored = store.restoreNote(meta.name, content)
            _notes.value = listOf(restored) + _notes.value.filter { it.uri != meta.uri }
            requestWidgetRefresh()
        } catch (e: Exception) {
            _errorMessage.value = NoteError.StorageError
        }
    }

    suspend fun readNote(uri: String): String = withContext(Dispatchers.IO) {
        try {
            store.readNote(uri)
        } catch (e: Exception) {
            _errorMessage.value = NoteError.StorageError
            ""
        }
    }

    suspend fun writeNote(uri: String, content: String) = withContext(Dispatchers.IO) {
        try {
            store.writeNote(uri, content)
            _notes.value = _notes.value.map { meta ->
                if (meta.uri == uri) {
                    val head = content.take(4096)
                    meta.copy(
                        title = deriveTitle(meta.name, head),
                        snippet = deriveSnippet(meta.name, head),
                        lastModified = System.currentTimeMillis(),
                    )
                } else meta
            }.sortedByDescending { it.lastModified }
            requestWidgetRefresh()
        } catch (e: Exception) {
            _errorMessage.value = NoteError.StorageError
        }
    }

    suspend fun deleteNote(meta: NoteMeta): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = store.readNote(meta.uri)
            store.deleteNote(meta.uri)
            _notes.value = _notes.value.filter { it.uri != meta.uri }
            DeletedNoteHolder.last = DeletedNote(meta, content)
            requestWidgetRefresh()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = NoteError.StorageError
            false
        }
    }

    suspend fun undoDelete() = withContext(Dispatchers.IO) {
        val last = DeletedNoteHolder.last ?: return@withContext
        DeletedNoteHolder.last = null
        restoreNote(last.meta, last.content)
    }

    private fun requestWidgetRefresh() {
        val intent = Intent(RecentNotesWidgetProvider.ACTION_REFRESH).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    private suspend fun <T> recover(failed: NoteStore, retry: suspend (NoteStore) -> T): T? {
        if (failed is InternalNoteStore) {
            return null
        }
        val internal = InternalNoteStore(context.filesDir.resolve("notes"))
        store = internal
        _folderRevoked.value = true
        _storageName.value = internal.displayName()
        _errorMessage.value = NoteError.FolderUnavailable
        return try {
            retry(internal)
        } catch (e: Exception) {
            null
        }
    }
}

data class DeletedNote(val meta: NoteMeta, val content: String)

private object DeletedNoteHolder {
    @Volatile
    var last: DeletedNote? = null
}
