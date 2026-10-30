package com.patryksliwinski.quicknotes.receiver

import android.content.BroadcastReceiver
import android.content.Context
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.resolveNoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun recentNotes(notes: List<NoteMeta>, limit: Int): List<NoteMeta> {
    return notes.sortedByDescending { it.lastModified }.take(limit)
}

data class WidgetRow(
    val title: String,
    val subtitle: String,
    val noteUri: String?,
)

data class WidgetLayout(
    val visibleRows: Int,
    val showSubtitles: Boolean,
)

private const val WIDGET_VERTICAL_CHROME_DP = 20
private const val ROW_FULL_DP = 36
private const val ROW_TITLE_ONLY_DP = 22

fun widgetLayoutForHeight(heightDp: Int, maxRows: Int): WidgetLayout {
    val available = (heightDp - WIDGET_VERTICAL_CHROME_DP).coerceAtLeast(0)
    val fullRows = available / ROW_FULL_DP
    return if (fullRows >= 1) {
        WidgetLayout(visibleRows = fullRows.coerceAtMost(maxRows), showSubtitles = true)
    } else {
        WidgetLayout(
            visibleRows = (available / ROW_TITLE_ONLY_DP).coerceIn(1, maxRows),
            showSubtitles = false,
        )
    }
}

fun widgetHeightDp(options: android.os.Bundle, fallbackDp: Int): Int {
    val maxH = options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    val minH = options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    return when {
        maxH > 0 -> maxH
        minH > 0 -> minH
        else -> fallbackDp
    }
}

fun widgetRows(notes: List<NoteMeta>, limit: Int, emptyText: String): List<WidgetRow> {
    val top = recentNotes(notes, limit)
    if (top.isEmpty()) {
        return listOf(WidgetRow(title = emptyText, subtitle = "", noteUri = null)) +
            List(limit - 1) { WidgetRow(title = "", subtitle = "", noteUri = null) }
    }
    return top.map { WidgetRow(title = it.title, subtitle = it.snippet, noteUri = it.uri) } +
        List(limit - top.size) { WidgetRow(title = "", subtitle = "", noteUri = null) }
}

object WidgetNoteLoader {

    fun loadRecentNotes(
        context: Context,
        pendingResult: BroadcastReceiver.PendingResult?,
        limit: Int,
        onLoaded: (List<NoteMeta>) -> Unit,
    ) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val notes = try {
                withContext(Dispatchers.IO) {
                    recentNotes(resolveNoteStore(appContext).first.listNotes(), limit)
                }
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                try {
                    onLoaded(notes)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
