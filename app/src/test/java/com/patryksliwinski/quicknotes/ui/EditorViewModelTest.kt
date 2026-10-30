package com.patryksliwinski.quicknotes.ui

import com.patryksliwinski.quicknotes.data.InternalNoteStore
import com.patryksliwinski.quicknotes.data.NoteMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `stats flow derives word and char count from content`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "" }
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        val seen = mutableListOf<TextStats>()
        val job = scope.launch { viewModel.stats.collect { seen += it } }
        viewModel.onContentChange("one two three")
        waitFor { seen.any { it == TextStats(words = 3, characters = 13) } }
        viewModel.onContentChange("")
        waitFor { seen.any { it == TextStats(words = 0, characters = 0) } }
        job.cancel()
        scope.cancel()
    }

    @Test
    fun `insertTimestamp appends formatted line through normal save path`() {
        val meta = seed("note body")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "note body" }
        viewModel.insertTimestamp()
        val after = viewModel.content.value
        assertTrue(after.startsWith("note body\n"))
        val line = after.trimEnd().substringAfterLast('\n')
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(line))
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == after }
    }

    @Test
    fun `undo restores previous content and persists it via autosave`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "" }
        viewModel.onContentChange("typed text")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "typed text" }
        assertTrue(viewModel.canUndo.value)
        viewModel.undo()
        assertEquals("", viewModel.content.value)
        assertFalse(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "" }
    }

    @Test
    fun `redo reapplies undone content and persists it`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "" }
        viewModel.onContentChange("typed text")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "typed text" }
        viewModel.undo()
        waitFor { viewModel.content.value == "" }
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "" }
        viewModel.redo()
        waitFor { viewModel.content.value == "typed text" }
        assertEquals("typed text", viewModel.content.value)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "typed text" }
    }

    @Test
    fun `undo with empty history is a no-op`() {
        val meta = seed("stable")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "stable" }
        viewModel.undo()
        viewModel.redo()
        assertEquals("stable", viewModel.content.value)
        assertFalse(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }

    @Test
    fun `new change after undo clears redo stack`() {
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "" }
        viewModel.onContentChange("first")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "first" }
        viewModel.undo()
        assertTrue(viewModel.canRedo.value)
        viewModel.onContentChange("branch")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "branch" }
        assertFalse(viewModel.canRedo.value)
        viewModel.redo()
        assertEquals("branch", viewModel.content.value)
    }

    @Test
    fun `opening another note resets undo stack`() {
        val a = seed("note a")
        val b = seed("note b")
        viewModel.open(a)
        waitFor { viewModel.content.value == "note a" }
        viewModel.onContentChange("note a edited")
        waitFor(timeoutMs = 5000) { fileOf(a).readText() == "note a edited" }
        assertTrue(viewModel.canUndo.value)
        viewModel.open(b)
        waitFor { viewModel.content.value == "note b" }
        assertFalse(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }

    @Test
    fun `configurable debounce is honored`() {
        app.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putLong("editor_autosave_debounce_ms", 1000L).commit()
        val meta = seed("")
        viewModel.open(meta)
        waitFor { viewModel.content.value == "" }
        viewModel.onContentChange("slow save")
        Thread.sleep(500)
        assertTrue(fileOf(meta).readText() != "slow save")
        waitFor(timeoutMs = 5000) { fileOf(meta).readText() == "slow save" }
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
}
