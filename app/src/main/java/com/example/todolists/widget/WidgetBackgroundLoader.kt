package com.example.todolists.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.todolists.data.WidgetBackgroundFit
import com.example.todolists.data.WidgetBackgroundRepository
import com.example.todolists.data.WidgetBackgroundSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the user-picked widget background as a downscaled bitmap, plus the
 * settings (scrim opacity, fit mode) the widget needs to render it.
 * RemoteViews bundles have a hard size limit (~1MB), so we never decode at
 * full resolution.
 */
data class LoadedBackground(
    val bitmap: Bitmap,
    val scrimAlpha: Float,
    val fit: WidgetBackgroundFit,
)

internal object WidgetBackgroundLoader {

    private const val MAX_DIMENSION = 600

    suspend fun load(context: Context): LoadedBackground? {
        val settings = WidgetBackgroundRepository.get(context).state.value
        val uri = settings.uri ?: return null
        val bitmap = decodeScaled(context, uri) ?: return null
        return LoadedBackground(bitmap, settings.scrimAlpha, settings.fit)
    }

    private suspend fun decodeScaled(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight)
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }

    private fun computeSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DIMENSION || height / sample > MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }
}
