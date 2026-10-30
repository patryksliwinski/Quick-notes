package com.patryksliwinski.quicknotes.ui

import com.patryksliwinski.quicknotes.data.InternalNoteStore
import com.patryksliwinski.quicknotes.data.NoteMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var viewModel: EditorViewModel
    private lateinit var store: InternalNoteStore

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.filesDir.resolve("notes").deleteRecursively()
        viewModel = EditorViewModel(app)
        store = InternalNoteStore(app.filesDir.resolve("notes"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("condition not met within ${timeoutMs}ms")
            }
            Thread.sleep(20)
        }
    }

    private fun seed(content: String): NoteMeta = runBlocking {
        val meta = store.createNote()
        store.writeNote(meta.uri, content)
        meta
    }

    private fun fileOf(meta: NoteMeta) = java.io.File(java.net.URI(meta.uri))

    @Test
    fun `open loads note content`() {
        val meta = seed("# Title\nhello")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "# Title\nhello" }
    }

    @Test
    fun `open same note with content does not reload`() {
        val meta = seed("original")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "original" }
        viewModel.onContentChange("edited")
        viewModel.open(meta)
        assertEquals("edited", viewModel.content.value)
    }

    @Test
    fun `open different note reloads content`() {
        val a = seed("first note")
        val b = seed("second note")
        viewModel.open(a)
        waitFor { viewModel.content.value == "first note" }
        viewModel.open(b)
        waitFor { viewModel.content.value == "second note" }
    }

    @Test
    fun `open same note again after content cleared reloads`() {
        val meta = seed("reload me")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "reload me" }
        viewModel.onContentChange("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "reload me" }
    }

    @Test
    fun `onContentChange debounces and autosaves`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "" }
        viewModel.onContentChange("autosaved")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "autosaved" }
    }

    @Test
    fun `rapid changes cancel pending autosave and save last value`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "" }
        viewModel.onContentChange("first")
        viewModel.onContentChange("second")
        viewModel.onContentChange("final")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "final" }
    }

    @Test
    fun `saveNow persists immediately without waiting for debounce`() {
        val meta = seed("before")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "before" }
        viewModel.onContentChange("now")
        viewModel.saveNow()
        waitFor { fileOf(meta).readText() == "now" }
    }

    @Test
    fun `onContentChange without open note does not crash`() {
        viewModel.onContentChange("orphan")
        assertEquals("orphan", viewModel.content.value)
    }

    @Test
    fun `saveNow without open note does not crash`() {
        viewModel.saveNow()
    }

    @Test
    fun `open marks note as saved once content is loaded`() {
        val meta = seed("hello")
        viewModel.open(meta)
        waitFor { viewModel.saveState.value == SaveState.Saved }
    }

    @Test
    fun `onContentChange reports saving then saved after autosave`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.saveState.value == SaveState.Saved }
        viewModel.onContentChange("typing")
        assertEquals(SaveState.Saving, viewModel.saveState.value)
        waitFor(timeoutMs = 5000) {
            viewModel.saveState.value == SaveState.Saved && fileOf(meta).readText() == "typing"
        }
    }

    @Test
    fun `saveNow reports saving then saved`() {
        val meta = seed("before")
        viewModel.open(meta)
        waitFor { viewModel.saveState.value == SaveState.Saved }
        viewModel.onContentChange("now")
        viewModel.saveNow()
        waitFor(timeoutMs = 5000) {
            viewModel.saveState.value == SaveState.Saved && fileOf(meta).readText() == "now"
        }
    }

    @Test
    fun `open with prefill sets content immediately and autosaves it`() {
        val meta = seed("")
        viewModel.open(meta, prefill = "shared from another app")
        assertEquals("shared from another app", viewModel.content.value)
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "shared from another app" }
    }

    @Test
    fun `open with prefill does not overwrite with a blocking read of the empty file`() {
        val meta = seed("")
        viewModel.open(meta, prefill = "prefilled")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "prefilled" }
        assertEquals("prefilled", viewModel.content.value)
    }

    @Test
    fun `duplicate creates a distinct note with the current content`() {
        val original = seed("# Original\nbody")
        viewModel.open(original)
        waitFor { viewModel.content.value == "# Original\nbody" }
        var duplicated: NoteMeta? = null
        viewModel.duplicate { duplicated = it }
        waitFor { duplicated != null }
        assertTrue(duplicated!!.uri != original.uri)
        assertTrue(Regex("""\d{8}-\d{6}-[a-z0-9]{4}\.md""").matches(duplicated!!.name))
        waitFor(timeoutMs = 5000) { fileOf(duplicated!!).readText() == "# Original\nbody" }
        assertEquals("# Original\nbody", fileOf(original).readText())
    }

    @Test
    fun `duplicate without an open note does not crash`() {
        viewModel.duplicate { fail("should not be called without an open note") }
    }

    @Test
    fun `hasUnsavedChanges is false right after open and true after typing`() {
        val meta = seed("saved content")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "saved content" }
        assertEquals(false, viewModel.hasUnsavedChanges)
        viewModel.onContentChange("edited")
        assertEquals(true, viewModel.hasUnsavedChanges)
    }

    @Test
    fun `hasUnsavedChanges is false again once the autosave completes`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.saveState.value == SaveState.Saved }
        viewModel.onContentChange("typed")
        assertEquals(true, viewModel.hasUnsavedChanges)
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "typed" }
        waitFor { viewModel.hasUnsavedChanges == false }
    }

    @Test
    fun `failed note load does not autosave empty content over the real file`() {
        val writes = mutableListOf<String>()
        val store = object : com.patryksliwinski.quicknotes.data.NoteStore {
            override suspend fun listNotes() = emptyList<com.patryksliwinski.quicknotes.data.NoteMeta>()
            override suspend fun createNote() = throw UnsupportedOperationException()
            override suspend fun createNoteWithContent(content: String) = throw UnsupportedOperationException()
            override suspend fun restoreNote(name: String, content: String) = throw UnsupportedOperationException()
            override suspend fun readNote(uri: String): String = throw java.io.IOException("volume unavailable")
            override suspend fun readHead(uri: String, maxBytes: Int): String = throw java.io.IOException("volume unavailable")
            override suspend fun writeNote(uri: String, content: String) { writes.add(content) }
            override suspend fun deleteNote(uri: String) {}
            override fun displayName() = "failing"
        }
        val repo = com.patryksliwinski.quicknotes.data.NoteRepository(app) { store to false }
        val viewModel = EditorViewModel(app, repo)
        val meta = NoteMeta("content://failing/doc1", "doc1.md", 0L, "doc1", "", size = 42L)
        viewModel.open(meta)
        waitFor { repo.errorMessage.value == com.patryksliwinski.quicknotes.data.NoteError.StorageError }
        waitFor { viewModel.content.value == "" && viewModel.saveState.value == null }
        assertTrue(viewModel.hasUnsavedChanges)
        viewModel.onContentChange("typed after failure")
        Thread.sleep(600)
        assertTrue(writes.isEmpty())
        assertEquals("typed after failure", viewModel.content.value)
        viewModel.saveNow()
        Thread.sleep(200)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `hasUnsavedChanges is true after saveNow while a newer keystroke is still pending`() {
        val meta = seed("before")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "before" }
        viewModel.onContentChange("first")
        viewModel.saveNow()
        waitFor { fileOf(meta).readText() == "first" }
        viewModel.onContentChange("second")
        assertEquals(true, viewModel.hasUnsavedChanges)
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "second" }
        waitFor { viewModel.hasUnsavedChanges == false }
    }
}
