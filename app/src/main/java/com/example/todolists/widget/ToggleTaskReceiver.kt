package com.example.todolists.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todolists.data.TaskRepository
import kotlinx.coroutines.launch

/**
 * Receives clicks from the V2 widget list items (CheckBox toggles or row
 * taps). Looks up the task and flips its done state. Fires from the
 * RemoteViews list-item PendingIntent template + per-item fillInIntent.
 */
class ToggleTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id < 0L) return
        val newDone = intent.getBooleanExtra(EXTRA_NEW_DONE, false)

        val pending = goAsync()
        val app = context.applicationContext
        WidgetWorkScope.launch {
            try {
                val repo = TaskRepository.get(app)
                val task = repo.findById(id)
                if (task != null && task.isDone != newDone) {
                    // toggle() handles DB write, calendar cleanup, and
                    // calls refreshWidgets() which notifies the V2 list.
                    repo.toggle(task)
                } else {
                    // Already in desired state; nudge widgets so any
                    // optimistic-but-stale UI catches up.
                    WidgetUpdateHelper.notifyDataChanged(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.example.todolists.action.WIDGET_TOGGLE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NEW_DONE = "new_done"
    }
}
