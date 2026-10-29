package com.patryksliwinski.quicknotes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patryksliwinski.quicknotes.data.NoteMeta

sealed interface Screen {
    data object List : Screen
    data class Editor(val note: NoteMeta) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val notesViewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot(
                    notesViewModel,
                    handleNewNote = intent?.action == ACTION_NEW_NOTE || pendingNewNote != null,
                    pendingNote = pendingNewNote,
                ) { handled ->
                    if (handled) {
                        intent = intent?.setAction(null)
                        pendingNewNote = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_NEW_NOTE) {
            notesViewModel.createNote { note ->
                // Posted to the main looper so Compose state is updated on the UI thread.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    pendingNewNote = note
                }
            }
        }
    }

    private var pendingNewNote by mutableStateOf<NoteMeta?>(null)

    override fun onResume() {
        super.onResume()
        notesViewModel.refresh()
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.patryksliwinski.quicknotes.NEW_NOTE"
    }
}

@Composable
fun AppRoot(
    notesViewModel: NotesViewModel,
    handleNewNote: Boolean,
    pendingNote: NoteMeta?,
    onHandled: (Boolean) -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val editorViewModel: EditorViewModel = viewModel()

    LaunchedEffect(handleNewNote, pendingNote) {
        if (!handleNewNote) return@LaunchedEffect
        val note = pendingNote ?: run {
            notesViewModel.createNote { created ->
                editorViewModel.open(created)
                screen = Screen.Editor(created)
            }
            onHandled(true)
            return@LaunchedEffect
        }
        editorViewModel.open(note)
        screen = Screen.Editor(note)
        onHandled(true)
    }

    when (val current = screen) {
        Screen.List -> NotesListScreen(
            viewModel = notesViewModel,
            onOpenNote = { note ->
                editorViewModel.open(note)
                screen = Screen.Editor(note)
            },
            onOpenSettings = { screen = Screen.Settings },
        )
        is Screen.Editor -> EditorScreen(
            viewModel = editorViewModel,
            noteTitle = current.note.title,
            onBack = { screen = Screen.List },
        )
        Screen.Settings -> SettingsScreen(
            viewModel = notesViewModel,
            onBack = { screen = Screen.List },
        )
    }
}
