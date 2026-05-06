package com.example.todolists.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.todolists.MainActivity
import com.example.todolists.R
import com.example.todolists.ui.TaskTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * V2 implementation of the simple-list widget. Built on top of the
 * traditional [AppWidgetProvider] + [SimpleListService] (RemoteViewsService)
 * pipeline so that list contents can update incrementally via
 * notifyAppWidgetViewDataChanged — that's the only way to get
 * Google-Tasks-style instant reflection.
 *
 * onUpdate is only called when the widget is added, resized, or its
 * surrounding chrome (background image, etc.) needs to change. Routine
 * task changes flow through [WidgetUpdateHelper.notifyDataChanged] and
 * never re-run onUpdate.
 */
class SimpleListWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
        val views = buildBaseViews(context, id)
        mgr.updateAppWidget(id, views)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)

        // Background image is optional and decoding it is too slow for
        // the main thread; load it asynchronously and re-push the views
        // once the bitmap is ready.
        WidgetWorkScope.launch {
            val bg = WidgetBackgroundLoader.load(context)
            if (bg != null) {
                val withBg = buildBaseViews(context, id).apply {
                    applyBackground(this, context, bg)
                }
                withContext(Dispatchers.Main) {
                    mgr.updateAppWidget(id, withBg)
                }
            }
        }
    }

    private fun buildBaseViews(context: Context, id: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_root)

        views.setTextViewText(R.id.widget_title, "簡易リスト")
        views.setTextColor(R.id.widget_title, context.getColor(R.color.widget_text_primary))
        views.setTextViewText(R.id.widget_empty, "簡易リストは空です")
        views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_text_secondary))

        // Title tap → open the app on the simple tab.
        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(
                context,
                ("open-simple-$id").hashCode(),
                MainActivity.intentForTab(context, TaskTab.SIMPLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        // Refresh button → re-broadcast our own ACTION_APPWIDGET_UPDATE.
        views.setOnClickPendingIntent(
            R.id.widget_btn_refresh,
            PendingIntent.getBroadcast(
                context,
                ("refresh-simple-$id").hashCode(),
                Intent(context, SimpleListWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        // Add button → QuickAddActivity.
        views.setOnClickPendingIntent(
            R.id.widget_btn_add,
            PendingIntent.getActivity(
                context,
                ("add-simple-$id").hashCode(),
                QuickAddActivity.intent(context, simple = true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        // List backed by RemoteViewsService. The data URI is unique per
        // widget id so each instance gets its own factory; the system
        // also fills in EXTRA_APPWIDGET_ID on the intent automatically.
        val serviceIntent = Intent(context, SimpleListService::class.java).apply {
            data = Uri.parse("widget://simplelist/$id")
        }
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // Click template merged with each item's fillInIntent. Routes to
        // ToggleTaskReceiver, which does the DB write and triggers a
        // notifyAppWidgetViewDataChanged.
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getBroadcast(
                context,
                ("toggle-template-simple-$id").hashCode(),
                Intent(context, ToggleTaskReceiver::class.java).apply {
                    action = ToggleTaskReceiver.ACTION_TOGGLE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )

        // Default: no background image; show the themed surface.
        views.setViewVisibility(R.id.widget_bg_image, View.GONE)
        views.setViewVisibility(R.id.widget_bg_scrim, View.GONE)
        return views
    }

    private fun applyBackground(
        views: RemoteViews,
        context: Context,
        bg: LoadedBackground,
    ) {
        views.setImageViewBitmap(R.id.widget_bg_image, bg.bitmap)
        views.setViewVisibility(R.id.widget_bg_image, View.VISIBLE)
        val alphaInt = (bg.scrimAlpha * 255).toInt().coerceIn(0, 255)
        views.setInt(
            R.id.widget_bg_scrim,
            "setBackgroundColor",
            alphaInt shl 24,
        )
        views.setViewVisibility(R.id.widget_bg_scrim, View.VISIBLE)
        // Light text over the photo.
        views.setTextColor(
            R.id.widget_title,
            context.getColor(R.color.widget_text_on_image),
        )
        views.setTextColor(
            R.id.widget_empty,
            context.getColor(R.color.widget_text_on_image_dim),
        )
    }
}
