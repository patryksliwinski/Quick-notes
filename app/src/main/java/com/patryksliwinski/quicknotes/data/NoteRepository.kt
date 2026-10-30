package com.patryksliwinski.quicknotes.data

import android.content.Context
import android.content.Intent
import com.patryksliwinski.quicknotes.receiver.RecentNotesWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class NoteError {
    CreateFailed,
    FolderUnavailable,
    StorageError,
    FolderPermissionLost,
}

enum class SaveOutcome {
    Written,
    Journaled,
}

class NoteRepository(
    private val context: Context,
    private val storeProvider: () -> Pair<NoteStore, Boolean> = { resolveNoteStore(context) },
) {

    private var store: NoteStore = InternalNoteStore(context.filesDir.resolve("notes"))
    private val journal = PendingSaveJournal(context)
    private val headCache = ConcurrentHashMap<HeadKey, HeadFields>()
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
        var listed: List<NoteMeta>
        var fromFallback = false
        try {
            listed = resolved.listNotes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fallback = recover(resolved) { it.listNotes() }
            if (fallback == null) {
                if (_errorMessage.value == null) _errorMessage.value = NoteError.StorageError
                return@withContext
            }
            listed = fallback
            fromFallback = true
        }
        if (listed.isEmpty() && !fromFallback && resolved !is InternalNoteStore) {
            _errorMessage.value = NoteError.FolderUnavailable
        }
        _notes.value = enrichHeads(listed)
    }

    private suspend fun enrichHeads(listed: List<NoteMeta>): List<NoteMeta> {
        val next = HashMap<HeadKey, HeadFields>(listed.size * 2)
        val enriched = listed.map { meta ->
            val key = HeadKey(meta.uri, meta.lastModified, meta.size)
            val cached = headCache[key]
            val fields = if (cached != null) {
                next[key] = cached
                cached
            } else {
                val head = try {
                    store.readHead(meta.uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val derived = deriveHead(meta.name, head.orEmpty())
                if (!head.isNullOrEmpty()) next[key] = derived
                derived
            }
            meta.copy(title = fields.title, snippet = fields.snippet)
        }
        headCache.clear()
        headCache.putAll(next)
        return enriched
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
        scheduleWidgetRefresh()
        note
    }

    suspend fun restoreNote(meta: NoteMeta, content: String) = withContext(Dispatchers.IO) {
        try {
            val restored = store.restoreNote(meta.name, content)
            _notes.value = listOf(restored) + _notes.value.filter { it.uri != meta.uri }
            scheduleWidgetRefresh()
        } catch (e: Exception) {
            _errorMessage.value = NoteError.StorageError
        }
    }

    suspend fun readNote(uri: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(readNoteChecked(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = NoteError.StorageError
            Result.failure(e)
        }
    }

    private suspend fun readNoteChecked(uri: String): String {
        val content = store.readNote(uri)
        val meta = _notes.value.firstOrNull { it.uri == uri }
        if (content.isEmpty() && meta != null && meta.size > 0) {
            throw java.io.IOException("Note unreadable (volume unavailable or permission lost): $uri")
        }
        return content
    }

    suspend fun writeNote(uri: String, content: String): SaveOutcome = withContext(Dispatchers.IO) {
        journal.record(uri, content)
        mutexFor(uri).withLock {
            try {
                store.writeNote(uri, content)
                journal.clear(uri)
                val now = System.currentTimeMillis()
                _notes.value = upsertEditedNote(_notes.value, uri, content, now)
                scheduleWidgetRefresh()
                SaveOutcome.Written
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveOutcome.Journaled
            }
        }
    }

    suspend fun flushPendingSaves() = withContext(Dispatchers.IO) {
        val pending = journal.pending()
        if (pending.isEmpty()) return@withContext
        val (resolved, revoked) = storeProvider()
        store = resolved
        _folderRevoked.value = revoked
        _storageName.value = resolved.displayName()
        for (save in pending) {
            mutexFor(save.uri).withLock {
                try {
                    resolved.writeNote(save.uri, save.content)
                    journal.clear(save.uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _errorMessage.value = NoteError.StorageError
                }
            }
        }
    }

    private fun mutexFor(uri: String): Mutex = writeMutexes.computeIfAbsent(uri) { Mutex() }

    private fun upsertEditedNote(
        current: List<NoteMeta>,
        uri: String,
        content: String,
        now: Long,
    ): List<NoteMeta> {
        val head = content.take(HEAD_BYTES)
        val index = current.indexOfFirst { it.uri == uri }
        if (index < 0) return current
        val existing = current[index]
        val fields = deriveHead(existing.name, head)
        val updated = existing.copy(
            title = fields.title,
            snippet = fields.snippet,
            lastModified = now,
            size = content.toByteArray(Charsets.UTF_8).size.toLong(),
        )
        headCache[HeadKey(uri, now, updated.size)] = fields
        val others = current.filterIndexed { i, _ -> i != index }
        return if (others.any { it.lastModified > now }) {
            (others + updated).sortedByDescending { it.lastModified }
        } else {
            listOf(updated) + others
        }
    }

    suspend fun deleteNote(meta: NoteMeta): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = readNoteChecked(meta.uri)
            store.deleteNote(meta.uri)
            _notes.value = _notes.value.filter { it.uri != meta.uri }
            DeletedNoteHolder.last = DeletedNote(meta, content)
            scheduleWidgetRefresh()
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

    private val widgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var widgetRefreshJob: Job? = null

    private fun scheduleWidgetRefresh() {
        widgetRefreshJob?.cancel()
        widgetRefreshJob = widgetRefreshScope.launch {
            delay(WIDGET_REFRESH_COALESCE_MS)
            if (!isActive) return@launch
            val intent = Intent(RecentNotesWidgetProvider.ACTION_REFRESH).apply {
                setPackage(context.packageName)
            }
            try {
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private companion object {
        val writeMutexes = ConcurrentHashMap<String, Mutex>()
        const val WIDGET_REFRESH_COALESCE_MS = 2000L
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
