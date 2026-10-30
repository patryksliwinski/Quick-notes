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
    fun `concurrent name generation is thread-safe and never corrupts names`() {
        val names = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val threads = (1..8).map {
            Thread {
                repeat(200) { names += newNoteFileName() }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1600, names.size)
        assertTrue(names.all { Regex("""\d{8}-\d{6}-[a-z0-9]{4}\.md""").matches(it) })
        assertTrue(names.toSet().size >= 1500)
    }

    @Test
    fun `deriveHead combines title and snippet derivation`() {
        val fields = deriveHead("x.md", "# Shopping\nbuy milk")
        assertEquals(HeadFields(title = "x", snippet = "buy milk"), fields)
    }

    @Test
    fun `deriveHead on empty head keeps the file name as title`() {
        assertEquals(
            HeadFields(title = "20250101-120000-abcd", snippet = ""),
            deriveHead("20250101-120000-abcd.md", ""),
        )
    }

    @Test
    fun `title is always the file name regardless of content`() {
        assertEquals("x", deriveTitle("x.md", "# Shopping\n\nbuy milk"))
        assertEquals("20250101-120000-abcd", deriveTitle("20250101-120000-abcd.md", ""))
        assertEquals("x", deriveTitle("x.md", "\n   \n## Ideas\nbody"))
        assertEquals("20250101-120000-abcd", deriveTitle("20250101-120000-abcd.md", "#   \nbody"))
        assertEquals("x", deriveTitle("x.md", "  ###   Deep  \nbody"))
        assertEquals("20250101-120000-abcd", deriveTitle("20250101-120000-abcd.md", "   "))
    }

    @Test
    fun `title ignores the head so the list never flips between file name and first line`() {
        assertEquals(deriveTitle("20260101-000000-abcd.md", ""), deriveTitle("20260101-000000-abcd.md", "# Fresh title\nbody"))
    }

    @Test
    fun `snippet takes first non-title line`() {
        assertEquals("buy milk", deriveSnippet("x.md", "# Shopping\nbuy milk"))
    }

    @Test
    fun `snippet is empty when there is no body line`() {
        assertEquals("", deriveSnippet("x.md", "# Shopping"))
    }

    @Test
    fun `snippet is empty for a one-line note without heading`() {
        assertEquals("", deriveSnippet("x.md", "Shopping"))
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
    fun `snippet is empty when every body line equals the title`() {
        assertEquals("", deriveSnippet("x.md", "Shopping\nShopping"))
        assertEquals("", deriveSnippet("x.md", "# Shopping\nShopping"))
    }
}
