package com.boxvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.boxvault.app.data.local.entity.RoomEntity
import com.boxvault.app.data.local.model.RoomSummary
import com.boxvault.app.data.local.model.RoomWithBoxes
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {

    @Upsert
    suspend fun upsertRoom(room: RoomEntity)

    @Upsert
    suspend fun upsertRooms(rooms: List<RoomEntity>)

    @Update
    suspend fun updateRoom(room: RoomEntity)

    @Delete
    suspend fun deleteRoom(room: RoomEntity)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoomById(roomId: String)

    @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    fun observeRoomById(roomId: String): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms ORDER BY name COLLATE NOCASE ASC")
    fun observeAllRooms(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllRooms(): List<RoomEntity>

    @Transaction
    @Query("SELECT * FROM rooms ORDER BY name COLLATE NOCASE ASC")
    fun observeRoomsWithBoxes(): Flow<List<RoomWithBoxes>>

    @Query(
        """
        SELECT r.*,
               (SELECT COUNT(*) FROM boxes b WHERE b.roomId = r.id) AS boxCount,
               (SELECT COUNT(*) FROM items i WHERE i.boxId IN (SELECT b2.id FROM boxes b2 WHERE b2.roomId = r.id)) AS itemCount,
               (SELECT COALESCE(SUM(i.quantity), 0) FROM items i WHERE i.boxId IN (SELECT b3.id FROM boxes b3 WHERE b3.roomId = r.id)) AS totalQuantity
        FROM rooms r
        ORDER BY r.name COLLATE NOCASE ASC
        """
    )
    fun observeRoomSummaries(): Flow<List<RoomSummary>>

    @Query("SELECT COUNT(*) FROM rooms")
    fun observeRoomCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun getRoomCount(): Int

    @Query("DELETE FROM rooms")
    suspend fun deleteAllRooms()
}
