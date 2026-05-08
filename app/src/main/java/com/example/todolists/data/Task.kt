package com.example.todolists.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val remindAtDue: Boolean = false,
    val remindOnDay: Boolean = false,
    val remindOnDayHour: Int = 9,
    val remindOnDayMinute: Int = 0,
    /** Whether to fire a reminder at [remindCustomAt]. Independent of [dueAt]. */
    val remindCustom: Boolean = false,
    /** Epoch millis for the custom reminder. Null when [remindCustom] is false. */
    val remindCustomAt: Long? = null,
    val priority: Int = Priority.MEDIUM.storageValue,
    val isSimple: Boolean = false,
    val calendarEventId: Long? = null,
) {
    val priorityEnum: Priority get() = Priority.fromStorage(priority)
}
