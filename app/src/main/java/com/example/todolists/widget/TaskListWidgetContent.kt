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
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import com.example.todolists.ui.TaskTab
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskListWidgetContent(
    context: Context,
    title: String,
    tab: TaskTab,
    addKind: AddKind,
    showMeta: Boolean,
    emptyMessage: String,
    filterTasks: (List<Task>) -> List<Task>,
) {
    var items by remember { mutableStateOf<List<Task>>(emptyList()) }

    LaunchedEffect(Unit) {
        items = filterTasks(TaskRepository.get(context).tasks.first())
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(8.dp),
    ) {
        HeaderBar(
            context = context,
            title = title,
            tab = tab,
            addKind = addKind,
        )
        Spacer(GlanceModifier.height(4.dp))
        if (items.isEmpty()) {
            EmptyHint(emptyMessage)
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.id }) { task ->
                    TaskListWidgetRow(task = task, showMeta = showMeta)
                }
            }
        }
    }
}

enum class AddKind { NONE, SIMPLE, DETAILED }

@Composable
private fun HeaderBar(
    context: Context,
    title: String,
    tab: TaskTab,
    addKind: AddKind,
) {
    val openTab = actionStartActivity(MainActivity.intentForTab(context, tab))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            modifier = GlanceModifier.defaultWeight().clickable(openTab),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
        if (addKind != AddKind.NONE) {
            val addIntent = QuickAddActivity.intent(
                context = context,
                simple = addKind == AddKind.SIMPLE,
            )
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(8.dp)
                    .clickable(actionStartActivity(addIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "追加",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskListWidgetRow(task: Task, showMeta: Boolean) {
    val toggleAction = actionRunCallback<ToggleTaskAction>(
        actionParametersOf(ToggleTaskAction.TaskIdKey to task.id),
    )
    val checkboxRes = if (task.isDone) R.drawable.ic_widget_checked else R.drawable.ic_widget_unchecked
    val checkboxTint = if (task.isDone) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clickable(toggleAction),
    ) {
        Image(
            provider = ImageProvider(checkboxRes),
            contentDescription = if (task.isDone) "未完了に戻す" else "完了にする",
            colorFilter = ColorFilter.tint(checkboxTint),
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = task.title,
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            if (showMeta && task.dueAt != null) {
                Text(
                    text = formatCompactDue(task.dueAt),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
            ),
        )
    }
}

private fun formatCompactDue(millis: Long): String {
    val df = SimpleDateFormat("M/d HH:mm", Locale.getDefault())
    return df.format(Date(millis))
}
