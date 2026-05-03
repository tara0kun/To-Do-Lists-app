package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.example.todolists.data.TaskRepository

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[TaskIdKey] ?: return
        val repository = TaskRepository.get(context)
        val task = repository.findById(id) ?: return
        repository.toggle(task)
        // After the DB write the Glance auto-update sometimes loses the race
        // with the action callback's lifecycle, so explicitly recompose the
        // tapped widget here while we're still in the action's coroutine.
        runCatching { SimpleListWidget().update(context, glanceId) }
        runCatching { AllTasksWidget().update(context, glanceId) }
        runCatching { OverdueWidget().update(context, glanceId) }
        runCatching { CompletedWidget().update(context, glanceId) }
        // And refresh the others so any cross-widget changes (e.g. moving
        // a task from active to completed) propagate immediately.
        runCatching { SimpleListWidget().updateAll(context) }
        runCatching { AllTasksWidget().updateAll(context) }
        runCatching { OverdueWidget().updateAll(context) }
        runCatching { CompletedWidget().updateAll(context) }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
    }
}
