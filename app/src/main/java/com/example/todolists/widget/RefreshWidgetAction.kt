package com.example.todolists.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Tapped on the widget's refresh icon. Bumps a timestamp inside Glance's
 * own state so the framework triggers a fresh provideGlance pass for this
 * widget instance — that re-reads from Room and pushes new RemoteViews.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        runCatching {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[TOUCH_KEY] = System.currentTimeMillis()
                }.toPreferences()
            }
        }
    }

    companion object {
        private val TOUCH_KEY = longPreferencesKey("widget_touch")
    }
}
