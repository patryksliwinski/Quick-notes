package com.patryksliwinski.quicknotes.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        context.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.filesDir.resolve("notes").deleteRecursively()
        repository = NoteRepository(context)
        kotlinx.coroutines.runBlocking { repository.refresh() }
    }

    private suspend fun seed(vararg contents: String): List<NoteMeta> {
        return contents.map { repository.createNote().also { m -> repository.writeNote(m.uri, it) } }
    }

    @Test
    fun `refresh lists notes from internal storage`() = runTest {
        seed("# One\nfirst", "# Two\nsecond")
        repository.refresh()
        val notes = repository.notes.first()
        assertEquals(2, notes.size)
        assertEquals(setOf("One", "Two"), notes.map { it.title }.toSet())
    }

    @Test
    fun `createNote prepends to list`() = runTest {
        val meta = repository.createNote()
        assertEquals(meta.uri, repository.notes.first().first().uri)
    }

    @Test
    fun `writeNote updates title and snippet of the edited note`() = runTest {
        val (a, b) = seed("# A\na", "# B\nb")
        repository.writeNote(a.uri, "# Alpha\nbody a")
        val notes = repository.notes.first()
        val updated = notes.first { it.uri == a.uri }
        assertEquals("Alpha", updated.title)
        assertEquals("body a", updated.snippet)
        assertEquals(2, notes.size)
        val untouched = notes.first { it.uri == b.uri }
        assertEquals("B", untouched.title)
        assertEquals("b", untouched.snippet)
    }

    @Test
    fun `refresh sorts notes newest first as the user sees in the list`() = runTest {
        val (a, b) = seed("# A\na", "# B\nb")
        val aFile = java.io.File(java.net.URI(a.uri))
        aFile.setLastModified(aFile.lastModified() - 60_000)
        repository.refresh()
        val notes = repository.notes.first()
        assertEquals(listOf(b.uri, a.uri), notes.map { it.uri })
    }

    @Test
    fun `readNote returns content`() = runTest {
        val (meta) = seed("# Title\nhello world")
        assertEquals("# Title\nhello world", repository.readNote(meta.uri))
    }

    @Test
    fun `deleteNote removes note and undoDelete restores it with content`() = runTest {
        val (meta) = seed("# Keep\nrestore me")
        repository.deleteNote(meta)
        assertTrue(repository.notes.first().isEmpty())
        repository.undoDelete()
        val restored = repository.notes.first()
        assertEquals(1, restored.size)
        assertEquals(meta.uri, restored[0].uri)
        assertEquals("# Keep\nrestore me", repository.readNote(restored[0].uri))
    }

    @Test
    fun `undoDelete without prior delete is a no-op`() = runTest {
        seed("# A\na")
        repository.undoDelete()
        assertEquals(1, repository.notes.first().size)
    }

    @Test
    fun `restoreNote replaces deleted entry`() = runTest {
        val (meta) = seed("# Old\nold")
        repository.deleteNote(meta)
        repository.restoreNote(meta, "# New\nnew")
        val notes = repository.notes.first()
        assertEquals(1, notes.size)
        assertEquals(meta.name.removeSuffix(".md"), notes[0].title)
        assertEquals("# New\nnew", repository.readNote(notes[0].uri))
    }

    @Test
    fun `storageName is set after refresh`() = runTest {
        repository.refresh()
        assertEquals("App-private storage", repository.storageName.first())
    }
}
