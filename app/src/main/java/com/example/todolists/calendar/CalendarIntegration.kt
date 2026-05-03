package com.example.todolists.calendar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.todolists.data.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar handoff for tasks with a due date.
 *
 * Tries hardest path first:
 *   1. If READ_CALENDAR + WRITE_CALENDAR are granted and a writable
 *      calendar exists, insert the event silently via ContentResolver.
 *   2. Otherwise open the device calendar app prefilled (user confirms).
 *   3. Otherwise open Google Calendar's TEMPLATE URL in the browser.
 */
object CalendarIntegration {

    private const val DEFAULT_DURATION_MS = 60L * 60L * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun hasWritePermission(context: Context): Boolean {
        val app = context.applicationContext
        val read = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED &&
            write == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Adds the task to the user's calendar. Runs in the background; surfaces
     * the outcome via a Toast on the main thread.
     */
    fun linkEvent(context: Context, task: Task) {
        val dueAt = task.dueAt ?: return
        val app = context.applicationContext

        scope.launch {
            if (!hasWritePermission(app)) {
                showToast(app, "カレンダー権限が無いため確認画面で追加します")
                withContext(Dispatchers.Main) {
                    if (!tryNativeIntent(app, task, dueAt)) tryWebIntent(app, task, dueAt)
                }
                return@launch
            }
            val written = writeEventDirect(app, task, dueAt)
            if (written) {
                showToast(app, "カレンダーに追加しました")
                return@launch
            }
            showToast(app, "書き込めるカレンダーが見つかりません(Googleアカウント未設定?) — 確認画面で追加します")
            withContext(Dispatchers.Main) {
                if (tryNativeIntent(app, task, dueAt)) return@withContext
                if (tryWebIntent(app, task, dueAt)) return@withContext
                Toast.makeText(app, "カレンダーに追加できませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeEventDirect(context: Context, task: Task, dueAt: Long): Boolean = runCatching {
        val calId = findWritableCalendarId(context) ?: return false
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, task.title)
            put(CalendarContract.Events.DTSTART, dueAt)
            put(CalendarContract.Events.DTEND, dueAt + DEFAULT_DURATION_MS)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, "ToDoアプリから追加")
            if (task.remindAtDue || task.remindOnDay) {
                put(CalendarContract.Events.HAS_ALARM, 1)
            }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return false
        val eventId = ContentUris.parseId(uri)

        if (task.remindAtDue) {
            insertReminder(context, eventId, minutesBefore = 0)
        }
        if (task.remindOnDay) {
            val onDayMillis = computeOnDayTriggerMillis(task)
            val minutesBefore = ((dueAt - onDayMillis) / 60_000L).toInt().coerceAtLeast(0)
            insertReminder(context, eventId, minutesBefore = minutesBefore)
        }
        true
    }.getOrDefault(false)

    private fun insertReminder(context: Context, eventId: Long, minutesBefore: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values) }
    }

    private fun findWritableCalendarId(context: Context): Long? = runCatching {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
            "${CalendarContract.Calendars._ID} ASC"
        // Try CONTRIBUTOR-or-better first; if none, fall back to any calendar.
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null,
            sortOrder,
        )?.use { c -> if (c.moveToFirst()) return@runCatching c.getLong(0) }
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.getOrNull()

    private fun computeOnDayTriggerMillis(task: Task): Long {
        val dueAt = task.dueAt ?: return 0L
        val cal = Calendar.getInstance().apply {
            timeInMillis = dueAt
            set(Calendar.HOUR_OF_DAY, task.remindOnDayHour)
            set(Calendar.MINUTE, task.remindOnDayMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun tryNativeIntent(context: Context, task: Task, dueAt: Long): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueAt)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dueAt + DEFAULT_DURATION_MS)
            putExtra(CalendarContract.Events.TITLE, task.title)
            putExtra(CalendarContract.Events.DESCRIPTION, "ToDoアプリから追加")
            if (task.remindAtDue || task.remindOnDay) {
                putExtra(CalendarContract.Events.HAS_ALARM, 1)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent); true
        }.getOrElse {
            it !is ActivityNotFoundException
        }
    }

    private fun tryWebIntent(context: Context, task: Task, dueAt: Long): Boolean {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dates = "${fmt.format(Date(dueAt))}/${fmt.format(Date(dueAt + DEFAULT_DURATION_MS))}"
        val uri = Uri.parse("https://calendar.google.com/calendar/render").buildUpon()
            .appendQueryParameter("action", "TEMPLATE")
            .appendQueryParameter("text", task.title)
            .appendQueryParameter("dates", dates)
            .appendQueryParameter("details", "ToDoアプリから追加")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
