package com.example.todolists.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.todolists.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val mainHandler = Handler(Looper.getMainLooper())

    fun schedule(task: Task) {
        if (task.isDone || task.dueAt == null) return
        val now = System.currentTimeMillis()
        val parts = mutableListOf<String>()
        if (task.remindAtDue) {
            if (task.dueAt > now) {
                scheduleAt(task, ReminderKind.AT_DUE, task.dueAt)
                parts += "期限時刻 ${formatTime(task.dueAt)}"
            } else {
                parts += "期限時刻(過去のためスキップ)"
            }
        }
        if (task.remindOnDay) {
            val onDayMillis = computeOnDayMillis(task.dueAt, task.remindOnDayHour, task.remindOnDayMinute)
            if (onDayMillis > now) {
                scheduleAt(task, ReminderKind.ON_DAY, onDayMillis)
                parts += "当日 ${formatTime(onDayMillis)}"
            } else {
                parts += "当日(過去のためスキップ)"
            }
        }
        if (parts.isNotEmpty()) {
            showToast("リマインダー: ${parts.joinToString(", ")}")
        }
    }

    fun cancel(taskId: Long) {
        val am = alarmManager ?: return
        ReminderKind.values().forEach { kind ->
            val pi = pendingIntent(taskId, kind, mutable = false, createIfMissing = false) ?: return@forEach
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun scheduleAt(task: Task, kind: ReminderKind, triggerAt: Long) {
        val am = alarmManager ?: return
        val pi = pendingIntent(task.id, kind, mutable = false, createIfMissing = true) ?: return
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntent(
        taskId: Long,
        kind: ReminderKind,
        mutable: Boolean,
        createIfMissing: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_KIND, kind.name)
        }
        var flags = if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        if (!createIfMissing) flags = flags or PendingIntent.FLAG_NO_CREATE
        else flags = flags or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, requestCode(taskId, kind), intent, flags)
    }

    private fun computeOnDayMillis(dueAt: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dueAt
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun requestCode(taskId: Long, kind: ReminderKind): Int {
        val base = (taskId.toInt() and 0x3FFFFFFF) shl 1
        return base or kind.ordinal
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(millis))

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

enum class ReminderKind { AT_DUE, ON_DAY }
