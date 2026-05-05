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
    /**
     * Tries to add the task to the calendar. Returns the inserted event ID
     * when the direct ContentResolver write succeeded; returns null when it
     * fell back to the calendar app or web URL (we don't get the ID then).
     */
    suspend fun linkEvent(context: Context, task: Task): Long? = withContext(Dispatchers.IO) {
        val dueAt = task.dueAt ?: return@withContext null
        val app = context.applicationContext

        if (hasWritePermission(app)) {
            val eventId = writeEventDirectAndReturnId(app, task, dueAt)
            if (eventId != null) {
                showToast(
                    app,
                    "カレンダーに追加: ${lastWrittenCalendarLabel.ifBlank { "id=$eventId" }}",
                )
                return@withContext eventId
            }
            showToast(app, "書き込めるカレンダーが見つかりません(Googleアカウント未設定?) — 確認画面で追加します")
        } else {
            showToast(app, "カレンダー権限が無いため確認画面で追加します")
        }

        withContext(Dispatchers.Main) {
            if (tryNativeIntent(app, task, dueAt)) return@withContext
            if (tryWebIntent(app, task, dueAt)) return@withContext
            Toast.makeText(app, "カレンダーに追加できませんでした", Toast.LENGTH_LONG).show()
        }
        null
    }

    /** Removes the calendar event previously created by [linkEvent]. */
    suspend fun deleteEvent(context: Context, eventId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val result = runCatching {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                app.contentResolver.delete(uri, null, null)
            }
            when {
                result.isSuccess && (result.getOrDefault(0) ?: 0) > 0 -> {
                    showToast(app, "カレンダーから予定を削除しました")
                    true
                }
                result.isSuccess -> {
                    showToast(app, "予定が見つかりません (id=$eventId)")
                    false
                }
                else -> {
                    showToast(app, "予定の削除に失敗: ${result.exceptionOrNull()?.message}")
                    false
                }
            }
        }

    private fun writeEventDirectAndReturnId(context: Context, task: Task, dueAt: Long): Long? = runCatching {
        val (calId, calLabel) = findWritableCalendar(context) ?: return@runCatching null
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
            ?: return@runCatching null
        val eventId = ContentUris.parseId(uri)

        if (task.remindAtDue) {
            insertReminder(context, eventId, minutesBefore = 0)
        }
        if (task.remindOnDay) {
            val onDayMillis = computeOnDayTriggerMillis(task)
            val minutesBefore = ((dueAt - onDayMillis) / 60_000L).toInt().coerceAtLeast(0)
            insertReminder(context, eventId, minutesBefore = minutesBefore)
        }
        // Persist where we wrote so the success toast can tell the user.
        lastWrittenCalendarLabel = calLabel
        eventId
    }.getOrNull()

    @Volatile private var lastWrittenCalendarLabel: String = ""

    private fun insertReminder(context: Context, eventId: Long, minutesBefore: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values) }
    }

    private fun findWritableCalendar(context: Context): Pair<Long, String>? {
        val id = findWritableCalendarId(context) ?: return null
        val label = describeCalendar(context, id)
        return id to label
    }

    private fun describeCalendar(context: Context, calendarId: Long): String = runCatching {
        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val account = c.getString(0).orEmpty()
                val type = c.getString(1).orEmpty()
                val display = c.getString(2).orEmpty()
                return@runCatching "$display ($account, $type)"
            }
        }
        "unknown calendar id=$calendarId"
    }.getOrDefault("calendar id=$calendarId")

    private fun findWritableCalendarId(context: Context): Long? = runCatching {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
            "${CalendarContract.Calendars._ID} ASC"
        // 1) Prefer a Google-account calendar (so the event shows up in
        //    the Google Calendar app the user is checking).
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            arrayOf("com.google"),
            sortOrder,
        )?.use { c -> if (c.moveToFirst()) return@runCatching c.getLong(0) }
        // 2) Any CONTRIBUTOR-or-better calendar (could be Exchange etc.).
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null,
            sortOrder,
        )?.use { c -> if (c.moveToFirst()) return@runCatching c.getLong(0) }
        // 3) Last resort: the first calendar of any kind (likely a local-only
        //    "Phone" calendar — visible in stock Calendar but NOT in
        //    Google Calendar app).
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
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
