package com.example.todolists.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.todolists.data.TaskRepository

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[TaskIdKey] ?: return
        val wasDone = parameters[WasDoneKey] ?: false
        val newDone = !wasDone

        // 1. Optimistic UI: store the new done value in Glance state immediately
        //    so the checkbox flips before the slower DB / scheduler work runs.
        runCatching {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[optimisticKey(id)] = newDone
                }.toPreferences()
            }
        }

        // 2. Persist the toggle in Room (cancels/reschedules reminders, refreshes
        //    other widgets, deletes calendar events when applicable).
        val repository = TaskRepository.get(context)
        val task = repository.findById(id) ?: return
        repository.toggle(task)

        // 3. Drop the optimistic override now that the DB is in sync, so future
        //    renders read straight from Room again.
        runCatching {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    remove(optimisticKey(id))
                }.toPreferences()
            }
        }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
        val WasDoneKey = ActionParameters.Key<Boolean>("wasDone")

        fun optimisticKey(taskId: Long) = booleanPreferencesKey("opt_done_$taskId")
    }
}
