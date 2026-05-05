package com.example.todolists.widget

import android.content.Context
import android.util.Log
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
        val t0 = System.currentTimeMillis()
        Log.d(TAG, "[$t0] onAction enter id=${parameters[TaskIdKey]}")
        val id = parameters[TaskIdKey] ?: return
        val wasDone = parameters[WasDoneKey] ?: false
        val newDone = !wasDone
        val app = context.applicationContext

        // 1. Optimistic UI: persist the new done value into Glance state
        //    right away. This is the only thing we await — Glance's
        //    auto-recompose then flips the checkbox before the action
        //    returns, no matter how slow the DB / calendar work is.
        val tStateStart = System.currentTimeMillis()
        runCatching {
            updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[optimisticKey(id)] = newDone
                }.toPreferences()
            }
        }.onFailure { Log.e(TAG, "optimistic write failed", it) }
        Log.d(TAG, "[${System.currentTimeMillis()}] optimistic state written (${System.currentTimeMillis() - tStateStart}ms)")

        // 2. Move the heavy work (DB write, scheduler, calendar delete,
        //    other widgets' refresh) off the action's coroutine so the
        //    callback can return immediately and let Glance redraw.
        WidgetWorkScope.launch {
            val tBgStart = System.currentTimeMillis()
            Log.d(TAG, "[$tBgStart] background work start (action enter→bg start: ${tBgStart - t0}ms)")
            val repository = TaskRepository.get(app)
            val task = repository.findById(id) ?: return@launch
            repository.toggle(task)
            val tToggleEnd = System.currentTimeMillis()
            Log.d(TAG, "[$tToggleEnd] repository.toggle done (${tToggleEnd - tBgStart}ms)")
            runCatching {
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        remove(optimisticKey(id))
                    }.toPreferences()
                }
            }
            val tEnd = System.currentTimeMillis()
            Log.d(TAG, "[$tEnd] background work done (total ${tEnd - tBgStart}ms)")
        }

        val tExit = System.currentTimeMillis()
        Log.d(TAG, "[$tExit] onAction returning (${tExit - t0}ms)")
    }

    companion object {
        private const val TAG = "WidgetDbg"
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
        val WasDoneKey = ActionParameters.Key<Boolean>("wasDone")

        fun optimisticKey(taskId: Long) = booleanPreferencesKey("opt_done_$taskId")
    }
}
