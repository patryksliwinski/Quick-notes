package com.patryksliwinski.quicknotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patryksliwinski.quicknotes.data.ListPrefs
import com.patryksliwinski.quicknotes.data.NoteError
import com.patryksliwinski.quicknotes.data.NoteListRow
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteRepository
import com.patryksliwinski.quicknotes.data.NoteSortOrder
import com.patryksliwinski.quicknotes.data.filterNotesPrecomputed
import com.patryksliwinski.quicknotes.data.groupByDay
import com.patryksliwinski.quicknotes.data.sortNotes
import com.patryksliwinski.quicknotes.data.toSearchEntries
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    constructor(application: Application, repository: NoteRepository) : this(application) {
        this.repository = repository
    }

    private var repository = NoteRepositoryHolder.get(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _sortOrder = MutableStateFlow(ListPrefs.loadSortOrder(application))
    val sortOrder: StateFlow<NoteSortOrder> = _sortOrder

    private val _confirmDelete = MutableStateFlow(ListPrefs.loadConfirmDelete(application))
    val confirmDelete: StateFlow<Boolean> = _confirmDelete

    val notes: StateFlow<List<NoteMeta>> = combine(
        repository.notes.map { all -> all.toSearchEntries() },
        _query,
        _sortOrder,
    ) { entries, q, order ->
        sortNotes(filterNotesPrecomputed(entries, q), order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listRows: StateFlow<List<NoteListRow>> = notes
        .map { groupByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderRevoked: StateFlow<Boolean> = repository.folderRevoked
    val storageName: StateFlow<String> = repository.storageName
    val errorMessage: StateFlow<NoteError?> = repository.errorMessage

    private val _deleteEvents = MutableSharedFlow<NoteMeta?>(extraBufferCapacity = 1)
    val deleteEvents: SharedFlow<NoteMeta?> = _deleteEvents.asSharedFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { repository.refresh() }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun onSortOrderChange(order: NoteSortOrder) {
        _sortOrder.value = order
        ListPrefs.saveSortOrder(getApplication(), order)
    }

    fun onConfirmDeleteChange(enabled: Boolean) {
        _confirmDelete.value = enabled
        ListPrefs.saveConfirmDelete(getApplication(), enabled)
    }

    fun clearError() = repository.clearError()

    fun reportFolderPermissionLost() = repository.reportError(NoteError.FolderPermissionLost)

    fun createNote(onCreated: (NoteMeta) -> Unit) = viewModelScope.launch {
        val note = repository.createNote() ?: return@launch
        withContext(Dispatchers.Main) {
            onCreated(note)
        }
    }

    fun deleteNote(meta: NoteMeta) = viewModelScope.launch {
        val deleted = repository.deleteNote(meta)
        _deleteEvents.tryEmit(if (deleted) meta else null)
    }

    fun undoDelete() = viewModelScope.launch { repository.undoDelete() }

    fun flushPendingSaves() = viewModelScope.launch { repository.flushPendingSaves() }
}
