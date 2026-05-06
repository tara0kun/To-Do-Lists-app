package com.example.todolists.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.todolists.R
import com.example.todolists.data.Task
import com.example.todolists.data.TaskDatabase
import kotlinx.coroutines.runBlocking

/**
 * RemoteViewsService for the V2 SimpleListWidget. The system calls
 * [onGetViewFactory] to obtain a factory that streams list items to the
 * launcher one by one. This is the path that supports incremental list
 * refresh via notifyAppWidgetViewDataChanged — Glance's LazyColumn doesn't
 * give us this.
 */
class SimpleListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        SimpleListFactory(applicationContext)
}

private class SimpleListFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<Task> = emptyList()

    override fun onCreate() = Unit
    override fun onDestroy() { items = emptyList() }

    override fun onDataSetChanged() {
        // Called on a worker thread by the system. Snapshot the current
        // simple-list contents from Room.
        items = runBlocking {
            TaskDatabase.get(context).taskDao().simpleVisibleSnapshot(MAX_ITEMS)
        }
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_item_task)
        val views = RemoteViews(context.packageName, R.layout.widget_item_task)

        val title: CharSequence = if (task.isDone) {
            SpannableString(task.title).apply {
                setSpan(StrikethroughSpan(), 0, length, 0)
            }
        } else task.title
        views.setTextViewText(R.id.item_title, title)
        views.setTextColor(
            R.id.item_title,
            context.getColor(R.color.widget_text_primary),
        )

        // Simple list has no due dates, so hide the meta row.
        views.setViewVisibility(R.id.item_meta, View.GONE)

        // Bind the checkbox state. setCompoundButtonChecked requires API 31+.
        views.setCompoundButtonChecked(R.id.item_check, task.isDone)

        // Per-item fillInIntent merged with the widget's PendingIntent
        // template to deliver task-id specific extras to ToggleTaskReceiver.
        val fillIn = Intent().apply {
            putExtra(ToggleTaskReceiver.EXTRA_TASK_ID, task.id)
            putExtra(ToggleTaskReceiver.EXTRA_NEW_DONE, !task.isDone)
        }
        // CompoundButton response → launcher local-toggles the checkmark
        // before our broadcast even fires (Android 12+).
        views.setOnCheckedChangeResponse(
            R.id.item_check,
            RemoteViews.RemoteResponse.fromFillInIntent(fillIn),
        )
        // Tapping the row body falls back to a normal click → same intent.
        views.setOnClickFillInIntent(R.id.item_root, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    companion object { private const val MAX_ITEMS = 30 }
}
