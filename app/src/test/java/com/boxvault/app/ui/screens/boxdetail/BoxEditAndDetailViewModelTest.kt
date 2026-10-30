package com.boxvault.app.ui.screens.boxdetail

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.screens.boxedit.BoxEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxEditAndDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: BoxVaultDatabase
    private lateinit var tempFilesDir: File
    private lateinit var photoStorageManager: PhotoStorageManager
    private lateinit var repository: BoxVaultRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxVaultDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        tempFilesDir = Files.createTempDirectory("boxvault_detail_vm_test").toFile()
        photoStorageManager = PhotoStorageManager(
            baseFilesDir = tempFilesDir,
            ioDispatcher = testDispatcher
        )
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
        Dispatchers.resetMain()
    }

    @Test
    fun `BoxEditViewModel auto-suggests short code and validates duplicate codes`() = runTest(testDispatcher) {
        val createVm = BoxEditViewModel(repository, initialBoxId = null)
        val initialCreateState = createVm.uiState.first { !it.isLoading }
        assertEquals("BOX-001", initialCreateState.shortCode)

        createVm.onNameChange("Power Tools")
        createVm.onLocationChange("Garage Shelf 1")
        createVm.onCategoryChange("Tools")

        var savedBoxId: String? = null
        createVm.saveBox { id -> savedBoxId = id }
        assertNotNull(savedBoxId)
        assertTrue(createVm.uiState.value.isSaved)

        // Second box auto-suggests BOX-002 and rejects collision with BOX-001
        val secondCreateVm = BoxEditViewModel(repository, initialBoxId = null)
        val secondState = secondCreateVm.uiState.first { !it.isLoading }
        assertEquals("BOX-002", secondState.shortCode)

        secondCreateVm.onNameChange("Second Box")
        secondCreateVm.onShortCodeChange("BOX-001")
        secondCreateVm.saveBox()

        assertFalse(secondCreateVm.uiState.value.isSaved)
        assertNotNull(secondCreateVm.uiState.value.errorMessage)
    }

    @Test
    fun `BoxDetailViewModel manages items, quantities, and local photos`() = runTest(testDispatcher) {
        val box = repository.createBox(
            name = "Electronics Spare Parts",
            shortCode = "BOX-010",
            location = "Office Closet"
        )

        val detailVm = BoxDetailViewModel(repository, box.id)
        detailVm.uiState.first { !it.isLoading && it.box != null }

        // Add item
        detailVm.saveItem(
            name = "USB-C Braided Cable",
            quantity = 3,
            category = "Cables",
            notes = "2m 100W PD"
        )

        val stateWithItem = detailVm.uiState.first { it.itemsWithPhotos.size == 1 }
        val addedItem = stateWithItem.itemsWithPhotos.first().item
        assertEquals("USB-C Braided Cable", addedItem.name)
        assertEquals(3, addedItem.quantity)

        // Increment & Decrement quantity
        detailVm.incrementItemQuantity(addedItem)
        val incrementedState = detailVm.uiState.first {
            it.itemsWithPhotos.firstOrNull()?.item?.quantity == 4
        }
        val itemQty4 = incrementedState.itemsWithPhotos.first().item
        assertEquals(4, itemQty4.quantity)

        detailVm.decrementItemQuantity(itemQty4)
        val decrementedState = detailVm.uiState.first {
            it.itemsWithPhotos.firstOrNull()?.item?.quantity == 3
        }
        assertEquals(3, decrementedState.itemsWithPhotos.first().item.quantity)

        // Attach photo to box
        detailVm.attachPhotoToBox(byteArrayOf(1, 2, 3, 4, 5))
        val stateWithPhoto = detailVm.uiState.first { it.boxLevelPhotos.size == 1 }
        val photo = stateWithPhoto.boxLevelPhotos.first()
        assertTrue(photoStorageManager.photoExists(photo.relativePath))

        // Delete photo
        detailVm.deletePhoto(photo.id)
        detailVm.uiState.first { it.boxLevelPhotos.isEmpty() }
        assertFalse(photoStorageManager.photoExists(photo.relativePath))
    }
}
