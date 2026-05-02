package com.example.todolists.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.example.todolists.MainActivity
import com.example.todolists.R
import com.example.todolists.data.Task
import com.example.todolists.data.TaskRepository
import kotlinx.coroutines.flow.first

class SimpleListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                SimpleListContent(context = context)
            }
        }
    }

    @Composable
    private fun SimpleListContent(context: Context) {
        var items by remember { mutableStateOf<List<Task>>(emptyList()) }

        LaunchedEffect(Unit) {
            items = TaskRepository.get(context).tasks.first()
                .filter { it.isSimple && !it.isDone }
                .sortedByDescending { it.createdAt }
                .take(MAX_ITEMS)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(8.dp),
        ) {
            HeaderBar(context)
            Spacer(GlanceModifier.height(4.dp))
            if (items.isEmpty()) {
                EmptyHint()
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items, itemId = { it.id }) { task ->
                        SimpleRow(task)
                    }
                }
            }
        }
    }

    @Composable
    private fun HeaderBar(context: Context) {
        val openSimpleTab = actionStartActivity(
            MainActivity.intentForSimpleTab(context)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "簡易リスト",
                modifier = GlanceModifier.defaultWeight().clickable(openSimpleTab),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                ),
            )
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(8.dp)
                    .clickable(openSimpleTab),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "簡易リストを追加",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size(18.dp),
                )
            }
        }
    }

    @Composable
    private fun SimpleRow(task: Task) {
        val toggleAction = actionRunCallback<ToggleSimpleTaskAction>(
            actionParametersOf(ToggleSimpleTaskAction.TaskIdKey to task.id),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .clickable(toggleAction),
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_unchecked),
                contentDescription = "完了にする",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(18.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = task.title,
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }

    @Composable
    private fun EmptyHint() {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "簡易リストは空です",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                ),
            )
        }
    }

    companion object {
        private const val MAX_ITEMS = 12
    }
}
