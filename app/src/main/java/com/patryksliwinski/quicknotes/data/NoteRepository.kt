package com.patryksliwinski.quicknotes.data

import android.content.Context
import android.content.Intent
import com.patryksliwinski.quicknotes.receiver.RecentNotesWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class NoteRepository(private val context: Context) {

    private var store: NoteStore = InternalNoteStore(context.filesDir.resolve("notes"))
    private val _notes = MutableStateFlow<List<NoteMeta>>(emptyList())
    val notes: StateFlow<List<NoteMeta>> = _notes
    private val _folderRevoked = MutableStateFlow(false)
    val folderRevoked: StateFlow<Boolean> = _folderRevoked
    private val _storageName = MutableStateFlow("")
    val storageName: StateFlow<String> = _storageName

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val (resolved, revoked) = resolveNoteStore(context)
        store = resolved
        _folderRevoked.value = revoked
        _storageName.value = resolved.displayName()
        _notes.value = resolved.listNotes()
    }

    suspend fun createNote(): NoteMeta = withContext(Dispatchers.IO) {
        val note = store.createNote()
        _notes.value = listOf(note) + _notes.value
        requestWidgetRefresh()
        note
    }

    suspend fun restoreNote(meta: NoteMeta, content: String) = withContext(Dispatchers.IO) {
        val restored = store.restoreNote(meta.name, content)
        _notes.value = listOf(restored) + _notes.value.filter { it.uri != meta.uri }
        requestWidgetRefresh()
    }

    suspend fun readNote(uri: String): String = withContext(Dispatchers.IO) {
        store.readNote(uri)
    }

    suspend fun writeNote(uri: String, content: String) = withContext(Dispatchers.IO) {
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
}

data class DeletedNote(val meta: NoteMeta, val content: String)

private object DeletedNoteHolder {
    @Volatile
    var last: DeletedNote? = null
}
