package com.boxvault.app.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.local.entity.RoomEntity

data class ItemWithPhotos(
    @Embedded
    val item: ItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val photos: List<PhotoEntity> = emptyList()
)

data class BoxWithItemsAndPhotos(
    @Embedded
    val box: BoxEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "boxId"
    )
    val items: List<ItemEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "boxId"
    )
    val photos: List<PhotoEntity> = emptyList()
) {
    val distinctItemCount: Int
        get() = items.size

    val totalItemQuantity: Int
        get() = items.sumOf { it.quantity }
}

data class ItemWithBox(
    @Embedded
    val item: ItemEntity,
    @Relation(
        parentColumn = "boxId",
        entityColumn = "id"
    )
    val box: BoxEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val photos: List<PhotoEntity> = emptyList()
)

data class BoxSummary(
    @Embedded
    val box: BoxEntity,
    val itemCount: Int,
    val totalQuantity: Int,
    val photoCount: Int
)

data class RoomWithBoxes(
    @Embedded
    val room: RoomEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "roomId"
    )
    val boxes: List<BoxEntity> = emptyList()
) {
    val boxCount: Int
        get() = boxes.size
}

data class RoomSummary(
    @Embedded
    val room: RoomEntity,
    val boxCount: Int,
    val itemCount: Int,
    val totalQuantity: Int
)

data class GlobalSearchResult(
    val query: String,
    val matchingBoxes: List<BoxWithItemsAndPhotos>,
    val matchingItems: List<ItemWithBox>
)
