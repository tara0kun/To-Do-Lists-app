package com.example.todolists.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CalendarSettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _selectedCalendarId = MutableStateFlow(loadSelectedId())
    val selectedCalendarId: StateFlow<Long?> = _selectedCalendarId.asStateFlow()

    fun setSelectedCalendarId(id: Long?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_CAL_ID) else putLong(KEY_CAL_ID, id)
        }.apply()
        _selectedCalendarId.value = id
    }

    private fun loadSelectedId(): Long? {
        val raw = prefs.getLong(KEY_CAL_ID, NO_VALUE)
        return if (raw == NO_VALUE) null else raw
    }

    companion object {
        private const val FILE = "calendar_prefs"
        private const val KEY_CAL_ID = "selected_calendar_id"
        private const val NO_VALUE = -1L

        @Volatile private var INSTANCE: CalendarSettingsRepository? = null
        fun get(context: Context): CalendarSettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CalendarSettingsRepository(context).also { INSTANCE = it }
            }
    }
}
