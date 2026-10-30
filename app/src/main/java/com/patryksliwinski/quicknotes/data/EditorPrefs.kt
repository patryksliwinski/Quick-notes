package com.patryksliwinski.quicknotes.data

import android.content.Context

enum class AppTheme { SYSTEM, LIGHT, DARK }

object EditorPrefs {
    private const val PREFS = "quicknotes_prefs"
    private const val KEY_FONT_SIZE = "editor_font_size_sp"
    private const val KEY_DEBOUNCE_MS = "editor_autosave_debounce_ms"
    private const val KEY_THEME = "editor_theme"

    const val DEFAULT_FONT_SIZE_SP = 16
    const val DEFAULT_DEBOUNCE_MS = 50L

    val fontSizeOptions = listOf(12, 16, 20)
    val debounceOptions = listOf(50L, 200L, 400L, 1000L)
    val themeOptions = AppTheme.entries.toList()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadFontSizeSp(context: Context): Int =
        prefs(context).getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE_SP)

    fun saveFontSizeSp(context: Context, sp: Int) {
        prefs(context).edit().putInt(KEY_FONT_SIZE, sp).apply()
    }

    fun loadDebounceMs(context: Context): Long =
        prefs(context).getLong(KEY_DEBOUNCE_MS, DEFAULT_DEBOUNCE_MS)

    fun saveDebounceMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_DEBOUNCE_MS, ms).apply()
    }

    fun loadTheme(context: Context): AppTheme =
        runCatching { AppTheme.valueOf(prefs(context).getString(KEY_THEME, null) ?: AppTheme.SYSTEM.name) }
            .getOrElse { AppTheme.SYSTEM }

    fun saveTheme(context: Context, theme: AppTheme) {
        prefs(context).edit().putString(KEY_THEME, theme.name).apply()
    }
}
