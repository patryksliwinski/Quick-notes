package com.patryksliwinski.quicknotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchTest {

    private fun meta(name: String, title: String, snippet: String) = NoteMeta(
        uri = "file:///notes/$name",
        name = name,
        lastModified = 0L,
        title = title,
        snippet = snippet,
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
    fun `toSearchEntries lowercases title snippet and name once per list`() {
        val entries = listOf(meta("X.MD", "MiXeD", "Snippet")).toSearchEntries()
        assertEquals("mixed", entries[0].titleLower)
        assertEquals("snippet", entries[0].snippetLower)
        assertEquals("x.md", entries[0].nameLower)
    }
}
