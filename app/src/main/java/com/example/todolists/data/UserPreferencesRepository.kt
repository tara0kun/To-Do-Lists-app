package com.example.todolists.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SortMode(val label: String) {
    PRIORITY_THEN_DUE("重要度 → 期限"),
    DUE_DATE_ASC("期限が近い順"),
    PRIORITY_DESC("重要度が高い順"),
    CREATED_DESC("作成日が新しい順");

    companion object {
        val DEFAULT = PRIORITY_THEN_DUE
    }
}

enum class AppTheme(val label: String) {
    SYSTEM("システムに合わせる"),
    LIGHT("ライト"),
    DARK("ダーク");

    companion object { val DEFAULT = SYSTEM }
}

data class UserPreferences(
    val sortMode: SortMode = SortMode.DEFAULT,
    val separateCompleted: Boolean = true,
    val overdueOnTop: Boolean = true,
    val appTheme: AppTheme = AppTheme.DEFAULT,
)

class UserPreferencesRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserPreferences> = _state.asStateFlow()

    fun setSortMode(mode: SortMode) = update { it.copy(sortMode = mode) }
    fun setSeparateCompleted(value: Boolean) = update { it.copy(separateCompleted = value) }
    fun setOverdueOnTop(value: Boolean) = update { it.copy(overdueOnTop = value) }
    fun setAppTheme(theme: AppTheme) = update { it.copy(appTheme = theme) }

    private fun update(transform: (UserPreferences) -> UserPreferences) {
        val next = transform(_state.value)
        prefs.edit()
            .putString(KEY_SORT, next.sortMode.name)
            .putBoolean(KEY_SEPARATE, next.separateCompleted)
            .putBoolean(KEY_OVERDUE_TOP, next.overdueOnTop)
            .putString(KEY_APP_THEME, next.appTheme.name)
            .apply()
        _state.value = next
    }

    private fun load(): UserPreferences = UserPreferences(
        sortMode = prefs.getString(KEY_SORT, null)
            ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
            ?: SortMode.DEFAULT,
        separateCompleted = prefs.getBoolean(KEY_SEPARATE, true),
        overdueOnTop = prefs.getBoolean(KEY_OVERDUE_TOP, true),
        appTheme = prefs.getString(KEY_APP_THEME, null)
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
            ?: AppTheme.DEFAULT,
    )

    companion object {
        private const val FILE = "user_prefs"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_SEPARATE = "separate_completed"
        private const val KEY_OVERDUE_TOP = "overdue_on_top"
        private const val KEY_APP_THEME = "app_theme"

        @Volatile private var INSTANCE: UserPreferencesRepository? = null
        fun get(context: Context): UserPreferencesRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPreferencesRepository(context).also { INSTANCE = it }
            }
    }
}
