package com.patryksliwinski.quicknotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteNamingTest {

    @Test
    fun `file name ends with md and has timestamp prefix`() {
        val name = newNoteFileName()
        assertTrue(name.endsWith(".md"))
        assertTrue(Regex("""\d{8}-\d{6}-[a-z0-9]{4}\.md""").matches(name))
    }

    @Test
    fun `two rapid creations produce different names`() {
        val names = (1..50).map { newNoteFileName() }.toSet()
        assertEquals(50, names.size)
    }

    @Test
    fun `title derives from first markdown heading`() {
        assertEquals("Shopping", deriveTitle("x.md", "# Shopping\n\nbuy milk"))
    }

    @Test
    fun `title falls back to file name when empty`() {
        assertEquals("20250101-120000-abcd", deriveTitle("20250101-120000-abcd.md", ""))
    }

    @Test
    fun `snippet takes first non-title line`() {
        assertEquals("buy milk", deriveSnippet("x.md", "# Shopping\nbuy milk"))
    }

    @Test
    fun `distinct names never equal`() {
        assertNotEquals(newNoteFileName(), newNoteFileName())
    }
}
