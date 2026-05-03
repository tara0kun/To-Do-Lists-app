package com.example.todolists.notifications

import android.content.Context
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.todolists.R

/**
 * Diagnostic helper used by the テスト通知 button in the top bar. Fires a
 * notification immediately, bypassing AlarmManager. Surfacing this helps
 * separate "notifications themselves don't work" (POST_NOTIFICATIONS denied
 * or channel blocked) from "alarms don't fire" (scheduling problem).
 */
object NotificationDebug {

    fun fireTestNotification(context: Context) {
        NotificationChannels.ensureCreated(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("即時通知テスト")
            .setContentText("通知システムは正常に動作しています")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Toast.makeText(
                context,
                "通知が許可されていません — 設定で「通知」をONにしてください",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val notifyResult = runCatching {
            manager.notify(TEST_NOTIFICATION_ID, notification)
        }
        // Also schedule an alarm-driven test notification 10 seconds ahead
        // so we can tell whether AlarmManager itself is delivering.
        val alarmStatus = TestAlarmReceiver.scheduleIn(context, delayMs = 10_000L)
        val msg = if (notifyResult.isSuccess) {
            "即時通知を発行 / $alarmStatus"
        } else {
            "通知の発行に失敗: ${notifyResult.exceptionOrNull()?.message}"
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    private const val TEST_NOTIFICATION_ID = 99_999
}
