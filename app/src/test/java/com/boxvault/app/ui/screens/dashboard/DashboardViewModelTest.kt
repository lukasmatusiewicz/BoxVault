package com.boxvault.app.ui.screens.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
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
import org.junit.Assert.assertNull
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
class DashboardViewModelTest {

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
        tempFilesDir = Files.createTempDirectory("boxvault_dashboard_vm_test").toFile()
        photoStorageManager = PhotoStorageManager(
            baseFilesDir = tempFilesDir,
            ioDispatcher = testDispatcher
        )
        repository = BoxVaultRepository(
            boxDao = database.boxDao(),
            itemDao = database.itemDao(),
            photoDao = database.photoDao(),
            photoStorageManager = photoStorageManager,
            boxVaultDatabase = database,
            roomDao = database.roomDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
        tempFilesDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun `dashboard aggregates box and item counts and filters by location`() = runTest(testDispatcher) {
        val garageBox = repository.upsertBox(
            BoxEntity(
                id = "box-garage",
                shortCode = "BOX-001",
                name = "Automotive Tools",
                location = "Garage"
            )
        )
        val basementBox = repository.upsertBox(
            BoxEntity(
                id = "box-basement",
                shortCode = "BOX-002",
                name = "Holiday Ornaments",
                location = "Basement"
            )
        )
        repository.upsertItem(
            ItemEntity(boxId = garageBox.id, name = "Torque Wrench", quantity = 2)
        )
        repository.upsertItem(
            ItemEntity(boxId = basementBox.id, name = "LED String Lights", quantity = 4)
        )

        val viewModel = DashboardViewModel(repository)

        val state = viewModel.uiState.first { !it.isLoading && it.totalBoxes == 2 }
        assertEquals(2, state.totalBoxes)
        assertEquals(6, state.totalItemQuantity)
        assertEquals(2, state.distinctItemCount)
        assertEquals(listOf("Basement", "Garage"), state.locations)
        assertEquals(2, state.filteredBoxes.size)

        // Filter by Garage location
        viewModel.onSelectLocation("Garage")
        assertEquals("Garage", viewModel.uiState.value.selectedLocation)
        assertEquals(1, viewModel.uiState.value.filteredBoxes.size)
        assertEquals("BOX-001", viewModel.uiState.value.filteredBoxes.first().box.shortCode)

        // Toggling same location clears filter
        viewModel.onSelectLocation("Garage")
        assertNull(viewModel.uiState.value.selectedLocation)
        assertEquals(2, viewModel.uiState.value.filteredBoxes.size)
    }

    @Test
    fun `dashboard state with 0 rooms falls back automatically to clean flat box list`() = runTest(testDispatcher) {
        // Seed boxes without any rooms created
        repository.upsertBox(
            BoxEntity(
                id = "box-flat-1",
                shortCode = "BOX-001",
                name = "Seasonal Clothes",
                location = "Closet"
            )
        )
        repository.upsertBox(
            BoxEntity(
                id = "box-flat-2",
                shortCode = "BOX-002",
                name = "Winter Boots",
                location = "Closet"
            )
        )

        val viewModel = DashboardViewModel(repository)
        val state = viewModel.uiState.first { !it.isLoading && it.totalBoxes == 2 }

        // When 0 rooms exist, hasRooms must be false and flat list fallback is active
        assertFalse(state.hasRooms)
        assertEquals(0, state.totalRooms)
        assertTrue(state.rooms.isEmpty())
        assertTrue(state.roomSections.isEmpty())
        assertEquals(2, state.filteredBoxes.size)
        val shortCodes = state.filteredBoxes.map { it.box.shortCode }
        assertTrue(shortCodes.contains("BOX-001"))
        assertTrue(shortCodes.contains("BOX-002"))
    }

    @Test
    fun `dashboard state with multiple rooms organizes boxes into room-grouped hierarchy with unassigned section`() = runTest(testDispatcher) {
        val garageRoom = repository.createRoom(name = "Garage", colorHex = "#10B981")
        val atticRoom = repository.createRoom(name = "Attic", colorHex = "#F59E0B")

        val garageBox = repository.createBox(
            name = "Power Tools",
            shortCode = "BOX-G01",
            location = "Bench",
            roomId = garageRoom.id
        )
        val atticBox = repository.createBox(
            name = "Holiday Decor",
            shortCode = "BOX-A01",
            location = "Rafters",
            roomId = atticRoom.id
        )
        val unassignedBox = repository.createBox(
            name = "General Hallway Box",
            shortCode = "BOX-U01",
            location = "Hallway",
            roomId = null
        )

        repository.upsertItem(ItemEntity(boxId = garageBox.id, name = "Drill Bits", quantity = 10))
        repository.upsertItem(ItemEntity(boxId = atticBox.id, name = "Ornaments", quantity = 25))

        val viewModel = DashboardViewModel(repository)
        val state = viewModel.uiState.first { !it.isLoading && it.totalRooms == 2 && it.totalBoxes == 3 }

        assertTrue(state.hasRooms)
        assertEquals(2, state.totalRooms)
        assertEquals(2, state.roomSections.size)

        val garageSection = state.roomSections.first { it.room.id == garageRoom.id }
        assertEquals(1, garageSection.boxCount)
        assertEquals(1, garageSection.totalItems)
        assertEquals(10, garageSection.totalQuantity)
        assertEquals(garageBox.id, garageSection.boxes.first().box.id)

        val atticSection = state.roomSections.first { it.room.id == atticRoom.id }
        assertEquals(1, atticSection.boxCount)
        assertEquals(1, atticSection.totalItems)
        assertEquals(25, atticSection.totalQuantity)
        assertEquals(atticBox.id, atticSection.boxes.first().box.id)

        // Verify unassigned section
        assertEquals(1, state.unassignedBoxes.size)
        assertEquals(unassignedBox.id, state.unassignedBoxes.first().box.id)
    }

    @Test
    fun `room management operations create, update, and delete rooms with SET_NULL fallback`() = runTest(testDispatcher) {
        val viewModel = DashboardViewModel(repository)
        viewModel.uiState.first { !it.isLoading }

        viewModel.createRoom(name = "Office Area", colorHex = "#3B82F6")
        val stateAfterCreate = viewModel.uiState.first { it.totalRooms == 1 }
        val createdRoom = stateAfterCreate.rooms.first()
        assertEquals("Office Area", createdRoom.name)
        assertEquals("#3B82F6", createdRoom.colorHex)

        // Update room
        viewModel.updateRoom(createdRoom.id, name = "Home Office & Studio", colorHex = "#8B5CF6")
        val stateAfterUpdate = viewModel.uiState.first { it.rooms.firstOrNull()?.name == "Home Office & Studio" }
        assertEquals("#8B5CF6", stateAfterUpdate.rooms.first().colorHex)

        // Assign a box to this room
        val box = repository.createBox(
            name = "Office Documents",
            location = "Desk",
            roomId = createdRoom.id
        )

        val stateWithBox = viewModel.uiState.first { it.totalBoxes == 1 && it.roomSections.firstOrNull()?.boxes?.isNotEmpty() == true }
        assertEquals(1, stateWithBox.roomSections.first().boxCount)
        assertEquals(0, stateWithBox.unassignedBoxes.size)

        // Delete room and verify child box becomes unassigned (SET_NULL safety)
        viewModel.deleteRoom(createdRoom.id)
        val stateAfterDelete = viewModel.uiState.first { it.totalRooms == 0 && it.totalBoxes == 1 }
        assertFalse(stateAfterDelete.hasRooms)
        assertEquals(1, stateAfterDelete.filteredBoxes.size)
        assertNull(stateAfterDelete.filteredBoxes.first().box.roomId)
    }

    @Test
    fun `instant global search returns matching items with parent box shortCode and location`() = runTest(testDispatcher) {
        val box = repository.upsertBox(
            BoxEntity(
                id = "box-camping",
                shortCode = "BOX-007",
                name = "Alpine Camping Gear",
                location = "Basement Rack C"
            )
        )
        repository.upsertItem(
            ItemEntity(
                id = "item-stove",
                boxId = box.id,
                name = "Isobutane Camp Stove",
                quantity = 1,
                notes = "Piezo igniter included"
            )
        )

        val viewModel = DashboardViewModel(repository)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onSearchQueryChange("Isobutane")
        assertTrue(viewModel.uiState.value.isSearchActive)

        val searchState = viewModel.uiState.first {
            it.searchResult.query == "Isobutane" && it.searchResult.matchingItems.isNotEmpty()
        }
        assertEquals(1, searchState.searchResult.matchingItems.size)
        val matchedItemWithBox = searchState.searchResult.matchingItems.first()
        assertEquals("Isobutane Camp Stove", matchedItemWithBox.item.name)
        assertEquals("BOX-007", matchedItemWithBox.box.shortCode)
        assertEquals("Alpine Camping Gear", matchedItemWithBox.box.name)
        assertEquals("Basement Rack C", matchedItemWithBox.box.location)

        viewModel.onClearSearch()
        assertFalse(viewModel.uiState.value.isSearchActive)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }
}
