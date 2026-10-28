package org.syezw.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_offers",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class ProductOffer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val merchant: String,
    val price: Double,
    val discount: Double = 1.0,
    val quantity: Double,
    val quantityUnit: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val unitPrice: Double get() = if (quantity > 0) price * discount / quantity else 0.0
}
