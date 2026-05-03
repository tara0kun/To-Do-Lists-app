package com.example.todolists.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import com.example.todolists.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Best-effort calendar linkage.
 *
 * Tries CalendarContract.Events insert intent first (works for Google
 * Calendar / Samsung Calendar / any calendar app the user has). Falls back to
 * opening Google Calendar's web event-creation URL in a browser if no
 * calendar app is reachable.
 */
object CalendarIntegration {

    private const val DEFAULT_DURATION_MS = 60L * 60L * 1000L

    fun openInsertEvent(context: Context, task: Task) {
        val dueAt = task.dueAt ?: return
        val end = dueAt + DEFAULT_DURATION_MS

        if (tryNativeCalendar(context, task, dueAt, end)) return
        if (tryWebCalendar(context, task, dueAt, end)) return
        Toast.makeText(context, "カレンダーを開けるアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
    }

    private fun tryNativeCalendar(context: Context, task: Task, begin: Long, end: Long): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.Events.TITLE, task.title)
            putExtra(CalendarContract.Events.DESCRIPTION, "ToDoアプリから追加")
            if (task.remindAtDue || task.remindOnDay) {
                putExtra(CalendarContract.Events.HAS_ALARM, 1)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            it is ActivityNotFoundException
            false
        }
    }

    private fun tryWebCalendar(context: Context, task: Task, begin: Long, end: Long): Boolean {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dates = "${fmt.format(Date(begin))}/${fmt.format(Date(end))}"
        val uri = Uri.parse("https://calendar.google.com/calendar/render").buildUpon()
            .appendQueryParameter("action", "TEMPLATE")
            .appendQueryParameter("text", task.title)
            .appendQueryParameter("dates", dates)
            .appendQueryParameter("details", "ToDoアプリから追加")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
