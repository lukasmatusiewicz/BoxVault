package com.boxvault.app.ui.screens.pdfgenerator

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.util.pdf.PdfPageSize
import com.boxvault.app.util.pdf.QrGridPreset
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfGeneratorViewModelTest {

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
        tempFilesDir = Files.createTempDirectory("boxvault_pdf_vm_test").toFile()
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
    fun `empty database defaults to pre-allocated blank labels mode and exports valid PDF`() = runTest(testDispatcher) {
        val viewModel = PdfGeneratorViewModel(repository, initialBoxId = null, ioDispatcher = testDispatcher)
        val initial = viewModel.uiState.first { !it.isLoading }

        assertEquals(PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS, initial.sourceMode)
        assertEquals(QrGridPreset.LARGE_2X4.labelsPerPage, initial.activeLabelSpecs.size)
        assertEquals(1, initial.estimatedPageCount)

        val pdfOutput = ByteArrayOutputStream()
        var completedOk = false
        viewModel.exportPdfToStream(pdfOutput) { ok -> completedOk = ok }

        val afterExport = viewModel.uiState.first { !it.isExporting && it.statusMessage != null }
        assertTrue(completedOk)
        assertNull(afterExport.errorMessage)
        assertTrue(pdfOutput.toString(Charsets.UTF_8.name()).startsWith("%PDF-"))
    }

    @Test
    fun `initialBoxId preselects only that box and toggle, selectAll, clearSelection work`() = runTest(testDispatcher) {
        val box1 = repository.createBox(name = "Box 1", shortCode = "BOX-001", location = "Garage")
        val box2 = repository.createBox(name = "Box 2", shortCode = "BOX-002", location = "Attic")

        val viewModel = PdfGeneratorViewModel(repository, initialBoxId = box2.id, ioDispatcher = testDispatcher)
        val loaded = viewModel.uiState.first { !it.isLoading && it.allBoxes.size == 2 }

        assertEquals(PdfLabelSourceMode.EXISTING_BOXES, loaded.sourceMode)
        assertEquals(setOf(box2.id), loaded.selectedBoxIds)
        assertEquals(1, loaded.activeLabelSpecs.size)

        viewModel.toggleBoxSelection(box1.id)
        assertEquals(setOf(box1.id, box2.id), viewModel.uiState.value.selectedBoxIds)

        viewModel.clearBoxSelection()
        assertTrue(viewModel.uiState.value.selectedBoxIds.isEmpty())

        // Exporting with 0 selected boxes fails cleanly with error message
        var emptyOk = true
        viewModel.exportPdfToStream(ByteArrayOutputStream()) { ok -> emptyOk = ok }
        assertFalse(emptyOk)
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.selectAllBoxes()
        assertEquals(setOf(box1.id, box2.id), viewModel.uiState.value.selectedBoxIds)
    }

    @Test
    fun `grid preset, page size, cut guides, blank count clamping, and UUID regeneration update state`() = runTest(testDispatcher) {
        val viewModel = PdfGeneratorViewModel(repository, ioDispatcher = testDispatcher)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onSourceModeChange(PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS)
        viewModel.onGridPresetChange(QrGridPreset.SMALL_4X10)
        assertEquals(QrGridPreset.SMALL_4X10, viewModel.uiState.value.gridPreset)
        assertEquals(40, viewModel.uiState.value.blankLabelCount)

        viewModel.onPageSizeChange(PdfPageSize.US_LETTER)
        assertEquals(PdfPageSize.US_LETTER, viewModel.uiState.value.pageSize)

        viewModel.onDrawCutGuidesChange(false)
        assertFalse(viewModel.uiState.value.drawCutGuides)

        // Clamping: 0 -> 1, 999 -> 200
        viewModel.onBlankLabelCountChange(0)
        assertEquals(1, viewModel.uiState.value.blankLabelCount)
        viewModel.onBlankLabelCountChange(999)
        assertEquals(200, viewModel.uiState.value.blankLabelCount)
        assertEquals(5, viewModel.uiState.value.estimatedPageCount) // 200 / 40 = 5 pages

        val oldIds = viewModel.uiState.value.preallocatedBlankSpecs.map { it.boxId }
        viewModel.regenerateBlankLabelUuids()
        val newIds = viewModel.uiState.value.preallocatedBlankSpecs.map { it.boxId }
        assertNotEquals(oldIds, newIds)
        assertNotNull(viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `exportPdfToStream surfaces stream IOException and reportError updates errorMessage`() = runTest(testDispatcher) {
        repository.createBox(name = "Test Box", shortCode = "BOX-001", location = "Shelf")
        val viewModel = PdfGeneratorViewModel(repository, ioDispatcher = testDispatcher)
        viewModel.uiState.first { !it.isLoading && it.allBoxes.isNotEmpty() }

        val failingStream = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("Disk full")
            }
        }
        var exportOk = true
        viewModel.exportPdfToStream(failingStream) { ok -> exportOk = ok }
        val failedState = viewModel.uiState.first { !it.isExporting && it.errorMessage != null }
        assertFalse(exportOk)
        assertTrue(failedState.errorMessage.orEmpty().contains("Disk full"))

        viewModel.reportError("Print service unavailable")
        assertEquals("Print service unavailable", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isExporting)
    }
}
