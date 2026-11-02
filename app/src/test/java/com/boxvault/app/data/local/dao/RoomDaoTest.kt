package com.boxvault.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.RoomEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDaoTest {

    private lateinit var database: BoxVaultDatabase
    private lateinit var roomDao: RoomDao
    private lateinit var boxDao: BoxDao
    private lateinit var itemDao: ItemDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        roomDao = database.roomDao()
        boxDao = database.boxDao()
        itemDao = database.itemDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `room CRUD operations work as expected`() = runTest {
        val room = RoomEntity(
            id = "room-garage",
            name = "Garage Workshop",
            colorHex = "#10B981",
            iconName = "garage"
        )
        roomDao.upsertRoom(room)

        val fetched = roomDao.getRoomById(room.id)
        assertNotNull(fetched)
        assertEquals("Garage Workshop", fetched?.name)
        assertEquals("#10B981", fetched?.colorHex)

        val observed = roomDao.observeRoomById(room.id).first()
        assertEquals("Garage Workshop", observed?.name)

        val allRooms = roomDao.observeAllRooms().first()
        assertEquals(1, allRooms.size)
        assertEquals("Garage Workshop", allRooms.first().name)

        val updated = room.copy(name = "Main Garage & Workshop")
        roomDao.updateRoom(updated)
        assertEquals("Main Garage & Workshop", roomDao.getRoomById(room.id)?.name)

        roomDao.deleteRoomById(room.id)
        assertNull(roomDao.getRoomById(room.id))
        assertTrue(roomDao.getAllRooms().isEmpty())
    }

    @Test
    fun `relational RoomWithBoxes query loads parent room with all assigned child boxes`() = runTest {
        val roomLiving = RoomEntity(id = "room-living", name = "Living Room")
        val roomAttic = RoomEntity(id = "room-attic", name = "Attic")
        roomDao.upsertRooms(listOf(roomLiving, roomAttic))

        val box1 = BoxEntity(
            id = "box-1",
            roomId = roomLiving.id,
            shortCode = "BOX-001",
            name = "Living Room Books",
            location = "Bookshelf"
        )
        val box2 = BoxEntity(
            id = "box-2",
            roomId = roomLiving.id,
            shortCode = "BOX-002",
            name = "Board Games",
            location = "TV Stand"
        )
        val box3 = BoxEntity(
            id = "box-3",
            roomId = roomAttic.id,
            shortCode = "BOX-003",
            name = "Winter Coats",
            location = "Rafters"
        )
        val boxUnassigned = BoxEntity(
            id = "box-unassigned",
            roomId = null,
            shortCode = "BOX-004",
            name = "Mystery Box",
            location = "Hallway"
        )
        boxDao.upsertBoxes(listOf(box1, box2, box3, boxUnassigned))

        val roomsWithBoxes = roomDao.observeRoomsWithBoxes().first()
        assertEquals(2, roomsWithBoxes.size)

        val atticGroup = roomsWithBoxes.first { it.room.id == roomAttic.id }
        assertEquals(1, atticGroup.boxes.size)
        assertEquals("BOX-003", atticGroup.boxes.first().shortCode)

        val livingGroup = roomsWithBoxes.first { it.room.id == roomLiving.id }
        assertEquals(2, livingGroup.boxes.size)
        assertTrue(livingGroup.boxes.any { it.id == "box-1" })
        assertTrue(livingGroup.boxes.any { it.id == "box-2" })
    }

    @Test
    fun `CRITICAL SAFEGUARD deleting a RoomEntity sets child box roomId to null and preserves child boxes`() = runTest {
        val room = RoomEntity(
            id = "room-basement",
            name = "Basement Storage"
        )
        roomDao.upsertRoom(room)

        val childBox1 = BoxEntity(
            id = "child-box-1",
            roomId = room.id,
            shortCode = "BOX-101",
            name = "Christmas Trees & Lights",
            location = "Basement Shelf A"
        )
        val childBox2 = BoxEntity(
            id = "child-box-2",
            roomId = room.id,
            shortCode = "BOX-102",
            name = "Old Tax Records",
            location = "Basement Shelf B"
        )
        boxDao.upsertBoxes(listOf(childBox1, childBox2))

        val item = ItemEntity(
            id = "item-tree-stand",
            boxId = childBox1.id,
            name = "Heavy Metal Tree Stand",
            quantity = 1
        )
        itemDao.upsertItem(item)

        // Verify initial state: child boxes are assigned to the room
        val boxesBeforeDelete = boxDao.getBoxesByRoom(room.id)
        assertEquals(2, boxesBeforeDelete.size)

        // Trigger Room deletion
        roomDao.deleteRoomById(room.id)

        // Room is deleted
        assertNull(roomDao.getRoomById(room.id))

        // ARCHITECTURAL SAFEGUARD VERIFICATION:
        // 1. Both child boxes MUST still exist in database
        val allBoxesAfterDelete = boxDao.getAllBoxes()
        assertEquals(2, allBoxesAfterDelete.size)

        // 2. Both child boxes MUST now have roomId == null (ForeignKey.SET_NULL)
        val reloadedBox1 = boxDao.getBoxById(childBox1.id)
        val reloadedBox2 = boxDao.getBoxById(childBox2.id)
        assertNotNull(reloadedBox1)
        assertNotNull(reloadedBox2)
        assertNull(reloadedBox1?.roomId)
        assertNull(reloadedBox2?.roomId)

        // 3. Child items are completely intact
        val reloadedItem = itemDao.getItemById(item.id)
        assertNotNull(reloadedItem)
        assertEquals(childBox1.id, reloadedItem?.boxId)

        // 4. Boxes now cleanly show up in unassigned queries
        val unassignedBoxes = boxDao.getUnassignedBoxes()
        assertEquals(2, unassignedBoxes.size)
        assertTrue(unassignedBoxes.any { it.id == childBox1.id })
        assertTrue(unassignedBoxes.any { it.id == childBox2.id })
    }

    @Test
    fun `RoomSummary aggregation computes boxCount, itemCount, and totalQuantity accurately`() = runTest {
        val room = RoomEntity(
            id = "room-kitchen",
            name = "Kitchen Pantry"
        )
        roomDao.upsertRoom(room)

        val box = BoxEntity(
            id = "box-spices",
            roomId = room.id,
            shortCode = "BOX-K01",
            name = "Baking Supplies",
            location = "Top Shelf"
        )
        boxDao.upsertBox(box)

        itemDao.upsertItems(
            listOf(
                ItemEntity(id = "item-flour", boxId = box.id, name = "Bread Flour 5kg", quantity = 2),
                ItemEntity(id = "item-sugar", boxId = box.id, name = "Brown Sugar 1kg", quantity = 3)
            )
        )

        val summaries = roomDao.observeRoomSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals("Kitchen Pantry", summaries[0].room.name)
        assertEquals(1, summaries[0].boxCount)
        assertEquals(2, summaries[0].itemCount)
        assertEquals(5, summaries[0].totalQuantity)
    }
}
