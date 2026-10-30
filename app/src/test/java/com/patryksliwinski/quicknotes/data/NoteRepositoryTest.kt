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
        context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.filesDir.resolve("notes").deleteRecursively()
        val holder = Class.forName("com.patryksliwinski.quicknotes.data.DeletedNoteHolder")
        val last = holder.getDeclaredField("last")
        last.isAccessible = true
        last.set(null, null)
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
    fun `refresh keeps the last known list when the folder becomes unreadable`() = runTest {
        var fail = false
        val internal = InternalNoteStore(context.filesDir.resolve("notes"))
        val flaky = object : NoteStore by internal {
            override suspend fun listNotes(): List<NoteMeta> {
                if (fail) throw java.io.IOException("SD card unavailable")
                return internal.listNotes()
            }
        }
        val repo = NoteRepository(context) { flaky to false }
        val meta = repo.createNote("# Kept\nbody")!!
        repo.refresh()
        assertEquals(1, repo.notes.first().size)
        fail = true
        repo.refresh()
        assertEquals(NoteError.FolderUnavailable, repo.errorMessage.first())
        assertTrue(repo.folderRevoked.first())
        assertEquals(listOf(meta.uri), repo.notes.first().map { it.uri })
        assertEquals("Kept", repo.notes.first()[0].title)
    }

    @Test
    fun `writeNote failure keeps the journal entry and reports Journaled without a StorageError`() = runTest {
        val journal = PendingSaveJournal(context)
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.refresh()
        repo.clearError()
        val outcome = repo.writeNote("content://failing/doc1", "hello")
        assertEquals(SaveOutcome.Journaled, outcome)
        assertNull(repo.errorMessage.first())
        assertEquals(listOf(PendingSave("content://failing/doc1", "hello")), journal.pending())
    }

    @Test
    fun `writeNote success returns Written and clears the journal without an error`() = runTest {
        val (meta) = seed("# A\na")
        val outcome = repository.writeNote(meta.uri, "# A2\na2")
        assertEquals(SaveOutcome.Written, outcome)
        assertNull(repository.errorMessage.first())
        assertTrue(PendingSaveJournal(context).pending().isEmpty())
    }

    @Test
    fun `readNote failure sets StorageError and returns failure instead of empty content`() = runTest {
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.refresh()
        assertTrue(repo.readNote("content://failing/doc1").isFailure)
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
    }

    @Test
    fun `refresh surfaces FolderUnavailable when the store silently returns no notes`() = runTest {
        val repo = NoteRepository(context) { NullReturningNoteStore() to false }
        repo.refresh()
        assertEquals(NoteError.FolderUnavailable, repo.errorMessage.first())
        assertTrue(repo.notes.first().isEmpty())
    }

    @Test
    fun `readNote on a store that silently returns empty for a non-empty note surfaces StorageError`() = runTest {
        val meta = NoteMeta("content://null/doc1", "doc1.md", 0L, "doc1", "", size = 42L)
        val listed = object : NoteStore by NullReturningNoteStore() {
            override suspend fun listNotes() = listOf(meta)
        }
        val repo = NoteRepository(context) { listed to false }
        repo.refresh()
        repo.clearError()
        assertTrue(repo.readNote(meta.uri).isFailure)
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
    }

    @Test
    fun `readNote on a store that silently returns empty for a zero byte note is success`() = runTest {
        val meta = NoteMeta("content://null/doc1", "doc1.md", 0L, "doc1", "", size = 0L)
        val listed = object : NoteStore by NullReturningNoteStore() {
            override suspend fun listNotes() = listOf(meta)
        }
        val repo = NoteRepository(context) { listed to false }
        repo.refresh()
        val result = repo.readNote(meta.uri)
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow())
    }

    @Test
    fun `readNote succeeds with genuinely empty content for a zero byte note`() = runTest {
        val (meta) = seed("")
        val result = repository.readNote(meta.uri)
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow())
        assertNull(repository.errorMessage.first())
    }

    @Test
    fun `deleteNote aborts when the note content cannot be read so undo keeps real content`() = runTest {
        val (meta) = seed("# Real\ncontent")
        val silentEmpty = object : NoteStore by InternalNoteStore(context.filesDir.resolve("notes")) {
            override suspend fun readNote(uri: String): String = ""
        }
        val field = NoteRepository::class.java.getDeclaredField("store")
        field.isAccessible = true
        field.set(repository, silentEmpty)
        assertFalse(repository.deleteNote(meta))
        assertEquals(NoteError.StorageError, repository.errorMessage.first())
        assertEquals(1, repository.notes.first().size)
        repository.undoDelete()
        assertEquals(1, repository.notes.first().size)
        assertEquals("# Real\ncontent", java.io.File(java.net.URI(meta.uri)).readText())
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
        assertEquals("# Title\nhello world", repository.readNote(meta.uri).getOrThrow())
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
        assertEquals("# Keep\nrestore me", repository.readNote(restored[0].uri).getOrThrow())
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
        assertEquals("New", notes[0].title)
        assertEquals("new", notes[0].snippet)
        assertEquals("# New\nnew", repository.readNote(notes[0].uri).getOrThrow())
    }

    @Test
    fun `storageName is set after refresh`() = runTest {
        repository.refresh()
        assertEquals("App-private storage", repository.storageName.first())
    }

    @Test
    fun `createNote with content writes the content and derives title in one write`() = runTest {
        val meta = repository.createNote("# Shared\nfrom another app")!!
        assertEquals("# Shared\nfrom another app", repository.readNote(meta.uri).getOrThrow())
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
        assertEquals("shared text", repo.readNote(meta.uri).getOrThrow())
        assertTrue(repo.folderRevoked.first())
    }

    @Test
    fun `refresh reads full content only once per unchanged file across refreshes`() = runTest {
        val counting = CountingNoteStore(context.filesDir.resolve("notes"))
        val repo = NoteRepository(context) { counting to false }
        repo.refresh()
        val meta = repo.createNote()!!
        repo.writeNote(meta.uri, "# Cached\nbody")
        repo.refresh()
        val readsAfterFirstRefresh = counting.fullReads.get()
        repo.refresh()
        repo.refresh()
        assertEquals(readsAfterFirstRefresh, counting.fullReads.get())
        val notes = repo.notes.first()
        assertEquals(1, notes.size)
        assertEquals("Cached", notes[0].title)
        assertEquals("body", notes[0].snippet)
        assertEquals("# Cached\nbody", notes[0].content)
    }

    @Test
    fun `refresh re-reads content only for files changed since last scan`() = runTest {
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
        val before = counting.fullReads.get()
        repo.refresh()
        assertEquals(before, counting.fullReads.get())
        java.io.File(java.net.URI(b.uri)).writeText("# B2\nb2 changed")
        repo.refresh()
        assertEquals(before + 1, counting.fullReads.get())
        val notes = repo.notes.first()
        assertEquals("A", notes.first { it.uri == a.uri }.title)
        assertEquals("B2", notes.first { it.uri == b.uri }.title)
        assertEquals("# B2\nb2 changed", notes.first { it.uri == b.uri }.content)
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
    fun `refresh falls back to file name when the note read fails`() = runTest {
        val broken = object : NoteStore by InternalNoteStore(context.filesDir.resolve("notes")) {
            override suspend fun readNote(uri: String): String =
                throw java.io.IOException("note unavailable")
        }
        val repo = NoteRepository(context) { broken to false }
        val meta = repo.createNote()!!
        repo.refresh()
        val notes = repo.notes.first()
        assertEquals(1, notes.size)
        assertEquals(meta.name.removeSuffix(".md"), notes[0].title)
        assertEquals("", notes[0].snippet)
        assertEquals("", notes[0].content)
    }

    @Test
    fun `failed note read is not cached and later refresh recovers title and content`() = runTest {
        val notesDir = context.filesDir.resolve("notes")
        val delegate = InternalNoteStore(notesDir)
        var fail = true
        val flaky = object : NoteStore by delegate {
            override suspend fun readNote(uri: String): String {
                if (fail) throw java.io.IOException("SD card unavailable")
                return delegate.readNote(uri)
            }
        }
        val repo = NoteRepository(context) { flaky to false }
        val meta = repo.createNote("# Recovered\nbody")!!
        repo.refresh()
        assertEquals(meta.name.removeSuffix(".md"), repo.notes.first()[0].title)
        assertEquals("", repo.notes.first()[0].snippet)
        assertEquals("", repo.notes.first()[0].content)
        fail = false
        repo.refresh()
        val notes = repo.notes.first()
        assertEquals("Recovered", notes[0].title)
        assertEquals("body", notes[0].snippet)
        assertEquals("# Recovered\nbody", notes[0].content)
    }

    @Test
    fun `empty note read is not cached and later refresh recovers title and content`() = runTest {
        val notesDir = context.filesDir.resolve("notes")
        val delegate = InternalNoteStore(notesDir)
        var returnEmpty = true
        val flaky = object : NoteStore by delegate {
            override suspend fun readNote(uri: String): String {
                if (returnEmpty) return ""
                return delegate.readNote(uri)
            }
        }
        val repo = NoteRepository(context) { flaky to false }
        val meta = repo.createNote("# Later\nbody")!!
        repo.refresh()
        assertEquals(meta.name.removeSuffix(".md"), repo.notes.first()[0].title)
        returnEmpty = false
        repo.refresh()
        val notes = repo.notes.first()
        assertEquals("Later", notes[0].title)
        assertEquals("body", notes[0].snippet)
        assertEquals("# Later\nbody", notes[0].content)
    }

    @Test
    fun `refresh indexes the full note content beyond the head window for search`() = runTest {
        val deep = "# Deep\nshort snippet\n" + "x".repeat(HEAD_BYTES) + "\nneedle-at-the-end"
        val (meta) = seed(deep)
        repository.refresh()
        val note = repository.notes.first().first { it.uri == meta.uri }
        assertEquals(deep, note.content)
        val entries = repository.notes.first().toSearchEntries()
        assertEquals(listOf(note), filterNotesPrecomputed(entries, "needle-at-the-end"))
        assertTrue(filterNotesPrecomputed(entries, "not-anywhere").isEmpty())
    }

    @Test
    fun `writeNote keeps the indexed content in sync for search`() = runTest {
        val (meta) = seed("# A\nold body")
        repository.writeNote(meta.uri, "# A\nnew body with fresh-token")
        val notes = repository.notes.first()
        val updated = notes.first { it.uri == meta.uri }
        assertEquals("# A\nnew body with fresh-token", updated.content)
        val entries = notes.toSearchEntries()
        assertEquals(listOf(updated), filterNotesPrecomputed(entries, "fresh-token"))
        assertTrue(filterNotesPrecomputed(entries, "old body").isEmpty())
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

    @Test
    fun `writeNote journals the pending save and clears it after a successful write`() = runTest {
        val journal = PendingSaveJournal(context)
        val meta = repository.createNote()!!
        repository.writeNote(meta.uri, "durable")
        assertTrue(journal.pending().isEmpty())
        assertEquals("durable", repository.readNote(meta.uri).getOrThrow())
    }

    @Test
    fun `writeNote keeps the journal entry when the store write fails`() = runTest {
        val journal = PendingSaveJournal(context)
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.refresh()
        repo.writeNote("content://failing/doc1", "must survive")
        val pending = journal.pending()
        assertEquals(listOf(PendingSave("content://failing/doc1", "must survive")), pending)
    }

    @Test
    fun `flushPendingSaves rewrites journaled content and clears the journal`() = runTest {
        val journal = PendingSaveJournal(context)
        val meta = repository.createNote()!!
        repository.writeNote(meta.uri, "original")
        journal.record(meta.uri, "typed but killed before write")
        java.io.File(java.net.URI(meta.uri)).writeText("original")
        repository.flushPendingSaves()
        assertEquals("typed but killed before write", repository.readNote(meta.uri).getOrThrow())
        assertTrue(journal.pending().isEmpty())
    }

    @Test
    fun `flushPendingSaves without journal entries is a no-op`() = runTest {
        PendingSaveJournal(context).clear("file:///nonexistent")
        context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val meta = repository.createNote()!!
        repository.writeNote(meta.uri, "stable")
        repository.flushPendingSaves()
        assertEquals("stable", repository.readNote(meta.uri).getOrThrow())
    }

    @Test
    fun `flushPendingSaves keeps the journal entry when the rewrite fails`() = runTest {
        val journal = PendingSaveJournal(context)
        context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        journal.record("content://failing/doc1", "keep me")
        val repo = NoteRepository(context) { FailingNoteStore(failCreate = false) to false }
        repo.flushPendingSaves()
        assertEquals(listOf(PendingSave("content://failing/doc1", "keep me")), journal.pending())
        assertEquals(NoteError.StorageError, repo.errorMessage.first())
    }

    private class CountingNoteStore(dir: java.io.File) : NoteStore {
        private val delegate = InternalNoteStore(dir)
        val fullReads = java.util.concurrent.atomic.AtomicInteger(0)

        override suspend fun listNotes() = delegate.listNotes()
        override suspend fun createNote() = delegate.createNote()
        override suspend fun createNoteWithContent(content: String) = delegate.createNoteWithContent(content)
        override suspend fun restoreNote(name: String, content: String) = delegate.restoreNote(name, content)
        override suspend fun writeNote(uri: String, content: String) = delegate.writeNote(uri, content)
        override suspend fun deleteNote(uri: String) = delegate.deleteNote(uri)
        override fun displayName() = delegate.displayName()

        override suspend fun readHead(uri: String, maxBytes: Int): String {
            return delegate.readHead(uri, maxBytes)
        }

        override suspend fun readNote(uri: String): String {
            fullReads.incrementAndGet()
            return delegate.readNote(uri)
        }
    }
}
