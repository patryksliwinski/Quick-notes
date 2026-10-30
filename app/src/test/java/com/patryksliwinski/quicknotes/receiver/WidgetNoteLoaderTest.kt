package com.patryksliwinski.quicknotes.receiver

import com.patryksliwinski.quicknotes.data.NoteMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WidgetNoteLoaderTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun meta(name: String, lastModified: Long, snippet: String = "") = NoteMeta(
        uri = "file:///notes/$name",
        name = name,
        lastModified = lastModified,
        title = name.removeSuffix(".md"),
        snippet = snippet,
    )

    @Test
    fun `loadRecentNotes returns the newest notes with derived titles and finishes the pending result`() {
        context.filesDir.resolve("notes").deleteRecursively()
        runBlocking {
            val store = com.patryksliwinski.quicknotes.data.InternalNoteStore(context.filesDir.resolve("notes"))
            store.createNoteWithContent("# First\nbody one")
            store.createNoteWithContent("# Second\nbody two")
        }
        var loaded: List<NoteMeta>? = null
        WidgetNoteLoader.loadRecentNotes(context, null, limit = 5) { loaded = it }
        val deadline = System.currentTimeMillis() + 5000
        while (loaded == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(2, loaded!!.size)
        assertEquals(setOf("First", "Second"), loaded!!.map { it.title }.toSet())
    }

    @Test
    fun `loadRecentNotes returns empty when the store listing throws`() {
        context.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(
                "folder_uri",
                "content://com.android.externalstorage.documents/tree/XXXX-XXXX%3ANotes",
            )
            .commit()
        var loaded: List<NoteMeta>? = null
        WidgetNoteLoader.loadRecentNotes(context, null, limit = 5) { loaded = it }
        val deadline = System.currentTimeMillis() + 5000
        while (loaded == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(loaded!!.isEmpty())
        context.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
