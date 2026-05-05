package com.example.todolists.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.todolists.data.WidgetBackgroundRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetBackgroundSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { WidgetBackgroundRepository.get(context) }
    val current by repo.backgroundUri.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            repo.setBackgroundUri(uri)
            Toast.makeText(context, "背景画像を更新しました", Toast.LENGTH_LONG).show()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ウィジェット背景画像", style = MaterialTheme.typography.titleLarge)
            Text(
                "選んだ画像が全ウィジェットの背景に使われます。文字の可読性のため半透明の暗いオーバーレイが重なります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (current == null) "現在: 未設定（テーマ色）" else "現在: 画像を設定中",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (current == null) "画像を選択" else "画像を変更") }
                if (current != null) {
                    OutlinedButton(
                        onClick = {
                            repo.setBackgroundUri(null)
                            Toast.makeText(context, "背景画像を解除しました", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("解除") }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        }
    }
}
