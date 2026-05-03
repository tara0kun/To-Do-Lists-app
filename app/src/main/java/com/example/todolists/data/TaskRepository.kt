package com.example.todolists.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.todolists.notifications.ReminderScheduler
import com.example.todolists.widget.AllTasksWidget
import com.example.todolists.widget.CompletedWidget
import com.example.todolists.widget.OverdueWidget
import com.example.todolists.widget.SimpleListWidget
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

    suspend fun toggle(task: Task) = update(task.copy(isDone = !task.isDone))

    suspend fun delete(task: Task) {
        scheduler.cancel(task.id)
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
        runCatching { SimpleListWidget().updateAll(context) }
        runCatching { AllTasksWidget().updateAll(context) }
        runCatching { OverdueWidget().updateAll(context) }
        runCatching { CompletedWidget().updateAll(context) }
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
