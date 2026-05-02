package com.example.todolists.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolists.data.SortMode
import com.example.todolists.data.Task
import com.example.todolists.data.TaskRepository
import com.example.todolists.data.UserPreferences
import com.example.todolists.data.UserPreferencesRepository
import com.example.todolists.notifications.NotificationChannels
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskListSection(
    val key: String,
    val label: String?,
    val tasks: List<Task>,
)

data class TaskListUiState(
    val sections: List<TaskListSection> = emptyList(),
    val total: Int = 0,
    val hasCompleted: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
)

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TaskRepository.get(app)
    private val prefsRepo = UserPreferencesRepository.get(app)

    val preferences: StateFlow<UserPreferences> = prefsRepo.state

    init {
        NotificationChannels.ensureCreated(app)
    }

    val uiState: StateFlow<TaskListUiState> =
        combine(repository.tasks, prefsRepo.state) { tasks, prefs ->
            buildState(tasks, prefs)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskListUiState(),
        )

    fun add(
        title: String,
        dueAt: Long?,
        remindAtDue: Boolean,
        remindOnDay: Boolean,
        remindOnDayHour: Int,
        remindOnDayMinute: Int,
        priority: Int,
    ) = viewModelScope.launch {
        repository.add(title, dueAt, remindAtDue, remindOnDay, remindOnDayHour, remindOnDayMinute, priority)
    }

    fun update(task: Task) = viewModelScope.launch { repository.update(task) }
    fun toggle(task: Task) = viewModelScope.launch { repository.toggle(task) }
    fun delete(task: Task) = viewModelScope.launch { repository.delete(task) }
    fun clearCompleted() = viewModelScope.launch { repository.clearCompleted() }

    fun setSortMode(mode: SortMode) = prefsRepo.setSortMode(mode)
    fun setSeparateCompleted(value: Boolean) = prefsRepo.setSeparateCompleted(value)
    fun setOverdueOnTop(value: Boolean) = prefsRepo.setOverdueOnTop(value)

    private fun buildState(tasks: List<Task>, prefs: UserPreferences): TaskListUiState {
        val now = System.currentTimeMillis()
        val active = tasks.filter { !it.isDone }
        val done = tasks.filter { it.isDone }

        val sortedActive = active.sortedWith(comparator(prefs.sortMode))

        val activeArranged = if (prefs.overdueOnTop) {
            val (overdue, others) = sortedActive.partition { t ->
                t.dueAt != null && t.dueAt < now
            }
            overdue.sortedBy { it.dueAt } + others
        } else sortedActive

        val sections = mutableListOf<TaskListSection>()
        if (prefs.separateCompleted) {
            if (activeArranged.isNotEmpty()) {
                sections += TaskListSection(key = "active", label = null, tasks = activeArranged)
            }
            if (done.isNotEmpty()) {
                sections += TaskListSection(
                    key = "completed",
                    label = "完了済み (${done.size})",
                    tasks = done.sortedByDescending { it.createdAt },
                )
            }
        } else {
            val all = activeArranged + done.sortedByDescending { it.createdAt }
            if (all.isNotEmpty()) {
                sections += TaskListSection(key = "all", label = null, tasks = all)
            }
        }

        return TaskListUiState(
            sections = sections,
            total = tasks.size,
            hasCompleted = done.isNotEmpty(),
            preferences = prefs,
        )
    }

    private fun comparator(mode: SortMode): Comparator<Task> = when (mode) {
        SortMode.PRIORITY_THEN_DUE -> compareByDescending<Task> { it.priority }
            .thenBy(nullsLast()) { it.dueAt }
            .thenByDescending { it.createdAt }
        SortMode.DUE_DATE_ASC -> compareBy<Task>(nullsLast()) { it.dueAt }
            .thenByDescending { it.priority }
            .thenByDescending { it.createdAt }
        SortMode.PRIORITY_DESC -> compareByDescending<Task> { it.priority }
            .thenByDescending { it.createdAt }
        SortMode.CREATED_DESC -> compareByDescending { it.createdAt }
    }
}
