package com.boxvault.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
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
class BoxVaultDaoTest {

    private lateinit var database: BoxVaultDatabase
    private lateinit var boxDao: BoxDao
    private lateinit var itemDao: ItemDao
    private lateinit var photoDao: PhotoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        boxDao = database.boxDao()
        itemDao = database.itemDao()
        photoDao = database.photoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `box CRUD and BoxSummary aggregation return accurate item and photo counts`() = runTest {
        val box = BoxEntity(
            id = "box-uuid-1",
            shortCode = "BOX-001",
            name = "Winter Holiday Decor",
            location = "Basement Shelf A",
            category = "Seasonal",
            colorHex = "#EF4444",
            notes = "Fragile glass ornaments"
        )
        boxDao.upsertBox(box)

        itemDao.upsertItems(
            listOf(
                ItemEntity(
                    id = "item-1",
                    boxId = box.id,
                    name = "LED String Lights",
                    quantity = 4,
                    category = "Lighting"
                ),
                ItemEntity(
                    id = "item-2",
                    boxId = box.id,
                    name = "Tree Star Topper",
                    quantity = 1,
                    category = "Decor"
                )
            )
        )

        photoDao.insertPhoto(
            PhotoEntity(
                id = "photo-1",
                boxId = box.id,
                itemId = null,
                relativePath = "box_1.jpg"
            )
        )

        val fetchedDetails = boxDao.getBoxWithItemsAndPhotos(box.id)
        assertNotNull(fetchedDetails)
        assertEquals("Winter Holiday Decor", fetchedDetails?.box?.name)
        assertEquals(2, fetchedDetails?.distinctItemCount)
        assertEquals(5, fetchedDetails?.totalItemQuantity)
        assertEquals(1, fetchedDetails?.photos?.size)

        val summaries = boxDao.observeBoxSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(2, summaries[0].itemCount)
        assertEquals(5, summaries[0].totalQuantity)
        assertEquals(1, summaries[0].photoCount)
    }

    @Test
    fun `deleting a box cascades deletion to its items and photos`() = runTest {
        val box = BoxEntity(
            id = "box-cascade-1",
            shortCode = "BOX-010",
            name = "Power Tools",
            location = "Garage Rack 2"
        )
        boxDao.upsertBox(box)

        val item = ItemEntity(
            id = "item-drill",
            boxId = box.id,
            name = "Cordless Impact Driver",
            quantity = 1
        )
        itemDao.upsertItem(item)

        val boxPhoto = PhotoEntity(
            id = "photo-box",
            boxId = box.id,
            itemId = null,
            relativePath = "box_cascade.jpg"
        )
        val itemPhoto = PhotoEntity(
            id = "photo-item",
            boxId = box.id,
            itemId = item.id,
            relativePath = "item_cascade.jpg"
        )
        photoDao.insertPhotos(listOf(boxPhoto, itemPhoto))

        assertEquals(1, itemDao.getItemsForBox(box.id).size)
        assertEquals(2, photoDao.getAllPhotos().size)

        boxDao.deleteBoxById(box.id)

        assertNull(boxDao.getBoxById(box.id))
        assertTrue(itemDao.getItemsForBox(box.id).isEmpty())
        assertTrue(photoDao.getAllPhotos().isEmpty())
    }

    @Test
    fun `searchBoxes and searchItemsWithBox match using SQL LIKE queries`() = runTest {
        val boxBasement = BoxEntity(
            id = "box-basement",
            shortCode = "BOX-001",
            name = "Camping Gear",
            location = "Basement North",
            category = "Outdoors",
            notes = "Summer tent and sleeping bags"
        )
        val boxAttic = BoxEntity(
            id = "box-attic",
            shortCode = "BOX-002",
            name = "Retro Electronics",
            location = "Attic Loft",
            category = "Tech",
            notes = "Cables and consoles"
        )
        boxDao.upsertBoxes(listOf(boxBasement, boxAttic))

        val itemLantern = ItemEntity(
            id = "item-lantern",
            boxId = boxBasement.id,
            name = "Rechargeable Camping Lantern",
            quantity = 2,
            category = "Lighting",
            notes = "USB-C battery pack included"
        )
        val itemConsole = ItemEntity(
            id = "item-console",
            boxId = boxAttic.id,
            name = "Super Nintendo Controller",
            quantity = 2,
            category = "Gaming",
            notes = "Original grey"
        )
        itemDao.upsertItems(listOf(itemLantern, itemConsole))

        // Search boxes by location substring
        val basementMatches = boxDao.searchBoxes("Basement").first()
        assertEquals(1, basementMatches.size)
        assertEquals(boxBasement.id, basementMatches[0].box.id)

        // Search boxes by shortCode
        val codeMatches = boxDao.searchBoxes("BOX-002").first()
        assertEquals(1, codeMatches.size)
        assertEquals(boxAttic.id, codeMatches[0].box.id)

        // Search items by name/notes and verify parent Box relation is populated
        val itemMatches = itemDao.searchItemsWithBox("Nintendo").first()
        assertEquals(1, itemMatches.size)
        assertEquals("Super Nintendo Controller", itemMatches[0].item.name)
        assertEquals("Attic Loft", itemMatches[0].box.location)
    }
}

