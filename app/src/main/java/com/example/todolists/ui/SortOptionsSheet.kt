package com.example.todolists.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todolists.data.SortMode
import com.example.todolists.data.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionsSheet(
    preferences: UserPreferences,
    onDismiss: () -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onSeparateCompletedChange: (Boolean) -> Unit,
    onOverdueOnTopChange: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("並び替え", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            SortMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = preferences.sortMode == mode,
                            onClick = { onSortModeChange(mode) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = preferences.sortMode == mode,
                        onClick = { onSortModeChange(mode) },
                    )
                    Text(mode.label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text("表示オプション", style = MaterialTheme.typography.titleMedium)

            ToggleRow(
                label = "完了済みを別セクションに分ける",
                checked = preferences.separateCompleted,
                onCheckedChange = onSeparateCompletedChange,
            )
            ToggleRow(
                label = "期限切れを上に固定",
                checked = preferences.overdueOnTop,
                onCheckedChange = onOverdueOnTopChange,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
