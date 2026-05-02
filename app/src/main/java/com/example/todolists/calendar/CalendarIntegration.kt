package com.example.todolists.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.example.todolists.data.Task

/**
 * Best-effort Google Calendar linkage.
 *
 * Uses [CalendarContract.Events.CONTENT_URI] with [Intent.ACTION_INSERT] so the
 * device's calendar app (Google Calendar, Samsung Calendar, etc.) opens with
 * the event prefilled. The user confirms inside that app — we never write to
 * the calendar directly, so no WRITE_CALENDAR permission is required.
 *
 * If the task has no due date this is a no-op; if there is no calendar app
 * installed a short toast is shown.
 */
object CalendarIntegration {

    private const val DEFAULT_DURATION_MS = 60L * 60L * 1000L

    fun openInsertEvent(context: Context, task: Task) {
        val dueAt = task.dueAt ?: return
        val end = dueAt + DEFAULT_DURATION_MS

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueAt)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.Events.TITLE, task.title)
            putExtra(CalendarContract.Events.DESCRIPTION, "ToDoアプリから追加")
            if (task.remindAtDue || task.remindOnDay) {
                putExtra(CalendarContract.Events.HAS_ALARM, 1)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "カレンダーアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
        }
    }
}
