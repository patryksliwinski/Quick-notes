package com.patryksliwinski.quicknotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NoteListLogicTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        set(year, month - 1, day, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun note(name: String, lastModified: Long, title: String = name, snippet: String = ""): NoteMeta =
        NoteMeta(uri = "file:///notes/$name", name = name, lastModified = lastModified, title = title, snippet = snippet)

    @Test
    fun `sort by last modified is descending`() {
        val a = note("20260101-000000-aaaa.md", 1000)
        val b = note("20260102-000000-bbbb.md", 3000)
        val c = note("20260103-000000-cccc.md", 2000)
        assertEquals(listOf(b, c, a), sortNotes(listOf(a, b, c), NoteSortOrder.LAST_MODIFIED))
    }

    @Test
    fun `sort by title is case-insensitive ascending`() {
        val a = note("20260101-000000-aaaa.md", 3000, title = "banana")
        val b = note("20260102-000000-bbbb.md", 2000, title = "Apple")
        val c = note("20260103-000000-cccc.md", 1000, title = "cherry")
        assertEquals(listOf(b, a, c), sortNotes(listOf(a, b, c), NoteSortOrder.TITLE))
    }

    @Test
    fun `sort by filename date is descending lexicographic`() {
        val a = note("20260101-000000-aaaa.md", 3000)
        val b = note("20260103-235959-zzzz.md", 1000)
        val c = note("20260102-120000-mmmm.md", 2000)
        assertEquals(listOf(b, c, a), sortNotes(listOf(a, b, c), NoteSortOrder.FILENAME_DATE))
    }

    @Test
    fun `filter blank query returns all`() {
        val notes = listOf(note("a.md", 1), note("b.md", 2))
        assertEquals(notes, filterNotes(notes, ""))
        assertEquals(notes, filterNotes(notes, "   "))
    }

    @Test
    fun `filter matches title and snippet case-insensitively`() {
        val alpha = note("20260101-000000-aaaa.md", 1, title = "Alpha", snippet = "first")
        val beta = note("20260102-000000-bbbb.md", 2, title = "Beta", snippet = "unique-snippet")
        assertEquals(listOf(alpha), filterNotes(listOf(alpha, beta), "ALPHA"))
        assertEquals(listOf(beta), filterNotes(listOf(alpha, beta), "Unique"))
    }

    @Test
    fun `filter matches filename`() {
        val a = note("20260101-000000-aaaa.md", 1, title = "Alpha")
        val b = note("20260615-120000-zzzz.md", 2, title = "Beta")
        assertEquals(listOf(b), filterNotes(listOf(a, b), "20260615"))
        assertEquals(listOf(b), filterNotes(listOf(a, b), "ZZZZ.MD"))
        assertTrue(filterNotes(listOf(a, b), "no-such-file").isEmpty())
    }

    @Test
    fun `dayKeyOf maps today yesterday and older dates`() {
        val now = at(2026, 10, 30, 15)
        assertEquals(DAY_KEY_TODAY, dayKeyOf(at(2026, 10, 30, 0), now))
        assertEquals(DAY_KEY_TODAY, dayKeyOf(at(2026, 10, 30, 23), now))
        assertEquals(DAY_KEY_YESTERDAY, dayKeyOf(at(2026, 10, 29, 23), now))
        assertEquals("2026-10-28", dayKeyOf(at(2026, 10, 28, 12), now))
        assertEquals("2025-12-31", dayKeyOf(at(2025, 12, 31, 12), now))
    }

    @Test
    fun `groupByDay inserts one header per consecutive day group`() {
        val now = at(2026, 10, 30, 15)
        val today1 = note("20261030-100000-aaaa.md", at(2026, 10, 30, 10))
        val today2 = note("20261030-090000-bbbb.md", at(2026, 10, 30, 9))
        val yesterday = note("20261029-090000-cccc.md", at(2026, 10, 29, 9))
        val older = note("20261028-090000-dddd.md", at(2026, 10, 28, 9))
        val rows = groupByDay(listOf(today1, today2, yesterday, older), now)
        assertEquals(
            listOf(
                NoteListRow.Header(DAY_KEY_TODAY),
                NoteListRow.Note(today1),
                NoteListRow.Note(today2),
                NoteListRow.Header(DAY_KEY_YESTERDAY),
                NoteListRow.Note(yesterday),
                NoteListRow.Header("2026-10-28"),
                NoteListRow.Note(older),
            ),
            rows,
        )
    }

    @Test
    fun `groupByDay on empty list is empty`() {
        assertTrue(groupByDay(emptyList()).isEmpty())
    }

    @Test
    fun `groupByDay merges non-consecutive notes of the same day under one header`() {
        val now = at(2026, 10, 30, 15)
        val today = note("20261030-100000-aaaa.md", at(2026, 10, 30, 10))
        val yesterday = note("20261029-090000-cccc.md", at(2026, 10, 29, 9))
        val today2 = note("20261030-110000-eeee.md", at(2026, 10, 30, 11))
        val rows = groupByDay(listOf(today, yesterday, today2), now)
        assertEquals(
            listOf(
                NoteListRow.Header(DAY_KEY_TODAY),
                NoteListRow.Note(today),
                NoteListRow.Note(today2),
                NoteListRow.Header(DAY_KEY_YESTERDAY),
                NoteListRow.Note(yesterday),
            ),
            rows,
        )
    }
}
