package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.example.todolists.ui.TaskTab

class OverdueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val now = System.currentTimeMillis()
                TaskListWidgetContent(
                    context = context,
                    title = "期限切れ",
                    tab = TaskTab.OVERDUE,
                    addKind = AddKind.NONE,
                    showMeta = true,
                    emptyMessage = "期限切れのタスクはありません",
                    filterTasks = { tasks ->
                        tasks.asSequence()
                            .filter { !it.isSimple && !it.isDone && it.dueAt != null && it.dueAt < now }
                            .sortedBy { it.dueAt }
                            .take(MAX_ITEMS)
                            .toList()
                    },
                )
            }
        }
    }

    companion object { private const val MAX_ITEMS = 20 }
}

class OverdueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverdueWidget()
}
