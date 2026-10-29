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
