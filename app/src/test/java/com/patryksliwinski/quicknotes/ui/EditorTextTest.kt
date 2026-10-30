package com.patryksliwinski.quicknotes.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class EditorTextTest {

    @Test
    fun `computeStats of empty string is zero`() {
        assertEquals(TextStats(0, 0), computeStats(""))
    }

    @Test
    fun `computeStats of blank string is zero words`() {
        assertEquals(TextStats(0, 5), computeStats("  \n\t "))
    }

    @Test
    fun `computeStats counts whitespace-separated words`() {
        val stats = computeStats("one two  three\nfour")
        assertEquals(4, stats.words)
        assertEquals(21, stats.characters)
    }

    @Test
    fun `computeStats counts characters including newlines`() {
        val stats = computeStats("a\nb")
        assertEquals(2, stats.words)
        assertEquals(3, stats.characters)
    }

    @Test
    fun `timestampLine formats date as yyyy-MM-dd HH:mm`() {
        val date = Date(0L) // 1970-01-01 00:00 UTC; local tz shifts the hour
        val line = timestampLine(date)
        assertEquals(16, line.length)
        assertEquals('-', line[4])
        assertEquals(':', line[13])
    }

    @Test
    fun `appendTimestampLine appends line to empty content`() {
        assertEquals("2026-10-30 12:34\n", appendTimestampLine("", "2026-10-30 12:34"))
    }

    @Test
    fun `appendTimestampLine adds newline when content lacks trailing newline`() {
        assertEquals("hello\n2026-10-30 12:34\n", appendTimestampLine("hello", "2026-10-30 12:34"))
    }

    @Test
    fun `appendTimestampLine does not double newline when content ends with one`() {
        assertEquals("hello\n2026-10-30 12:34\n", appendTimestampLine("hello\n", "2026-10-30 12:34"))
    }
}
