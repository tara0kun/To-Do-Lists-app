package com.example.todolists.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import com.example.todolists.data.TaskDatabase
import com.example.todolists.ui.TaskTab

class SimpleListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = TaskDatabase.get(context).taskDao().simpleVisibleSnapshot(MAX_ITEMS)

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

    companion object { private const val MAX_ITEMS = 30 }
}
