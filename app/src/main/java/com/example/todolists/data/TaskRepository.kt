package com.example.todolists.data

import android.content.Context
import com.example.todolists.notifications.ReminderScheduler
import kotlinx.coroutines.flow.Flow

class TaskRepository(
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
        return id
    }

    suspend fun addSimple(title: String): Long {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return -1L
        return dao.insert(Task(title = trimmed, isSimple = true))
    }

    suspend fun update(task: Task) {
        scheduler.cancel(task.id)
        val sanitized = if (task.dueAt == null) {
            task.copy(remindAtDue = false, remindOnDay = false)
        } else task
        dao.update(sanitized)
        if (!sanitized.isDone) scheduler.schedule(sanitized)
    }

    suspend fun toggle(task: Task) = update(task.copy(isDone = !task.isDone))

    suspend fun delete(task: Task) {
        scheduler.cancel(task.id)
        dao.delete(task)
    }

    suspend fun clearCompleted() {
        dao.completed().forEach { scheduler.cancel(it.id) }
        dao.deleteCompleted()
    }

    suspend fun rescheduleAll() {
        dao.activeRemindable().forEach { scheduler.schedule(it) }
    }

    companion object {
        @Volatile private var INSTANCE: TaskRepository? = null
        fun get(context: Context): TaskRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskRepository(
                    dao = TaskDatabase.get(context).taskDao(),
                    scheduler = ReminderScheduler(context.applicationContext),
                ).also { INSTANCE = it }
            }
    }
}
