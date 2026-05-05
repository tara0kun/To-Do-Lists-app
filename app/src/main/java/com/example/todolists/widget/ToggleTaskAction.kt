package com.example.todolists.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.todolists.data.TaskRepository
import kotlinx.coroutines.launch

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[TaskIdKey] ?: return
        val wasDone = parameters[WasDoneKey] ?: false
        val newDone = !wasDone
        val app = context.applicationContext

        // 1. Optimistic UI: persist the new done value into Glance state
        //    right away. This is the only thing we await — Glance's
        //    auto-recompose then flips the checkbox before the action
        //    returns, no matter how slow the DB / calendar work is.
        runCatching {
            updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[optimisticKey(id)] = newDone
                }.toPreferences()
            }
        }

        // 2. Move the heavy work (DB write, scheduler, calendar delete,
        //    other widgets' refresh) off the action's coroutine so the
        //    callback can return immediately and let Glance redraw.
        WidgetWorkScope.launch {
            val repository = TaskRepository.get(app)
            val task = repository.findById(id) ?: return@launch
            repository.toggle(task)
            runCatching {
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        remove(optimisticKey(id))
                    }.toPreferences()
                }
            }
        }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
        val WasDoneKey = ActionParameters.Key<Boolean>("wasDone")

        fun optimisticKey(taskId: Long) = booleanPreferencesKey("opt_done_$taskId")
    }
}
