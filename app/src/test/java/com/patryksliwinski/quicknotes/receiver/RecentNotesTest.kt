package com.patryksliwinski.quicknotes.receiver

import com.patryksliwinski.quicknotes.data.NoteMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentNotesTest {

    private fun meta(name: String, lastModified: Long) = NoteMeta(
        uri = "file:///notes/$name",
        name = name,
        lastModified = lastModified,
        title = name.removeSuffix(".md"),
        snippet = "",
    )

    @Test
    fun `recentNotes sorts by lastModified descending and limits`() {
        val notes = listOf(
            meta("a.md", 100),
            meta("b.md", 300),
            meta("c.md", 200),
        )
        val result = recentNotes(notes, 2)
        assertEquals(listOf("b.md", "c.md"), result.map { it.name })
    }

    @Test
    fun `recentNotes with limit larger than list returns all sorted`() {
        val notes = listOf(meta("a.md", 100), meta("b.md", 300), meta("c.md", 200))
        assertEquals(3, recentNotes(notes, 10).size)
    }

    @Test
    fun `recentNotes of empty list is empty`() {
        assertEquals(emptyList<NoteMeta>(), recentNotes(emptyList(), 5))
    }

    @Test
    fun `recentNotes does not mutate input order`() {
        val notes = listOf(meta("a.md", 100), meta("b.md", 300))
        recentNotes(notes, 5)
        assertEquals(listOf("a.md", "b.md"), notes.map { it.name })
    }
}
