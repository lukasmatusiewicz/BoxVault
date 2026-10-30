package com.boxvault.app.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.repository.BoxVaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface QrScanResolution {
    data class ExistingBoxFound(
        val boxId: String,
        val shortCode: String,
        val name: String
    ) : QrScanResolution

    data class UnassignedQrLabelScanned(
        val presetBoxId: String,
        val qrPayload: String
    ) : QrScanResolution
}

data class QrScannerUiState(
    val manualInput: String = "",
    val isProcessing: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val lastScannedRawValue: String? = null,
    val resolution: QrScanResolution? = null,
    val errorMessage: String? = null
)

class QrScannerViewModel(
    private val repository: BoxVaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    fun onManualInputChange(value: String) {
        _uiState.update {
            it.copy(
                manualInput = value,
                errorMessage = null
            )
        }
    }

    fun onTorchToggled(enabled: Boolean) {
        _uiState.update { it.copy(isTorchEnabled = enabled) }
    }

    /**
     * Processes a QR payload detected by CameraX + bundled ML Kit BarcodeScanning.
     * Supports both existing boxes in Room and pre-allocated blank `boxvault://box/<uuid>` labels.
     */
    fun onQrPayloadDetected(rawValue: String) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return

        val snapshot = _uiState.value
        if (snapshot.isProcessing || snapshot.resolution != null) return

        _uiState.update {
            it.copy(
                isProcessing = true,
                lastScannedRawValue = trimmed,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            resolveInput(trimmed, isCameraScan = true)
        }
    }

    /**
     * Processes manual short-code (e.g. `BOX-001` or `1`) or full QR URI (`boxvault://box/<uuid>`) fallback.
     */
    fun submitManualLookup() {
        val trimmed = _uiState.value.manualInput.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Enter a box short code (e.g. BOX-001) or QR URI.")
            }
            return
        }

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            resolveInput(trimmed, isCameraScan = false)
        }
    }

    private suspend fun resolveInput(input: String, isCameraScan: Boolean) {
        // 1. Check if input is a valid BoxVault QR URI (`boxvault://box/<id>`) or UUID
        val parsedBoxId = BoxEntity.parseBoxIdFromQrPayload(input)
        val hasExplicitScheme = input.startsWith(BoxEntity.QR_URI_PREFIX, ignoreCase = true)

        if (parsedBoxId != null) {
            val existingById = repository.getBoxById(parsedBoxId)
            if (existingById != null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        resolution = QrScanResolution.ExistingBoxFound(
                            boxId = existingById.id,
                            shortCode = existingById.shortCode,
                            name = existingById.name
                        ),
                        errorMessage = null
                    )
                }
                return
            }

            // Pre-printed blank QR label scanned or entered -> route to BoxEditScreen with presetBoxId
            if (hasExplicitScheme || isCameraScan) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        resolution = QrScanResolution.UnassignedQrLabelScanned(
                            presetBoxId = parsedBoxId,
                            qrPayload = "${BoxEntity.QR_URI_PREFIX}$parsedBoxId"
                        ),
                        errorMessage = null
                    )
                }
                return
            }
        }

        // 2. Fallback: look up by shortCode (e.g. "BOX-001", or numeric "1" -> "BOX-001")
        val directMatch = repository.getBoxByShortCode(input)
            ?: input.toIntOrNull()?.let { number ->
                repository.getBoxByShortCode(String.format(Locale.US, "BOX-%03d", number))
            }

        if (directMatch != null) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    resolution = QrScanResolution.ExistingBoxFound(
                        boxId = directMatch.id,
                        shortCode = directMatch.shortCode,
                        name = directMatch.name
                    ),
                    errorMessage = null
                )
            }
            return
        }

        // 3. Unrecognized QR or unknown manual short code
        val message = if (isCameraScan) {
            "Unrecognized QR code: \"$input\". Scan a BoxVault label (boxvault://box/...) or type a short code below."
        } else {
            "No box found matching \"$input\"."
        }

        _uiState.update {
            it.copy(
                isProcessing = false,
                errorMessage = message
            )
        }
    }

    fun consumeResolution() {
        _uiState.update {
            it.copy(
                resolution = null,
                isProcessing = false
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, lastScannedRawValue = null) }
    }

    class Factory(
        private val repository: BoxVaultRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(QrScannerViewModel::class.java)) {
                return QrScannerViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

