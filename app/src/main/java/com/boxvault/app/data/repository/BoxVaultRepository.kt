package com.boxvault.app.data.repository

import com.boxvault.app.data.local.dao.BoxDao
import com.boxvault.app.data.local.dao.ItemDao
import com.boxvault.app.data.local.dao.PhotoDao
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.local.model.BoxSummary
import com.boxvault.app.data.local.model.BoxWithItemsAndPhotos
import com.boxvault.app.data.local.model.GlobalSearchResult
import com.boxvault.app.data.local.model.ItemWithBox
import com.boxvault.app.data.local.model.ItemWithPhotos
import com.boxvault.app.data.storage.PhotoStorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

class BoxVaultRepository(
    private val boxDao: BoxDao,
    private val itemDao: ItemDao,
    private val photoDao: PhotoDao,
    private val photoStorageManager: PhotoStorageManager
) {

    fun observeAllBoxesWithDetails(): Flow<List<BoxWithItemsAndPhotos>> =
        boxDao.observeAllBoxesWithDetails()

    fun observeBoxSummaries(): Flow<List<BoxSummary>> =
        boxDao.observeBoxSummaries()

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

    suspend fun upsertBox(box: BoxEntity): BoxEntity {
        val now = System.currentTimeMillis()
        val updated = box.copy(
            shortCode = box.shortCode.trim(),
            name = box.name.trim(),
            location = box.location.trim(),
            category = box.category.trim().ifEmpty { "General" },
            updatedAt = now
        )
        boxDao.upsertBox(updated)
        return updated
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
}
