package com.example.todolists.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.todolists.R
import com.example.todolists.calendar.CalendarIntegration
import com.example.todolists.data.Priority
import com.example.todolists.data.Task
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAddSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCalendarSettings by remember { mutableStateOf(false) }
    var showWidgetBackground by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    val isSimpleTab = uiState.selectedTab == TaskTab.SIMPLE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "外観")
                    }
                    IconButton(onClick = { showWidgetBackground = true }) {
                        Icon(Icons.Filled.Wallpaper, contentDescription = "ウィジェット背景")
                    }
                    IconButton(onClick = { showCalendarSettings = true }) {
                        Icon(Icons.Filled.Event, contentDescription = "カレンダー連携先")
                    }
                    if (!isSimpleTab) {
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "並び替え")
                        }
                    }
                    if (uiState.hasCompleted) {
                        IconButton(onClick = viewModel::clearCompleted) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_completed),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isSimpleTab) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_task)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TaskTabRow(
                selected = uiState.selectedTab,
                counts = uiState.counts,
                onSelect = viewModel::selectTab,
            )
            if (isSimpleTab) {
                SimpleQuickAddRow(onAdd = viewModel::addSimple)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                when {
                    uiState.total == 0 && !isSimpleTab -> EmptyState(stringResource(R.string.empty_state))
                    uiState.sections.isEmpty() -> EmptyState(uiState.emptyMessage ?: "")
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = if (isSimpleTab) 24.dp else 96.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        uiState.sections.forEach { section ->
                            if (section.label != null) {
                                item(key = "header_${section.key}") {
                                    SectionHeader(section.label)
                                }
                            }
                            items(section.tasks, key = { "${section.key}_${it.id}" }) { task ->
                                TaskRow(
                                    task = task,
                                    onToggle = { viewModel.toggle(task) },
                                    onDelete = { viewModel.delete(task) },
                                    onClick = { if (!task.isSimple) editingTask = task },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddEditTaskSheet(
            onDismiss = { showAddSheet = false },
            onSubmit = { draft ->
                coroutineScope.launch {
                    val eventId = if (draft.addToCalendar && draft.combinedDueAt != null) {
                        CalendarIntegration.linkEvent(
                            context,
                            Task(
                                title = draft.title.trim(),
                                dueAt = draft.combinedDueAt,
                                remindAtDue = draft.remindAtDue,
                                remindOnDay = draft.remindOnDay,
                                remindOnDayHour = draft.onDayHour,
                                remindOnDayMinute = draft.onDayMinute,
                            ),
                        )
                    } else null
                    viewModel.add(
                        title = draft.title,
                        dueAt = draft.combinedDueAt,
                        remindAtDue = draft.remindAtDue && draft.combinedDueAt != null,
                        remindOnDay = draft.remindOnDay && draft.dueDateMillis != null,
                        remindOnDayHour = draft.onDayHour,
                        remindOnDayMinute = draft.onDayMinute,
                        priority = draft.priority.storageValue,
                        calendarEventId = eventId,
                    )
                }
            },
            submitLabel = "追加",
        )
    }

    editingTask?.let { task ->
        AddEditTaskSheet(
            initial = task.toDraft(),
            onDismiss = { editingTask = null },
            onSubmit = { draft ->
                coroutineScope.launch {
                    val newEventId = if (draft.addToCalendar && draft.combinedDueAt != null) {
                        CalendarIntegration.linkEvent(
                            context,
                            task.copy(
                                title = draft.title.trim(),
                                dueAt = draft.combinedDueAt,
                                remindAtDue = draft.remindAtDue,
                                remindOnDay = draft.remindOnDay,
                                remindOnDayHour = draft.onDayHour,
                                remindOnDayMinute = draft.onDayMinute,
                            ),
                        )
                    } else null
                    val updated = task.copy(
                        title = draft.title.trim(),
                        dueAt = draft.combinedDueAt,
                        remindAtDue = draft.remindAtDue && draft.combinedDueAt != null,
                        remindOnDay = draft.remindOnDay && draft.dueDateMillis != null,
                        remindOnDayHour = draft.onDayHour,
                        remindOnDayMinute = draft.onDayMinute,
                        priority = draft.priority.storageValue,
                        calendarEventId = newEventId ?: task.calendarEventId,
                    )
                    viewModel.update(updated)
                }
            },
            submitLabel = "保存",
        )
    }

    if (showCalendarSettings) {
        CalendarSettingsSheet(onDismiss = { showCalendarSettings = false })
    }

    if (showWidgetBackground) {
        WidgetBackgroundSheet(onDismiss = { showWidgetBackground = false })
    }

    if (showAppearance) {
        AppearanceSheet(onDismiss = { showAppearance = false })
    }

    if (showSortSheet) {
        SortOptionsSheet(
            preferences = uiState.preferences,
            onDismiss = { showSortSheet = false },
            onSortModeChange = viewModel::setSortMode,
            onSeparateCompletedChange = viewModel::setSeparateCompleted,
            onOverdueOnTopChange = viewModel::setOverdueOnTop,
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val overdue = !task.isDone && task.dueAt != null && task.dueAt < now

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!task.isSimple) {
                        PriorityDot(task.priorityEnum)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = task.title,
                        style = if (task.isDone) {
                            MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = TextDecoration.LineThrough,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (!task.isSimple && task.priorityEnum == Priority.HIGH) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
                if (!task.isSimple && task.dueAt != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp),
                            tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDueAt(task.dueAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (task.remindAtDue) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = "期限時刻に通知",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (task.remindOnDay) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "当日に通知",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_task),
                )
            }
        }
    }
}

@Composable
private fun PriorityDot(priority: Priority) {
    val color = when (priority) {
        Priority.HIGH -> Color(0xFFE53935)
        Priority.MEDIUM -> Color(0xFFF9A825)
        Priority.LOW -> Color(0xFF43A047)
        Priority.SOMEDAY -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskTabRow(
    selected: TaskTab,
    counts: TabCounts,
    onSelect: (TaskTab) -> Unit,
) {
    val tabs = TaskTab.values()
    TabRow(selectedTabIndex = tabs.indexOf(selected)) {
        tabs.forEach { tab ->
            val count = when (tab) {
                TaskTab.ALL -> counts.all
                TaskTab.SIMPLE -> counts.simple
                TaskTab.OVERDUE -> counts.overdue
                TaskTab.COMPLETED -> counts.completed
            }
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = {
                    if (count > 0 && tab != TaskTab.ALL) {
                        BadgedBox(badge = { Badge { Text(count.toString()) } }) {
                            Text(tab.label, modifier = Modifier.padding(end = 6.dp))
                        }
                    } else {
                        Text(tab.label)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleQuickAddRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("買い物・やる事などを入力") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = ::submit, enabled = text.isNotBlank()) {
            Icon(Icons.Filled.Send, contentDescription = "追加")
        }
    }
}

private fun formatDueAt(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(millis))
