package com.example.todolists.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.todolists.data.WidgetBackgroundFit
import com.example.todolists.data.WidgetBackgroundRepository
import com.example.todolists.data.WidgetForegroundMode
import com.example.todolists.widget.WidgetUpdateHelper
import java.io.File

/** Curated palette for the custom widget foreground colour. */
private val WidgetCustomPalette: List<Long> = listOf(
    0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF000000,
    0xFFEF5350, 0xFFFF9800, 0xFFFFEB3B, 0xFF66BB6A,
    0xFF26A69A, 0xFF42A5F5, 0xFF5C6BC0, 0xFFAB47BC,
    0xFFEC407A, 0xFF8D6E63, 0xFF78909C, 0xFFFFD180,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetBackgroundSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { WidgetBackgroundRepository.get(context) }
    val state by repo.state.collectAsState()

    val cropLauncher = rememberLauncherForActivityResult(CropImageActivity.Contract) { result: Uri? ->
        if (result != null) {
            repo.setBackgroundUri(result)
            WidgetUpdateHelper.forceFullUpdate(context)
            Toast.makeText(context, "背景画像を更新しました", Toast.LENGTH_SHORT).show()
        }
    }

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
            cropLauncher.launch(uri)
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
                if (state.uri == null) "現在: 未設定（テーマ色）" else "現在: 画像を設定中",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.uri == null) "画像を選択" else "画像を変更") }
                if (state.uri != null) {
                    OutlinedButton(
                        onClick = {
                            File(context.filesDir, CropImageActivity.OUTPUT_FILE_NAME).delete()
                            repo.setBackgroundUri(null)
                            WidgetUpdateHelper.forceFullUpdate(context)
                            Toast.makeText(context, "背景画像を解除しました", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("解除") }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("文字・アイコンの色", style = MaterialTheme.typography.titleSmall)
            Text(
                "背景画像によっては既定だと見えにくくなる場合があります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WidgetForegroundMode.values().forEach { mode ->
                val pickMode = {
                    repo.setForegroundMode(mode)
                    WidgetUpdateHelper.forceFullUpdate(context)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.foregroundMode == mode,
                            onClick = pickMode,
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.foregroundMode == mode,
                        onClick = pickMode,
                    )
                    Text(mode.label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (state.foregroundMode == WidgetForegroundMode.CUSTOM) {
                Text(
                    "好きな色を選んでください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
                val rows = WidgetCustomPalette.chunked(8)
                rows.forEach { rowColors ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowColors.forEach { argb ->
                            val color = Color(argb)
                            val selected = state.customForegroundColor == argb.toInt()
                            val borderColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                            val borderWidth = if (selected) 3.dp else 1.dp
                            Spacer(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(borderWidth, borderColor, CircleShape)
                                    .clickable {
                                        repo.setCustomForegroundColor(argb.toInt())
                                        WidgetUpdateHelper.forceFullUpdate(context)
                                    },
                            )
                        }
                    }
                }
            }

            if (state.uri != null) {
                Spacer(Modifier.height(4.dp))
                Text("表示モード", style = MaterialTheme.typography.titleSmall)
                WidgetBackgroundFit.values().forEach { fit ->
                    val pickFit = {
                        repo.setFit(fit)
                        WidgetUpdateHelper.forceFullUpdate(context)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.fit == fit,
                                onClick = pickFit,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.fit == fit, onClick = pickFit)
                        Text(fit.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "背景の暗さ: ${(state.scrimAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "0% で画像そのまま、100% で完全に暗くなります（文字が読みやすくなる）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.scrimAlpha,
                    onValueChange = { repo.setScrimAlpha(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "画像を選ぶと、表示モードと暗さの設定ができます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    // Apply scrim slider changes to widgets when the user
                    // closes the sheet (we deliberately don't fire on every
                    // slider tick to avoid broadcast spam).
                    WidgetUpdateHelper.forceFullUpdate(context)
                    onDismiss()
                }) { Text("閉じる") }
            }
        }
    }
}
