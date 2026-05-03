package com.example.todolists.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.todolists.R

/**
 * Receiver that fires when the test alarm scheduled by [NotificationDebug]
 * comes due. Posts a notification so we can confirm AlarmManager is
 * actually delivering broadcasts.
 */
class TestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationChannels.ensureCreated(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("アラームテスト")
            .setContentText("AlarmManagerは正常に動作しています")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(TEST_ALARM_ID, notification)
        }
    }

    companion object {
        const val ACTION = "com.example.todolists.action.TEST_ALARM"
        private const val TEST_ALARM_ID = 88_888

        fun scheduleIn(context: Context, delayMs: Long): String {
            val am = context.getSystemService(AlarmManager::class.java)
                ?: return "AlarmManager にアクセスできません"
            val intent = Intent(context, TestAlarmReceiver::class.java).apply { action = ACTION }
            val pi = PendingIntent.getBroadcast(
                context,
                987,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val triggerAt = System.currentTimeMillis() + delayMs
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            return runCatching {
                if (exactAllowed) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    "exact alarm を ${delayMs / 1000}秒後にセット"
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    "inexact alarm を ${delayMs / 1000}秒後にセット (Exact未許可)"
                }
            }.getOrElse { "アラーム設定失敗: ${it.message}" }
        }
    }
}
