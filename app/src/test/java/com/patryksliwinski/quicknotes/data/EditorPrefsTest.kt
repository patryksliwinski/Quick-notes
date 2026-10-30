package com.patryksliwinski.quicknotes.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EditorPrefsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("quicknotes_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `font size defaults to 16 sp`() {
        assertEquals(16, EditorPrefs.loadFontSizeSp(context))
    }

    @Test
    fun `font size roundtrips`() {
        EditorPrefs.saveFontSizeSp(context, 20)
        assertEquals(20, EditorPrefs.loadFontSizeSp(context))
    }

    @Test
    fun `debounce defaults to 50 ms`() {
        assertEquals(50L, EditorPrefs.loadDebounceMs(context))
    }

    @Test
    fun `debounce roundtrips`() {
        EditorPrefs.saveDebounceMs(context, 1000L)
        assertEquals(1000L, EditorPrefs.loadDebounceMs(context))
    }

    @Test
    fun `theme defaults to system`() {
        assertEquals(AppTheme.SYSTEM, EditorPrefs.loadTheme(context))
    }

    @Test
    fun `theme roundtrips`() {
        EditorPrefs.saveTheme(context, AppTheme.DARK)
        assertEquals(AppTheme.DARK, EditorPrefs.loadTheme(context))
    }

    @Test
    fun `theme falls back to system on corrupt value`() {
        context.getSharedPreferences("quicknotes_prefs", Context.MODE_PRIVATE)
            .edit().putString("editor_theme", "not-a-theme").commit()
        assertEquals(AppTheme.SYSTEM, EditorPrefs.loadTheme(context))
    }

    @Test
    fun `options are the documented choices`() {
        assertEquals(listOf(12, 16, 20), EditorPrefs.fontSizeOptions)
        assertEquals(listOf(50L, 200L, 400L, 1000L), EditorPrefs.debounceOptions)
        assertEquals(listOf(AppTheme.SYSTEM, AppTheme.LIGHT, AppTheme.DARK), EditorPrefs.themeOptions)
    }
}
