package com.patryksliwinski.quicknotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val notes: StateFlow<List<NoteMeta>> = combine(repository.notes, _query) { all, q ->
        if (q.isBlank()) all
        else all.filter { it.title.contains(q, ignoreCase = true) || it.snippet.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderRevoked: StateFlow<Boolean> = repository.folderRevoked
    val storageName: StateFlow<String> = repository.storageName

    fun refresh() = viewModelScope.launch { repository.refresh() }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun createNote(onCreated: (NoteMeta) -> Unit) = viewModelScope.launch {
        val note = repository.createNote()
        withContext(Dispatchers.Main) {
            onCreated(note)
        }
    }

    fun deleteNote(meta: NoteMeta) = viewModelScope.launch { repository.deleteNote(meta) }

    fun undoDelete() = viewModelScope.launch { repository.undoDelete() }
}
