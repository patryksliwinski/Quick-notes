package com.patryksliwinski.quicknotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ListPrefsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `sort order defaults to last modified`() {
        assertEquals(NoteSortOrder.LAST_MODIFIED, ListPrefs.loadSortOrder(context))
    }

    @Test
    fun `save then load sort order roundtrips`() {
        ListPrefs.saveSortOrder(context, NoteSortOrder.TITLE)
        assertEquals(NoteSortOrder.TITLE, ListPrefs.loadSortOrder(context))
        ListPrefs.saveSortOrder(context, NoteSortOrder.FILENAME_DATE)
        assertEquals(NoteSortOrder.FILENAME_DATE, ListPrefs.loadSortOrder(context))
    }

    @Test
    fun `unknown stored sort order falls back to last modified`() {
        context.getSharedPreferences("quicknotes_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("sort_order", "not-a-real-order").commit()
        assertEquals(NoteSortOrder.LAST_MODIFIED, ListPrefs.loadSortOrder(context))
    }

    @Test
    fun `confirm delete defaults to false`() {
        assertFalse(ListPrefs.loadConfirmDelete(context))
    }

    @Test
    fun `save then load confirm delete roundtrips`() {
        ListPrefs.saveConfirmDelete(context, true)
        assertTrue(ListPrefs.loadConfirmDelete(context))
        ListPrefs.saveConfirmDelete(context, false)
        assertFalse(ListPrefs.loadConfirmDelete(context))
    }
}
