package com.example.todolists.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isDone ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND (remindAtDue = 1 OR remindOnDay = 1)")
    suspend fun activeRemindable(): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task): Int

    @Delete
    suspend fun delete(task: Task): Int

    @Query("SELECT * FROM tasks WHERE isDone = 1")
    suspend fun completed(): List<Task>

    @Query("DELETE FROM tasks WHERE isDone = 1")
    fun deleteCompleted(): Int
}
