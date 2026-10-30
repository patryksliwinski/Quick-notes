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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SharedNoteRepositoryTest {

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
        collectorJob.cancel()
        collectorScope.cancel()
        Dispatchers.resetMain()
    }

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("condition not met within ${timeoutMs}ms (notes=${notesViewModel.notes.value.size})")
            }
            Thread.sleep(20)
        }
    }

    private fun createNoteWithContent(content: String): NoteMeta {
        var created: NoteMeta? = null
        notesViewModel.createNote { created = it }
        waitFor { created != null }
        val meta = created!!
        runBlocking { repository.writeNote(meta.uri, content) }
        return meta
    }

    @Test
    fun `holder returns one process-wide instance shared by both view models`() {
        val holderRepo = NoteRepositoryHolder.get(app)
        assertSame(holderRepo, NoteRepositoryHolder.get(app))
        val listVm = NotesViewModel(app)
        val editorVm = EditorViewModel(app)
        val listField = NotesViewModel::class.java.getDeclaredField("repository")
        listField.isAccessible = true
        val editorField = EditorViewModel::class.java.getDeclaredField("repository")
        editorField.isAccessible = true
        assertSame(holderRepo, listField.get(listVm))
        assertSame(holderRepo, editorField.get(editorVm))
    }

    @Test
    fun `duplicate from the editor appears in the list flow without a refresh`() {
        val original = createNoteWithContent("# Original\nbody")
        waitFor { notesViewModel.notes.value.any { it.uri == original.uri } }
        editorViewModel.open(original)
        waitFor { editorViewModel.content.value == "# Original\nbody" }
        var duplicated: NoteMeta? = null
        editorViewModel.duplicate { duplicated = it }
        waitFor { duplicated != null }
        assertTrue(duplicated!!.uri != original.uri)
        waitFor { notesViewModel.notes.value.any { it.uri == duplicated!!.uri } }
        assertEquals(duplicated!!.uri, notesViewModel.notes.value.first().uri)
    }

    @Test
    fun `editing via the editor updates title and re-sorts the list flow in place`() {
        val older = createNoteWithContent("# Alpha\nfirst")
        Thread.sleep(5)
        val newer = createNoteWithContent("# Beta\nsecond")
        waitFor { notesViewModel.notes.value.size == 2 }
        assertEquals(newer.uri, notesViewModel.notes.value.first().uri)
        editorViewModel.open(older)
        waitFor { editorViewModel.content.value == "# Alpha\nfirst" }
        editorViewModel.onContentChange("# Renamed\nedited")
        editorViewModel.saveNow()
        waitFor {
            notesViewModel.notes.value.firstOrNull()?.let { it.uri == older.uri && it.title == "Renamed" } == true
        }
        assertEquals(2, notesViewModel.notes.value.size)
    }

    @Test
    fun `delete via the list view model is visible on the shared repository flow`() {
        val meta = createNoteWithContent("# Temp\nbye")
        waitFor { repository.notes.value.any { it.uri == meta.uri } }
        notesViewModel.deleteNote(meta)
        waitFor { notesViewModel.notes.value.isEmpty() }
        assertTrue(repository.notes.value.isEmpty())
    }
}
