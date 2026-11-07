package com.wishwell.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.data.model.PricePoint
import com.wishwell.app.data.model.WishItem
import kotlinx.coroutines.flow.Flow

private const val ITEMS_WITH_STATS = """
    SELECT i.*,
        (SELECT MIN(p.price) FROM price_points p WHERE p.itemId = i.id) AS lowest,
        (SELECT MAX(p.price) FROM price_points p WHERE p.itemId = i.id) AS highest,
        (SELECT AVG(p.price) FROM price_points p WHERE p.itemId = i.id) AS average,
        (SELECT p.price FROM price_points p WHERE p.itemId = i.id ORDER BY p.recordedAt ASC LIMIT 1) AS firstPrice,
        (SELECT p.price FROM price_points p WHERE p.itemId = i.id ORDER BY p.recordedAt DESC LIMIT 1) AS latestPrice,
        (SELECT COUNT(*) FROM price_points p WHERE p.itemId = i.id) AS pointCount,
        c.name AS categoryName,
        c.colorArgb AS categoryColor
    FROM items i
    LEFT JOIN categories c ON c.id = i.categoryId
"""

@Dao
interface ItemDao {

    @Query("$ITEMS_WITH_STATS WHERE i.isArchived = 0 ORDER BY i.createdAt DESC")
    fun observeActiveItems(): Flow<List<ItemWithStats>>

    @Query("$ITEMS_WITH_STATS WHERE i.id = :id")
    fun observeItem(id: Long): Flow<ItemWithStats?>

    @Query("$ITEMS_WITH_STATS ORDER BY i.createdAt DESC")
    suspend fun getAllWithStats(): List<ItemWithStats>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): WishItem?

    @Query("SELECT * FROM items WHERE isArchived = 0")
    suspend fun getActiveItems(): List<WishItem>

    @Insert
    suspend fun insert(item: WishItem): Long

    @Update
    suspend fun update(item: WishItem)

    @Delete
    suspend fun delete(item: WishItem)

    @Query("SELECT * FROM price_points WHERE itemId = :itemId ORDER BY recordedAt ASC")
    fun observeHistory(itemId: Long): Flow<List<PricePoint>>

    @Query("SELECT * FROM price_points WHERE itemId = :itemId ORDER BY recordedAt ASC")
    suspend fun history(itemId: Long): List<PricePoint>

    @Query("SELECT * FROM price_points WHERE itemId = :itemId ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latestPoint(itemId: Long): PricePoint?

    @Query("SELECT MIN(price) FROM price_points WHERE itemId = :itemId")
    suspend fun minPrice(itemId: Long): Double?

    @Insert
    suspend fun insertPoint(point: PricePoint): Long

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
