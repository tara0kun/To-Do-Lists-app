package com.example.todolists.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Tapped on the widget's refresh icon. Tries every refresh path we have so
 * that whichever one HyperOS / launcher honours actually fires:
 *   - Bumps Glance state (triggers Glance auto-recomposition)
 *   - Calls Glance updateAll on every widget class
 *   - Sends APPWIDGET_UPDATE broadcast with FLAG_RECEIVER_FOREGROUND
 * Also shows a quick toast so the user can confirm the tap registered.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val app = context.applicationContext

        runCatching {
            updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[TOUCH_KEY] = System.currentTimeMillis()
                }.toPreferences()
            }
        }
        runCatching { SimpleListWidget().updateAll(app) }
        runCatching { AllTasksWidget().updateAll(app) }
        runCatching { OverdueWidget().updateAll(app) }
        runCatching { CompletedWidget().updateAll(app) }

        listOf(
            SimpleListWidgetReceiver::class.java,
            AllTasksWidgetReceiver::class.java,
            OverdueWidgetReceiver::class.java,
            CompletedWidgetReceiver::class.java,
        ).forEach { cls ->
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, cls))
            if (ids.isNotEmpty()) {
                val intent = Intent(app, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                app.sendBroadcast(intent)
            }
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, "ウィジェットを更新しました", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val TOUCH_KEY = longPreferencesKey("widget_touch")
    }
}
