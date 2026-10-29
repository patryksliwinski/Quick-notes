package com.patryksliwinski.quicknotes.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
        return contents.map { repository.createNote()!!.also { m -> repository.writeNote(m.uri, it) } }
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
        val meta = repository.createNote()!!
        assertEquals(meta.uri, repository.notes.first().first().uri)
    }

    @Test
    fun `createNote falls back to internal storage when SAF create fails`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = true) to false }
        repo.refresh()
        val meta = repo.createNote()
        assertTrue(meta != null)
        assertTrue(meta!!.uri.startsWith("file://"))
        assertTrue(java.io.File(java.net.URI(meta.uri)).exists())
        assertTrue(repo.folderRevoked.first())
        assertEquals(NoteError.FolderUnavailable, repo.errorMessage.first())
        assertEquals(1, repo.notes.first().size)
        assertEquals(meta.uri, repo.notes.first().first().uri)
    }

    @Test
    fun `createNote returns null with CreateFailed when even internal storage fails`() = runTest {
        val notesDir = context.filesDir.resolve("notes")
        notesDir.deleteRecursively()
        notesDir.createNewFile()
        repository.refresh()
        val meta = repository.createNote()
        assertNull(meta)
        assertEquals(NoteError.CreateFailed, repository.errorMessage.first())
        assertTrue(repository.notes.first().isEmpty())
        notesDir.delete()
    }

    @Test
    fun `createNote returns null when both SAF and internal stores fail`() = runTest {
        val notesDir = context.filesDir.resolve("notes")
        notesDir.deleteRecursively()
        notesDir.createNewFile()
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = true) to false }
        repo.refresh()
        assertNull(repo.createNote())
        assertTrue(repo.folderRevoked.first())
        assertEquals(NoteError.FolderUnavailable, repo.errorMessage.first())
        notesDir.delete()
    }

    @Test
    fun `refresh falls back to internal storage when SAF listing fails`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failList = true) to false }
        repo.refresh()
        assertTrue(repo.folderRevoked.first())
        assertEquals(NoteError.FolderUnavailable, repo.errorMessage.first())
        assertEquals("App-private storage", repo.storageName.first())
        assertTrue(repo.notes.first().isEmpty())
    }

    @Test
    fun `writeNote failure sets StorageError and does not crash`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.refresh()
        repo.writeNote("content://failing/doc1", "hello")
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
    }

    @Test
    fun `readNote failure sets StorageError and returns empty`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.refresh()
        assertEquals("", repo.readNote("content://failing/doc1"))
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
    }

    @Test
    fun `deleteNote failure sets StorageError and keeps the note`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.refresh()
        val meta = repo.createNote()!!
        assertFalse(repo.deleteNote(meta))
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
        assertEquals(1, repo.notes.first().size)
    }

    @Test
    fun `restoreNote failure sets StorageError and does not crash`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.refresh()
        val meta = NoteMeta("content://failing/doc1", "doc1.md", 0L, "doc1", "")
        repo.restoreNote(meta, "content")
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
        assertTrue(repo.notes.first().isEmpty())
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
    fun `deleteNote returns false and keeps the note when storage throws`() = runTest {
        val (meta) = seed("# Keep\nsafe")
        val failing = object : NoteStore {
            override suspend fun listNotes() = emptyList<NoteMeta>()
            override suspend fun createNote(): NoteMeta = throw UnsupportedOperationException()
            override suspend fun restoreNote(name: String, content: String): NoteMeta = throw UnsupportedOperationException()
            override suspend fun readNote(uri: String) = ""
            override suspend fun writeNote(uri: String, content: String) {}
            override suspend fun deleteNote(uri: String): Nothing = throw java.io.IOException("disk gone")
            override fun displayName() = "failing"
        }
        val field = NoteRepository::class.java.getDeclaredField("store")
        field.isAccessible = true
        field.set(repository, failing)
        assertFalse(repository.deleteNote(meta))
        assertEquals(1, repository.notes.first().size)
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
