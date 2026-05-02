package com.example.todolists.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    val tasks: Flow<List<Task>> = dao.observeAll()

    suspend fun add(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        dao.insert(Task(title = trimmed))
    }

    suspend fun toggle(task: Task) = dao.update(task.copy(isDone = !task.isDone))

    suspend fun delete(task: Task) = dao.delete(task)

    suspend fun clearCompleted() = dao.clearCompleted()
}
