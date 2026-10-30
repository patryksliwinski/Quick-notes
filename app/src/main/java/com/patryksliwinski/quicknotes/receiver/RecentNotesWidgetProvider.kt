package com.patryksliwinski.quicknotes.receiver

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.patryksliwinski.quicknotes.R
import com.patryksliwinski.quicknotes.data.NoteMeta
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
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id, notes)
            }
        }
    }

    override fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val pendingResult = goAsync()
        WidgetNoteLoader.loadRecentNotes(context, pendingResult, MAX_NOTES) { notes ->
            updateWidget(context, appWidgetManager, appWidgetId, notes)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        notes: List<NoteMeta>,
    ) {
        val heightDp = widgetHeightDp(
            appWidgetManager.getAppWidgetOptions(appWidgetId), DEFAULT_HEIGHT_DP
        )
        val layout = widgetLayoutForHeight(heightDp, MAX_NOTES)
        val rows = widgetRows(notes, layout.visibleRows, context.getString(R.string.widget_no_notes))
        val views = buildViews(context)
        bindRows(context, views, rows, layout)
        attachClickIntents(context, views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun attachClickIntents(context: Context, views: RemoteViews) {
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        setActivityClick(context, views, R.id.widget_header, requestCode = 0, intent = openApp)
    }

    private fun bindRows(
        context: Context,
        views: RemoteViews,
        rows: List<WidgetRow>,
        layout: WidgetLayout,
    ) {
        for (i in ROW_IDS.indices) {
            val visible = i < rows.size
            views.setViewVisibility(ROW_IDS[i], if (visible) View.VISIBLE else View.GONE)
            val row = rows.getOrElse(i) { WidgetRow(title = "", subtitle = "", noteUri = null) }
            views.setTextViewText(TITLE_IDS[i], row.title)
            views.setTextViewText(SUBTITLE_IDS[i], row.subtitle)
            if (!visible) {
                views.setViewVisibility(SUBTITLE_IDS[i], View.GONE)
                continue
            }
            val showSubtitle = layout.showSubtitles && row.subtitle.isNotEmpty()
            views.setViewVisibility(SUBTITLE_IDS[i], if (showSubtitle) View.VISIBLE else View.GONE)
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
        private const val DEFAULT_HEIGHT_DP = 180

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
