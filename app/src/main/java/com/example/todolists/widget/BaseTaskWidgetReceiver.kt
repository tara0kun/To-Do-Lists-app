package com.example.todolists.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.todolists.MainActivity
import com.example.todolists.R
import com.example.todolists.data.WidgetBackgroundRepository
import com.example.todolists.data.WidgetForegroundMode
import com.example.todolists.ui.TaskTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared scaffolding for the V2 AppWidgetProvider widgets. Subclasses
 * declare what tab they represent, what RemoteViewsService backs the
 * list, and which (if any) "add" affordance to show in the header.
 */
abstract class BaseTaskWidgetReceiver : AppWidgetProvider() {

    enum class AddKind { NONE, SIMPLE, DETAILED }

    protected abstract val title: String
    protected abstract val tab: TaskTab
    protected abstract val emptyMessage: String
    protected abstract val addKind: AddKind
    protected abstract val serviceClass: Class<out RemoteViewsService>
    protected abstract val tag: String

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
        val settings = runCatching {
            WidgetBackgroundRepository.get(context).state.value
        }.getOrNull()
        val fgMode = settings?.foregroundMode ?: WidgetForegroundMode.AUTO
        val customColor = settings?.customForegroundColor ?: 0xFFFFFFFF.toInt()
        runCatching {
            val views = buildBaseViews(context, id, fgMode, customColor, hasBg = false)
            mgr.updateAppWidget(id, views)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }.onFailure { Log.e(tag, "updateOne id=$id failed", it) }

        // Background image is optional. Decoding it is too heavy for the
        // main thread; load asynchronously and re-push the views.
        WidgetWorkScope.launch {
            val bg = WidgetBackgroundLoader.load(context)
            if (bg != null) {
                val withBg = buildBaseViews(context, id, fgMode, customColor, hasBg = true).apply {
                    applyBackground(this, bg)
                }
                withContext(Dispatchers.Main) {
                    mgr.updateAppWidget(id, withBg)
                }
            }
        }
    }

    private fun buildBaseViews(
        context: Context,
        id: Int,
        fgMode: WidgetForegroundMode,
        customColor: Int,
        hasBg: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        val fgPrimary = foregroundPrimary(context, fgMode, customColor, hasBg)
        val fgSecondary = foregroundSecondary(context, fgMode, customColor, hasBg)

        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, fgPrimary)
        views.setTextViewText(R.id.widget_empty, emptyMessage)
        views.setTextColor(R.id.widget_empty, fgSecondary)
        // Tint the header icons to match the title.
        views.setInt(R.id.widget_btn_refresh, "setColorFilter", fgPrimary)
        views.setInt(R.id.widget_btn_add, "setColorFilter", fgPrimary)

        // Title tap → open the app on the matching tab.
        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(
                context,
                ("${tag}-open-$id").hashCode(),
                MainActivity.intentForTab(context, tab),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        // Refresh button → re-broadcast our own ACTION_APPWIDGET_UPDATE
        // so onUpdate runs again and pushes fresh RemoteViews.
        views.setOnClickPendingIntent(
            R.id.widget_btn_refresh,
            PendingIntent.getBroadcast(
                context,
                ("${tag}-refresh-$id").hashCode(),
                Intent(context, javaClass).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        // Add button — show or hide depending on kind.
        when (addKind) {
            AddKind.NONE -> views.setViewVisibility(R.id.widget_btn_add, View.GONE)
            AddKind.SIMPLE, AddKind.DETAILED -> {
                views.setViewVisibility(R.id.widget_btn_add, View.VISIBLE)
                views.setOnClickPendingIntent(
                    R.id.widget_btn_add,
                    PendingIntent.getActivity(
                        context,
                        ("${tag}-add-$id").hashCode(),
                        QuickAddActivity.intent(
                            context,
                            simple = (addKind == AddKind.SIMPLE),
                        ),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }

        // List backed by our RemoteViewsService. Data URI is unique per
        // widget id so each instance gets its own factory.
        val serviceIntent = Intent(context, serviceClass).apply {
            data = Uri.parse("widget://${javaClass.simpleName}/$id")
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
                ("${tag}-toggle-template-$id").hashCode(),
                Intent(context, ToggleTaskReceiver::class.java).apply {
                    action = ToggleTaskReceiver.ACTION_TOGGLE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )

        // Background image hidden by default; the async path turns it on
        // if the user has set one.
        views.setViewVisibility(R.id.widget_bg_image, View.GONE)
        views.setViewVisibility(R.id.widget_bg_scrim, View.GONE)
        return views
    }

    private fun applyBackground(views: RemoteViews, bg: LoadedBackground) {
        views.setImageViewBitmap(R.id.widget_bg_image, bg.bitmap)
        views.setViewVisibility(R.id.widget_bg_image, View.VISIBLE)
        val alphaInt = (bg.scrimAlpha * 255).toInt().coerceIn(0, 255)
        views.setInt(
            R.id.widget_bg_scrim,
            "setBackgroundColor",
            alphaInt shl 24,
        )
        views.setViewVisibility(R.id.widget_bg_scrim, View.VISIBLE)
    }

    private fun foregroundPrimary(
        context: Context,
        mode: WidgetForegroundMode,
        customColor: Int,
        hasBg: Boolean,
    ): Int = when (mode) {
        WidgetForegroundMode.AUTO ->
            if (hasBg) context.getColor(R.color.widget_text_on_image)
            else context.getColor(R.color.widget_text_primary)
        WidgetForegroundMode.LIGHT ->
            context.getColor(R.color.widget_text_on_image)
        WidgetForegroundMode.DARK ->
            android.graphics.Color.BLACK
        WidgetForegroundMode.CUSTOM ->
            customColor
    }

    private fun foregroundSecondary(
        context: Context,
        mode: WidgetForegroundMode,
        customColor: Int,
        hasBg: Boolean,
    ): Int = when (mode) {
        WidgetForegroundMode.AUTO ->
            if (hasBg) context.getColor(R.color.widget_text_on_image_dim)
            else context.getColor(R.color.widget_text_secondary)
        WidgetForegroundMode.LIGHT ->
            context.getColor(R.color.widget_text_on_image_dim)
        WidgetForegroundMode.DARK ->
            android.graphics.Color.parseColor("#FF555555")
        // For custom, dim the secondary text by reducing alpha to ~70%.
        WidgetForegroundMode.CUSTOM ->
            (customColor and 0x00FFFFFF) or (0xB0 shl 24)
    }
}
