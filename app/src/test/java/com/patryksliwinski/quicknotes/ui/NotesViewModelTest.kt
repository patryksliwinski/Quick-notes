package com.patryksliwinski.quicknotes.ui

import com.patryksliwinski.quicknotes.data.DAY_KEY_TODAY
import com.patryksliwinski.quicknotes.data.ListPrefs
import com.patryksliwinski.quicknotes.data.NoteError
import com.patryksliwinski.quicknotes.data.NoteListRow
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteRepository
import com.patryksliwinski.quicknotes.data.NoteSortOrder
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        app.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.filesDir.resolve("notes").deleteRecursively()
        viewModel = NotesViewModel(app)
        collectorScope = CoroutineScope(Dispatchers.Unconfined)
        collectorJob = collectorScope.launch { viewModel.notes.collect { } }
        collectorScope.launch { viewModel.listRows.collect { } }
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
        viewModel.onQueryChange("no-such-note")
        waitFor { viewModel.notes.value.isEmpty() }
        viewModel.onQueryChange("")
        waitFor { viewModel.notes.value.size == 2 }
    }

    @Test
    fun `quick path always creates a new note, never reopens existing`() {
        viewModel.refresh()
        waitFor { viewModel.storageName.value.isNotEmpty() }
        val first = createWithContent("# First", "one")
        var second: NoteMeta? = null
        viewModel.createNote { second = it }
        waitFor { second != null }
        assertTrue(second!!.uri != first.uri)
        assertEquals(second!!.uri, viewModel.notes.value.first().uri)
    }

    @Test
    fun `createNote does not invoke callback when creation fails`() {
        val failing = NotesViewModel(app)
        failing.refresh()
        waitFor { failing.storageName.value.isNotEmpty() }
        val notesDir = app.filesDir.resolve("notes")
        notesDir.deleteRecursively()
        notesDir.createNewFile()
        var called = false
        failing.createNote { called = true }
        waitFor { failing.errorMessage.value != null }
        Thread.sleep(100)
        assertFalse(called)
        assertEquals(NoteError.CreateFailed, failing.errorMessage.value)
        failing.clearError()
        assertEquals(null, failing.errorMessage.value)
        notesDir.delete()
        viewModel.refresh()
    }

    @Test
    fun `reportFolderPermissionLost surfaces FolderPermissionLost error`() {
        viewModel.refresh()
        waitFor { viewModel.storageName.value.isNotEmpty() }
        viewModel.reportFolderPermissionLost()
        waitFor { viewModel.errorMessage.value != null }
        assertEquals(NoteError.FolderPermissionLost, viewModel.errorMessage.value)
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
        runBlocking {
            val repo = NoteRepository(app)
            repo.refresh()
            assertEquals("# Temp\nbye", repo.readNote(meta.uri).getOrThrow())
        }
    }

    @Test
    fun `deleteNote emits the deleted note on success`() {
        val meta = createWithContent("# Temp", "bye")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 1 }
        val events = mutableListOf<NoteMeta?>()
        val job = collectorScope.launch { viewModel.deleteEvents.collect { events.add(it) } }
        viewModel.deleteNote(meta)
        waitFor { events.isNotEmpty() }
        assertEquals(meta.uri, events.first()!!.uri)
        job.cancel()
    }

    @Test
    fun `deleteNote emits null and keeps the note when storage fails`() {
        val meta = createWithContent("# Ghost", "gone")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 1 }
        val ghost = meta.copy(uri = "file:///nonexistent/ghost.md")
        val events = mutableListOf<NoteMeta?>()
        val job = collectorScope.launch { viewModel.deleteEvents.collect { events.add(it) } }
        viewModel.deleteNote(ghost)
        waitFor { events.isNotEmpty() }
        assertNull(events.first())
        waitFor { viewModel.notes.value.size == 1 && viewModel.notes.value.first().uri == meta.uri }
        job.cancel()
    }

    @Test
    fun `folderRevoked is false for default internal storage`() {
        viewModel.refresh()
        waitFor { viewModel.storageName.value.isNotEmpty() }
        assertFalse(viewModel.folderRevoked.value)
    }

    @Test
    fun `query also matches the filename`() {
        val meta = createWithContent("# Alpha", "first")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 1 }
        val datePart = meta.name.substringBefore('-')
        viewModel.onQueryChange(datePart)
        waitFor { viewModel.notes.value.size == 1 && viewModel.notes.value[0].uri == meta.uri }
        viewModel.onQueryChange("no-such-filename-part")
        waitFor { viewModel.notes.value.isEmpty() }
        viewModel.onQueryChange("")
        waitFor { viewModel.notes.value.size == 1 }
    }

    @Test
    fun `sort order toggle reorders the visible list and persists`() {
        createWithContent("# Apple", "one")
        createWithContent("# Zebra", "two")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 2 }
        assertEquals(NoteSortOrder.LAST_MODIFIED, viewModel.sortOrder.value)
        val byModified = viewModel.notes.value.map { it.title }
        assertEquals(listOf("Zebra", "Apple"), byModified)
        viewModel.onSortOrderChange(NoteSortOrder.TITLE)
        waitFor { viewModel.notes.value.map { it.title } == listOf("Apple", "Zebra") }
        assertEquals(listOf("Apple", "Zebra"), viewModel.notes.value.map { it.title })
        assertEquals(NoteSortOrder.TITLE, ListPrefs.loadSortOrder(app))
        viewModel.onSortOrderChange(NoteSortOrder.FILENAME_DATE)
        waitFor { viewModel.notes.value.map { it.name } == viewModel.notes.value.map { it.name }.sortedDescending() }
        assertEquals(NoteSortOrder.FILENAME_DATE, viewModel.sortOrder.value)
        viewModel.onSortOrderChange(NoteSortOrder.LAST_MODIFIED)
        waitFor { viewModel.notes.value.map { it.title } == byModified }
    }

    @Test
    fun `sort order is restored from prefs on construction`() {
        ListPrefs.saveSortOrder(app, NoteSortOrder.TITLE)
        val restored = NotesViewModel(app)
        assertEquals(NoteSortOrder.TITLE, restored.sortOrder.value)
    }

    @Test
    fun `confirm delete toggle persists and defaults to false`() {
        assertFalse(viewModel.confirmDelete.value)
        viewModel.onConfirmDeleteChange(true)
        assertTrue(viewModel.confirmDelete.value)
        assertTrue(ListPrefs.loadConfirmDelete(app))
        val restored = NotesViewModel(app)
        assertTrue(restored.confirmDelete.value)
        viewModel.onConfirmDeleteChange(false)
        assertFalse(viewModel.confirmDelete.value)
    }

    @Test
    fun `listRows groups notes under day headers`() {
        createWithContent("# Alpha", "first")
        createWithContent("# Beta", "second")
        viewModel.refresh()
        waitFor { viewModel.notes.value.size == 2 }
        waitFor { viewModel.listRows.value.count { it is NoteListRow.Note } == 2 }
        val rows = viewModel.listRows.value
        assertEquals(NoteListRow.Header(DAY_KEY_TODAY), rows.first())
        assertTrue(rows.all { it !is NoteListRow.Header || it.dayKey == DAY_KEY_TODAY })
    }

    @Test
    fun `concurrent cold-start refreshes trigger only one underlying scan`() {
        val scans = java.util.concurrent.atomic.AtomicInteger(0)
        val internal = com.patryksliwinski.quicknotes.data.InternalNoteStore(app.filesDir.resolve("notes"))
        val countingStore = object : com.patryksliwinski.quicknotes.data.NoteStore by internal {
            override suspend fun listNotes(): List<NoteMeta> {
                scans.incrementAndGet()
                return internal.listNotes()
            }
        }
        val repo = NoteRepository(app) { countingStore to false }
        val vm = NotesViewModel(app)
        val field = NotesViewModel::class.java.getDeclaredField("repository")
        field.isAccessible = true
        field.set(vm, repo)
        vm.refresh()
        vm.refresh()
        vm.refresh()
        waitFor { scans.get() >= 1 }
        Thread.sleep(200)
        assertEquals(1, scans.get())
    }

    @Test
    fun `flushPendingSaves is exposed on the view model`() {
        val journal = com.patryksliwinski.quicknotes.data.PendingSaveJournal(app)
        app.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        viewModel.refresh()
        waitFor { viewModel.storageName.value.isNotEmpty() }
        val meta = createWithContent("# Alpha", "first")
        journal.record(meta.uri, "recovered after kill")
        viewModel.flushPendingSaves()
        val file = java.io.File(java.net.URI(meta.uri))
        waitFor { runCatching { file.readText() }.getOrNull() == "recovered after kill" }
        assertTrue(journal.pending().isEmpty())
    }

}
