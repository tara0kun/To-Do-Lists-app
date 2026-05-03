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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskTab(val label: String) {
    ALL("すべて"),
    SIMPLE("簡易"),
    OVERDUE("期限切れ"),
    COMPLETED("完了済み"),
}

data class TaskListSection(
    val key: String,
    val label: String?,
    val tasks: List<Task>,
)

data class TabCounts(
    val all: Int = 0,
    val simple: Int = 0,
    val overdue: Int = 0,
    val completed: Int = 0,
)

data class TaskListUiState(
    val sections: List<TaskListSection> = emptyList(),
    val total: Int = 0,
    val hasCompleted: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val selectedTab: TaskTab = TaskTab.ALL,
    val counts: TabCounts = TabCounts(),
    val emptyMessage: String? = null,
)

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TaskRepository.get(app)
    private val prefsRepo = UserPreferencesRepository.get(app)
    private val _selectedTab = MutableStateFlow(TaskTab.ALL)
    val selectedTab: StateFlow<TaskTab> = _selectedTab.asStateFlow()

    val preferences: StateFlow<UserPreferences> = prefsRepo.state

    init {
        NotificationChannels.ensureCreated(app)
    }

    val uiState: StateFlow<TaskListUiState> =
        combine(repository.tasks, prefsRepo.state, _selectedTab) { tasks, prefs, tab ->
            buildState(tasks, prefs, tab)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskListUiState(),
        )

    fun selectTab(tab: TaskTab) { _selectedTab.value = tab }

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

    fun addSimple(title: String) = viewModelScope.launch { repository.addSimple(title) }

    fun update(task: Task) = viewModelScope.launch { repository.update(task) }
    fun toggle(task: Task) = viewModelScope.launch { repository.toggle(task) }
    fun delete(task: Task) = viewModelScope.launch { repository.delete(task) }
    fun clearCompleted() = viewModelScope.launch { repository.clearCompleted() }

    fun setSortMode(mode: SortMode) = prefsRepo.setSortMode(mode)
    fun setSeparateCompleted(value: Boolean) = prefsRepo.setSeparateCompleted(value)
    fun setOverdueOnTop(value: Boolean) = prefsRepo.setOverdueOnTop(value)

    private fun buildState(
        tasks: List<Task>,
        prefs: UserPreferences,
        tab: TaskTab,
    ): TaskListUiState {
        val now = System.currentTimeMillis()
        val detailed = tasks.filter { !it.isSimple }
        val simple = tasks.filter { it.isSimple }
        val activeDetailed = detailed.filter { !it.isDone }
        val doneDetailed = detailed.filter { it.isDone }
        val overdueActive = activeDetailed.filter { it.dueAt != null && it.dueAt < now }

        val counts = TabCounts(
            all = detailed.size,
            simple = simple.size,
            overdue = overdueActive.size,
            completed = doneDetailed.size,
        )

        val sections: List<TaskListSection>
        val emptyMessage: String?

        when (tab) {
            TaskTab.ALL -> {
                val sortedActive = activeDetailed.sortedWith(comparator(prefs.sortMode))
                val activeArranged = if (prefs.overdueOnTop) {
                    val (overdue, others) = sortedActive.partition { t ->
                        t.dueAt != null && t.dueAt < now
                    }
                    overdue.sortedBy { it.dueAt } + others
                } else sortedActive

                val list = mutableListOf<TaskListSection>()
                if (prefs.separateCompleted) {
                    if (activeArranged.isNotEmpty()) {
                        list += TaskListSection("active", null, activeArranged)
                    }
                    if (doneDetailed.isNotEmpty()) {
                        list += TaskListSection(
                            key = "completed",
                            label = "完了済み (${doneDetailed.size})",
                            tasks = doneDetailed.sortedByDescending { it.createdAt },
                        )
                    }
                } else {
                    val all = activeArranged + doneDetailed.sortedByDescending { it.createdAt }
                    if (all.isNotEmpty()) list += TaskListSection("all", null, all)
                }
                sections = list
                emptyMessage = "詳細タスクはまだありません"
            }
            TaskTab.SIMPLE -> {
                val active = simple.filter { !it.isDone }.sortedByDescending { it.createdAt }
                val done = simple.filter { it.isDone }.sortedByDescending { it.createdAt }
                val list = mutableListOf<TaskListSection>()
                if (active.isNotEmpty()) list += TaskListSection("simple_active", null, active)
                if (done.isNotEmpty()) list += TaskListSection(
                    key = "simple_done",
                    label = "完了済み (${done.size})",
                    tasks = done,
                )
                sections = list
                emptyMessage = "簡易リストは空です。下の入力欄から追加できます。"
            }
            TaskTab.OVERDUE -> {
                val list = overdueActive.sortedBy { it.dueAt }
                sections = if (list.isEmpty()) emptyList()
                else listOf(TaskListSection("overdue", null, list))
                emptyMessage = "期限切れのタスクはありません"
            }
            TaskTab.COMPLETED -> {
                val list = doneDetailed.sortedByDescending { it.createdAt }
                sections = if (list.isEmpty()) emptyList()
                else listOf(TaskListSection("completed_only", null, list))
                emptyMessage = "完了済みのタスクはありません"
            }
        }

        return TaskListUiState(
            sections = sections,
            total = tasks.size,
            hasCompleted = doneDetailed.isNotEmpty() || simple.any { it.isDone },
            preferences = prefs,
            selectedTab = tab,
            counts = counts,
            emptyMessage = emptyMessage,
        )
    }

    private fun comparator(mode: SortMode): Comparator<Task> = when (mode) {
        SortMode.PRIORITY_THEN_DUE -> compareByDescending<Task> { it.priority }
            .thenBy(nullsLast()) { it.dueAt }
            .thenByDescending { it.createdAt }
        SortMode.DUE_DATE_ASC -> compareBy<Task, Long?>(nullsLast<Long>()) { it.dueAt }
            .thenByDescending { it.priority }
            .thenByDescending { it.createdAt }
        SortMode.PRIORITY_DESC -> compareByDescending<Task> { it.priority }
            .thenByDescending { it.createdAt }
        SortMode.CREATED_DESC -> compareByDescending { it.createdAt }
    }
}
