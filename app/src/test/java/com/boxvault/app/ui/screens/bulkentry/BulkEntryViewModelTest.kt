package com.boxvault.app.ui.screens.bulkentry

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
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
class BulkEntryViewModelTest {

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
        tempFilesDir = Files.createTempDirectory("boxvault_bulk_vm_test").toFile()
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
    fun `rapid consecutive bulk submissions immediately reset input state and preserve session history`() = runTest(testDispatcher) {
        val box = repository.createBox(
            name = "Garage Workshop Bin",
            shortCode = "BOX-001",
            location = "Garage"
        )

        val viewModel = BulkEntryViewModel(repository, box.id)
        viewModel.uiState.first { it.box != null }

        // Blank submission should be rejected without incrementing submissionCount
        viewModel.onItemNameChange("   ")
        assertFalse(viewModel.submitCurrentItem())
        assertEquals(0, viewModel.uiState.value.submissionCount)

        // Rapid entry #1: "Cordless Drill" (Qty 1, Category "Tools")
        viewModel.onCategoryChange("Tools")
        viewModel.onItemNameChange("Cordless Drill")
        viewModel.onQuantityChange(1)
        assertTrue(viewModel.submitCurrentItem())

        // Verify input fields are immediately cleared & reset while category remains sticky
        assertEquals("", viewModel.uiState.value.itemNameInput)
        assertEquals(1, viewModel.uiState.value.quantityInput)
        assertEquals("Tools", viewModel.uiState.value.categoryInput)
        assertEquals(1, viewModel.uiState.value.submissionCount)
        assertEquals("Cordless Drill", viewModel.uiState.value.lastAddedItemName)

        // Rapid entry #2: "Wood Clamps" (Qty 4)
        viewModel.onItemNameChange("Wood Clamps")
        viewModel.onQuantityChange(4)
        assertTrue(viewModel.submitCurrentItem())

        assertEquals("", viewModel.uiState.value.itemNameInput)
        assertEquals(1, viewModel.uiState.value.quantityInput)
        assertEquals(2, viewModel.uiState.value.submissionCount)
        assertEquals("Wood Clamps", viewModel.uiState.value.lastAddedItemName)

        // Rapid entry #3: "Safety Goggles" (Qty 2)
        viewModel.onItemNameChange("Safety Goggles")
        viewModel.onQuantityChange(2)
        assertTrue(viewModel.submitCurrentItem())

        val stateAfterThree = viewModel.uiState.first { it.sessionAddedItems.size == 3 }
        assertEquals(3, stateAfterThree.submissionCount)
        assertEquals(3, stateAfterThree.totalBoxItemCount)
        assertEquals(7, stateAfterThree.totalBoxQuantity)
        // Most recent item is first in session list
        assertEquals("Safety Goggles", stateAfterThree.sessionAddedItems[0].name)
        assertEquals("Wood Clamps", stateAfterThree.sessionAddedItems[1].name)
        assertEquals("Cordless Drill", stateAfterThree.sessionAddedItems[2].name)

        // Bump quantity of "Safety Goggles" (+1 -> Qty 3)
        val gogglesId = stateAfterThree.sessionAddedItems[0].id
        viewModel.bumpSessionItemQuantity(gogglesId)
        val stateAfterBump = viewModel.uiState.first {
            it.sessionAddedItems.firstOrNull { item -> item.id == gogglesId }?.quantity == 3
        }
        assertEquals(8, stateAfterBump.totalBoxQuantity)

        // Undo "Wood Clamps"
        val clampsId = stateAfterBump.sessionAddedItems[1].id
        viewModel.undoSessionItem(clampsId)
        val stateAfterUndo = viewModel.uiState.first { it.sessionAddedItems.size == 2 }
        assertEquals(2, stateAfterUndo.totalBoxItemCount)
        assertEquals(4, stateAfterUndo.totalBoxQuantity)
    }
}
