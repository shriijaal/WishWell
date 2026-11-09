package com.wishwell.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wishwell.app.data.model.ItemCategoryTag
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

    @Query("SELECT * FROM items ORDER BY createdAt ASC")
    suspend fun getAllItemsRaw(): List<WishItem>

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

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM item_category_tags WHERE itemId = :itemId")
    suspend fun clearItemTags(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemTags(tags: List<ItemCategoryTag>)

    @Query("SELECT categoryId FROM item_category_tags WHERE itemId = :itemId ORDER BY categoryId")
    suspend fun getTagIdsForItem(itemId: Long): List<Long>

    @Query("SELECT * FROM item_category_tags ORDER BY itemId ASC, categoryId ASC")
    fun observeItemTags(): Flow<List<ItemCategoryTag>>

    @Query("SELECT itemId, categoryId FROM item_category_tags ORDER BY itemId, categoryId")
    suspend fun getAllItemTags(): List<ItemCategoryTag>

    @Query("SELECT * FROM price_points WHERE itemId = :itemId ORDER BY recordedAt ASC")
    fun observeHistory(itemId: Long): Flow<List<PricePoint>>

    @Query("SELECT * FROM price_points WHERE itemId = :itemId ORDER BY recordedAt ASC")
    suspend fun history(itemId: Long): List<PricePoint>

    @Query("SELECT * FROM price_points WHERE itemId = :itemId ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latestPoint(itemId: Long): PricePoint?

    @Query("SELECT COUNT(*) FROM price_points WHERE itemId = :itemId")
    suspend fun countPoints(itemId: Long): Int

    @Query("SELECT MIN(price) FROM price_points WHERE itemId = :itemId")
    suspend fun minPrice(itemId: Long): Double?

    @Insert
    suspend fun insertPoint(point: PricePoint): Long

    @Query("SELECT * FROM price_points ORDER BY itemId ASC, recordedAt ASC")
    suspend fun getAllPoints(): List<PricePoint>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
