package com.boxvault.app.data.repository

import androidx.room.withTransaction
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.dao.BoxDao
import com.boxvault.app.data.local.dao.ItemDao
import com.boxvault.app.data.local.dao.PhotoDao
import com.boxvault.app.data.local.dao.RoomDao
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.local.entity.RoomEntity
import com.boxvault.app.data.local.model.BoxSummary
import com.boxvault.app.data.local.model.BoxWithItemsAndPhotos
import com.boxvault.app.data.local.model.GlobalSearchResult
import com.boxvault.app.data.local.model.ItemWithBox
import com.boxvault.app.data.local.model.ItemWithPhotos
import com.boxvault.app.data.local.model.RoomSummary
import com.boxvault.app.data.local.model.RoomWithBoxes
import com.boxvault.app.data.storage.PhotoStorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

class BoxVaultRepository(
    private val boxDao: BoxDao,
    private val itemDao: ItemDao,
    private val photoDao: PhotoDao,
    private val photoStorageManager: PhotoStorageManager,
    private val boxVaultDatabase: BoxVaultDatabase? = null,
    roomDao: RoomDao? = null
) {
    private val roomDao: RoomDao? = roomDao ?: boxVaultDatabase?.roomDao()

    // --- Room Operations ---

    fun observeAllRooms(): Flow<List<RoomEntity>> =
        roomDao?.observeAllRooms() ?: flowOf(emptyList())

    suspend fun getAllRooms(): List<RoomEntity> =
        roomDao?.getAllRooms() ?: emptyList()

    fun observeRoomsWithBoxes(): Flow<List<RoomWithBoxes>> =
        roomDao?.observeRoomsWithBoxes() ?: flowOf(emptyList())

    fun observeRoomSummaries(): Flow<List<RoomSummary>> =
        roomDao?.observeRoomSummaries() ?: flowOf(emptyList())

    fun observeRoomById(roomId: String): Flow<RoomEntity?> =
        roomDao?.observeRoomById(roomId) ?: flowOf(null)

    suspend fun getRoomById(roomId: String): RoomEntity? =
        roomDao?.getRoomById(roomId)

    fun observeRoomCount(): Flow<Int> =
        roomDao?.observeRoomCount() ?: flowOf(0)

    suspend fun getRoomCount(): Int =
        roomDao?.getRoomCount() ?: 0

    suspend fun createRoom(
        name: String,
        colorHex: String = "#3B82F6",
        iconName: String = "room"
    ): RoomEntity {
        return upsertRoom(
            RoomEntity(
                name = name.trim(),
                colorHex = colorHex.trim().ifEmpty { "#3B82F6" },
                iconName = iconName.trim().ifEmpty { "room" }
            )
        )
    }

    suspend fun upsertRoom(room: RoomEntity): RoomEntity {
        val now = System.currentTimeMillis()
        val updated = room.copy(
            name = room.name.trim(),
            colorHex = room.colorHex.trim().ifEmpty { "#3B82F6" },
            iconName = room.iconName.trim().ifEmpty { "room" },
            updatedAt = now
        )
        roomDao?.upsertRoom(updated)
        return updated
    }

    suspend fun deleteRoom(roomId: String) {
        // Critical safeguard: BoxEntity.roomId has onDelete = ForeignKey.SET_NULL,
        // which leaves all child boxes intact with roomId = null.
        roomDao?.deleteRoomById(roomId)
    }

    // --- Box Operations ---

    fun observeAllBoxesWithDetails(): Flow<List<BoxWithItemsAndPhotos>> =
        boxDao.observeAllBoxesWithDetails()

    fun observeBoxSummaries(): Flow<List<BoxSummary>> =
        boxDao.observeBoxSummaries()

    fun observeBoxesByRoom(roomId: String): Flow<List<BoxWithItemsAndPhotos>> =
        boxDao.observeBoxesByRoom(roomId)

    fun observeUnassignedBoxes(): Flow<List<BoxWithItemsAndPhotos>> =
        boxDao.observeUnassignedBoxes()

    fun observeBoxSummariesByRoom(roomId: String): Flow<List<BoxSummary>> =
        boxDao.observeBoxSummariesByRoom(roomId)

    fun observeUnassignedBoxSummaries(): Flow<List<BoxSummary>> =
        boxDao.observeUnassignedBoxSummaries()

    fun observeBoxWithItemsAndPhotos(boxId: String): Flow<BoxWithItemsAndPhotos?> =
        boxDao.observeBoxWithItemsAndPhotos(boxId)

    suspend fun getBoxWithItemsAndPhotos(boxId: String): BoxWithItemsAndPhotos? =
        boxDao.getBoxWithItemsAndPhotos(boxId)

    suspend fun getBoxById(boxId: String): BoxEntity? =
        boxDao.getBoxById(boxId)

    suspend fun getBoxByShortCode(shortCode: String): BoxEntity? =
        boxDao.getBoxByShortCode(shortCode.trim())

    fun observeBoxesByLocation(location: String): Flow<List<BoxWithItemsAndPhotos>> =
        boxDao.observeBoxesByLocation(location)

    fun observeDistinctLocations(): Flow<List<String>> =
        boxDao.observeDistinctLocations()

    fun observeTotalBoxCount(): Flow<Int> =
        boxDao.observeTotalBoxCount()

    fun observeTotalItemQuantity(): Flow<Int> =
        itemDao.observeTotalItemQuantity()

    fun observeDistinctItemCount(): Flow<Int> =
        itemDao.observeDistinctItemCount()

    fun observeItemsForBox(boxId: String): Flow<List<ItemEntity>> =
        itemDao.observeItemsForBox(boxId)

    fun observeItemsWithPhotosForBox(boxId: String): Flow<List<ItemWithPhotos>> =
        itemDao.observeItemsWithPhotosForBox(boxId)

    fun observeItemWithBox(itemId: String): Flow<ItemWithBox?> =
        itemDao.observeItemWithBox(itemId)

    fun observePhotosForBox(boxId: String): Flow<List<PhotoEntity>> =
        photoDao.observePhotosForBox(boxId)

    fun observePhotosForItem(itemId: String): Flow<List<PhotoEntity>> =
        photoDao.observePhotosForItem(itemId)

    /**
     * Performs a reactive search across both Boxes and Items using indexed SQL LIKE queries.
     */
    fun observeGlobalSearch(rawQuery: String): Flow<GlobalSearchResult> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return flowOf(
                GlobalSearchResult(
                    query = "",
                    matchingBoxes = emptyList(),
                    matchingItems = emptyList()
                )
            )
        }
        return combine(
            boxDao.searchBoxes(trimmed),
            itemDao.searchItemsWithBox(trimmed)
        ) { boxes, items ->
            GlobalSearchResult(
                query = trimmed,
                matchingBoxes = boxes,
                matchingItems = items
            )
        }
    }

    suspend fun generateNextShortCode(): String {
        var index = boxDao.getTotalBoxCount() + 1
        while (true) {
            val candidate = String.format(Locale.US, "BOX-%03d", index)
            if (boxDao.getBoxByShortCode(candidate) == null) {
                return candidate
            }
            index++
        }
    }

    suspend fun createBox(
        name: String,
        shortCode: String? = null,
        location: String,
        category: String = "General",
        colorHex: String = "#F59E0B",
        notes: String = "",
        roomId: String? = null
    ): BoxEntity {
        val resolvedShortCode = shortCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: generateNextShortCode()
        return upsertBox(
            BoxEntity(
                shortCode = resolvedShortCode,
                name = name,
                location = location,
                category = category,
                colorHex = colorHex,
                notes = notes,
                roomId = roomId
            )
        )
    }

    suspend fun upsertBox(box: BoxEntity): BoxEntity {
        val now = System.currentTimeMillis()
        val updated = box.copy(
            shortCode = box.shortCode.trim(),
            name = box.name.trim(),
            location = box.location.trim(),
            category = box.category.trim().ifEmpty { "General" },
            roomId = box.roomId?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = now
        )
        boxDao.upsertBox(updated)
        return updated
    }

    suspend fun assignBoxToRoom(boxId: String, roomId: String?) {
        val trimmedRoomId = roomId?.trim()?.takeIf { it.isNotEmpty() }
        boxDao.updateBoxRoom(boxId = boxId, roomId = trimmedRoomId, updatedAt = System.currentTimeMillis())
    }

    suspend fun deleteBox(boxId: String) {
        val boxPhotos = photoDao.getPhotosForBox(boxId)
        val items = itemDao.getItemsForBox(boxId)
        val itemPhotos = items.flatMap { item -> photoDao.getPhotosForItem(item.id) }

        boxDao.deleteBoxById(boxId)

        (boxPhotos + itemPhotos)
            .map { it.relativePath }
            .distinct()
            .forEach { relativePath ->
                runCatching { photoStorageManager.deletePhotoFile(relativePath) }
            }
    }

    suspend fun createItem(
        boxId: String,
        name: String,
        quantity: Int = 1,
        category: String = "General",
        notes: String = ""
    ): ItemEntity {
        return upsertItem(
            ItemEntity(
                boxId = boxId,
                name = name,
                quantity = quantity,
                category = category,
                notes = notes
            )
        )
    }

    suspend fun upsertItem(item: ItemEntity): ItemEntity {
        val now = System.currentTimeMillis()
        val sanitizedQuantity = item.quantity.coerceAtLeast(1)
        val updated = item.copy(
            name = item.name.trim(),
            quantity = sanitizedQuantity,
            category = item.category.trim().ifEmpty { "General" },
            updatedAt = now
        )
        itemDao.upsertItem(updated)
        boxDao.updateBoxTimestamp(updated.boxId, now)
        return updated
    }

    suspend fun deleteItem(itemId: String) {
        val item = itemDao.getItemById(itemId)
        val itemPhotos = photoDao.getPhotosForItem(itemId)

        itemDao.deleteItemById(itemId)

        item?.let {
            boxDao.updateBoxTimestamp(it.boxId, System.currentTimeMillis())
        }

        itemPhotos.forEach { photo ->
            runCatching { photoStorageManager.deletePhotoFile(photo.relativePath) }
        }
    }

    suspend fun attachPhotoToBox(boxId: String, imageBytes: ByteArray): PhotoEntity {
        val relativePath = photoStorageManager.savePhotoBytes(
            bytes = imageBytes,
            prefix = "box"
        )
        val photo = PhotoEntity(
            boxId = boxId,
            itemId = null,
            relativePath = relativePath
        )
        photoDao.insertPhoto(photo)
        boxDao.updateBoxTimestamp(boxId, System.currentTimeMillis())
        return photo
    }

    suspend fun attachPhotoToItem(boxId: String, itemId: String, imageBytes: ByteArray): PhotoEntity {
        val relativePath = photoStorageManager.savePhotoBytes(
            bytes = imageBytes,
            prefix = "item"
        )
        val photo = PhotoEntity(
            boxId = boxId,
            itemId = itemId,
            relativePath = relativePath
        )
        photoDao.insertPhoto(photo)
        boxDao.updateBoxTimestamp(boxId, System.currentTimeMillis())
        return photo
    }

    suspend fun deletePhoto(photoId: String) {
        val photo = photoDao.getPhotoById(photoId) ?: return
        photoDao.deletePhotoById(photoId)
        runCatching { photoStorageManager.deletePhotoFile(photo.relativePath) }
    }

    suspend fun resolveBoxFromQrPayload(qrPayload: String): BoxWithItemsAndPhotos? {
        val boxId = BoxEntity.parseBoxIdFromQrPayload(qrPayload) ?: return null
        return boxDao.getBoxWithItemsAndPhotos(boxId)
    }

    suspend fun getAllBoxes(): List<BoxEntity> =
        boxDao.getAllBoxes()

    suspend fun getAllItems(): List<ItemEntity> =
        itemDao.getAllItems()

    suspend fun getAllPhotos(): List<PhotoEntity> =
        photoDao.getAllPhotos()

    suspend fun replaceAllInventoryData(
        rooms: List<RoomEntity> = emptyList(),
        boxes: List<BoxEntity>,
        items: List<ItemEntity>,
        photos: List<PhotoEntity>
    ) {
        val executeRestore: suspend () -> Unit = {
            photoDao.deleteAllPhotos()
            itemDao.deleteAllItems()
            boxDao.deleteAllBoxes()
            roomDao?.deleteAllRooms()

            if (rooms.isNotEmpty()) {
                roomDao?.upsertRooms(rooms)
            }
            if (boxes.isNotEmpty()) {
                boxDao.upsertBoxes(boxes)
            }
            if (items.isNotEmpty()) {
                itemDao.upsertItems(items)
            }
            if (photos.isNotEmpty()) {
                photoDao.insertPhotos(photos)
            }
        }

        if (boxVaultDatabase != null) {
            boxVaultDatabase.withTransaction {
                executeRestore()
            }
        } else {
            executeRestore()
        }
    }

    suspend fun replaceAllInventoryData(
        boxes: List<BoxEntity>,
        items: List<ItemEntity>,
        photos: List<PhotoEntity>
    ) = replaceAllInventoryData(
        rooms = emptyList(),
        boxes = boxes,
        items = items,
        photos = photos
    )
}
