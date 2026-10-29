package com.patryksliwinski.quicknotes.receiver

import com.patryksliwinski.quicknotes.data.NoteMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `widgetRows maps title snippet and uri, limited to 5`() {
        val notes = (1..7).map { meta("n$it.md", it.toLong()) }
            .map { it.copy(snippet = "snippet ${it.name}") }
        val rows = widgetRows(notes, 5, "No notes yet")
        assertEquals(5, rows.size)
        assertEquals("n7", rows[0].title)
        assertEquals("snippet n7.md", rows[0].subtitle)
        assertEquals(notes.last().uri, rows[0].noteUri)
        assertEquals("n3", rows[4].title)
    }

    @Test
    fun `widgetRows pads with empty rows when fewer than limit`() {
        val rows = widgetRows(listOf(meta("a.md", 100)), 5, "No notes yet")
        assertEquals(5, rows.size)
        assertEquals("a", rows[0].title)
        assertEquals(4, rows.drop(1).count { it.title.isEmpty() && it.noteUri == null })
    }

    @Test
    fun `widgetRows empty list maps to empty-state row plus filler`() {
        val rows = widgetRows(emptyList(), 5, "No notes yet")
        assertEquals(5, rows.size)
        assertEquals(WidgetRow(title = "No notes yet", subtitle = "", noteUri = null), rows[0])
        assertTrue(rows.drop(1).all { it.title.isEmpty() && it.noteUri == null })
    }
}
