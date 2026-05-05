package com.example.todolists.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.todolists.ui.theme.ToDoListsAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Lets the user pan & zoom a picked image inside a viewport, then saves the
 * visible region as a PNG in app-internal storage. Returned via the
 * [Contract] as a file:// Uri so the caller can store it the same way as
 * a normal picked Uri.
 */
class CropImageActivity : ComponentActivity() {

    object Contract : ActivityResultContract<Uri, Uri?>() {
        override fun createIntent(context: Context, input: Uri): Intent =
            Intent(context, CropImageActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, input.toString())
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            if (resultCode != RESULT_OK) return null
            val path = intent?.getStringExtra(EXTRA_RESULT) ?: return null
            return Uri.fromFile(File(path))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra(EXTRA_SOURCE)?.let { Uri.parse(it) }
        if (source == null) { finish(); return }
        setContent {
            ToDoListsAppTheme {
                CropImageScreen(
                    source = source,
                    onCancel = { setResult(RESULT_CANCELED); finish() },
                    onCropped = { file ->
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(EXTRA_RESULT, file.absolutePath),
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_RESULT = "result"
        const val OUTPUT_FILE_NAME = "widget_bg.png"
    }
}

@Composable
private fun CropImageScreen(
    source: Uri,
    onCancel: () -> Unit,
    onCropped: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var srcBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        val bm = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(source)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
        if (bm == null) loadFailed = true else srcBitmap = bm
    }

    if (loadFailed) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) { Text("画像を読み込めませんでした", color = Color.White) }
        return
    }
    val bm = srcBitmap
    if (bm == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 6f)
                        offset += pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
        Text(
            text = "ピンチで拡大、ドラッグで移動。表示されている枠の中身がウィジェットに使われます。",
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) {
                Text("キャンセル", color = Color.White)
            }
            TextButton(
                onClick = { scale = 1f; offset = Offset.Zero },
                enabled = !saving,
            ) { Text("リセット", color = Color.White) }
            Button(
                enabled = !saving && viewport.width > 0 && viewport.height > 0,
                onClick = {
                    saving = true
                    val capturedViewport = viewport
                    val capturedScale = scale
                    val capturedOffset = offset
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            renderCrop(
                                src = bm,
                                viewportW = capturedViewport.width,
                                viewportH = capturedViewport.height,
                                userScale = capturedScale,
                                translationX = capturedOffset.x,
                                translationY = capturedOffset.y,
                            )
                        }
                        val file = File(context.filesDir, CropImageActivity.OUTPUT_FILE_NAME)
                        withContext(Dispatchers.IO) {
                            FileOutputStream(file).use { fos ->
                                out.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            }
                        }
                        out.recycle()
                        onCropped(file)
                    }
                },
            ) { Text(if (saving) "保存中…" else "決定") }
        }
    }
}

private fun renderCrop(
    src: Bitmap,
    viewportW: Int,
    viewportH: Int,
    userScale: Float,
    translationX: Float,
    translationY: Float,
): Bitmap {
    // Cap output so we never write a massive PNG even on tablets.
    val maxOut = 1200
    val outScale = min(1f, maxOut.toFloat() / max(viewportW, viewportH))
    val outW = (viewportW * outScale).toInt().coerceAtLeast(1)
    val outH = (viewportH * outScale).toInt().coerceAtLeast(1)
    val dst = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(dst)
    canvas.drawColor(AndroidColor.BLACK)
    // Replicate the on-screen transform: ContentScale.Fit centers the
    // image, then graphicsLayer scales around the viewport center and
    // translates.
    val baseScale = min(viewportW.toFloat() / src.width, viewportH.toFloat() / src.height)
    val matrix = Matrix().apply {
        postScale(baseScale, baseScale)
        postTranslate(
            (viewportW - src.width * baseScale) / 2f,
            (viewportH - src.height * baseScale) / 2f,
        )
        postTranslate(-viewportW / 2f, -viewportH / 2f)
        postScale(userScale, userScale)
        postTranslate(viewportW / 2f, viewportH / 2f)
        postTranslate(translationX, translationY)
        if (outScale != 1f) postScale(outScale, outScale)
    }
    canvas.drawBitmap(src, matrix, null)
    return dst
}
