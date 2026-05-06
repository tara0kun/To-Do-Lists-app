package com.example.todolists.widget

import android.content.Context
import com.example.todolists.data.Task
import com.example.todolists.data.TaskDatabase
import java.util.Calendar

class AllTasksService : BaseTaskListService() {
    override fun createFactory(context: Context) = AllTasksFactory(context)
}

private class AllTasksFactory(context: Context) : BaseTaskListFactory(context) {
    override val showMeta = true
    override val tag = "AllTasksFct"

    override suspend fun fetchItems(): List<Task> {
        val (todayStart, tomorrowStart) = todayBoundsMillis()
        return TaskDatabase.get(context).taskDao()
            .detailedVisibleSnapshot(todayStart, tomorrowStart, MAX_ITEMS)
    }

    private fun todayBoundsMillis(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val tomorrowStart = todayStart + 24L * 60L * 60L * 1000L
        return todayStart to tomorrowStart
    }

    private companion object { const val MAX_ITEMS = 30 }
}
