package com.patryksliwinski.quicknotes.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patryksliwinski.quicknotes.data.AppTheme
import com.patryksliwinski.quicknotes.data.EditorPrefs
import com.patryksliwinski.quicknotes.data.NoteMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object List : Screen
    data class Editor(val note: NoteMeta) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val notesViewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_OPEN_NOTE) {
            intent.getStringExtra(EXTRA_NOTE_URI)?.let { pendingOpenNote = noteMetaFromUri(it) }
        }
        setContent {
            var theme by remember { mutableStateOf(AppTheme.SYSTEM) }
            LaunchedEffect(Unit) {
                val loaded = withContext(Dispatchers.IO) {
                    EditorPrefs.loadTheme(this@MainActivity)
                }
                theme = loaded
            }
            QuickNotesTheme(theme) {
                AppRoot(
                    notesViewModel,
                    handleNewNote = intent?.action == ACTION_NEW_NOTE ||
                        intent?.action == ACTION_OPEN_NOTE ||
                        pendingNewNote != null || pendingOpenNote != null,
                    pendingNote = pendingOpenNote ?: pendingNewNote,
                    onThemeChanged = { theme = it },
                ) { handled ->
                    if (handled) {
                        intent = intent?.setAction(null)
                        pendingNewNote = null
                        pendingOpenNote = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            ACTION_NEW_NOTE -> {
                notesViewModel.createNote { note ->
                    // Posted to the main looper so Compose state is updated on the UI thread.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        pendingNewNote = note
                    }
                }
            }
            ACTION_OPEN_NOTE -> {
                val uri = intent.getStringExtra(EXTRA_NOTE_URI) ?: return
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    pendingOpenNote = noteMetaFromUri(uri)
                }
            }
        }
    }

    private fun noteMetaFromUri(uri: String): NoteMeta {
        val name = uri.substringAfterLast('/')
        return NoteMeta(
            uri = uri,
            name = name,
            lastModified = 0L,
            title = name.removeSuffix(".md"),
            snippet = "",
        )
    }

    private var pendingNewNote by mutableStateOf<NoteMeta?>(null)
    private var pendingOpenNote by mutableStateOf<NoteMeta?>(null)

    override fun onResume() {
        super.onResume()
        notesViewModel.refresh()
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.patryksliwinski.quicknotes.NEW_NOTE"
        const val ACTION_OPEN_NOTE = "com.patryksliwinski.quicknotes.OPEN_NOTE"
        const val EXTRA_NOTE_URI = "com.patryksliwinski.quicknotes.extra.NOTE_URI"
    }
}

@Composable
fun QuickNotesTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (theme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(LocalContext.current)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(LocalContext.current)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun AppRoot(
    notesViewModel: NotesViewModel,
    handleNewNote: Boolean,
    pendingNote: NoteMeta?,
    onThemeChanged: (AppTheme) -> Unit = {},
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
            onThemeChanged = onThemeChanged,
        )
    }
}
