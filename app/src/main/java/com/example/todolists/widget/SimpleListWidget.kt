package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import com.example.todolists.data.TaskRepository
import com.example.todolists.ui.TaskTab
import kotlinx.coroutines.flow.first

class SimpleListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = TaskRepository.get(context).tasks.first()
            .asSequence()
            .filter { it.isSimple && !it.isDone }
            .sortedByDescending { it.createdAt }
            .take(MAX_ITEMS)
            .toList()

        provideContent {
            GlanceTheme {
                TaskListWidgetContent(
                    context = context,
                    title = "簡易リスト",
                    tab = TaskTab.SIMPLE,
                    addKind = AddKind.SIMPLE,
                    showMeta = false,
                    emptyMessage = "簡易リストは空です",
                    items = items,
                )
            }
        }
    }

    companion object { private const val MAX_ITEMS = 20 }
}
