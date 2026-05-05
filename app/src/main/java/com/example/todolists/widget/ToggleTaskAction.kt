package com.example.todolists.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.ToggleableStateKey
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
        val id = parameters[TaskIdKey]
        val wasDone = parameters[WasDoneKey]
        // When invoked via Glance CheckBox / Switch on Android 12+, the
        // launcher injects the new checked state under ToggleableStateKey.
        // That's the most reliable source for newDone — using it avoids
        // the "flip back" problem caused by inferring from a stale
        // wasDone parameter.
        val toggled = parameters[ToggleableStateKey]
        Log.d(
            TAG,
            "[$t0] onAction enter id=$id wasDone=$wasDone toggleable=$toggled",
        )
        if (id == null) {
            Log.w(TAG, "TaskIdKey missing in action parameters; aborting")
            return
        }
        val newDone = toggled ?: !(wasDone ?: false)
        val app = context.applicationContext

        // Persist the new done value into Glance state immediately. The
        // CheckBox composable reads this on its next recompose, which is
        // what keeps the local launcher toggle from being overwritten
        // back to the old state.
        val tStateStart = System.currentTimeMillis()
        runCatching {
            updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[optimisticKey(id)] = newDone
                }.toPreferences()
            }
        }.onFailure { Log.e(TAG, "optimistic write failed", it) }
        Log.d(
            TAG,
            "[${System.currentTimeMillis()}] optimistic=$newDone written " +
                "(${System.currentTimeMillis() - tStateStart}ms)",
        )

        // Move the heavy work (DB write, scheduler, calendar delete,
        // other widgets' refresh) off the action's coroutine so the
        // callback can return immediately and let Glance redraw.
        WidgetWorkScope.launch {
            val tBgStart = System.currentTimeMillis()
            Log.d(TAG, "[$tBgStart] background work start (action enter→bg start: ${tBgStart - t0}ms)")
            val repository = TaskRepository.get(app)
            val task = repository.findById(id) ?: return@launch
            // If the user already toggled to newDone (e.g. the launcher
            // local-toggled to true and we wrote optimistic=true), but
            // task.isDone matches that, skip the toggle to avoid
            // double-flipping.
            if (task.isDone == newDone) {
                Log.d(TAG, "DB already matches newDone=$newDone; skipping toggle")
            } else {
                repository.toggle(task)
            }
            val tToggleEnd = System.currentTimeMillis()
            Log.d(TAG, "[$tToggleEnd] repository.toggle done (${tToggleEnd - tBgStart}ms)")
            // Note: we intentionally do NOT remove the optimistic key
            // here. Leaving it in place means the calling widget's
            // CheckBox keeps showing newDone even after Glance triggers
            // additional recompositions. The next tap on the same task
            // overwrites the optimistic key with the new desired value,
            // and DB updates flow through naturally on next refresh.
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
