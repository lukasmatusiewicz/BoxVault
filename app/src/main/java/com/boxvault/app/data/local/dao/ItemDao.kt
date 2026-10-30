package com.boxvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.model.ItemWithBox
import com.boxvault.app.data.local.model.ItemWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Upsert
    suspend fun upsertItem(item: ItemEntity)

    @Upsert
    suspend fun upsertItems(items: List<ItemEntity>)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): ItemEntity?

    @Query("SELECT * FROM items WHERE boxId = :boxId ORDER BY name ASC")
    fun observeItemsForBox(boxId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE boxId = :boxId ORDER BY name ASC")
    suspend fun getItemsForBox(boxId: String): List<ItemEntity>

    @Transaction
    @Query("SELECT * FROM items WHERE boxId = :boxId ORDER BY name ASC")
    fun observeItemsWithPhotosForBox(boxId: String): Flow<List<ItemWithPhotos>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    fun observeItemWithBox(itemId: String): Flow<ItemWithBox?>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    suspend fun getItemWithBox(itemId: String): ItemWithBox?

    @Transaction
    @Query(
        """
        SELECT * FROM items
        WHERE name LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun searchItemsWithBox(query: String): Flow<List<ItemWithBox>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM items")
    fun observeTotalItemQuantity(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items")
    fun observeDistinctItemCount(): Flow<Int>

    @Query("SELECT * FROM items ORDER BY createdAt ASC")
    suspend fun getAllItems(): List<ItemEntity>

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()
}
