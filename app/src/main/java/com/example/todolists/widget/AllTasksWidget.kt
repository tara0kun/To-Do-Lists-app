package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.todolists.ui.TaskTab

class AllTasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                TaskListWidgetContent(
                    context = context,
                    title = "すべてのタスク",
                    tab = TaskTab.ALL,
                    addKind = AddKind.DETAILED,
                    showMeta = true,
                    emptyMessage = "未完了のタスクはありません",
                    filterTasks = { tasks ->
                        tasks.asSequence()
                            .filter { !it.isSimple && !it.isDone }
                            .sortedWith(
                                compareByDescending<com.example.todolists.data.Task> { it.priority }
                                    .thenBy(nullsLast()) { it.dueAt }
                                    .thenByDescending { it.createdAt }
                            )
                            .take(MAX_ITEMS)
                            .toList()
                    },
                )
            }
        }
    }

    companion object { private const val MAX_ITEMS = 20 }
}

class AllTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllTasksWidget()
}
