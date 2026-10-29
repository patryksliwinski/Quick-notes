package com.patryksliwinski.quicknotes.receiver

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.patryksliwinski.quicknotes.R
import com.patryksliwinski.quicknotes.ui.MainActivity

class RecentNotesWidgetProvider : BaseWidgetProvider() {

    override fun widgetLayout(): Int = R.layout.recent_notes_widget

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RecentNotesWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        WidgetNoteLoader.loadRecentNotes(context, pendingResult, MAX_NOTES) { notes ->
            val rows = widgetRows(notes, MAX_NOTES, context.getString(R.string.widget_no_notes))
            for (id in appWidgetIds) {
                val views = buildViews(context)
                bindRows(context, views, rows)
                attachClickIntents(context, views)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    override fun attachClickIntents(context: Context, views: RemoteViews) {
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        setActivityClick(context, views, R.id.widget_header, requestCode = 0, intent = openApp)
    }

    private fun bindRows(context: Context, views: RemoteViews, rows: List<WidgetRow>) {
        for (i in rows.indices) {
            val row = rows[i]
            views.setTextViewText(TITLE_IDS[i], row.title)
            views.setTextViewText(SUBTITLE_IDS[i], row.subtitle)
            views.setViewVisibility(
                SUBTITLE_IDS[i],
                if (row.subtitle.isEmpty()) View.GONE else View.VISIBLE,
            )
            val uri = row.noteUri ?: continue
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOTE
                putExtra(MainActivity.EXTRA_NOTE_URI, uri)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            setActivityClick(context, views, ROW_IDS[i], requestCode = i + 1, intent = intent)
        }
    }

    companion object {
        const val MAX_NOTES = 5
        const val ACTION_REFRESH = "com.patryksliwinski.quicknotes.action.RECENT_NOTES_REFRESH"

        private val ROW_IDS = intArrayOf(
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
            R.id.widget_row_4, R.id.widget_row_5,
        )
        private val TITLE_IDS = intArrayOf(
            R.id.widget_row_1_title, R.id.widget_row_2_title, R.id.widget_row_3_title,
            R.id.widget_row_4_title, R.id.widget_row_5_title,
        )
        private val SUBTITLE_IDS = intArrayOf(
            R.id.widget_row_1_subtitle, R.id.widget_row_2_subtitle, R.id.widget_row_3_subtitle,
            R.id.widget_row_4_subtitle, R.id.widget_row_5_subtitle,
        )
    }
}
