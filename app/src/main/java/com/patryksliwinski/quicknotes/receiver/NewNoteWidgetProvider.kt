package com.patryksliwinski.quicknotes.receiver

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.patryksliwinski.quicknotes.R
import com.patryksliwinski.quicknotes.ui.MainActivity

class NewNoteWidgetProvider : BaseWidgetProvider() {

    override fun widgetLayout(): Int = R.layout.new_note_widget

    override fun attachClickIntents(context: Context, views: RemoteViews) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NEW_NOTE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        setActivityClick(context, views, R.id.widget_root, requestCode = 0, intent = intent)
    }
}
