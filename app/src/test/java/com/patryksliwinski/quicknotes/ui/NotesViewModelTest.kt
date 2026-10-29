package com.patryksliwinski.quicknotes.ui

import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotesViewModelTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var viewModel: NotesViewModel
    private lateinit var collectorScope: CoroutineScope
    private lateinit var collectorJob: Job

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.filesDir.resolve("notes").deleteRecursively()
        viewModel = NotesViewModel(app)
        collectorScope = CoroutineScope(Dispatchers.Unconfined)
        collectorJob = collectorScope.launch { viewModel.notes.collect { } }
    }

    @After
    fun tearDown() {
        collectorJob.cancel()
        collectorScope.cancel()
        Dispatchers.resetMain()
    }

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("condition not met within ${timeoutMs}ms (notes=${viewModel.notes.value.size})")
            }
            Thread.sleep(20)
        }
    }

    private fun createWithContent(title: String, body: String): NoteMeta {
        var created: NoteMeta? = null
        viewModel.createNote { created = it }
        waitFor { created != null }
        val meta = created!!
        runBlocking {
            val repo = NoteRepository(app)
            repo.refresh()
            repo.writeNote(meta.uri, "$title\n$body")
        }
        viewModel.refresh()
        waitFor { viewModel.notes.value.any { it.uri == meta.uri && it.title == title.removePrefix("# ").trim() } }
        return meta
    }

    @Test
    fun `refresh loads notes and storage name`() {
        createWithContent("# Alpha", "first")
        createWithContent("# Beta", "second")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 2 }
        assertEquals(setOf("Alpha", "Beta"), viewModel.notes.value.map { it.title }.toSet())
        waitFor { viewModel.storageName.value.isNotEmpty() }
        assertEquals("App-private storage", viewModel.storageName.value)
    }

    @Test
    fun `query filters by title and snippet case-insensitively`() {
        createWithContent("# Alpha", "first")
        createWithContent("# Beta", "unique-snippet")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 2 }
        viewModel.onQueryChange("ALPHA")
        waitFor { viewModel.notes.value.size == 1 }
        assertEquals("Alpha", viewModel.notes.value[0].title)
        viewModel.onQueryChange("unique")
        waitFor { viewModel.notes.value.size == 1 && viewModel.notes.value[0].title == "Beta" }
        viewModel.onQueryChange("")
        waitFor { viewModel.notes.value.size == 2 }
    }

    @Test
    fun `createNote prepends and invokes callback`() {
        viewModel.refresh()
        waitFor { viewModel.storageName.value.isNotEmpty() }
        var created: NoteMeta? = null
        viewModel.createNote { created = it }
        waitFor { created != null && viewModel.notes.value.isNotEmpty() }
        assertEquals(created!!.uri, viewModel.notes.value.first().uri)
    }

    @Test
    fun `deleteNote removes and undoDelete restores`() {
        val meta = createWithContent("# Temp", "bye")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 1 }
        viewModel.deleteNote(meta)
        waitFor { viewModel.notes.value.isEmpty() }
        viewModel.undoDelete()
        waitFor { viewModel.notes.value.size == 1 }
        assertEquals(meta.uri, viewModel.notes.value.first().uri)
    }

    @Test
    fun `folderRevoked is false for default internal storage`() {
        viewModel.refresh()
        waitFor { viewModel.storageName.value.isNotEmpty() }
        assertFalse(viewModel.folderRevoked.value)
    }
}
