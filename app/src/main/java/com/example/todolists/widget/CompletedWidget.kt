package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.example.todolists.data.TaskRepository
import com.example.todolists.ui.TaskTab
import kotlinx.coroutines.flow.first

class CompletedWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = TaskRepository.get(context).tasks.first()
            .asSequence()
            .filter { !it.isSimple && it.isDone }
            .sortedByDescending { it.createdAt }
            .take(MAX_ITEMS)
            .toList()

        provideContent {
            GlanceTheme {
                TaskListWidgetContent(
                    context = context,
                    title = "完了済み",
                    tab = TaskTab.COMPLETED,
                    addKind = AddKind.NONE,
                    showMeta = true,
                    emptyMessage = "完了済みのタスクはありません",
                    items = items,
                )
            }
        }
    }

    companion object { private const val MAX_ITEMS = 20 }
}

class CompletedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompletedWidget()
}
