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
    fun `writeNote updates title snippet and ordering`() = runTest {
        val (a, b) = seed("", "")
        repository.writeNote(a.uri, "# Alpha\nbody a")
        val notes = repository.notes.first()
        val updated = notes.first { it.uri == a.uri }
        assertEquals("Alpha", updated.title)
        assertEquals("body a", updated.snippet)
        assertEquals(2, notes.size)
        assertTrue(notes.any { it.uri == b.uri })
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
        assertEquals(meta.name.removeSuffix(".md"), restored[0].title)
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
