package com.boxvault.app.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boxvault.app.data.backup.BackupExportSummary
import com.boxvault.app.data.backup.BackupManager
import com.boxvault.app.data.backup.BackupRestoreSummary
import com.boxvault.app.data.repository.BoxVaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

data class BackupRestoreUiState(
    val liveBoxCount: Int = 0,
    val liveDistinctItemCount: Int = 0,
    val liveTotalItemQuantity: Int = 0,
    val isWorking: Boolean = false,
    val lastExportSummary: BackupExportSummary? = null,
    val lastRestoreSummary: BackupRestoreSummary? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class BackupRestoreViewModel(
    private val repository: BoxVaultRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeTotalBoxCount(),
                repository.observeDistinctItemCount(),
                repository.observeTotalItemQuantity()
            ) { boxes, distinctItems, totalQty ->
                Triple(boxes, distinctItems, totalQty)
            }.collect { (boxes, distinctItems, totalQty) ->
                _uiState.update {
                    it.copy(
                        liveBoxCount = boxes,
                        liveDistinctItemCount = distinctItems,
                        liveTotalItemQuantity = totalQty
                    )
                }
            }
        }
    }

    fun exportBackup(
        outputStream: OutputStream,
        onComplete: (Boolean) -> Unit = {}
    ) {
        _uiState.update {
            it.copy(
                isWorking = true,
                statusMessage = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                outputStream.use { stream ->
                    backupManager.exportBackupToZip(stream)
                }
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        lastExportSummary = summary,
                        statusMessage = "Exported ${summary.boxCount} boxes, ${summary.itemCount} items, and ${summary.photoCount} SHA-256 verified photos.",
                        errorMessage = null
                    )
                }
                onComplete(true)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        errorMessage = err.message ?: "Failed to export local ZIP backup."
                    )
                }
                onComplete(false)
            }
        }
    }

    fun restoreBackup(
        inputStream: InputStream,
        onComplete: (Boolean) -> Unit = {}
    ) {
        _uiState.update {
            it.copy(
                isWorking = true,
                statusMessage = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                inputStream.use { stream ->
                    backupManager.restoreBackupFromZip(stream)
                }
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        lastRestoreSummary = summary,
                        statusMessage = "Atomically restored ${summary.boxCount} boxes, ${summary.itemCount} items, and ${summary.photoCount} SHA-256 verified photos.",
                        errorMessage = null
                    )
                }
                onComplete(true)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        errorMessage = "Restore aborted safely (live data untouched): ${err.message ?: "Invalid archive."}"
                    )
                }
                onComplete(false)
            }
        }
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    class Factory(
        private val repository: BoxVaultRepository,
        private val backupManager: BackupManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BackupRestoreViewModel::class.java)) {
                return BackupRestoreViewModel(repository, backupManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

