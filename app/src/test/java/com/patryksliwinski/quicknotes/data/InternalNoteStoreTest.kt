package com.patryksliwinski.quicknotes.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InternalNoteStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: InternalNoteStore

    @Before
    fun setUp() {
        store = InternalNoteStore(temp.newFolder("notes"))
    }

    @Test
    fun `creates notes dir if missing`() {
        val dir = java.io.File(temp.root, "fresh/child")
        InternalNoteStore(dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `createNote writes empty file and returns meta`() = runTest {
        val meta = store.createNote()
        assertTrue(meta.name.endsWith(".md"))
        assertTrue(meta.uri.startsWith("file:"))
        val file = java.io.File(java.net.URI(meta.uri))
        assertTrue(file.isFile)
        assertEquals("", file.readText())
        assertEquals(meta.name.removeSuffix(".md"), meta.title)
        assertEquals("", meta.snippet)
        assertEquals(0L, meta.size)
    }

    @Test
    fun `createNoteWithContent writes content under a fresh timestamped name and derives title`() = runTest {
        val meta = store.createNoteWithContent("# Copied\nhello")
        assertTrue(Regex("""\d{8}-\d{6}-[a-z0-9]{4}\.md""").matches(meta.name))
        val file = java.io.File(java.net.URI(meta.uri))
        assertTrue(file.isFile)
        assertEquals("# Copied\nhello", file.readText())
        assertEquals("Copied", meta.title)
        assertEquals("hello", meta.snippet)
    }

    @Test
    fun `createNoteWithContent twice produces distinct files both holding their content`() = runTest {
        val a = store.createNoteWithContent("first")
        val b = store.createNoteWithContent("second")
        assertTrue(a.uri != b.uri)
        assertEquals("first", java.io.File(java.net.URI(a.uri)).readText())
        assertEquals("second", java.io.File(java.net.URI(b.uri)).readText())
    }

    @Test
    fun `listNotes returns md files sorted by last modified desc, ignoring dotfiles and non-md`() = runTest {
        val old = store.createNote()
        java.io.File(java.net.URI(old.uri)).setLastModified(1000)
        val recent = store.createNote()
        java.io.File(temp.root, "notes/.hidden.md").writeText("# Hidden")
        java.io.File(temp.root, "notes/readme.txt").writeText("not a note")
        java.io.File(temp.root, "notes/sub").mkdir()

        val notes = store.listNotes()
        assertEquals(2, notes.size)
        assertEquals(recent.uri, notes[0].uri)
        assertEquals(old.uri, notes[1].uri)
        assertTrue(notes.none { it.name.startsWith(".") })
    }

    @Test
    fun `listNotes returns metadata without head fields and readHead provides the head`() = runTest {
        store.restoreNote("20250101-120000-abcd.md", "# Shopping\nbuy milk")
        val notes = store.listNotes()
        assertEquals(1, notes.size)
        assertEquals("", notes[0].title)
        assertEquals("", notes[0].snippet)
        assertEquals("# Shopping\nbuy milk".length.toLong(), notes[0].size)
        val fields = deriveHead(notes[0].name, store.readHead(notes[0].uri))
        assertEquals("Shopping", fields.title)
        assertEquals("buy milk", fields.snippet)
    }

    @Test
    fun `readHead reads only the first maxBytes bytes`() = runTest {
        store.restoreNote("big.md", "# Title\n" + "x".repeat(10000))
        val meta = store.listNotes().single()
        assertEquals(HEAD_BYTES, store.readHead(meta.uri).length)
        assertEquals(10, store.readHead(meta.uri, maxBytes = 10).length)
    }

    @Test
    fun `readHead of missing file throws`() = runTest {
        var thrown = false
        try {
            store.readHead("file://" + temp.root.absolutePath + "/notes/missing.md")
        } catch (e: Exception) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `listNotes on missing dir returns empty`() = runTest {
        val empty = InternalNoteStore(java.io.File(temp.root, "does-not-exist-yet"))
        assertEquals(emptyList<NoteMeta>(), empty.listNotes())
    }

    @Test
    fun `listNotes picks up file added externally by sync`() = runTest {
        assertTrue(store.listNotes().isEmpty())
        java.io.File(temp.root, "notes/20250101-090000-sync.md").writeText("# Synced\nfrom git")
        val notes = store.listNotes()
        assertEquals(1, notes.size)
        val fields = deriveHead(notes[0].name, store.readHead(notes[0].uri))
        assertEquals("Synced", fields.title)
        assertEquals("from git", fields.snippet)
    }

    @Test
    fun `listNotes drops file deleted externally by sync`() = runTest {
        val meta = store.createNote()
        assertEquals(1, store.listNotes().size)
        java.io.File(java.net.URI(meta.uri)).delete()
        assertEquals(emptyList<NoteMeta>(), store.listNotes())
    }

    @Test
    fun `restoreNote writes content under given name`() = runTest {
        val meta = store.restoreNote("restored.md", "hello")
        assertEquals("restored.md", meta.name)
        assertEquals("hello", java.io.File(java.net.URI(meta.uri)).readText())
    }

    @Test
    fun `read and write note roundtrip`() = runTest {
        val meta = store.createNote()
        store.writeNote(meta.uri, "content v1")
        assertEquals("content v1", store.readNote(meta.uri))
        store.writeNote(meta.uri, "content v2")
        assertEquals("content v2", store.readNote(meta.uri))
    }

    @Test
    fun `deleteNote removes file`() = runTest {
        val meta = store.createNote()
        assertTrue(java.io.File(java.net.URI(meta.uri)).isFile)
        store.deleteNote(meta.uri)
        assertFalse(java.io.File(java.net.URI(meta.uri)).exists())
        assertTrue(store.listNotes().isEmpty())
    }

    @Test
    fun `displayName is app-private storage`() {
        assertEquals("App-private storage", store.displayName())
    }

    @Test
    fun `writeNote commits via tmp rename and leaves no tmp or bak behind`() = runTest {
        val meta = store.createNoteWithContent("v1")
        store.writeNote(meta.uri, "v2 longer content")
        val file = java.io.File(java.net.URI(meta.uri))
        assertEquals("v2 longer content", file.readText())
        assertFalse(java.io.File(temp.root, "notes/.${meta.name}.tmp").exists())
        assertFalse(java.io.File(temp.root, "notes/.${meta.name}.bak").exists())
        assertEquals(1, store.listNotes().size)
    }

    @Test
    fun `writeNote creates the file when it does not exist yet`() = runTest {
        val missing = java.io.File(temp.root, "notes/20260101-000000-zzzz.md")
        store.writeNote(missing.toURI().toString(), "born")
        assertEquals("born", missing.readText())
    }

    @Test
    fun `writeNote failure leaves the original content intact`() = runTest {
        val meta = store.createNoteWithContent("original")
        val dir = java.io.File(temp.root, "notes")
        dir.setWritable(false)
        try {
            var threw = false
            try {
                store.writeNote(meta.uri, "replacement")
            } catch (e: Exception) {
                threw = true
            }
            assertTrue(threw)
            assertEquals("original", java.io.File(java.net.URI(meta.uri)).readText())
        } finally {
            dir.setWritable(true)
        }
    }

    @Test
    fun `listNotes heals a leftover bak from an interrupted write`() = runTest {
        store.createNoteWithContent("kept")
        val orphan = java.io.File(temp.root, "notes/.20260101-000000-aaaa.md.bak")
        orphan.writeText("last good content")
        val notes = store.listNotes()
        assertEquals(2, notes.size)
        val healed = java.io.File(temp.root, "notes/20260101-000000-aaaa.md")
        assertTrue(healed.isFile)
        assertEquals("last good content", healed.readText())
        assertFalse(orphan.exists())
    }

    @Test
    fun `listNotes drops a leftover tmp when the note exists`() = runTest {
        val meta = store.createNoteWithContent("current")
        java.io.File(temp.root, "notes/.${meta.name}.tmp").writeText("stale")
        val notes = store.listNotes()
        assertEquals(1, notes.size)
        assertEquals("current", java.io.File(java.net.URI(meta.uri)).readText())
        assertFalse(java.io.File(temp.root, "notes/.${meta.name}.tmp").exists())
    }
}
