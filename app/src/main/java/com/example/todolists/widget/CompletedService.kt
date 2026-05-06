package com.example.todolists.widget

import android.content.Context
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.example.todolists.data.Task
import com.example.todolists.data.TaskDatabase

class CompletedService : BaseTaskListService() {
    override fun createFactory(context: Context): RemoteViewsFactory =
        CompletedFactory(context)
}

private class CompletedFactory(context: Context) : BaseTaskListFactory(context) {
    override val showMeta = true
    override val tag = "CompletedFct"
    override suspend fun fetchItems(): List<Task> =
        TaskDatabase.get(context).taskDao().completedDetailedSnapshot(MAX_ITEMS)

    private companion object { const val MAX_ITEMS = 20 }
}
