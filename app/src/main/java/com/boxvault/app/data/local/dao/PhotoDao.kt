package com.boxvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.boxvault.app.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: String)

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE boxId = :boxId ORDER BY createdAt DESC")
    fun observePhotosForBox(boxId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE boxId = :boxId ORDER BY createdAt DESC")
    suspend fun getPhotosForBox(boxId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun observePhotosForItem(itemId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE itemId = :itemId ORDER BY createdAt DESC")
    suspend fun getPhotosForItem(itemId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos ORDER BY createdAt DESC")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Query("DELETE FROM photos")
    suspend fun deleteAllPhotos()
}
