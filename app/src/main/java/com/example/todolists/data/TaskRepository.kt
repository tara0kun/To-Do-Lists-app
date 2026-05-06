package com.example.todolists.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.example.todolists.calendar.CalendarIntegration
import com.example.todolists.notifications.ReminderScheduler
import com.example.todolists.widget.AllTasksWidget
import com.example.todolists.widget.AllTasksWidgetReceiver
import com.example.todolists.widget.CompletedWidget
import com.example.todolists.widget.CompletedWidgetReceiver
import com.example.todolists.widget.OverdueWidget
import com.example.todolists.widget.OverdueWidgetReceiver
import com.example.todolists.widget.WidgetUpdateHelper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TaskRepository(
    private val context: Context,
    private val dao: TaskDao,
    private val scheduler: ReminderScheduler,
) {
    val tasks: Flow<List<Task>> = dao.observeAll()

    suspend fun add(
        title: String,
        dueAt: Long? = null,
        remindAtDue: Boolean = false,
        remindOnDay: Boolean = false,
        remindOnDayHour: Int = 9,
        remindOnDayMinute: Int = 0,
        priority: Int = Priority.MEDIUM.storageValue,
        calendarEventId: Long? = null,
    ): Long {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return -1L
        val id = dao.insert(
            Task(
                title = trimmed,
                dueAt = dueAt,
                remindAtDue = remindAtDue && dueAt != null,
                remindOnDay = remindOnDay && dueAt != null,
                remindOnDayHour = remindOnDayHour,
                remindOnDayMinute = remindOnDayMinute,
                priority = priority,
                calendarEventId = calendarEventId,
            )
        )
        dao.findById(id)?.let { scheduler.schedule(it) }
        refreshWidgets()
        return id
    }

    suspend fun addSimple(title: String): Long {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return -1L
        val id = dao.insert(Task(title = trimmed, isSimple = true))
        refreshWidgets()
        return id
    }

    suspend fun findById(id: Long): Task? = dao.findById(id)

    suspend fun update(task: Task) {
        scheduler.cancel(task.id)
        val sanitized = if (task.dueAt == null) {
            task.copy(remindAtDue = false, remindOnDay = false)
        } else task
        dao.update(sanitized)
        if (!sanitized.isDone) scheduler.schedule(sanitized)
        refreshWidgets()
    }

    suspend fun toggle(task: Task) {
        val newDone = !task.isDone

        val existingEventId = task.calendarEventId
        val cleanedEventId = if (newDone && existingEventId != null) {
            CalendarIntegration.deleteEvent(context, existingEventId)
            null
        } else {
            existingEventId
        }
        update(task.copy(isDone = newDone, calendarEventId = cleanedEventId))
        // Note: we no longer touch widget optimistic keys here. The
        // calling widget's optimistic key is set (and intentionally
        // left in place) by ToggleTaskAction; other widget instances
        // pick up the new state via the DB-backed refreshWidgets path.
        // Clearing the optimistic key here was racing with Glance's
        // post-action recompose and causing CheckBox flip-back.
    }

    suspend fun delete(task: Task) {
        scheduler.cancel(task.id)
        if (task.calendarEventId != null) {
            CalendarIntegration.deleteEvent(context, task.calendarEventId)
        }
        dao.delete(task)
        refreshWidgets()
    }

    suspend fun clearCompleted() {
        val completed = dao.completed()
        completed.forEach { scheduler.cancel(it.id) }
        dao.deleteCompleted()
        refreshWidgets()
    }

    suspend fun rescheduleAll() {
        dao.activeRemindable().forEach { scheduler.schedule(it) }
    }

    private suspend fun refreshWidgets() {
        // V2 widgets (RemoteViewsService-backed): just notify the list view,
        // launcher does an incremental refresh of just the items. This is
        // the path that gives Google-Tasks-style instant reflection.
        WidgetUpdateHelper.notifyDataChanged(context)

        // V1 (Glance) widgets that haven't migrated yet: full updateAll.
        coroutineScope {
            launch { runCatching { AllTasksWidget().updateAll(context) } }
            launch { runCatching { OverdueWidget().updateAll(context) } }
            launch { runCatching { CompletedWidget().updateAll(context) } }
        }
        broadcastUpdate(AllTasksWidgetReceiver::class.java)
        broadcastUpdate(OverdueWidgetReceiver::class.java)
        broadcastUpdate(CompletedWidgetReceiver::class.java)
    }

    private fun broadcastUpdate(cls: Class<*>) {
        val component = ComponentName(context, cls)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val intent = Intent(context, cls).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            // Foreground priority so battery-optimised launchers deliver
            // the broadcast immediately instead of batching it.
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        context.sendBroadcast(intent)
    }

    companion object {
        @Volatile private var INSTANCE: TaskRepository? = null
        fun get(context: Context): TaskRepository =
            INSTANCE ?: synchronized(this) {
                val app = context.applicationContext
                INSTANCE ?: TaskRepository(
                    context = app,
                    dao = TaskDatabase.get(app).taskDao(),
                    scheduler = ReminderScheduler(app),
                ).also { INSTANCE = it }
            }
    }
}
