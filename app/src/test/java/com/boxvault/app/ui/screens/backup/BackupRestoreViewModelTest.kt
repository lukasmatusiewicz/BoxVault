package com.boxvault.app.ui.screens.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.backup.BackupManager
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: BoxVaultDatabase
    private lateinit var tempFilesDir: File
    private lateinit var tempCacheDir: File
    private lateinit var repository: BoxVaultRepository
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempFilesDir = Files.createTempDirectory("boxvault_backup_vm_files").toFile()
        tempCacheDir = Files.createTempDirectory("boxvault_backup_vm_cache").toFile()
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
    fun `observes live inventory counts and exports + restores successfully`() = runTest(testDispatcher) {
        val room = repository.createRoom(name = "Basement")
        val box = repository.createBox(name = "Tools", shortCode = "BOX-001", location = "Shelf", roomId = room.id)
        repository.createItem(boxId = box.id, name = "Wrench", quantity = 3)

        val viewModel = BackupRestoreViewModel(repository, backupManager)
        val liveState = viewModel.uiState.first { it.liveBoxCount == 1 && it.liveTotalItemQuantity == 3 }
        assertEquals(1, liveState.liveDistinctItemCount)

        val zipBuffer = ByteArrayOutputStream()
        var exportOk = false
        viewModel.exportBackup(zipBuffer) { ok -> exportOk = ok }
        val afterExport = viewModel.uiState.first { !it.isWorking && it.lastExportSummary != null }
        assertTrue(exportOk)
        assertTrue(afterExport.statusMessage.orEmpty().contains("1 rooms"))
        assertNull(afterExport.errorMessage)

        var restoreOk = false
        viewModel.restoreBackup(ByteArrayInputStream(zipBuffer.toByteArray())) { ok -> restoreOk = ok }
        val afterRestore = viewModel.uiState.first { !it.isWorking && it.lastRestoreSummary != null }
        assertTrue(restoreOk)
        assertTrue(afterRestore.statusMessage.orEmpty().contains("Atomically restored"))
        assertNull(afterRestore.errorMessage)

        viewModel.clearMessages()
        assertNull(viewModel.uiState.value.statusMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `exportBackup failure, restoreBackup failure, and reportError surface errorMessage`() = runTest(testDispatcher) {
        val viewModel = BackupRestoreViewModel(repository, backupManager)

        val failingOut = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("Export stream failed")
            }
        }
        var exportOk = true
        viewModel.exportBackup(failingOut) { ok -> exportOk = ok }
        val exportFailState = viewModel.uiState.first { !it.isWorking && it.errorMessage != null }
        assertFalse(exportOk)
        assertTrue(exportFailState.errorMessage.orEmpty().contains("Export stream failed"))

        var restoreOk = true
        viewModel.restoreBackup(ByteArrayInputStream(byteArrayOf())) { ok -> restoreOk = ok }
        val restoreFailState = viewModel.uiState.first { !it.isWorking && it.errorMessage != null }
        assertFalse(restoreOk)
        assertTrue(restoreFailState.errorMessage.orEmpty().contains("Restore aborted safely"))

        viewModel.reportError("Unable to open SAF URI")
        assertEquals("Unable to open SAF URI", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isWorking)
    }
}
