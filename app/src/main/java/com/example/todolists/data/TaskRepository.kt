package com.example.todolists.data

import android.content.Context
import com.example.todolists.calendar.CalendarIntegration
import com.example.todolists.notifications.ReminderScheduler
import com.example.todolists.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.Flow

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

    private fun refreshWidgets() {
        // All four widgets are V2 (RemoteViewsService-backed). Just notify
        // the launcher that the list data changed; it re-fetches only the
        // visible items via the factory. No full re-render needed.
        WidgetUpdateHelper.notifyDataChanged(context)
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
