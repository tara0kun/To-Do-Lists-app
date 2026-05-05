package com.example.todolists.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WidgetBackgroundRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _backgroundUri = MutableStateFlow(loadUri())
    val backgroundUri: StateFlow<Uri?> = _backgroundUri.asStateFlow()

    fun setBackgroundUri(uri: Uri?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_URI) else putString(KEY_URI, uri.toString())
        }.apply()
        _backgroundUri.value = uri
    }

    private fun loadUri(): Uri? {
        val str = prefs.getString(KEY_URI, null) ?: return null
        return runCatching { Uri.parse(str) }.getOrNull()
    }

    companion object {
        private const val FILE = "widget_bg_prefs"
        private const val KEY_URI = "background_uri"

        @Volatile private var INSTANCE: WidgetBackgroundRepository? = null
        fun get(context: Context): WidgetBackgroundRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WidgetBackgroundRepository(context).also { INSTANCE = it }
            }
    }
}
