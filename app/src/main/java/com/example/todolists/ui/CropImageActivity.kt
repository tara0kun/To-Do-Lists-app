package com.example.todolists.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.todolists.ui.theme.ToDoListsAppTheme
import com.example.todolists.widget.AllTasksWidgetReceiver
import com.example.todolists.widget.CompletedWidgetReceiver
import com.example.todolists.widget.OverdueWidgetReceiver
import com.example.todolists.widget.SimpleListWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Lets the user pan & zoom a picked image inside a viewport, then saves the
 * region inside a widget-shaped crop frame as a PNG in app-internal storage.
 * The crop frame's aspect ratio mirrors the largest currently-placed widget
 * so what the user lines up here is what they see on the home screen.
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

private data class WidgetTargetSize(val widthDp: Int, val heightDp: Int) {
    val aspect: Float get() = widthDp.toFloat() / heightDp.toFloat()

    companion object {
        // ~ 4 cells wide x 2 cells tall: the typical Android widget
        // shape, used as a fallback if no widget is currently placed.
        val Default = WidgetTargetSize(280, 220)
    }
}

private fun queryPlacedWidgetSize(context: Context): WidgetTargetSize {
    val mgr = AppWidgetManager.getInstance(context)
    val classes = listOf(
        SimpleListWidgetReceiver::class.java,
        AllTasksWidgetReceiver::class.java,
        OverdueWidgetReceiver::class.java,
        CompletedWidgetReceiver::class.java,
    )
    var bestArea = 0
    var bestSize: WidgetTargetSize? = null
    for (cls in classes) {
        val ids = runCatching { mgr.getAppWidgetIds(ComponentName(context, cls)) }
            .getOrNull() ?: continue
        for (id in ids) {
            val opts = runCatching { mgr.getAppWidgetOptions(id) }
                .getOrNull() ?: continue
            val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            if (w > 0 && h > 0) {
                val area = w * h
                if (area > bestArea) {
                    bestArea = area
                    bestSize = WidgetTargetSize(w, h)
                }
            }
        }
    }
    return bestSize ?: WidgetTargetSize.Default
}

@Composable
private fun CropImageScreen(
    source: Uri,
    onCancel: () -> Unit,
    onCropped: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val widgetSize = remember { queryPlacedWidgetSize(context) }

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

    // Crop frame: a centered rectangle whose aspect ratio matches the
    // widget the user actually placed on their home screen.
    val cropFrame: Rect? = remember(viewport, widgetSize) {
        if (viewport.width <= 0 || viewport.height <= 0) return@remember null
        val maxFrameW = viewport.width * 0.92f
        val maxFrameH = viewport.height * 0.92f
        val aspect = widgetSize.aspect
        val frameW: Float
        val frameH: Float
        if (maxFrameW / aspect <= maxFrameH) {
            frameW = maxFrameW
            frameH = maxFrameW / aspect
        } else {
            frameH = maxFrameH
            frameW = maxFrameH * aspect
        }
        val frameLeft = (viewport.width - frameW) / 2f
        val frameTop = (viewport.height - frameH) / 2f
        Rect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH)
    }

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
            // Dim outside the frame, draw the frame itself + L-shaped
            // corner markers in a single Canvas overlay.
            if (cropFrame != null) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val r = cropFrame
                    val dim = Color.Black.copy(alpha = 0.55f)
                    drawRect(dim, Offset(0f, 0f), Size(w, r.top))
                    drawRect(dim, Offset(0f, r.bottom), Size(w, h - r.bottom))
                    drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
                    drawRect(dim, Offset(r.right, r.top), Size(w - r.right, r.height))
                    val border = 1.dp.toPx()
                    drawRect(
                        color = Color.White.copy(alpha = 0.7f),
                        topLeft = Offset(r.left, r.top),
                        size = Size(r.width, r.height),
                        style = Stroke(width = border),
                    )
                    val len = 24.dp.toPx()
                    val thick = 4.dp.toPx()
                    val white = Color.White
                    drawLine(white, Offset(r.left, r.top), Offset(r.left + len, r.top), thick, cap = StrokeCap.Square)
                    drawLine(white, Offset(r.left, r.top), Offset(r.left, r.top + len), thick, cap = StrokeCap.Square)
                    drawLine(white, Offset(r.right - len, r.top), Offset(r.right, r.top), thick, cap = StrokeCap.Square)
                    drawLine(white, Offset(r.right, r.top), Offset(r.right, r.top + len), thick, cap = StrokeCap.Square)
                    drawLine(white, Offset(r.left, r.bottom - len), Offset(r.left, r.bottom), thick, cap = StrokeCap.Square)
                    drawLine(white, Offset(r.left, r.bottom), Offset(r.left + len, r.bottom), thick, cap = StrokeCap.Square)
                    drawLine(white, Offset(r.right - len, r.bottom), Offset(r.right, r.bottom), thick, cap = StrokeCap.Square)
                    drawLine(white, Offset(r.right, r.bottom - len), Offset(r.right, r.bottom), thick, cap = StrokeCap.Square)
                }
            }
        }
        Text(
            text = "ピンチで拡大、ドラッグで移動。枠の中身が ${widgetSize.widthDp}×${widgetSize.heightDp}dp のウィジェットに使われます。",
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
                enabled = !saving && cropFrame != null,
                onClick = {
                    val frame = cropFrame ?: return@Button
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
                                frameLeft = frame.left,
                                frameTop = frame.top,
                                frameW = frame.width,
                                frameH = frame.height,
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
    frameLeft: Float,
    frameTop: Float,
    frameW: Float,
    frameH: Float,
    userScale: Float,
    translationX: Float,
    translationY: Float,
): Bitmap {
    // Cap output so we never write a massive PNG even on tablets.
    val maxOut = 1200
    val outScale = min(1f, maxOut.toFloat() / max(frameW, frameH))
    val outW = (frameW * outScale).toInt().coerceAtLeast(1)
    val outH = (frameH * outScale).toInt().coerceAtLeast(1)
    val dst = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(dst)
    canvas.drawColor(AndroidColor.BLACK)
    // Replicate the on-screen transform: ContentScale.Fit centers the
    // image in the viewport, graphicsLayer scales around the viewport
    // center and translates, then we shift so the crop frame's top-left
    // becomes (0, 0) in the output bitmap.
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
        postTranslate(-frameLeft, -frameTop)
        if (outScale != 1f) postScale(outScale, outScale)
    }
    canvas.drawBitmap(src, matrix, null)
    return dst
}
