package com.boxvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.model.BoxSummary
import com.boxvault.app.data.local.model.BoxWithItemsAndPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface BoxDao {

    @Upsert
    suspend fun upsertBox(box: BoxEntity)

    @Upsert
    suspend fun upsertBoxes(boxes: List<BoxEntity>)

    @Update
    suspend fun updateBox(box: BoxEntity)

    @Query("UPDATE boxes SET updatedAt = :updatedAt WHERE id = :boxId")
    suspend fun updateBoxTimestamp(boxId: String, updatedAt: Long)

    @Query("UPDATE boxes SET roomId = :roomId, updatedAt = :updatedAt WHERE id = :boxId")
    suspend fun updateBoxRoom(boxId: String, roomId: String?, updatedAt: Long)

    @Delete
    suspend fun deleteBox(box: BoxEntity)

    @Query("DELETE FROM boxes WHERE id = :boxId")
    suspend fun deleteBoxById(boxId: String)

    @Query("SELECT * FROM boxes WHERE id = :boxId LIMIT 1")
    suspend fun getBoxById(boxId: String): BoxEntity?

    @Query("SELECT * FROM boxes WHERE id = :boxId LIMIT 1")
    fun observeBoxById(boxId: String): Flow<BoxEntity?>

    @Query("SELECT * FROM boxes WHERE shortCode = :shortCode COLLATE NOCASE LIMIT 1")
    suspend fun getBoxByShortCode(shortCode: String): BoxEntity?

    @Transaction
    @Query("SELECT * FROM boxes WHERE id = :boxId LIMIT 1")
    fun observeBoxWithItemsAndPhotos(boxId: String): Flow<BoxWithItemsAndPhotos?>

    @Transaction
    @Query("SELECT * FROM boxes WHERE id = :boxId LIMIT 1")
    suspend fun getBoxWithItemsAndPhotos(boxId: String): BoxWithItemsAndPhotos?

    @Transaction
    @Query("SELECT * FROM boxes ORDER BY updatedAt DESC")
    fun observeAllBoxesWithDetails(): Flow<List<BoxWithItemsAndPhotos>>

    @Query(
        """
        SELECT b.*,
               (SELECT COUNT(*) FROM items i WHERE i.boxId = b.id) AS itemCount,
               (SELECT COALESCE(SUM(i.quantity), 0) FROM items i WHERE i.boxId = b.id) AS totalQuantity,
               (SELECT COUNT(*) FROM photos p WHERE p.boxId = b.id) AS photoCount
        FROM boxes b
        ORDER BY b.updatedAt DESC
        """
    )
    fun observeBoxSummaries(): Flow<List<BoxSummary>>

    @Transaction
    @Query("SELECT * FROM boxes WHERE roomId = :roomId ORDER BY updatedAt DESC")
    fun observeBoxesByRoom(roomId: String): Flow<List<BoxWithItemsAndPhotos>>

    @Transaction
    @Query("SELECT * FROM boxes WHERE roomId IS NULL ORDER BY updatedAt DESC")
    fun observeUnassignedBoxes(): Flow<List<BoxWithItemsAndPhotos>>

    @Query(
        """
        SELECT b.*,
               (SELECT COUNT(*) FROM items i WHERE i.boxId = b.id) AS itemCount,
               (SELECT COALESCE(SUM(i.quantity), 0) FROM items i WHERE i.boxId = b.id) AS totalQuantity,
               (SELECT COUNT(*) FROM photos p WHERE p.boxId = b.id) AS photoCount
        FROM boxes b
        WHERE b.roomId = :roomId
        ORDER BY b.updatedAt DESC
        """
    )
    fun observeBoxSummariesByRoom(roomId: String): Flow<List<BoxSummary>>

    @Query(
        """
        SELECT b.*,
               (SELECT COUNT(*) FROM items i WHERE i.boxId = b.id) AS itemCount,
               (SELECT COALESCE(SUM(i.quantity), 0) FROM items i WHERE i.boxId = b.id) AS totalQuantity,
               (SELECT COUNT(*) FROM photos p WHERE p.boxId = b.id) AS photoCount
        FROM boxes b
        WHERE b.roomId IS NULL
        ORDER BY b.updatedAt DESC
        """
    )
    fun observeUnassignedBoxSummaries(): Flow<List<BoxSummary>>

    @Transaction
    @Query(
        """
        SELECT * FROM boxes
        WHERE name LIKE '%' || :query || '%'
           OR shortCode LIKE '%' || :query || '%'
           OR location LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun searchBoxes(query: String): Flow<List<BoxWithItemsAndPhotos>>

    @Transaction
    @Query("SELECT * FROM boxes WHERE location = :location ORDER BY updatedAt DESC")
    fun observeBoxesByLocation(location: String): Flow<List<BoxWithItemsAndPhotos>>

    @Query("SELECT DISTINCT location FROM boxes WHERE location != '' ORDER BY location ASC")
    fun observeDistinctLocations(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM boxes")
    fun observeTotalBoxCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM boxes")
    suspend fun getTotalBoxCount(): Int

    @Query("SELECT * FROM boxes ORDER BY createdAt ASC")
    suspend fun getAllBoxes(): List<BoxEntity>

    @Query("SELECT * FROM boxes WHERE roomId = :roomId")
    suspend fun getBoxesByRoom(roomId: String): List<BoxEntity>

    @Query("SELECT * FROM boxes WHERE roomId IS NULL")
    suspend fun getUnassignedBoxes(): List<BoxEntity>

    @Query("DELETE FROM boxes")
    suspend fun deleteAllBoxes()
}
