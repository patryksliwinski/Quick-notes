package com.patryksliwinski.quicknotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SaveState {
    Saving,
    Saved,
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(application)

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _saveState = MutableStateFlow<SaveState?>(null)
    val saveState: StateFlow<SaveState?> = _saveState

    private var noteUri: String? = null
    private var saveJob: Job? = null
    private var savedContent: String? = null

    val hasUnsavedChanges: Boolean
        get() = noteUri != null && _content.value != savedContent

    fun open(meta: NoteMeta, prefill: String? = null) {
        if (noteUri == meta.uri && _content.value.isNotEmpty()) return
        noteUri = meta.uri
        saveJob?.cancel()
        _saveState.value = null
        if (prefill != null) {
            savedContent = null
            onContentChange(prefill)
        } else {
            viewModelScope.launch {
                val loaded = repository.readNote(meta.uri)
                _content.value = loaded
                savedContent = loaded
                _saveState.value = SaveState.Saved
            }
        }
    }

    fun duplicate(onDuplicated: (NoteMeta) -> Unit) {
        if (noteUri == null) return
        val content = _content.value
        saveJob?.cancel()
        viewModelScope.launch {
            val note = repository.createNote(content) ?: return@launch
            onDuplicated(note)
        }
    }

    fun onContentChange(text: String) {
        _content.value = text
        val uri = noteUri ?: return
        _saveState.value = SaveState.Saving
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            repository.writeNote(uri, text)
            if (_content.value == text) {
                savedContent = text
                _saveState.value = SaveState.Saved
            }
        }
    }

    fun saveNow() {
        val uri = noteUri ?: return
        val text = _content.value
        saveJob?.cancel()
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            repository.writeNote(uri, text)
            if (_content.value == text) {
                savedContent = text
                _saveState.value = SaveState.Saved
            }
        }
    }
}
