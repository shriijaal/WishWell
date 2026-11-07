package com.wishwell.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.CategoryWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM items i WHERE i.categoryId = c.id AND i.isArchived = 0) AS itemCount,
            (SELECT COALESCE(SUM(i.currentPrice), 0.0) FROM items i WHERE i.categoryId = c.id AND i.isArchived = 0) AS totalValue
        FROM categories c
        ORDER BY c.sortOrder ASC, c.createdAt ASC
        """
    )
    fun observeAll(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<Category>

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
