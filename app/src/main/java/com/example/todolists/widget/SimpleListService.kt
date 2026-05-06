package com.example.todolists.widget

import android.content.Context
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.example.todolists.data.Task
import com.example.todolists.data.TaskDatabase

class SimpleListService : BaseTaskListService() {
    override fun createFactory(context: Context): RemoteViewsFactory =
        SimpleListFactory(context)
}

private class SimpleListFactory(context: Context) : BaseTaskListFactory(context) {
    override val showMeta = false
    override val tag = "SimpleListFct"
    override suspend fun fetchItems(): List<Task> =
        TaskDatabase.get(context).taskDao().simpleVisibleSnapshot(MAX_ITEMS)

    private companion object { const val MAX_ITEMS = 30 }
}
