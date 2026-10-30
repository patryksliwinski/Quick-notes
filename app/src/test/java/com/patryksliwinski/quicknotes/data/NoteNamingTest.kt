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
    fun `distinct names never equal`() {
        assertNotEquals(newNoteFileName(), newNoteFileName())
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
    fun `title skips blank lines before first heading`() {
        assertEquals("Ideas", deriveTitle("x.md", "\n   \n## Ideas\nbody"))
    }

    @Test
    fun `title falls back to file name when heading is empty`() {
        assertEquals("20250101-120000-abcd", deriveTitle("20250101-120000-abcd.md", "#   \nbody"))
    }

    @Test
    fun `title strips multiple leading hashes and whitespace`() {
        assertEquals("Deep", deriveTitle("x.md", "  ###   Deep  \nbody"))
    }

    @Test
    fun `title falls back to file name for blank head`() {
        assertEquals("20250101-120000-abcd", deriveTitle("20250101-120000-abcd.md", "   "))
    }

    @Test
    fun `snippet takes first non-title line`() {
        assertEquals("buy milk", deriveSnippet("x.md", "# Shopping\nbuy milk"))
    }

    @Test
    fun `snippet falls back to title line when there is no body`() {
        assertEquals("Shopping", deriveSnippet("x.md", "# Shopping"))
    }

    @Test
    fun `snippet is empty when head is empty`() {
        assertEquals("", deriveSnippet("x.md", ""))
    }

    @Test
    fun `snippet is truncated to 80 chars`() {
        val long = "a".repeat(200)
        assertEquals(80, deriveSnippet("x.md", "t\n$long").length)
    }

    @Test
    fun `snippet skips blank lines`() {
        assertEquals("second", deriveSnippet("x.md", "# T\n\n\nsecond"))
    }

    @Test
    fun `snippet prefers body line even when it equals raw title line`() {
        assertEquals("Shopping", deriveSnippet("x.md", "Shopping\nShopping"))
    }
}
