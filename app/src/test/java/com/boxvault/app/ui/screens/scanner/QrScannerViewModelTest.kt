package com.boxvault.app.ui.screens.scanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrScannerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: BoxVaultDatabase
    private lateinit var tempFilesDir: File
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
        tempFilesDir = Files.createTempDirectory("boxvault_scanner_vm_test").toFile()
        val photoStorageManager = PhotoStorageManager(
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
    fun `onTorchToggled updates state and blank camera QR is ignored`() = runTest(testDispatcher) {
        val viewModel = QrScannerViewModel(repository)

        assertFalse(viewModel.uiState.value.isTorchEnabled)
        viewModel.onTorchToggled(true)
        assertTrue(viewModel.uiState.value.isTorchEnabled)

        viewModel.onQrPayloadDetected("   ")
        assertFalse(viewModel.uiState.value.isProcessing)
        assertNull(viewModel.uiState.value.resolution)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onQrPayloadDetected resolves existing box, unassigned label, and consumeResolution clears state`() = runTest(testDispatcher) {
        val existing = repository.upsertBox(
            BoxEntity(
                id = UUID.randomUUID().toString(),
                shortCode = "BOX-010",
                name = "Camping Gear",
                location = "Garage"
            )
        )
        val viewModel = QrScannerViewModel(repository)

        viewModel.onQrPayloadDetected("boxvault://box/${existing.id}")
        val foundState = viewModel.uiState.first { it.resolution != null }
        val found = foundState.resolution as QrScanResolution.ExistingBoxFound
        assertEquals(existing.id, found.boxId)
        assertEquals("BOX-010", found.shortCode)

        viewModel.consumeResolution()
        assertNull(viewModel.uiState.value.resolution)
        assertFalse(viewModel.uiState.value.isProcessing)

        val newUuid = UUID.randomUUID().toString()
        viewModel.onQrPayloadDetected("boxvault://box/$newUuid")
        val unassignedState = viewModel.uiState.first { it.resolution != null }
        val unassigned = unassignedState.resolution as QrScanResolution.UnassignedQrLabelScanned
        assertEquals(newUuid, unassigned.presetBoxId)
        assertEquals("boxvault://box/$newUuid", unassigned.qrPayload)
    }

    @Test
    fun `unrecognized QR payload sets error and clearError resets it`() = runTest(testDispatcher) {
        val viewModel = QrScannerViewModel(repository)

        viewModel.onQrPayloadDetected("https://example.com/not-boxvault")
        val errState = viewModel.uiState.first { it.errorMessage != null }
        assertTrue(errState.errorMessage.orEmpty().contains("Unrecognized QR code"))
        assertEquals("https://example.com/not-boxvault", errState.lastScannedRawValue)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.lastScannedRawValue)
    }

    @Test
    fun `submitManualLookup handles empty input, shortCode, numeric shorthand, and unknown code`() = runTest(testDispatcher) {
        val box = repository.createBox(name = "Cables", shortCode = "BOX-001", location = "Office")
        val viewModel = QrScannerViewModel(repository)

        // Empty input guard
        viewModel.onManualInputChange("   ")
        viewModel.submitManualLookup()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Typing clears error
        viewModel.onManualInputChange("1")
        assertNull(viewModel.uiState.value.errorMessage)

        // Numeric shorthand "1" -> "BOX-001"
        viewModel.submitManualLookup()
        val numState = viewModel.uiState.first { it.resolution != null }
        assertEquals(box.id, (numState.resolution as QrScanResolution.ExistingBoxFound).boxId)
        viewModel.consumeResolution()

        // Unknown short code
        viewModel.onManualInputChange("BOX-999")
        viewModel.submitManualLookup()
        val unknownState = viewModel.uiState.first { it.errorMessage != null }
        assertTrue(unknownState.errorMessage.orEmpty().contains("No box found"))
    }
}
