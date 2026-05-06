package com.example.todolists.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.todolists.R
import com.example.todolists.data.Task
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared scaffolding for the V2 RemoteViewsServices. Subclasses just
 * supply how to fetch their list of tasks and whether each row should
 * render the optional meta line (due date).
 */
abstract class BaseTaskListService : RemoteViewsService() {

    abstract fun createFactory(context: Context): RemoteViewsFactory

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d(TAG, "${javaClass.simpleName}.onGetViewFactory data=${intent.data}")
        return createFactory(applicationContext)
    }

    private companion object { const val TAG = "TaskListSvc" }
}

abstract class BaseTaskListFactory(
    protected val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    protected var items: List<Task> = emptyList()

    /** Whether to render the meta (due date) text under each task title. */
    protected abstract val showMeta: Boolean

    /** Logging tag for diagnostics. */
    protected abstract val tag: String

    /** Worker-thread DB query for the latest snapshot. */
    protected abstract suspend fun fetchItems(): List<Task>

    override fun onCreate() {
        Log.d(tag, "factory onCreate")
    }

    override fun onDestroy() {
        Log.d(tag, "factory onDestroy")
        items = emptyList()
    }

    override fun onDataSetChanged() {
        runCatching {
            items = runBlocking { fetchItems() }
            Log.d(tag, "onDataSetChanged loaded ${items.size} items")
        }.onFailure { Log.e(tag, "onDataSetChanged failed", it) }
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

        if (showMeta && task.dueAt != null) {
            views.setViewVisibility(R.id.item_meta, View.VISIBLE)
            views.setTextViewText(R.id.item_meta, formatCompactDue(task.dueAt))
            views.setTextColor(
                R.id.item_meta,
                context.getColor(R.color.widget_text_secondary),
            )
        } else {
            views.setViewVisibility(R.id.item_meta, View.GONE)
        }

        views.setCompoundButtonChecked(R.id.item_check, task.isDone)

        // Per-item fillInIntent merged with the widget's PendingIntent
        // template. Both the checkbox and the row body share it.
        val fillIn = Intent().apply {
            putExtra(ToggleTaskReceiver.EXTRA_TASK_ID, task.id)
            putExtra(ToggleTaskReceiver.EXTRA_NEW_DONE, !task.isDone)
        }
        views.setOnCheckedChangeResponse(
            R.id.item_check,
            RemoteViews.RemoteResponse.fromFillInIntent(fillIn),
        )
        views.setOnClickFillInIntent(R.id.item_root, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun formatCompactDue(millis: Long): String =
        SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(millis))
}
