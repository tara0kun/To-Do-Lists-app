package com.example.todolists.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val REMINDERS = "reminders"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(REMINDERS) != null) return
        val channel = NotificationChannel(
            REMINDERS,
            "タスクのリマインダー",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "期限・当日リマインダーの通知"
        }
        manager.createNotificationChannel(channel)
    }
}
