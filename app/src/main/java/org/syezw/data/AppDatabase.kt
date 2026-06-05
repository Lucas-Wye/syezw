package org.syezw.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.syezw.model.PeriodRecord

@Database(entities = [Diary::class, TodoTask::class, PeriodRecord::class, GpsLocation::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao
    abstract fun todoTaskDao(): TodoTaskDao
    abstract fun periodDao(): PeriodDao
    abstract fun gpsLocationDao(): GpsLocationDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gps_locations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy REAL,
                        altitude REAL,
                        speed REAL,
                        timestamp INTEGER NOT NULL,
                        author TEXT NOT NULL DEFAULT ''
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gps_locations_timestamp ON gps_locations(timestamp)")
                // Add synced column to diary_list
                db.execSQL("ALTER TABLE diary_list ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                
                // Add synced column to todo_list
                db.execSQL("ALTER TABLE todo_list ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                
                // Add synced column to period_records
                db.execSQL("ALTER TABLE period_records ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE gps_locations ADD COLUMN endTimestamp INTEGER")
                db.execSQL("UPDATE gps_locations SET endTimestamp = timestamp WHERE endTimestamp IS NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syezw_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
