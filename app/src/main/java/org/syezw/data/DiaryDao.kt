package org.syezw.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diary: Diary): Long

    @Update
    suspend fun update(diary: Diary)

    @Delete
    suspend fun delete(diary: Diary)

    @Query("SELECT * FROM diary_list ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Diary>>

    @Query("SELECT * FROM diary_list WHERE id = :id")
    fun getEntryById(id: Int): Flow<Diary?>

    @Query("SELECT * FROM diary_list ORDER BY timestamp DESC")
    suspend fun getAllEntriesList(): List<Diary> // New: For export and merge check

    // Optional: For more efficient batch insert during import
    @Insert(onConflict = OnConflictStrategy.IGNORE) // IGNORE if you don't want to replace based on PrimaryKey
    suspend fun insertAll(diaries: List<Diary>)
}