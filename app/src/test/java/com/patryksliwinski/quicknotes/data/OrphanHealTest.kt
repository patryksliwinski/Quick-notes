package com.patryksliwinski.quicknotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OrphanHealTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `orphanNameOf maps tmp and bak names back to the note name`() {
        assertEquals("20260130-101010-abcd.md", orphanNameOf(".20260130-101010-abcd.md.tmp"))
        assertEquals("20260130-101010-abcd.md", orphanNameOf(".20260130-101010-abcd.md.bak"))
    }

    @Test
    fun `orphanNameOf returns null for regular and unrelated dotfiles`() {
        assertNull(orphanNameOf("20260130-101010-abcd.md"))
        assertNull(orphanNameOf(".hidden.md"))
        assertNull(orphanNameOf("..tmp"))
        assertNull(orphanNameOf("readme.txt"))
    }

    @Test
    fun `heal deletes tmp orphan when the note exists`() {
        val dir = temp.newFolder("notes")
        java.io.File(dir, "a.md").writeText("current")
        java.io.File(dir, ".a.md.tmp").writeText("newer")
        healOrphanedNoteFiles(dir)
        assertTrue(java.io.File(dir, "a.md").exists())
        assertEquals("current", java.io.File(dir, "a.md").readText())
        assertFalse(java.io.File(dir, ".a.md.tmp").exists())
    }

    @Test
    fun `heal restores bak orphan when the note is missing`() {
        val dir = temp.newFolder("notes")
        java.io.File(dir, ".b.md.bak").writeText("last good")
        healOrphanedNoteFiles(dir)
        assertTrue(java.io.File(dir, "b.md").exists())
        assertEquals("last good", java.io.File(dir, "b.md").readText())
        assertFalse(java.io.File(dir, ".b.md.bak").exists())
    }

    @Test
    fun `heal deletes bak orphan when the note already exists`() {
        val dir = temp.newFolder("notes")
        java.io.File(dir, "c.md").writeText("current")
        java.io.File(dir, ".c.md.bak").writeText("older")
        healOrphanedNoteFiles(dir)
        assertEquals("current", java.io.File(dir, "c.md").readText())
        assertFalse(java.io.File(dir, ".c.md.bak").exists())
    }

    @Test
    fun `heal deletes tmp orphan when the note is missing`() {
        val dir = temp.newFolder("notes")
        java.io.File(dir, ".d.md.tmp").writeText("partial")
        healOrphanedNoteFiles(dir)
        assertFalse(java.io.File(dir, ".d.md.tmp").exists())
        assertFalse(java.io.File(dir, "d.md").exists())
    }

    @Test
    fun `heal leaves regular notes and unrelated dotfiles alone`() {
        val dir = temp.newFolder("notes")
        java.io.File(dir, "e.md").writeText("keep me")
        java.io.File(dir, ".other-tool-marker").writeText("not ours")
        healOrphanedNoteFiles(dir)
        assertEquals("keep me", java.io.File(dir, "e.md").readText())
        assertTrue(java.io.File(dir, ".other-tool-marker").exists())
    }

    @Test
    fun `heal ignores orphans whose base name is not a note`() {
        val dir = temp.newFolder("notes")
        java.io.File(dir, ".readme.txt.tmp").writeText("junk")
        healOrphanedNoteFiles(dir)
        assertTrue(java.io.File(dir, ".readme.txt.tmp").exists())
    }
}
