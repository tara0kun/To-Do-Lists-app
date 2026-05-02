package com.example.todolists.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.todolists.data.Task
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TaskDraft(
    val title: String = "",
    val dueDateMillis: Long? = null,
    val dueHour: Int? = null,
    val dueMinute: Int? = null,
    val remindAtDue: Boolean = false,
    val remindOnDay: Boolean = false,
    val onDayHour: Int = 9,
    val onDayMinute: Int = 0,
    val addToCalendar: Boolean = false,
) {
    val combinedDueAt: Long?
        get() {
            val date = dueDateMillis ?: return null
            val cal = Calendar.getInstance().apply {
                timeInMillis = date
                set(Calendar.HOUR_OF_DAY, dueHour ?: 0)
                set(Calendar.MINUTE, dueMinute ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
}

fun Task.toDraft(): TaskDraft {
    val cal = dueAt?.let { Calendar.getInstance().apply { timeInMillis = it } }
    return TaskDraft(
        title = title,
        dueDateMillis = dueAt,
        dueHour = cal?.get(Calendar.HOUR_OF_DAY),
        dueMinute = cal?.get(Calendar.MINUTE),
        remindAtDue = remindAtDue,
        remindOnDay = remindOnDay,
        onDayHour = remindOnDayHour,
        onDayMinute = remindOnDayMinute,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskSheet(
    initial: TaskDraft = TaskDraft(),
    onDismiss: () -> Unit,
    onSubmit: (TaskDraft) -> Unit,
    submitLabel: String = "追加",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDueTimePicker by remember { mutableStateOf(false) }
    var showOnDayTimePicker by remember { mutableStateOf(false) }

    fun close(after: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            after()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (submitLabel == "追加") "新しいタスク" else "タスクを編集",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text("タイトル") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("期限（任意）", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Event, contentDescription = null)
                    Spacer(Modifier.padding(2.dp))
                    Text(draft.dueDateMillis?.let { formatDate(it) } ?: "日付を選択")
                }
                OutlinedButton(
                    onClick = { showDueTimePicker = true },
                    enabled = draft.dueDateMillis != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null)
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        if (draft.dueHour != null) formatTime(draft.dueHour!!, draft.dueMinute ?: 0)
                        else "時刻を選択",
                    )
                }
            }
            if (draft.dueDateMillis != null) {
                TextButton(onClick = {
                    draft = draft.copy(
                        dueDateMillis = null,
                        dueHour = null,
                        dueMinute = null,
                        remindAtDue = false,
                        remindOnDay = false,
                    )
                }) {
                    Text("期限をクリア")
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("リマインダー", style = MaterialTheme.typography.titleSmall)

            ReminderRow(
                label = "期限時刻に通知",
                enabled = draft.combinedDueAt != null,
                checked = draft.remindAtDue,
                onCheckedChange = { draft = draft.copy(remindAtDue = it) },
            )
            ReminderRow(
                label = "当日に通知",
                enabled = draft.dueDateMillis != null,
                checked = draft.remindOnDay,
                onCheckedChange = { draft = draft.copy(remindOnDay = it) },
            )
            if (draft.remindOnDay && draft.dueDateMillis != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("当日通知の時刻", modifier = Modifier.weight(1f))
                    AssistChip(
                        onClick = { showOnDayTimePicker = true },
                        label = { Text(formatTime(draft.onDayHour, draft.onDayMinute)) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("カレンダー連携", style = MaterialTheme.typography.titleSmall)
            ReminderRow(
                label = "Googleカレンダーにも登録",
                enabled = draft.combinedDueAt != null,
                checked = draft.addToCalendar,
                onCheckedChange = { draft = draft.copy(addToCalendar = it) },
            )
            if (draft.combinedDueAt == null) {
                Text(
                    text = "期限を設定するとカレンダーに登録できます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { close() }, modifier = Modifier.weight(1f)) {
                    Text("キャンセル")
                }
                Button(
                    onClick = {
                        if (draft.title.isNotBlank()) {
                            close { onSubmit(draft) }
                        }
                    },
                    enabled = draft.title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(submitLabel)
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.dueDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        // Convert UTC midnight to local-zone same calendar date
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utc
                        }
                        val local = Calendar.getInstance().apply {
                            set(Calendar.YEAR, cal.get(Calendar.YEAR))
                            set(Calendar.MONTH, cal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        draft = draft.copy(dueDateMillis = local.timeInMillis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showDueTimePicker) {
        TimePickerDialog(
            initialHour = draft.dueHour ?: 9,
            initialMinute = draft.dueMinute ?: 0,
            onDismiss = { showDueTimePicker = false },
            onConfirm = { h, m ->
                draft = draft.copy(dueHour = h, dueMinute = m)
                showDueTimePicker = false
            },
        )
    }

    if (showOnDayTimePicker) {
        TimePickerDialog(
            initialHour = draft.onDayHour,
            initialMinute = draft.onDayMinute,
            onDismiss = { showOnDayTimePicker = false },
            onConfirm = { h, m ->
                draft = draft.copy(onDayHour = h, onDayMinute = m)
                showOnDayTimePicker = false
            },
        )
    }
}

@Composable
private fun ReminderRow(
    label: String,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked && enabled, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("時刻を選択", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

private fun formatDate(millis: Long): String {
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
    return df.format(Date(millis))
}

private fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
