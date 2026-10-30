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
        assertTrue(meta!!.uri.startsWith("file:"))
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
            override suspend fun createNoteWithContent(content: String): NoteMeta = throw UnsupportedOperationException()
            override suspend fun restoreNote(name: String, content: String): NoteMeta = throw UnsupportedOperationException()
            override suspend fun readNote(uri: String) = ""
            override suspend fun readHead(uri: String, maxBytes: Int) = ""
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

    @Test
    fun `createNote with content writes the content and derives title in one write`() = runTest {
        val meta = repository.createNote("# Shared\nfrom another app")!!
        assertEquals("# Shared\nfrom another app", repository.readNote(meta.uri))
        assertTrue(Regex("""\d{8}-\d{6}-[a-z0-9]{4}\.md""").matches(meta.name))
        assertEquals("Shared", meta.title)
        assertEquals("from another app", meta.snippet)
        assertEquals(meta.uri, repository.notes.first().first().uri)
    }

    @Test
    fun `createNote with content falls back to internal storage when SAF create fails`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = true) to false }
        repo.refresh()
        val meta = repo.createNote("shared text")
        assertTrue(meta != null)
        assertTrue(meta!!.uri.startsWith("file:"))
        assertEquals("shared text", repo.readNote(meta.uri))
        assertTrue(repo.folderRevoked.first())
    }

    @Test
    fun `refresh reads heads only once per unchanged file across refreshes`() = runTest {
        val counting = CountingNoteStore(context.filesDir.resolve("notes"))
        val repo = NoteRepository(context) { counting to false }
        repo.refresh()
        val meta = repo.createNote()!!
        repo.writeNote(meta.uri, "# Cached\nbody")
        repo.refresh()
        val readsAfterFirstRefresh = counting.headReads.get()
        repo.refresh()
        repo.refresh()
        assertEquals(readsAfterFirstRefresh, counting.headReads.get())
        val notes = repo.notes.first()
        assertEquals(1, notes.size)
        assertEquals("Cached", notes[0].title)
        assertEquals("body", notes[0].snippet)
    }

    @Test
    fun `refresh re-reads head only for files changed since last scan`() = runTest {
        val counting = CountingNoteStore(context.filesDir.resolve("notes"))
        val repo = NoteRepository(context) { counting to false }
        repo.refresh()
        val (a, b) = listOf(repo.createNote()!!, repo.createNote()!!)
        repo.writeNote(a.uri, "# A\na")
        repo.writeNote(b.uri, "# B\nb")
        repo.refresh()
        val notesBefore = repo.notes.first()
        assertEquals("A", notesBefore.first { it.uri == a.uri }.title)
        assertEquals("B", notesBefore.first { it.uri == b.uri }.title)
        val before = counting.headReads.get()
        repo.refresh()
        assertEquals(before, counting.headReads.get())
        java.io.File(java.net.URI(b.uri)).writeText("# B2\nb2 changed")
        repo.refresh()
        assertEquals(before + 1, counting.headReads.get())
        val notes = repo.notes.first()
        assertEquals("A", notes.first { it.uri == a.uri }.title)
        assertEquals("B2", notes.first { it.uri == b.uri }.title)
    }

    @Test
    fun `writeNote moves the edited note to the top without resorting the rest`() = runTest {
        val (a, b, c) = listOf(
            repository.createNote()!!,
            repository.createNote()!!,
            repository.createNote()!!,
        )
        repository.writeNote(a.uri, "# A\na")
        repository.writeNote(b.uri, "# B\nb")
        repository.writeNote(c.uri, "# C\nc")
        java.io.File(java.net.URI(a.uri)).setLastModified(1000)
        java.io.File(java.net.URI(b.uri)).setLastModified(2000)
        java.io.File(java.net.URI(c.uri)).setLastModified(3000)
        repository.refresh()
        assertEquals(listOf(c.uri, b.uri, a.uri), repository.notes.first().map { it.uri })
        repository.writeNote(a.uri, "# Alpha\nbody a")
        val notes = repository.notes.first()
        assertEquals(listOf(a.uri, c.uri, b.uri), notes.map { it.uri })
        assertEquals("Alpha", notes[0].title)
        assertEquals("body a", notes[0].snippet)
    }

    @Test
    fun `writeNote resorts only when another note is newer than the edit`() = runTest {
        val (a, b) = listOf(repository.createNote()!!, repository.createNote()!!)
        repository.writeNote(a.uri, "# A\na")
        repository.writeNote(b.uri, "# B\nb")
        java.io.File(java.net.URI(a.uri)).setLastModified(1000)
        java.io.File(java.net.URI(b.uri)).setLastModified(2000)
        repository.refresh()
        assertEquals(listOf(b.uri, a.uri), repository.notes.first().map { it.uri })
        java.io.File(java.net.URI(b.uri)).setLastModified(Long.MAX_VALUE - 1)
        repository.refresh()
        repository.writeNote(a.uri, "# Alpha\nbody a")
        assertEquals(listOf(b.uri, a.uri), repository.notes.first().map { it.uri })
        assertEquals("Alpha", repository.notes.first().first { it.uri == a.uri }.title)
    }

    @Test
    fun `refresh falls back to file name when head read fails`() = runTest {
        val broken = object : NoteStore by InternalNoteStore(context.filesDir.resolve("notes")) {
            override suspend fun readHead(uri: String, maxBytes: Int): String =
                throw java.io.IOException("head unavailable")
        }
        val repo = NoteRepository(context) { broken to false }
        val meta = repo.createNote()!!
        repo.refresh()
        val notes = repo.notes.first()
        assertEquals(1, notes.size)
        assertEquals(meta.name.removeSuffix(".md"), notes[0].title)
        assertEquals("", notes[0].snippet)
    }

    @Test
    fun `writeNote for a note not in the list leaves the list unchanged`() = runTest {
        val external = InternalNoteStore(context.filesDir.resolve("elsewhere"))
        val meta = external.createNote()
        repository.refresh()
        assertTrue(repository.notes.first().isEmpty())
        repository.writeNote(meta.uri, "# Outside\nnot listed")
        assertTrue(repository.notes.first().isEmpty())
        external.deleteNote(meta.uri)
    }

    private class CountingNoteStore(dir: java.io.File) : NoteStore {
        private val delegate = InternalNoteStore(dir)
        val headReads = java.util.concurrent.atomic.AtomicInteger(0)

        override suspend fun listNotes() = delegate.listNotes()
        override suspend fun createNote() = delegate.createNote()
        override suspend fun createNoteWithContent(content: String) = delegate.createNoteWithContent(content)
        override suspend fun restoreNote(name: String, content: String) = delegate.restoreNote(name, content)
        override suspend fun readNote(uri: String) = delegate.readNote(uri)
        override suspend fun writeNote(uri: String, content: String) = delegate.writeNote(uri, content)
        override suspend fun deleteNote(uri: String) = delegate.deleteNote(uri)
        override fun displayName() = delegate.displayName()

        override suspend fun readHead(uri: String, maxBytes: Int): String {
            headReads.incrementAndGet()
            return delegate.readHead(uri, maxBytes)
        }
    }
}
