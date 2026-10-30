package com.boxvault.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.storage.PhotoStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxVaultRepositoryTest {

    private lateinit var database: BoxVaultDatabase
    private lateinit var tempFilesDir: File
    private lateinit var photoStorageManager: PhotoStorageManager
    private lateinit var repository: BoxVaultRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempFilesDir = Files.createTempDirectory("boxvault_repo_test").toFile()
        photoStorageManager = PhotoStorageManager(baseFilesDir = tempFilesDir)
        repository = BoxVaultRepository(
            boxDao = database.boxDao(),
            itemDao = database.itemDao(),
            photoDao = database.photoDao(),
            photoStorageManager = photoStorageManager
        )
    }

    @After
    fun tearDown() {
        database.close()
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `generateNextShortCode increments sequentially and avoids collisions`() = runTest {
        assertEquals("BOX-001", repository.generateNextShortCode())

        repository.upsertBox(
            BoxEntity(
                shortCode = "BOX-001",
                name = "First Box",
                location = "Basement"
            )
        )
        assertEquals("BOX-002", repository.generateNextShortCode())
    }

    @Test
    fun `QR payload remains immutable when box name or location is updated`() = runTest {
        val createdBox = repository.upsertBox(
            BoxEntity(
                shortCode = "BOX-001",
                name = "Kitchen Appliances",
                location = "Basement Shelf 1"
            )
        )
        val initialQrPayload = createdBox.qrPayload
        assertTrue(initialQrPayload.startsWith("boxvault://box/"))

        // Rename box and change location
        repository.upsertBox(
            createdBox.copy(
                name = "Small Kitchen & Baking Gear",
                location = "Pantry Top Shelf"
            )
        )

        val resolvedAfterEdit = repository.resolveBoxFromQrPayload(initialQrPayload)
        assertNotNull(resolvedAfterEdit)
        assertEquals("Small Kitchen & Baking Gear", resolvedAfterEdit?.box?.name)
        assertEquals("Pantry Top Shelf", resolvedAfterEdit?.box?.location)
    }

    @Test
    fun `observeGlobalSearch combines matching boxes and items`() = runTest {
        val box = repository.upsertBox(
            BoxEntity(
                id = "box-100",
                shortCode = "BOX-100",
                name = "Winter Ski Equipment",
                location = "Garage Loft"
            )
        )
        repository.upsertItem(
            ItemEntity(
                id = "item-goggles",
                boxId = box.id,
                name = "Snowboard Goggles",
                quantity = 2,
                notes = "Polarized lens for winter"
            )
        )

        val searchResult = repository.observeGlobalSearch("winter").first()
        assertEquals("winter", searchResult.query)
        assertEquals(1, searchResult.matchingBoxes.size)
        assertEquals(1, searchResult.matchingItems.size)
        assertEquals("Snowboard Goggles", searchResult.matchingItems[0].item.name)
    }

    @Test
    fun `deleteBox removes both database rows and physical photo files on disk`() = runTest {
        val box = repository.upsertBox(
            BoxEntity(
                id = "box-photo-cleanup",
                shortCode = "BOX-050",
                name = "Photo Cleanup Box",
                location = "Closet"
            )
        )
        val item = repository.upsertItem(
            ItemEntity(
                id = "item-photo-cleanup",
                boxId = box.id,
                name = "Camera Tripod",
                quantity = 1
            )
        )

        val boxPhoto = repository.attachPhotoToBox(box.id, byteArrayOf(10, 20, 30))
        val itemPhoto = repository.attachPhotoToItem(box.id, item.id, byteArrayOf(40, 50, 60))

        assertTrue(photoStorageManager.photoExists(boxPhoto.relativePath))
        assertTrue(photoStorageManager.photoExists(itemPhoto.relativePath))

        repository.deleteBox(box.id)

        assertFalse(photoStorageManager.photoExists(boxPhoto.relativePath))
        assertFalse(photoStorageManager.photoExists(itemPhoto.relativePath))
    }
}

