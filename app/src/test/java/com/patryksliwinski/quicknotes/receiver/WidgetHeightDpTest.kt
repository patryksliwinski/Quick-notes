package com.patryksliwinski.quicknotes.receiver

import android.appwidget.AppWidgetManager
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetHeightDpTest {

    @Test
    fun `prefers max height when present`() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        }
        assertEquals(180, widgetHeightDp(options, 999))
    }

    @Test
    fun `falls back to min height when max missing`() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        }
        assertEquals(110, widgetHeightDp(options, 999))
    }

    @Test
    fun `falls back to provided default when neither bound present`() {
        assertEquals(999, widgetHeightDp(Bundle(), 999))
    }
}
