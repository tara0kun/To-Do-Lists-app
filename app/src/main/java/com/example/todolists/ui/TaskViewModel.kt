package com.example.todolists.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolists.data.Task
import com.example.todolists.data.TaskDatabase
import com.example.todolists.data.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TaskRepository(TaskDatabase.get(app).taskDao())

    val tasks: StateFlow<List<Task>> = repository.tasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun add(title: String) = viewModelScope.launch { repository.add(title) }
    fun toggle(task: Task) = viewModelScope.launch { repository.toggle(task) }
    fun delete(task: Task) = viewModelScope.launch { repository.delete(task) }
    fun clearCompleted() = viewModelScope.launch { repository.clearCompleted() }
}
