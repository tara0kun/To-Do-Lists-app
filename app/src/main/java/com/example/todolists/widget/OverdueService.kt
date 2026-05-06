package com.example.todolists.widget

import android.content.Context
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.example.todolists.data.Task
import com.example.todolists.data.TaskDatabase

class OverdueService : BaseTaskListService() {
    override fun createFactory(context: Context): RemoteViewsFactory =
        OverdueFactory(context)
}

private class OverdueFactory(context: Context) : BaseTaskListFactory(context) {
    override val showMeta = true
    override val tag = "OverdueFct"
    override suspend fun fetchItems(): List<Task> =
        TaskDatabase.get(context).taskDao()
            .overdueSnapshot(System.currentTimeMillis(), MAX_ITEMS)

    private companion object { const val MAX_ITEMS = 30 }
}
