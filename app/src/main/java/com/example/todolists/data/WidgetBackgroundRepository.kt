package com.example.todolists.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WidgetBackgroundFit(val label: String) {
    /** 中央でクロップしてはみ出る部分は切り捨て (デフォルト)。 */
    CROP("切り抜き（推奨）"),
    /** アスペクト比保ったまま全体を表示。余白が出る。 */
    FIT("全体を表示"),
    /** ウィジェット枠に合わせて引き伸ばす。 */
    FILL("引き伸ばし"),
}

enum class WidgetForegroundMode(val label: String) {
    /** 背景画像があれば白系、なければシステムテーマに追従。 */
    AUTO("自動 (背景に応じて切替)"),
    /** タイトル・アイコン・空メッセージを常に白系に。 */
    LIGHT("白系 (明るい色)"),
    /** タイトル・アイコン・空メッセージを常に黒系に。 */
    DARK("黒系 (暗い色)"),
    /** ユーザーが選んだ色（[WidgetBackgroundSettings.customForegroundColor]）。 */
    CUSTOM("カスタム色");

    companion object { val DEFAULT = AUTO }
}

data class WidgetBackgroundSettings(
    val uri: Uri? = null,
    val scrimAlpha: Float = 0.50f,
    val fit: WidgetBackgroundFit = WidgetBackgroundFit.CROP,
    val foregroundMode: WidgetForegroundMode = WidgetForegroundMode.DEFAULT,
    /** ARGB int. Only used when [foregroundMode] is CUSTOM. Defaults to white. */
    val customForegroundColor: Int = 0xFFFFFFFF.toInt(),
)

class WidgetBackgroundRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<WidgetBackgroundSettings> = _state.asStateFlow()

    /** Convenience flow for code paths that only care about the URI. */
    val backgroundUri: StateFlow<Uri?>
        get() = _backgroundUriFlow

    private val _backgroundUriFlow = MutableStateFlow(_state.value.uri)

    fun setBackgroundUri(uri: Uri?) = update { it.copy(uri = uri) }
    fun setScrimAlpha(alpha: Float) = update { it.copy(scrimAlpha = alpha.coerceIn(0f, 1f)) }
    fun setFit(fit: WidgetBackgroundFit) = update { it.copy(fit = fit) }
    fun setForegroundMode(mode: WidgetForegroundMode) = update { it.copy(foregroundMode = mode) }
    fun setCustomForegroundColor(color: Int) = update { it.copy(customForegroundColor = color) }

    private fun update(transform: (WidgetBackgroundSettings) -> WidgetBackgroundSettings) {
        val next = transform(_state.value)
        prefs.edit().apply {
            if (next.uri == null) remove(KEY_URI) else putString(KEY_URI, next.uri.toString())
            putFloat(KEY_SCRIM, next.scrimAlpha)
            putString(KEY_FIT, next.fit.name)
            putString(KEY_FG_MODE, next.foregroundMode.name)
            putInt(KEY_FG_COLOR, next.customForegroundColor)
        }.apply()
        _state.value = next
        _backgroundUriFlow.value = next.uri
    }

    private fun load(): WidgetBackgroundSettings {
        val uri = prefs.getString(KEY_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val scrim = prefs.getFloat(KEY_SCRIM, 0.50f).coerceIn(0f, 1f)
        val fit = prefs.getString(KEY_FIT, null)
            ?.let { runCatching { WidgetBackgroundFit.valueOf(it) }.getOrNull() }
            ?: WidgetBackgroundFit.CROP
        val fgMode = prefs.getString(KEY_FG_MODE, null)
            ?.let { runCatching { WidgetForegroundMode.valueOf(it) }.getOrNull() }
            ?: WidgetForegroundMode.DEFAULT
        val fgColor = prefs.getInt(KEY_FG_COLOR, 0xFFFFFFFF.toInt())
        return WidgetBackgroundSettings(
            uri = uri,
            scrimAlpha = scrim,
            fit = fit,
            foregroundMode = fgMode,
            customForegroundColor = fgColor,
        )
    }

    companion object {
        private const val FILE = "widget_bg_prefs"
        private const val KEY_URI = "background_uri"
        private const val KEY_SCRIM = "scrim_alpha"
        private const val KEY_FIT = "fit"
        private const val KEY_FG_MODE = "fg_mode"
        private const val KEY_FG_COLOR = "fg_color"

        @Volatile private var INSTANCE: WidgetBackgroundRepository? = null
        fun get(context: Context): WidgetBackgroundRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WidgetBackgroundRepository(context).also { INSTANCE = it }
            }
    }
}
