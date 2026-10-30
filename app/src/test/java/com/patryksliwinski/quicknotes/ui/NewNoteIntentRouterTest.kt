package com.patryksliwinski.quicknotes.ui

import android.content.Intent
import com.patryksliwinski.quicknotes.data.EmptyResultNoteStore
import com.patryksliwinski.quicknotes.data.InternalNoteStore
import com.patryksliwinski.quicknotes.data.NoteError
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteRepository
import com.patryksliwinski.quicknotes.data.NoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NewNoteIntentRouterTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var repository: NoteRepository
    private lateinit var notesViewModel: NotesViewModel
    private lateinit var editorViewModel: EditorViewModel
    private lateinit var collectorScope: CoroutineScope
    private lateinit var collectorJob: Job

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.filesDir.resolve("notes").deleteRecursively()
        repository = NoteRepository(app)
        notesViewModel = NotesViewModel(app, repository)
        editorViewModel = EditorViewModel(app, repository)
        collectorScope = CoroutineScope(Dispatchers.Unconfined)
        collectorJob = collectorScope.launch { notesViewModel.notes.collect { } }
        collectorScope.launch { notesViewModel.listRows.collect { } }
        notesViewModel.refresh()
        waitFor { repository.storageName.value.isNotEmpty() }
    }

    @After
    fun tearDown() {
        clearViewModel(notesViewModel)
        clearViewModel(editorViewModel)
        collectorJob.cancel()
        collectorScope.cancel()
        Dispatchers.resetMain()
    }

    private fun clearViewModel(viewModel: androidx.lifecycle.ViewModel) {
        val method = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("condition not met within ${timeoutMs}ms (notes=${repository.notes.value.size})")
            }
            Thread.sleep(20)
        }
    }

    private fun newNoteIntent() = Intent(MainActivity.ACTION_NEW_NOTE)

    private fun openNoteIntent(uri: String) = Intent(MainActivity.ACTION_OPEN_NOTE)
        .putExtra(MainActivity.EXTRA_NOTE_URI, uri)

    @Test
    fun `intent router maps widget or tile ACTION_NEW_NOTE to a new-note request`() {
        val request = incomingNoteRequestFromIntent(newNoteIntent())
        assertTrue(request is IncomingNoteRequest.NewNote)
        assertNull((request as IncomingNoteRequest.NewNote).sharedText)
    }

    @Test
    fun `intent router maps widget row ACTION_OPEN_NOTE with the uri extra to an open-note request`() {
        val uri = "content://com.android.externalstorage.documents/tree/XXXX%3ANotes/document/XXXX%3ANotes%3Aa.md"
        val request = incomingNoteRequestFromIntent(openNoteIntent(uri))
        assertTrue(request is IncomingNoteRequest.OpenNote)
        val note = (request as IncomingNoteRequest.OpenNote).note
        assertEquals(uri, note.uri)
        assertEquals("a.md", note.name)
        assertEquals("a", note.title)
    }

    @Test
    fun `intent router maps ACTION_OPEN_NOTE without uri to no request`() {
        assertNull(incomingNoteRequestFromIntent(Intent(MainActivity.ACTION_OPEN_NOTE)))
    }

    @Test
    fun `intent router maps share ACTION_SEND to a new-note request carrying the shared text`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "shared from browser")
        }
        val request = incomingNoteRequestFromIntent(intent)
        assertTrue(request is IncomingNoteRequest.NewNote)
        assertEquals("shared from browser", (request as IncomingNoteRequest.NewNote).sharedText)
    }

    @Test
    fun `intent router maps ACTION_SEND without usable text to no request`() {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        assertNull(incomingNoteRequestFromIntent(intent))
    }

    @Test
    fun `intent router ignores launcher MAIN and unrelated actions`() {
        assertNull(incomingNoteRequestFromIntent(Intent(Intent.ACTION_MAIN)))
        assertNull(incomingNoteRequestFromIntent(Intent("com.example.OTHER")))
        assertNull(incomingNoteRequestFromIntent(null))
    }

    @Test
    fun `widget tap creates a new note that appears in the shared list flow`() {
        assertTrue(incomingNoteRequestFromIntent(newNoteIntent()) is IncomingNoteRequest.NewNote)
        var created: NoteMeta? = null
        notesViewModel.createNote { created = it }
        waitFor { created != null }
        waitFor { repository.notes.value.any { it.uri == created!!.uri } }
        assertEquals(created!!.uri, repository.notes.value.first().uri)
    }

    @Test
    fun `activity alive then onNewIntent re-delivery creates another new note, never reopens`() {
        val first = createNote()
        val secondRequest = incomingNoteRequestFromIntent(newNoteIntent())
        assertTrue(secondRequest is IncomingNoteRequest.NewNote)
        var second: NoteMeta? = null
        notesViewModel.createNote { second = it }
        waitFor { second != null }
        assertNotEquals(first.uri, second!!.uri)
        waitFor { repository.notes.value.size == 2 }
        assertEquals(second!!.uri, repository.notes.value.first().uri)
    }

    @Test
    fun `share intent creates a new note and the editor prefills the shared text`() {
        val request = incomingNoteRequestFromIntent(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "shared text")
            }
        ) as IncomingNoteRequest.NewNote
        var created: NoteMeta? = null
        notesViewModel.createNote { created = it }
        waitFor { created != null }
        editorViewModel.open(created!!, prefill = request.sharedText)
        assertEquals("shared text", editorViewModel.content.value)
        waitFor(timeoutMs = 5000) {
            runCatching {
                java.io.File(java.net.URI(created!!.uri)).readText()
            }.getOrNull() == "shared text"
        }
        waitFor { repository.notes.value.any { it.uri == created!!.uri } }
    }

    @Test
    fun `disposed editor saveNow persists unsaved keystrokes through the shared repository`() {
        val meta = createNote()
        editorViewModel.open(meta)
        waitFor { editorViewModel.saveState.value == SaveState.Saved }
        editorViewModel.onContentChange("typed then left")
        editorViewModel.saveNow()
        waitFor(timeoutMs = 5000) {
            runCatching {
                java.io.File(java.net.URI(meta.uri)).readText()
            }.getOrNull() == "typed then left"
        }
        waitFor {
            repository.notes.value.firstOrNull()?.let {
                it.uri == meta.uri && it.snippet == "typed then left"
            } == true
        }
        assertEquals(1, repository.notes.value.size)
    }

    private fun createNote(): NoteMeta {
        var created: NoteMeta? = null
        notesViewModel.createNote { created = it }
        waitFor { created != null }
        waitFor { repository.notes.value.any { it.uri == created!!.uri } }
        return created!!
    }
}

@RunWith(RobolectricTestRunner::class)
class SilentEmptyNoteStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.filesDir.resolve("notes").deleteRecursively()
    }

    @Test
    fun `repository surfaces FolderUnavailable when a SAF-like store silently lists empty`() = kotlinx.coroutines.test.runTest {
        val repo = NoteRepository(context) { EmptyResultNoteStore() to false }
        repo.refresh()
        assertEquals(NoteError.FolderUnavailable, repo.errorMessage.first())
        assertTrue(repo.notes.first().isEmpty())
    }

    @Test
    fun `repository readNote treats a silent empty read of a non-empty note as failure`() = kotlinx.coroutines.test.runTest {
        val meta = NoteMeta("content://empty/doc1", "doc1.md", 0L, "doc1", "", size = 42L)
        val listed = object : NoteStore by EmptyResultNoteStore() {
            override suspend fun listNotes() = listOf(meta)
        }
        val repo = NoteRepository(context) { listed to false }
        repo.refresh()
        repo.clearError()
        val result = repo.readNote(meta.uri)
        assertTrue(result.isFailure)
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
    }

    @Test
    fun `repository accepts a genuinely empty read of a zero-byte note`() = kotlinx.coroutines.test.runTest {
        val internal = InternalNoteStore(context.filesDir.resolve("notes"))
        val meta = internal.createNote()
        val repo = NoteRepository(context) { EmptyResultNoteStore() to false }
        repo.refresh()
        val result = repo.readNote(meta.uri)
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow())
    }
}
