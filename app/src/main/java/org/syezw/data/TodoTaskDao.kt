package org.syezw.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todoTask: TodoTask)

    @Update
    suspend fun update(todoTask: TodoTask)

    @Delete
    suspend fun delete(todoTask: TodoTask)

    @Query("SELECT * FROM todo_list ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TodoTask>>

//    @Query("DELETE FROM todo_list")
//    suspend fun deleteAll()

    @Query("SELECT * FROM todo_list ORDER BY createdAt DESC")
    suspend fun getAllTasksList(): List<TodoTask> // Non-Flow for one-shot export

    @Query("SELECT * FROM todo_list WHERE id = :taskId")
    fun getTaskById(taskId: Int): Flow<TodoTask?>
}