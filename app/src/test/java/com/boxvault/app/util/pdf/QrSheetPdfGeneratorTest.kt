package com.boxvault.app.util.pdf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.screens.pdfgenerator.PdfGeneratorViewModel
import com.boxvault.app.ui.screens.pdfgenerator.PdfLabelSourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrSheetPdfGeneratorTest {

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
        tempFilesDir = Files.createTempDirectory("boxvault_pdf_test").toFile()
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
    fun `calculatePageCount computes accurate multi-page counts for all grid presets`() {
        assertEquals(0, QrSheetPdfGenerator.calculatePageCount(0, QrGridPreset.LARGE_2X4))
        assertEquals(1, QrSheetPdfGenerator.calculatePageCount(1, QrGridPreset.LARGE_2X4))
        assertEquals(1, QrSheetPdfGenerator.calculatePageCount(8, QrGridPreset.LARGE_2X4))
        assertEquals(2, QrSheetPdfGenerator.calculatePageCount(9, QrGridPreset.LARGE_2X4))

        assertEquals(1, QrSheetPdfGenerator.calculatePageCount(21, QrGridPreset.MEDIUM_3X7))
        assertEquals(2, QrSheetPdfGenerator.calculatePageCount(22, QrGridPreset.MEDIUM_3X7))

        assertEquals(1, QrSheetPdfGenerator.calculatePageCount(40, QrGridPreset.SMALL_4X10))
        assertEquals(3, QrSheetPdfGenerator.calculatePageCount(85, QrGridPreset.SMALL_4X10))
    }

    @Test
    fun `generatePreAllocatedBlankLabels creates unique UUIDs and sequential short codes`() {
        val labels = QrSheetPdfGenerator.generatePreAllocatedBlankLabels(
            count = 10,
            startingShortCodeNumber = 5,
            shortCodePrefix = "BOX"
        )

        assertEquals(10, labels.size)
        assertEquals("BOX-005", labels.first().shortCode)
        assertEquals("BOX-014", labels.last().shortCode)
        assertEquals(10, labels.map { it.boxId }.toSet().size)
        assertTrue(labels.all { it.isBlankTemplate && it.qrPayload.startsWith("boxvault://box/") })
    }

    @Test
    fun `generatePdfSheet writes valid multi-page PDF stream for existing and blank labels`() {
        val labels = QrSheetPdfGenerator.generatePreAllocatedBlankLabels(
            count = 10,
            startingShortCodeNumber = 1
        )
        val output = ByteArrayOutputStream()

        val pagesWritten = QrSheetPdfGenerator.generatePdfSheet(
            labels = labels,
            outputStream = output,
            preset = QrGridPreset.LARGE_2X4,
            pageSize = PdfPageSize.A4,
            drawCutGuides = true
        )

        assertEquals(2, pagesWritten)
        val pdfBytes = output.toByteArray()
        assertTrue("Generated PDF byte stream should be non-empty", pdfBytes.isNotEmpty())
        val header = String(pdfBytes.take(5).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }

    @Test
    fun `PdfGeneratorViewModel supports existing boxes and pre-allocated blank sheets`() = runTest(testDispatcher) {
        val box1 = repository.upsertBox(
            BoxEntity(
                shortCode = "BOX-001",
                name = "Holiday Lights",
                location = "Attic"
            )
        )
        val box2 = repository.upsertBox(
            BoxEntity(
                shortCode = "BOX-002",
                name = "Winter Coats",
                location = "Hall Closet"
            )
        )

        val viewModel = PdfGeneratorViewModel(
            repository = repository,
            initialBoxId = box1.id,
            ioDispatcher = testDispatcher
        )
        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(PdfLabelSourceMode.EXISTING_BOXES, state.sourceMode)
        assertEquals(setOf(box1.id), state.selectedBoxIds)

        // Select all boxes and export PDF
        viewModel.selectAllBoxes()
        assertEquals(setOf(box1.id, box2.id), viewModel.uiState.value.selectedBoxIds)

        val pdfOut = ByteArrayOutputStream()
        var exportSucceeded = false
        viewModel.exportPdfToStream(pdfOut) { success -> exportSucceeded = success }

        assertTrue(exportSucceeded)
        assertTrue(pdfOut.toByteArray().isNotEmpty())
    }

    @Test
    fun `BoxVaultPrintDocumentAdapter holds correct label specs and computes page counts`() {
        val labels = (1..10).map { i ->
            QrLabelSpec(
                shortCode = "BOX-$i",
                name = "Box $i"
            )
        }
        val adapter = BoxVaultPrintDocumentAdapter(
            labels = labels,
            preset = QrGridPreset.LARGE_2X4,
            pageSize = PdfPageSize.A4,
            jobName = "Test_Job"
        )

        assertEquals(10, adapter.labels.size)
        assertEquals(QrGridPreset.LARGE_2X4, adapter.preset)
        assertEquals(PdfPageSize.A4, adapter.pageSize)
        assertEquals("Test_Job", adapter.jobName)
        assertEquals(2, adapter.computeTotalPages())
    }
}

