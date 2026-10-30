package com.patryksliwinski.quicknotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchTest {

    private fun meta(name: String, title: String, snippet: String, content: String = "") = NoteMeta(
        uri = "file:///notes/$name",
        name = name,
        lastModified = 0L,
        title = title,
        snippet = snippet,
        content = content,
    )

    @Test
    fun `blank query returns all notes`() {
        val notes = listOf(meta("a.md", "Alpha", "one"), meta("b.md", "Beta", "two"))
        assertEquals(2, notes.toSearchEntries().let { filterNotesPrecomputed(it, "") }.size)
        assertEquals(2, notes.toSearchEntries().let { filterNotesPrecomputed(it, "   ") }.size)
    }

    @Test
    fun `query matches precomputed lowercase title snippet and filename`() {
        val alpha = meta("20260101-000000-aaaa.md", "Alpha", "one")
        val beta = meta("20260615-120000-zzzz.md", "Beta", "UNIQUE-SNIPPET")
        val entries = listOf(alpha, beta).toSearchEntries()
        assertEquals(listOf(alpha), filterNotesPrecomputed(entries, "alpha"))
        assertEquals(listOf(beta), filterNotesPrecomputed(entries, "unique-snippet"))
        assertEquals(listOf(beta), filterNotesPrecomputed(entries, "ZZZZ.MD"))
        assertTrue(filterNotesPrecomputed(entries, "no-match").isEmpty())
    }

    @Test
    fun `toSearchEntries lowercases title snippet name and content once per list`() {
        val entries = listOf(meta("X.MD", "MiXeD", "Snippet", "BoDy CoNtEnT")).toSearchEntries()
        assertEquals("mixed", entries[0].titleLower)
        assertEquals("snippet", entries[0].snippetLower)
        assertEquals("x.md", entries[0].nameLower)
        assertEquals("body content", entries[0].contentLower)
    }

    @Test
    fun `query matches the full note content beyond the snippet`() {
        val alpha = meta("20260101-000000-aaaa.md", "Alpha", "one", "# Alpha\none\ndeep body keyword")
        val beta = meta("20260615-120000-zzzz.md", "Beta", "two", "# Beta\ntwo")
        val entries = listOf(alpha, beta).toSearchEntries()
        assertEquals(listOf(alpha), filterNotesPrecomputed(entries, "DEEP BODY"))
        assertTrue(filterNotesPrecomputed(entries, "absent-word").isEmpty())
    }

    @Test
    fun `query matches content beyond the 4 KiB head and the 80 char snippet cap`() {
        val deep = "x".repeat(HEAD_BYTES + 100) + " needle-in-the-deep"
        val alpha = meta("a.md", "Alpha", "one", deep)
        val entries = listOf(alpha).toSearchEntries()
        assertEquals(listOf(alpha), filterNotesPrecomputed(entries, "needle-in-the-deep"))
    }

    @Test
    fun `note with empty content still matches title and filename`() {
        val alpha = meta("20260101-000000-aaaa.md", "Alpha", "")
        val entries = listOf(alpha).toSearchEntries()
        assertEquals(listOf(alpha), filterNotesPrecomputed(entries, "alpha"))
        assertEquals(listOf(alpha), filterNotesPrecomputed(entries, "AAAA.MD"))
        assertTrue(filterNotesPrecomputed(entries, "body").isEmpty())
    }
}
