package com.boxvault.app.ui.screens.boxedit

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
import java.util.UUID

data class BoxEditUiState(
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val boxId: String = UUID.randomUUID().toString(),
    val shortCode: String = "",
    val name: String = "",
    val location: String = "",
    val category: String = "General",
    val colorHex: String = "#F59E0B",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val existingLocations: List<String> = emptyList(),
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val savedBoxId: String? = null
) {
    val qrPayloadPreview: String
        get() = "${BoxEntity.QR_URI_PREFIX}$boxId"
}

class BoxEditViewModel(
    private val repository: BoxVaultRepository,
    private val initialBoxId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BoxEditUiState(
            isEditMode = !initialBoxId.isNullOrBlank(),
            boxId = initialBoxId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        )
    )
    val uiState: StateFlow<BoxEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDistinctLocations().collect { locations ->
                _uiState.update { it.copy(existingLocations = locations) }
            }
        }

        viewModelScope.launch {
            val targetId = initialBoxId?.takeIf { it.isNotBlank() }
            if (targetId != null) {
                val existing = repository.getBoxById(targetId)
                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEditMode = true,
                            boxId = existing.id,
                            shortCode = existing.shortCode,
                            name = existing.name,
                            location = existing.location,
                            category = existing.category,
                            colorHex = existing.colorHex,
                            notes = existing.notes,
                            createdAt = existing.createdAt
                        )
                    }
                } else {
                    val suggestedCode = repository.generateNextShortCode()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEditMode = false,
                            shortCode = suggestedCode
                        )
                    }
                }
            } else {
                val suggestedCode = repository.generateNextShortCode()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditMode = false,
                        shortCode = suggestedCode
                    )
                }
            }
        }
    }

    fun onShortCodeChange(shortCode: String) {
        _uiState.update { it.copy(shortCode = shortCode, errorMessage = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onLocationChange(location: String) {
        _uiState.update { it.copy(location = location, errorMessage = null) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category, errorMessage = null) }
    }

    fun onColorHexChange(colorHex: String) {
        _uiState.update { it.copy(colorHex = colorHex, errorMessage = null) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes, errorMessage = null) }
    }

    fun regenerateShortCode() {
        viewModelScope.launch {
            val nextCode = repository.generateNextShortCode()
            _uiState.update { it.copy(shortCode = nextCode, errorMessage = null) }
        }
    }

    fun saveBox(onSaved: (String) -> Unit = {}) {
        val snapshot = _uiState.value
        val trimmedName = snapshot.name.trim()
        val trimmedShortCode = snapshot.shortCode.trim()
        val trimmedLocation = snapshot.location.trim()

        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Box name is required.") }
            return
        }
        if (trimmedShortCode.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Box short code (e.g. BOX-001) is required.") }
            return
        }

        viewModelScope.launch {
            val existingWithCode = repository.getBoxByShortCode(trimmedShortCode)
            if (existingWithCode != null && existingWithCode.id != snapshot.boxId) {
                _uiState.update {
                    it.copy(errorMessage = "Short code \"$trimmedShortCode\" is already used by \"${existingWithCode.name}\".")
                }
                return@launch
            }

            val saved = repository.upsertBox(
                BoxEntity(
                    id = snapshot.boxId,
                    shortCode = trimmedShortCode,
                    name = trimmedName,
                    location = trimmedLocation,
                    category = snapshot.category.trim().ifEmpty { "General" },
                    colorHex = snapshot.colorHex,
                    notes = snapshot.notes.trim(),
                    createdAt = snapshot.createdAt
                )
            )
            _uiState.update {
                it.copy(
                    isSaved = true,
                    savedBoxId = saved.id,
                    errorMessage = null
                )
            }
            onSaved(saved.id)
        }
    }

    class Factory(
        private val repository: BoxVaultRepository,
        private val boxId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BoxEditViewModel::class.java)) {
                return BoxEditViewModel(repository, boxId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
