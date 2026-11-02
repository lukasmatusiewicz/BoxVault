package com.boxvault.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.RoomEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.screens.backup.BackupRestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: BoxVaultDatabase
    private lateinit var tempFilesDir: File
    private lateinit var tempCacheDir: File
    private lateinit var photoStorageManager: PhotoStorageManager
    private lateinit var repository: BoxVaultRepository
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempFilesDir = Files.createTempDirectory("boxvault_backup_files").toFile()
        tempCacheDir = Files.createTempDirectory("boxvault_backup_cache").toFile()
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
        backupManager = BackupManager(
            repository = repository,
            photoStorageManager = photoStorageManager,
            cacheDir = tempCacheDir,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        database.close()
        tempFilesDir.deleteRecursively()
        tempCacheDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun `export and atomic staged restore round-trip preserves all rooms, boxes, items, and photo bytes`() = runTest(testDispatcher) {
        // 1. Seed initial inventory with rooms, boxes, items, and photos
        val room1 = repository.createRoom(
            name = "Main Garage",
            colorHex = "#10B981"
        )
        val box1 = repository.upsertBox(
            BoxEntity(
                id = "box-uuid-1",
                roomId = room1.id,
                shortCode = "BOX-001",
                name = "Garage Power Tools",
                location = "Garage Rack 1",
                category = "Tools",
                notes = "Cordless drills and saws"
            )
        )
        val item1 = repository.upsertItem(
            ItemEntity(
                id = "item-uuid-1",
                boxId = box1.id,
                name = "18V Impact Driver",
                quantity = 2,
                category = "Power Tools",
                notes = "Includes two 5Ah batteries"
            )
        )
        val boxPhotoBytes = byteArrayOf(11, 22, 33, 44, 55, 66)
        val legacyJpgBytes = byteArrayOf(99, 88, 77, 66, 55, 44, 33)

        // Modern WebP photo attached to box
        val boxPhoto = repository.attachPhotoToBox(box1.id, boxPhotoBytes)
        assertTrue(boxPhoto.relativePath.endsWith(".webp"))

        // Backward-compatible legacy JPEG photo attached to item
        val legacyJpgPath = photoStorageManager.savePhotoBytes(
            bytes = legacyJpgBytes,
            prefix = "item_legacy",
            extension = "jpg"
        )
        assertTrue(legacyJpgPath.endsWith(".jpg"))
        val itemPhoto = PhotoEntity(
            boxId = box1.id,
            itemId = item1.id,
            relativePath = legacyJpgPath
        )
        database.photoDao().insertPhoto(itemPhoto)

        // 2. Export backup to ZIP byte array
        val zipOutput = ByteArrayOutputStream()
        val exportSummary = backupManager.exportBackupToZip(zipOutput)
        assertEquals(1, exportSummary.roomCount)
        assertEquals(1, exportSummary.boxCount)
        assertEquals(1, exportSummary.itemCount)
        assertEquals(2, exportSummary.photoCount)

        val archiveBytes = zipOutput.toByteArray()
        assertTrue(archiveBytes.isNotEmpty())

        // 3. Wipe live database and live photos (and insert decoy box to prove full atomic replacement)
        repository.deleteBox(box1.id)
        repository.deleteRoom(room1.id)
        repository.upsertBox(
            BoxEntity(
                id = "decoy-box",
                shortCode = "BOX-999",
                name = "Temporary Decoy Box",
                location = "Nowhere"
            )
        )
        assertEquals(1, repository.getAllBoxes().size)
        assertFalse(photoStorageManager.photoExists(boxPhoto.relativePath))

        // 4. Restore from ZIP archive
        val restoreSummary = backupManager.restoreBackupFromZip(ByteArrayInputStream(archiveBytes))
        assertEquals(1, restoreSummary.roomCount)
        assertEquals(1, restoreSummary.boxCount)
        assertEquals(1, restoreSummary.itemCount)
        assertEquals(2, restoreSummary.photoCount)

        // 5. Verify restored database rows and physical photo bytes match 100%
        val restoredRooms = repository.getAllRooms()
        assertEquals(1, restoredRooms.size)
        assertEquals(room1.id, restoredRooms.first().id)
        assertEquals("Main Garage", restoredRooms.first().name)
        assertEquals("#10B981", restoredRooms.first().colorHex)

        val restoredBoxes = repository.getAllBoxes()
        assertEquals(1, restoredBoxes.size)
        assertEquals("BOX-001", restoredBoxes.first().shortCode)
        assertEquals("Garage Power Tools", restoredBoxes.first().name)
        assertEquals(room1.id, restoredBoxes.first().roomId)

        val restoredItems = repository.getAllItems()
        assertEquals(1, restoredItems.size)
        assertEquals("18V Impact Driver", restoredItems.first().name)
        assertEquals(2, restoredItems.first().quantity)

        assertTrue(photoStorageManager.photoExists(boxPhoto.relativePath))
        assertTrue(photoStorageManager.photoExists(itemPhoto.relativePath))
        assertArrayEquals(boxPhotoBytes, photoStorageManager.readPhotoBytes(boxPhoto.relativePath))
        assertArrayEquals(legacyJpgBytes, photoStorageManager.readPhotoBytes(itemPhoto.relativePath))

        // 6. Verify staging directory was cleaned up in finally block
        assertFalse(backupManager.stagingDirectory.exists())
    }

    @Test
    fun `restoreBackupFromZip successfully restores legacy schemaVersion 1 backups without rooms`() = runTest(testDispatcher) {
        val legacyManifest = JSONObject().apply {
            put("schemaVersion", 1)
            put("appVersion", "1.0.0")
            put("exportedAt", System.currentTimeMillis())
            put(
                "boxes",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "legacy-box-1")
                        put("shortCode", "BOX-LEGACY")
                        put("name", "Legacy Attic Box")
                        put("location", "Attic Shelf")
                        put("category", "General")
                        put("colorHex", "#F59E0B")
                        put("notes", "Restored from version 1 backup")
                    }
                )
            )
            put(
                "items",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "legacy-item-1")
                        put("boxId", "legacy-box-1")
                        put("name", "Vintage Radio")
                        put("quantity", 1)
                    }
                )
            )
            put("photos", JSONArray())
        }

        val zipBuffer = ByteArrayOutputStream()
        ZipOutputStream(zipBuffer).use { zipOut ->
            zipOut.putNextEntry(ZipEntry(BackupManager.MANIFEST_FILE_NAME))
            zipOut.write(legacyManifest.toString().toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        val summary = backupManager.restoreBackupFromZip(ByteArrayInputStream(zipBuffer.toByteArray()))
        assertEquals(0, summary.roomCount)
        assertEquals(1, summary.boxCount)
        assertEquals(1, summary.itemCount)

        val boxes = repository.getAllBoxes()
        assertEquals(1, boxes.size)
        assertEquals("BOX-LEGACY", boxes.first().shortCode)
        assertNull(boxes.first().roomId) // Unassigned because legacy backup had no rooms
    }

    @Test
    fun `restoreBackupFromZip rejects Zip-Slip traversal entry and leaves live data untouched`() = runTest(testDispatcher) {
        // Seed live data that must NOT be lost when a malicious ZIP is rejected
        val liveBox = repository.upsertBox(
            BoxEntity(
                id = "live-box-safe",
                shortCode = "BOX-007",
                name = "Important Passports & Deeds",
                location = "Safe"
            )
        )
        val livePhotoBytes = byteArrayOf(7, 7, 7, 7)
        val livePhoto = repository.attachPhotoToBox(liveBox.id, livePhotoBytes)

        // Craft a malicious ZIP archive containing a Zip-Slip entry "../../evil.sh"
        val maliciousZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zipOut ->
                zipOut.putNextEntry(ZipEntry("../../evil.sh"))
                zipOut.write("rm -rf /".toByteArray())
                zipOut.closeEntry()
            }
        }.toByteArray()

        try {
            backupManager.restoreBackupFromZip(ByteArrayInputStream(maliciousZip))
            fail("Expected SecurityException for Zip-Slip path traversal entry")
        } catch (expected: SecurityException) {
            assertTrue(expected.message.orEmpty().contains("Zip-Slip", ignoreCase = true))
        }

        // Verify live database and live photo file were completely untouched
        val remainingBoxes = repository.getAllBoxes()
        assertEquals(1, remainingBoxes.size)
        assertEquals("BOX-007", remainingBoxes.first().shortCode)
        assertTrue(photoStorageManager.photoExists(livePhoto.relativePath))
        assertArrayEquals(livePhotoBytes, photoStorageManager.readPhotoBytes(livePhoto.relativePath))

        // Verify staging directory was cleaned up
        assertFalse(backupManager.stagingDirectory.exists())
    }

    @Test
    fun `restoreBackupFromZip rejects tampered photo SHA-256 checksum during staging without corrupting live data`() = runTest(testDispatcher) {
        // Seed live data
        val liveBox = repository.upsertBox(
            BoxEntity(
                id = "live-box-intact",
                shortCode = "BOX-042",
                name = "Intact Live Inventory",
                location = "Vault"
            )
        )
        val livePhotoBytes = byteArrayOf(42, 42, 42)
        val livePhoto = repository.attachPhotoToBox(liveBox.id, livePhotoBytes)

        // Craft a ZIP backup where the photo bytes do NOT match the manifest SHA-256 hash
        val tamperedPhotoBytes = byteArrayOf(1, 2, 3, 4, 5)
        val wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

        val manifestJson = JSONObject().apply {
            put("schemaVersion", 2)
            put("exportedAt", System.currentTimeMillis())
            put(
                "boxes",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "restored-box-id")
                        put("shortCode", "BOX-100")
                        put("name", "Tampered Backup Box")
                        put("location", "Attic")
                    }
                )
            )
            put("items", JSONArray())
            put(
                "photos",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "photo-tampered-1")
                        put("boxId", "restored-box-id")
                        put("itemId", JSONObject.NULL)
                        put("relativePath", "box_tampered.jpg")
                        put("createdAt", System.currentTimeMillis())
                        put("sha256", wrongSha256)
                        put("sizeBytes", tamperedPhotoBytes.size)
                    }
                )
            )
        }

        val tamperedZipBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zipOut ->
                zipOut.putNextEntry(ZipEntry(BackupManager.MANIFEST_FILE_NAME))
                zipOut.write(manifestJson.toString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("photos/box_tampered.jpg"))
                zipOut.write(tamperedPhotoBytes)
                zipOut.closeEntry()
            }
        }.toByteArray()

        try {
            backupManager.restoreBackupFromZip(ByteArrayInputStream(tamperedZipBytes))
            fail("Expected SecurityException for SHA-256 checksum mismatch")
        } catch (expected: SecurityException) {
            assertTrue(expected.message.orEmpty().contains("SHA-256", ignoreCase = true))
        }

        // Verify live data was NOT overwritten by the tampered backup
        val boxesAfterFailedRestore = repository.getAllBoxes()
        assertEquals(1, boxesAfterFailedRestore.size)
        assertEquals("BOX-042", boxesAfterFailedRestore.first().shortCode)
        assertNotNull(repository.getBoxById(liveBox.id))
        assertTrue(photoStorageManager.photoExists(livePhoto.relativePath))
        assertArrayEquals(livePhotoBytes, photoStorageManager.readPhotoBytes(livePhoto.relativePath))
        assertFalse(photoStorageManager.photoExists("box_tampered.jpg"))
        assertFalse(backupManager.stagingDirectory.exists())
    }

    @Test
    fun `restoreBackupFromZip rejects archive exceeding MAX_ENTRIES`() = runTest(testDispatcher) {
        val zipBuffer = ByteArrayOutputStream()
        ZipOutputStream(zipBuffer).use { zipOut ->
            for (i in 0..BackupManager.MAX_ENTRIES) {
                zipOut.putNextEntry(ZipEntry("entry_$i.txt"))
                zipOut.write("x".toByteArray())
                zipOut.closeEntry()
            }
        }

        try {
            backupManager.restoreBackupFromZip(ByteArrayInputStream(zipBuffer.toByteArray()))
            fail("Expected SecurityException due to MAX_ENTRIES violation")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("maximum entry limit") == true)
        }
        assertFalse(backupManager.stagingDirectory.exists())
    }

    @Test
    fun `BackupRestoreViewModel coordinates export and restore status messages`() = runTest(testDispatcher) {
        repository.createBox(
            name = "Backup VM Test Box",
            shortCode = "BOX-001",
            location = "Shelf 1"
        )
        val viewModel = BackupRestoreViewModel(repository, backupManager)

        val zipBuffer = ByteArrayOutputStream()
        var exportOk = false
        viewModel.exportBackup(zipBuffer) { ok -> exportOk = ok }
        val exportState = viewModel.uiState.first { !it.isWorking && (it.lastExportSummary != null || it.errorMessage != null) }
        assertTrue(exportState.errorMessage ?: "export failed", exportOk)
        assertNotNull(exportState.lastExportSummary)
        assertNotNull(exportState.statusMessage)

        var restoreOk = false
        viewModel.restoreBackup(ByteArrayInputStream(zipBuffer.toByteArray())) { ok -> restoreOk = ok }
        val restoreState = viewModel.uiState.first { !it.isWorking && (it.lastRestoreSummary != null || it.errorMessage != null) }
        assertTrue(restoreState.errorMessage ?: "restore failed", restoreOk)
        assertNotNull(restoreState.lastRestoreSummary)
    }
}
