package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.example.todolists.data.Task
import com.example.todolists.data.TaskRepository
import com.example.todolists.ui.TaskTab
import kotlinx.coroutines.flow.first

class AllTasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = TaskRepository.get(context).tasks.first()
            .asSequence()
            .filter { !it.isSimple && !it.isDone }
            .sortedWith(
                compareByDescending<Task> { it.priority }
                    .thenBy(nullsLast()) { it.dueAt }
                    .thenByDescending { it.createdAt },
            )
            .take(MAX_ITEMS)
            .toList()

        provideContent {
            GlanceTheme {
                TaskListWidgetContent(
                    context = context,
                    title = "すべてのタスク",
                    tab = TaskTab.ALL,
                    addKind = AddKind.DETAILED,
                    showMeta = true,
                    emptyMessage = "未完了のタスクはありません",
                    items = items,
                )
            }
        }
    }

    companion object { private const val MAX_ITEMS = 20 }
}

class AllTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllTasksWidget()
}
