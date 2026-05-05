package com.example.todolists.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.currentState
import androidx.glance.layout.ContentScale
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import com.example.todolists.ui.TaskTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AddKind { NONE, SIMPLE, DETAILED }

@Composable
fun TaskListWidgetContent(
    context: Context,
    title: String,
    tab: TaskTab,
    addKind: AddKind,
    showMeta: Boolean,
    emptyMessage: String,
    items: List<Task>,
    backgroundBitmap: Bitmap? = null,
) {
    Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)) {
        if (backgroundBitmap != null) {
            Image(
                provider = ImageProvider(backgroundBitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp),
            )
            // Semi-transparent scrim for text readability over the image.
            Image(
                provider = ImageProvider(R.drawable.widget_scrim),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp),
            )
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .let {
                    if (backgroundBitmap == null) it.background(GlanceTheme.colors.surface) else it
                }
                .cornerRadius(16.dp)
                .padding(8.dp),
        ) {
            HeaderBar(
                context = context,
                title = title,
                tab = tab,
                addKind = addKind,
                hasBackground = backgroundBitmap != null,
            )
            Spacer(GlanceModifier.height(4.dp))
            if (items.isEmpty()) {
                EmptyHint(emptyMessage, hasBackground = backgroundBitmap != null)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items, itemId = { it.id }) { task ->
                        TaskListWidgetRow(
                            task = task,
                            showMeta = showMeta,
                            forceLightText = backgroundBitmap != null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    context: Context,
    title: String,
    tab: TaskTab,
    addKind: AddKind,
    hasBackground: Boolean,
) {
    val openTab = actionStartActivity(MainActivity.intentForTab(context, tab))
    val refreshAction = actionRunCallback<RefreshWidgetAction>()
    val textColor = if (hasBackground) androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White) else GlanceTheme.colors.onSurface
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
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(8.dp)
                .clickable(refreshAction),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "更新",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.size(16.dp),
            )
        }
        if (addKind != AddKind.NONE) {
            Spacer(GlanceModifier.width(4.dp))
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
private fun TaskListWidgetRow(task: Task, showMeta: Boolean, forceLightText: Boolean = false) {
    val state = currentState<Preferences>()
    val optimistic = state[ToggleTaskAction.optimisticKey(task.id)]
    val effectiveDone = optimistic ?: task.isDone

    val toggleAction = actionRunCallback<ToggleTaskAction>(
        actionParametersOf(
            ToggleTaskAction.TaskIdKey to task.id,
            ToggleTaskAction.WasDoneKey to effectiveDone,
        ),
    )
    val checkboxRes = if (effectiveDone) R.drawable.ic_widget_checked else R.drawable.ic_widget_unchecked
    val whiteColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White)
    val whiteFaintColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFE0E0E0))
    val titleColor = if (forceLightText) whiteColor else GlanceTheme.colors.onSurface
    val metaColor = if (forceLightText) whiteFaintColor else GlanceTheme.colors.onSurfaceVariant
    val checkboxTint = when {
        effectiveDone && forceLightText -> whiteColor
        effectiveDone -> GlanceTheme.colors.primary
        forceLightText -> whiteFaintColor
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clickable(toggleAction),
    ) {
        Image(
            provider = ImageProvider(checkboxRes),
            contentDescription = if (effectiveDone) "未完了に戻す" else "完了にする",
            colorFilter = ColorFilter.tint(checkboxTint),
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = task.title,
                maxLines = 2,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 14.sp,
                    textDecoration = if (effectiveDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            if (showMeta && task.dueAt != null) {
                Text(
                    text = formatCompactDue(task.dueAt),
                    style = TextStyle(
                        color = metaColor,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(message: String, hasBackground: Boolean = false) {
    val whiteFaint = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFE0E0E0))
    val color = if (hasBackground) whiteFaint else GlanceTheme.colors.onSurfaceVariant
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = color,
                fontSize = 13.sp,
            ),
        )
    }
}

private fun formatCompactDue(millis: Long): String {
    val df = SimpleDateFormat("M/d HH:mm", Locale.getDefault())
    return df.format(Date(millis))
}
