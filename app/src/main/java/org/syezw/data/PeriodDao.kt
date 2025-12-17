package org.syezw.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.syezw.model.PeriodRecord

@Dao
interface PeriodDao {
    @Upsert
    suspend fun upsert(record: PeriodRecord)

    @Delete
    suspend fun delete(record: PeriodRecord)

    @Query("SELECT * FROM period_records ORDER BY startDate DESC")
    fun getAllRecords(): Flow<List<PeriodRecord>>

    @Query("DELETE FROM period_records")
    suspend fun clearAll()

    @Upsert
    suspend fun upsertAll(records: List<PeriodRecord>)

    // 使用 @Transaction 注解，确保这两个操作在同一个事务中完成
    @Transaction
    suspend fun clearAndInsert(records: List<PeriodRecord>) {
        clearAll()
        upsertAll(records)
    }
}
