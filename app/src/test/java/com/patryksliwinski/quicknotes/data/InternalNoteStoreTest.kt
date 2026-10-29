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
    fun `listNotes derives title and snippet from file head`() = runTest {
        store.restoreNote("20250101-120000-abcd.md", "# Shopping\nbuy milk")
        val notes = store.listNotes()
        assertEquals(1, notes.size)
        assertEquals("Shopping", notes[0].title)
        assertEquals("buy milk", notes[0].snippet)
    }

    @Test
    fun `listNotes on missing dir returns empty`() = runTest {
        val empty = InternalNoteStore(java.io.File(temp.root, "does-not-exist-yet"))
        assertEquals(emptyList<NoteMeta>(), empty.listNotes())
    }

    @Test
    fun `listNotes reads only the first 4096 bytes for head`() = runTest {
        val big = "# Title\n" + "x".repeat(10000)
        store.restoreNote("big.md", big)
        val notes = store.listNotes()
        assertEquals("Title", notes[0].title)
        assertTrue(notes[0].snippet.length <= 80)
    }

    @Test
    fun `listNotes handles file that disappears mid-scan`() = runTest {
        val meta = store.createNote()
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
}
