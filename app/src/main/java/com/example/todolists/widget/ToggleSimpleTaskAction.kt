package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.todolists.data.TaskRepository

class ToggleSimpleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[TaskIdKey] ?: return
        val repository = TaskRepository.get(context)
        val task = repository.findById(id) ?: return
        repository.toggle(task)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
    }
}
