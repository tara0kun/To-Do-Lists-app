package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.example.todolists.data.TaskDatabase
import com.example.todolists.ui.TaskTab
import java.util.Calendar

class AllTasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (todayStart, tomorrowStart) = todayBoundsMillis()
        val items = TaskDatabase.get(context).taskDao()
            .detailedVisibleSnapshot(todayStart, tomorrowStart, MAX_ITEMS)
        val background = WidgetBackgroundLoader.load(context)

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
                    background = background,
                )
            }
        }
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

    companion object { private const val MAX_ITEMS = 30 }
}

class AllTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllTasksWidget()
}
