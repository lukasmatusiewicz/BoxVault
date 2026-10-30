package com.boxvault.app.util.qr

import android.content.Context
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.screens.boxedit.BoxEditViewModel
import com.boxvault.app.ui.screens.scanner.QrScanResolution
import com.boxvault.app.ui.screens.scanner.QrScannerViewModel
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
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
class QrCodeGeneratorTest {

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
        tempFilesDir = Files.createTempDirectory("boxvault_qr_test").toFile()
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
    fun `generateBoxQrBitmap encodes boxvault URI with quiet zone and decodes via ZXing reader`() {
        val boxId = UUID.randomUUID().toString()
        val expectedPayload = "boxvault://box/$boxId"

        val bitmap = QrCodeGenerator.generateBoxQrBitmap(
            boxId = boxId,
            sizePx = 256,
            marginModules = 2
        )

        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)

        // Outer border pixels must be pure white (quiet zone)
        for (x in 0 until bitmap.width) {
            assertEquals(Color.WHITE, bitmap.getPixel(x, 0))
            assertEquals(Color.WHITE, bitmap.getPixel(x, bitmap.height - 1))
        }
        for (y in 0 until bitmap.height) {
            assertEquals(Color.WHITE, bitmap.getPixel(0, y))
            assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width - 1, y))
        }

        // Decode bitmap with ZXing QRCodeReader to verify round-trip fidelity
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val decodedResult = QRCodeReader().decode(binaryBitmap)

        assertEquals(expectedPayload, decodedResult.text)
        assertEquals(boxId, QrCodeGenerator.extractBoxId(decodedResult.text))
    }

    @Test
    fun `QrScannerViewModel resolves existing box vs unassigned pre-printed QR label`() = runTest(testDispatcher) {
        val existingBox = repository.upsertBox(
            BoxEntity(
                id = "11111111-2222-3333-4444-555555555555",
                shortCode = "BOX-001",
                name = "Camping Stove & Lanterns",
                location = "Basement Rack A"
            )
        )

        val scannerVm = QrScannerViewModel(repository)

        // 1. Scan existing box QR code
        scannerVm.onQrPayloadDetected(existingBox.qrPayload)
        val existingResolution = scannerVm.uiState.value.resolution
        assertTrue(existingResolution is QrScanResolution.ExistingBoxFound)
        assertEquals(
            existingBox.id,
            (existingResolution as QrScanResolution.ExistingBoxFound).boxId
        )
        scannerVm.consumeResolution()

        // 2. Scan unassigned pre-printed blank QR label (UUID not yet in DB)
        val prePrintedUuid = "99999999-8888-7777-6666-555555555555"
        scannerVm.onQrPayloadDetected("boxvault://box/$prePrintedUuid")
        val unassignedResolution = scannerVm.uiState.value.resolution
        assertTrue(unassignedResolution is QrScanResolution.UnassignedQrLabelScanned)
        assertEquals(
            prePrintedUuid,
            (unassignedResolution as QrScanResolution.UnassignedQrLabelScanned).presetBoxId
        )
        scannerVm.consumeResolution()

        // 3. Initialize BoxEditViewModel with that scanned presetBoxId and verify it binds that exact UUID
        val editVm = BoxEditViewModel(
            repository = repository,
            initialBoxId = null,
            presetBoxId = prePrintedUuid
        )
        val editState = editVm.uiState.first { !it.isLoading }
        assertFalse(editState.isEditMode)
        assertTrue(editState.isPreAllocatedQrLabel)
        assertEquals(prePrintedUuid, editState.boxId)
        assertEquals("BOX-002", editState.shortCode)

        editVm.onNameChange("Newly Assigned Basement Bin")
        editVm.onLocationChange("Basement Shelf 3")
        editVm.saveBox()
        assertNotNull(repository.getBoxById(prePrintedUuid))

        // 4. Manual lookup by short code ("BOX-001" and numeric shorthand "1")
        scannerVm.onManualInputChange("1")
        scannerVm.submitManualLookup()
        val manualResolution = scannerVm.uiState.value.resolution
        assertTrue(manualResolution is QrScanResolution.ExistingBoxFound)
        assertEquals(
            existingBox.id,
            (manualResolution as QrScanResolution.ExistingBoxFound).boxId
        )
        scannerVm.consumeResolution()

        // 5. Invalid QR code produces helpful error without resolution
        scannerVm.onQrPayloadDetected("https://example.invalid/not-a-box")
        assertNull(scannerVm.uiState.value.resolution)
        assertNotNull(scannerVm.uiState.value.errorMessage)
    }
}
