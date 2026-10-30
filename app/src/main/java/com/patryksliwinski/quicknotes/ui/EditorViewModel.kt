package com.patryksliwinski.quicknotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patryksliwinski.quicknotes.data.EditorPrefs
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SaveState {
    Saving,
    Saved,
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    constructor(application: Application, repository: NoteRepository) : this(application) {
        this.repository = repository
    }

    private var repository = NoteRepositoryHolder.get(application)
    private val undoStack = UndoStack()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _saveState = MutableStateFlow<SaveState?>(null)
    val saveState: StateFlow<SaveState?> = _saveState

    val stats: StateFlow<TextStats> = _content
        .map { computeStats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeStats(""))

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    private val _fontSizeSp = MutableStateFlow(EditorPrefs.DEFAULT_FONT_SIZE_SP)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp

    private var noteUri: String? = null
    private var openGeneration = 0
    private var contentDirty = false
    private var saveJob: Job? = null
    private var lastSaved = ""
    private var savedContent: String? = null
    private var restoring = false
    private var loadFailed = false

    val hasUnsavedChanges: Boolean
        get() = noteUri != null && _content.value != savedContent

    fun open(meta: NoteMeta, prefill: String? = null) {
        if (noteUri == meta.uri && _content.value.isNotEmpty()) return
        noteUri = meta.uri
        val generation = ++openGeneration
        contentDirty = false
        loadFailed = false
        saveJob?.cancel()
        _saveState.value = null
        undoStack.reset()
        lastSaved = ""
        restoring = false
        syncUndoFlags()
        if (prefill != null) {
            savedContent = null
            onContentChange(prefill)
        } else {
            viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    val text = repository.readNote(meta.uri)
                    text to EditorPrefs.loadFontSizeSp(getApplication())
                }
                if (generation != openGeneration || contentDirty) return@launch
                loaded.first.fold(
                    onSuccess = { text ->
                        _content.value = text
                        lastSaved = text
                        savedContent = text
                        _saveState.value = SaveState.Saved
                        _fontSizeSp.value = loaded.second
                    },
                    onFailure = {
                        loadFailed = true
                        _content.value = ""
                        savedContent = null
                        _saveState.value = null
                    },
                )
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
        contentDirty = true
        _content.value = text
        if (loadFailed) return
        scheduleSave(text)
    }

    fun undo() {
        val restored = undoStack.undo(_content.value) ?: return
        restore(restored)
    }

    fun redo() {
        val restored = undoStack.redo(_content.value) ?: return
        restore(restored)
    }

    fun insertTimestamp() {
        onContentChange(appendTimestampLine(_content.value))
    }

    fun saveNow() {
        val uri = noteUri ?: return
        if (loadFailed) return
        saveJob?.cancel()
        val text = _content.value
        _saveState.value = SaveState.Saving
        saveJob = viewModelScope.launch {
            commitSnapshot(text)
            repository.writeNote(uri, text)
            if (_content.value == text) {
                savedContent = text
                _saveState.value = SaveState.Saved
            }
        }
    }

    private fun restore(snapshot: String) {
        contentDirty = true
        _content.value = snapshot
        syncUndoFlags()
        scheduleSave(snapshot, restoring = true)
    }

    private fun scheduleSave(text: String, restoring: Boolean = false) {
        val uri = noteUri ?: return
        this.restoring = restoring
        _saveState.value = SaveState.Saving
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val debounce = withContext(Dispatchers.IO) {
                EditorPrefs.loadDebounceMs(getApplication())
            }
            delay(debounce)
            commitSnapshot(text)
            repository.writeNote(uri, text)
            if (_content.value == text) {
                savedContent = text
                _saveState.value = SaveState.Saved
            }
        }
    }

    private fun commitSnapshot(text: String) {
        if (restoring) {
            restoring = false
        } else if (lastSaved != text) {
            undoStack.push(lastSaved)
            syncUndoFlags()
        }
        lastSaved = text
    }

    private fun syncUndoFlags() {
        _canUndo.value = undoStack.canUndo
        _canRedo.value = undoStack.canRedo
    }
}
