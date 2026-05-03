package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.example.todolists.data.TaskDatabase
import com.example.todolists.ui.TaskTab

class AllTasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = TaskDatabase.get(context).taskDao().detailedActiveSnapshot(MAX_ITEMS)

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
