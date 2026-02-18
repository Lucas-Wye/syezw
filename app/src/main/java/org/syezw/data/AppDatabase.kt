package org.syezw.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.syezw.model.PeriodRecord

@Database(entities = [Diary::class, TodoTask::class, PeriodRecord::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao
    abstract fun todoTaskDao(): TodoTaskDao
    abstract fun periodDao(): PeriodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_list ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diary_list ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE diary_list SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
                db.execSQL("UPDATE diary_list SET updatedAt = timestamp WHERE updatedAt = 0")

                db.execSQL("ALTER TABLE todo_list ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE todo_list ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE todo_list SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
                db.execSQL(
                    "UPDATE todo_list SET updatedAt = CASE WHEN completedAt IS NOT NULL THEN completedAt ELSE createdAt END WHERE updatedAt = 0"
                )

                db.execSQL("ALTER TABLE period_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE period_records SET updatedAt = strftime('%s','now') * 1000 WHERE updatedAt = 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syezw_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
