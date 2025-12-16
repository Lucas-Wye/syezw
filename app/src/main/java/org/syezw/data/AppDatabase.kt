package org.syezw.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.syezw.model.PeriodRecord // 导入 PeriodRecord

// 1. 在 entities 中添加 PeriodRecord::class
// 2. 将 version 从 1 提升到 2
// 3. 添加 @TypeConverters(Converters::class)
@Database(entities = [Diary::class, TodoTask::class, PeriodRecord::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao
    abstract fun todoTaskDao(): TodoTaskDao
    abstract fun periodDao(): PeriodDao // 4. 添加 periodDao() 抽象方法

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syezw_database"
                )
                    // 数据库结构改变，需要迁移策略。这里使用破坏性迁移，会清空所有数据。
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
