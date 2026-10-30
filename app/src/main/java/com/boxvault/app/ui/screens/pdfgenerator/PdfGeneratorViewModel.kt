package com.boxvault.app.ui.screens.pdfgenerator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.util.pdf.PdfPageSize
import com.boxvault.app.util.pdf.QrGridPreset
import com.boxvault.app.util.pdf.QrLabelSpec
import com.boxvault.app.util.pdf.QrSheetPdfGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

enum class PdfLabelSourceMode {
    EXISTING_BOXES,
    PRE_ALLOCATED_BLANK_LABELS
}

data class PdfGeneratorUiState(
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val allBoxes: List<BoxEntity> = emptyList(),
    val selectedBoxIds: Set<String> = emptySet(),
    val sourceMode: PdfLabelSourceMode = PdfLabelSourceMode.EXISTING_BOXES,
    val gridPreset: QrGridPreset = QrGridPreset.LARGE_2X4,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val drawCutGuides: Boolean = true,
    val blankLabelCount: Int = QrGridPreset.LARGE_2X4.labelsPerPage,
    val blankStartingCodeNumber: Int = 1,
    val preallocatedBlankSpecs: List<QrLabelSpec> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val activeLabelSpecs: List<QrLabelSpec>
        get() = when (sourceMode) {
            PdfLabelSourceMode.EXISTING_BOXES -> {
                allBoxes
                    .filter { it.id in selectedBoxIds }
                    .map(QrLabelSpec::fromBoxEntity)
            }
            PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS -> preallocatedBlankSpecs
        }

    val estimatedPageCount: Int
        get() = QrSheetPdfGenerator.calculatePageCount(activeLabelSpecs.size, gridPreset)
}

class PdfGeneratorViewModel(
    private val repository: BoxVaultRepository,
    private val initialBoxId: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfGeneratorUiState())
    val uiState: StateFlow<PdfGeneratorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllBoxesWithDetails().collect { boxesWithDetails ->
                val boxes = boxesWithDetails.map { it.box }
                val nextStartNumber = boxes.size + 1

                _uiState.update { current ->
                    val resolvedSelectedIds = when {
                        current.selectedBoxIds.isNotEmpty() -> {
                            current.selectedBoxIds.intersect(boxes.map { it.id }.toSet())
                        }
                        !initialBoxId.isNullOrBlank() && boxes.any { it.id == initialBoxId } -> {
                            setOf(initialBoxId)
                        }
                        else -> boxes.map { it.id }.toSet()
                    }

                    val resolvedMode = if (boxes.isEmpty() && current.isLoading) {
                        PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS
                    } else {
                        current.sourceMode
                    }

                    val blankSpecs = current.preallocatedBlankSpecs.ifEmpty {
                        QrSheetPdfGenerator.generatePreAllocatedBlankLabels(
                            count = current.blankLabelCount,
                            startingShortCodeNumber = nextStartNumber
                        )
                    }

                    current.copy(
                        isLoading = false,
                        allBoxes = boxes,
                        selectedBoxIds = resolvedSelectedIds,
                        sourceMode = resolvedMode,
                        blankStartingCodeNumber = nextStartNumber,
                        preallocatedBlankSpecs = blankSpecs
                    )
                }
            }
        }
    }

    fun onSourceModeChange(mode: PdfLabelSourceMode) {
        _uiState.update { current ->
            val updatedBlankSpecs = if (mode == PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS && current.preallocatedBlankSpecs.isEmpty()) {
                QrSheetPdfGenerator.generatePreAllocatedBlankLabels(
                    count = current.blankLabelCount,
                    startingShortCodeNumber = current.blankStartingCodeNumber
                )
            } else {
                current.preallocatedBlankSpecs
            }
            current.copy(
                sourceMode = mode,
                preallocatedBlankSpecs = updatedBlankSpecs,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun onGridPresetChange(preset: QrGridPreset) {
        _uiState.update { current ->
            val updatedBlankCount = preset.labelsPerPage
            val updatedBlankSpecs = QrSheetPdfGenerator.generatePreAllocatedBlankLabels(
                count = updatedBlankCount,
                startingShortCodeNumber = current.blankStartingCodeNumber
            )
            current.copy(
                gridPreset = preset,
                blankLabelCount = updatedBlankCount,
                preallocatedBlankSpecs = updatedBlankSpecs,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun onPageSizeChange(pageSize: PdfPageSize) {
        _uiState.update {
            it.copy(
                pageSize = pageSize,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun onDrawCutGuidesChange(enabled: Boolean) {
        _uiState.update { it.copy(drawCutGuides = enabled) }
    }

    fun toggleBoxSelection(boxId: String) {
        _uiState.update { current ->
            val updated = if (boxId in current.selectedBoxIds) {
                current.selectedBoxIds - boxId
            } else {
                current.selectedBoxIds + boxId
            }
            current.copy(
                selectedBoxIds = updated,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun selectAllBoxes() {
        _uiState.update { current ->
            current.copy(
                selectedBoxIds = current.allBoxes.map { it.id }.toSet(),
                errorMessage = null
            )
        }
    }

    fun clearBoxSelection() {
        _uiState.update { current ->
            current.copy(
                selectedBoxIds = emptySet(),
                errorMessage = null
            )
        }
    }

    fun onBlankLabelCountChange(count: Int) {
        val clamped = count.coerceIn(1, 200)
        _uiState.update { current ->
            val regenerated = QrSheetPdfGenerator.generatePreAllocatedBlankLabels(
                count = clamped,
                startingShortCodeNumber = current.blankStartingCodeNumber
            )
            current.copy(
                blankLabelCount = clamped,
                preallocatedBlankSpecs = regenerated,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun regenerateBlankLabelUuids() {
        _uiState.update { current ->
            val regenerated = QrSheetPdfGenerator.generatePreAllocatedBlankLabels(
                count = current.blankLabelCount,
                startingShortCodeNumber = current.blankStartingCodeNumber
            )
            current.copy(
                preallocatedBlankSpecs = regenerated,
                statusMessage = "Generated ${regenerated.size} fresh pre-allocated QR UUIDs.",
                errorMessage = null
            )
        }
    }

    fun exportPdfToStream(
        outputStream: OutputStream,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val snapshot = _uiState.value
        val specs = snapshot.activeLabelSpecs
        if (specs.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Select at least one box or switch to Pre-Allocated Blank Labels mode.")
            }
            onComplete(false)
            return
        }

        _uiState.update {
            it.copy(
                isExporting = true,
                statusMessage = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    outputStream.use { stream ->
                        QrSheetPdfGenerator.generatePdfSheet(
                            labels = specs,
                            outputStream = stream,
                            preset = snapshot.gridPreset,
                            pageSize = snapshot.pageSize,
                            drawCutGuides = snapshot.drawCutGuides
                        )
                    }
                }
            }.onSuccess { pagesWritten ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = "Exported ${specs.size} QR labels across $pagesWritten PDF page(s) (${snapshot.pageSize.displayName}).",
                        errorMessage = null
                    )
                }
                onComplete(true)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = err.message ?: "Failed to generate printable PDF sheet."
                    )
                }
                onComplete(false)
            }
        }
    }

    class Factory(
        private val repository: BoxVaultRepository,
        private val initialBoxId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PdfGeneratorViewModel::class.java)) {
                return PdfGeneratorViewModel(repository, initialBoxId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
