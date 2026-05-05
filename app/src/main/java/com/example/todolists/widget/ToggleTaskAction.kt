package com.example.todolists.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
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
        // The new state we're moving to:
        val willBeDone = !task.isDone
        repository.toggle(task)
        // Toast confirms the tap registered even if the launcher hasn't
        // re-rendered the widget yet (HyperOS / battery-savvy launchers
        // can take a moment).
        Handler(Looper.getMainLooper()).post {
            val message = if (willBeDone) "✓ 完了にしました" else "未完了に戻しました"
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
    }
}
