package org.syezw.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductOfferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(offer: ProductOffer): Long

    @Update
    suspend fun update(offer: ProductOffer)

    @Delete
    suspend fun delete(offer: ProductOffer)

    @Query("SELECT * FROM product_offers ORDER BY name COLLATE NOCASE, timestamp DESC")
    fun getAll(): Flow<List<ProductOffer>>

    @Query("SELECT * FROM product_offers ORDER BY timestamp DESC")
    suspend fun getAllList(): List<ProductOffer>

    @Query("SELECT * FROM product_offers WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ProductOffer?
}
