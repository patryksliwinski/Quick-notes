package com.patryksliwinski.quicknotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PendingSaveJournalTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var journal: PendingSaveJournal

    @Before
    fun setUp() {
        context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        journal = PendingSaveJournal(context)
    }

    @Test
    fun `pending is empty when nothing recorded`() {
        assertTrue(journal.pending().isEmpty())
    }

    @Test
    fun `record then pending returns the uri and content`() {
        journal.record("file:///notes/a.md", "# Title\nhello")
        val pending = journal.pending()
        assertEquals(1, pending.size)
        assertEquals(PendingSave("file:///notes/a.md", "# Title\nhello"), pending[0])
    }

    @Test
    fun `record overwrites the previous entry for the same uri`() {
        journal.record("file:///notes/a.md", "first")
        journal.record("file:///notes/a.md", "second")
        val pending = journal.pending()
        assertEquals(1, pending.size)
        assertEquals("second", pending[0].content)
    }

    @Test
    fun `clear removes only that uri`() {
        journal.record("file:///notes/a.md", "a")
        journal.record("file:///notes/b.md", "b")
        journal.clear("file:///notes/a.md")
        val pending = journal.pending()
        assertEquals(listOf(PendingSave("file:///notes/b.md", "b")), pending)
    }

    @Test
    fun `content with quotes and newlines roundtrips`() {
        val tricky = "line1\n\"quoted\" \\ backslash \u00e9\u4e2d\n"
        journal.record("file:///notes/t.md", tricky)
        assertEquals(tricky, journal.pending().single().content)
    }

    @Test
    fun `corrupt entries are skipped`() {
        context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE)
            .edit().putString("file:///notes/bad.md", "not-json").commit()
        journal.record("file:///notes/ok.md", "ok")
        val pending = journal.pending()
        assertEquals(listOf(PendingSave("file:///notes/ok.md", "ok")), pending)
    }

    @Test
    fun `record is synchronous so a process kill cannot lose the journaled content`() {
        val recordingPrefs = object : android.content.SharedPreferences by
            context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE) {
            var committed = false
            override fun edit(): android.content.SharedPreferences.Editor {
                val delegate = super.edit()
                return object : android.content.SharedPreferences.Editor by delegate {
                    override fun commit(): Boolean {
                        committed = true
                        return delegate.commit()
                    }
                    override fun apply() {
                        throw AssertionError("record must not use async apply() on the durability path")
                    }
                }
            }
        }
        val durableJournal = PendingSaveJournal(recordingPrefs)
        durableJournal.record("file:///notes/a.md", "typed before kill")
        assertTrue(recordingPrefs.getString("file:///notes/a.md", null) != null)
        assertEquals("typed before kill", durableJournal.pending().single().content)
    }

    @Test
    fun `clear stays asynchronous`() {
        val recordingPrefs = object : android.content.SharedPreferences by
            context.getSharedPreferences("quicknotes_pending_saves", android.content.Context.MODE_PRIVATE) {
            var applyCalled = false
            override fun edit(): android.content.SharedPreferences.Editor {
                val delegate = super.edit()
                return object : android.content.SharedPreferences.Editor by delegate {
                    override fun commit(): Boolean {
                        throw AssertionError("clear may stay async and must not use commit()")
                    }
                    override fun apply() {
                        applyCalled = true
                        delegate.apply()
                    }
                }
            }
        }
        val journal = PendingSaveJournal(recordingPrefs)
        recordingPrefs.edit().putString("file:///notes/a.md", "{\"content\":\"x\"}").commit()
        journal.clear("file:///notes/a.md")
        assertTrue(applyCalled)
        assertTrue(journal.pending().isEmpty())
    }
}
