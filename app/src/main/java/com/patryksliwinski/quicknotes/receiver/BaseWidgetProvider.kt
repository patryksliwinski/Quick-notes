package com.patryksliwinski.quicknotes.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.patryksliwinski.quicknotes.R

abstract class BaseWidgetProvider : AppWidgetProvider() {

    @LayoutRes
    abstract fun widgetLayout(): Int

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    open fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = buildViews(context)
        attachClickIntents(context, views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    protected fun buildViews(context: Context): RemoteViews {
        return RemoteViews(context.packageName, widgetLayout()).apply {
            setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_ripple)
        }
    }

    protected open fun attachClickIntents(context: Context, views: RemoteViews) {
    }

    protected fun activityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
    ): PendingIntent {
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    protected fun setActivityClick(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        requestCode: Int,
        intent: Intent,
    ) {
        views.setOnClickPendingIntent(
            viewId,
            activityPendingIntent(context, requestCode, intent),
        )
    }
}
