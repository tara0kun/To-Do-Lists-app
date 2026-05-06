package com.example.todolists.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.todolists.R

/**
 * Common entry points for refreshing the V2 (traditional) widgets.
 *
 * Routine task data changes (add/edit/delete/toggle) → [notifyDataChanged].
 * The launcher only re-fetches the list items via the RemoteViewsService;
 * the surrounding chrome is unchanged. This is the path that gives the
 * Google-Tasks-style instant feel.
 *
 * Settings changes that affect the whole widget (background image, scrim,
 * theme) → [forceFullUpdate]. We send ACTION_APPWIDGET_UPDATE so the
 * provider's onUpdate rebuilds RemoteViews from scratch.
 */
object WidgetUpdateHelper {

    /** Widget provider classes that use the V2 (RemoteViewsService) path. */
    private val v2Providers: List<Class<*>> = listOf(
        SimpleListWidgetReceiver::class.java,
        AllTasksWidgetReceiver::class.java,
        OverdueWidgetReceiver::class.java,
        CompletedWidgetReceiver::class.java,
    )

    fun notifyDataChanged(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        v2Providers.forEach { cls ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }
    }

    /**
     * Sends ACTION_APPWIDGET_UPDATE to every widget receiver. Each
     * provider's onUpdate rebuilds RemoteViews from scratch — used when
     * something outside the task list changes (e.g. the background
     * image / scrim / fit settings).
     */
    fun forceFullUpdate(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        v2Providers.forEach { cls ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
