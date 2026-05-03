package com.example.todolists.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.todolists.MainActivity
import com.example.todolists.R
import com.example.todolists.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: ReminderKind.AT_DUE.name
        val kind = runCatching { ReminderKind.valueOf(kindName) }.getOrDefault(ReminderKind.AT_DUE)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val dueAt = intent.getLongExtra(EXTRA_DUE_AT, 0L).takeIf { it > 0L }

        // Show the notification synchronously, exactly like the working
        // TestAlarmReceiver path. We carry the task title in the intent
        // extras so we don't need to wait on a Room query.
        showNotification(context, taskId, title, dueAt, kind)

        // Best-effort: if the task has been completed/deleted between
        // scheduling and now, cancel the notification we just posted.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = TaskDatabase.get(context).taskDao().findById(taskId)
                if (task == null || task.isDone) {
                    runCatching {
                        NotificationManagerCompat.from(context)
                            .cancel(notificationId(taskId, kind))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        taskId: Long,
        title: String,
        dueAt: Long?,
        kind: ReminderKind,
    ) {
        NotificationChannels.ensureCreated(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPI = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val heading = when (kind) {
            ReminderKind.AT_DUE -> "期限になりました"
            ReminderKind.ON_DAY -> "今日が期限のタスク"
        }
        val body = buildString {
            append(title.ifBlank { "タスク" })
            if (dueAt != null) {
                append(" (")
                append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(dueAt)))
                append(")")
            }
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(heading)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPI)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(taskId, kind), notification)
        }
    }

    private fun notificationId(taskId: Long, kind: ReminderKind): Int {
        val base = (taskId.toInt() and 0x3FFFFFFF) shl 1
        return base or kind.ordinal
    }

    companion object {
        const val ACTION = "com.example.todolists.action.REMINDER"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DUE_AT = "due_at"
    }
}
