package org.syezw.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TodoTask::class], version = 2, exportSchema = false)
abstract class TodoTaskDatabase : RoomDatabase() {
    abstract fun todoTaskDao(): TodoTaskDao

    companion object {
        @Volatile
        private var INSTANCE: TodoTaskDatabase? = null

        fun getDatabase(context: Context): TodoTaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TodoTaskDatabase::class.java,
                    "todo_task_database"
                ).fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
                INSTANCE = instance
                instance
            }
        }
    }
}