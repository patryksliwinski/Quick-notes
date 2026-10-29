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

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(application)

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private var noteUri: String? = null
    private var saveJob: Job? = null

    fun open(meta: NoteMeta) {
        if (noteUri == meta.uri && _content.value.isNotEmpty()) return
        noteUri = meta.uri
        viewModelScope.launch {
            _content.value = repository.readNote(meta.uri)
        }
    }

    fun onContentChange(text: String) {
        _content.value = text
        val uri = noteUri ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            repository.writeNote(uri, text)
        }
    }

    fun saveNow() {
        val uri = noteUri ?: return
        saveJob?.cancel()
        viewModelScope.launch {
            repository.writeNote(uri, _content.value)
        }
    }
}
