package com.example.todolists.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.todolists.calendar.CalendarIntegration
import com.example.todolists.data.CalendarSettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settingsRepo = remember { CalendarSettingsRepository.get(context) }
    val savedId by settingsRepo.selectedCalendarId.collectAsState()

    var calendars by remember { mutableStateOf<List<CalendarIntegration.CalendarInfo>>(emptyList()) }
    var selectedId by remember { mutableStateOf(savedId) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        calendars = CalendarIntegration.listAvailableCalendars(context)
        loaded = true
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("カレンダー連携先", style = MaterialTheme.typography.titleLarge)
            Text(
                "タスクを登録するカレンダーを選んでください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                !loaded -> {
                    Text("読み込み中…", style = MaterialTheme.typography.bodyMedium)
                }
                calendars.isEmpty() -> {
                    Text(
                        "書き込み可能なカレンダーが見つかりません。" +
                            "カレンダー権限と Google アカウントの同期を確認してください。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            CalendarRow(
                                title = "自動（Google アカウントを優先）",
                                subtitle = "おすすめの初期設定",
                                selected = selectedId == null,
                                onClick = { selectedId = null },
                            )
                        }
                        items(calendars, key = { it.id }) { cal ->
                            CalendarRow(
                                title = cal.displayName.ifBlank { cal.accountName },
                                subtitle = "${cal.accountName} (${cal.accountType})" +
                                    if (cal.isPrimary) " ⭐" else "",
                                selected = selectedId == cal.id,
                                onClick = { selectedId = cal.id },
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                TextButton(onClick = {
                    settingsRepo.setSelectedCalendarId(selectedId)
                    onDismiss()
                }) { Text("保存") }
            }
        }
    }
}

@Composable
private fun CalendarRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Box(modifier = Modifier.padding(start = 8.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
