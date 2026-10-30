package com.boxvault.app.ui.screens.boxdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.local.model.BoxWithItemsAndPhotos
import com.boxvault.app.data.local.model.ItemWithPhotos
import com.boxvault.app.data.repository.BoxVaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BoxDetailUiState(
    val isLoading: Boolean = true,
    val boxId: String = "",
    val boxWithDetails: BoxWithItemsAndPhotos? = null,
    val itemsWithPhotos: List<ItemWithPhotos> = emptyList(),
    val filteredItemsWithPhotos: List<ItemWithPhotos> = emptyList(),
    val boxLevelPhotos: List<PhotoEntity> = emptyList(),
    val itemFilterQuery: String = "",
    val isBoxDeleted: Boolean = false,
    val errorMessage: String? = null
) {
    val box: BoxEntity?
        get() = boxWithDetails?.box

    val totalQuantity: Int
        get() = itemsWithPhotos.sumOf { it.item.quantity }

    val distinctItemCount: Int
        get() = itemsWithPhotos.size
}

class BoxDetailViewModel(
    private val repository: BoxVaultRepository,
    val boxId: String
) : ViewModel() {

    private val _itemFilterQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(BoxDetailUiState(boxId = boxId))
    val uiState: StateFlow<BoxDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeBoxWithItemsAndPhotos(boxId),
                repository.observeItemsWithPhotosForBox(boxId),
                _itemFilterQuery
            ) { boxDetails, itemsWithPhotos, filterQuery ->
                Triple(boxDetails, itemsWithPhotos, filterQuery)
            }.collect { (boxDetails, itemsWithPhotos, filterQuery) ->
                val trimmedQuery = filterQuery.trim()
                val filteredItems = if (trimmedQuery.isEmpty()) {
                    itemsWithPhotos
                } else {
                    itemsWithPhotos.filter { entry ->
                        entry.item.name.contains(trimmedQuery, ignoreCase = true) ||
                            entry.item.category.contains(trimmedQuery, ignoreCase = true) ||
                            entry.item.notes.contains(trimmedQuery, ignoreCase = true)
                    }
                }
                val boxPhotos = boxDetails?.photos?.filter { it.itemId == null } ?: emptyList()

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        boxWithDetails = boxDetails,
                        itemsWithPhotos = itemsWithPhotos,
                        filteredItemsWithPhotos = filteredItems,
                        boxLevelPhotos = boxPhotos,
                        itemFilterQuery = filterQuery
                    )
                }
            }
        }
    }

    fun onItemFilterQueryChange(query: String) {
        _itemFilterQuery.value = query
        val trimmed = query.trim()
        _uiState.update { current ->
            val filtered = if (trimmed.isEmpty()) {
                current.itemsWithPhotos
            } else {
                current.itemsWithPhotos.filter { entry ->
                    entry.item.name.contains(trimmed, ignoreCase = true) ||
                        entry.item.category.contains(trimmed, ignoreCase = true) ||
                        entry.item.notes.contains(trimmed, ignoreCase = true)
                }
            }
            current.copy(
                itemFilterQuery = query,
                filteredItemsWithPhotos = filtered
            )
        }
    }

    fun incrementItemQuantity(item: ItemEntity) {
        viewModelScope.launch {
            repository.upsertItem(
                item.copy(quantity = item.quantity + 1)
            )
        }
    }

    fun decrementItemQuantity(item: ItemEntity) {
        viewModelScope.launch {
            if (item.quantity > 1) {
                repository.upsertItem(
                    item.copy(quantity = item.quantity - 1)
                )
            }
        }
    }

    fun saveItem(
        name: String,
        quantity: Int = 1,
        category: String = "General",
        notes: String = "",
        existingItemId: String? = null
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Item name cannot be empty.") }
            return
        }
        viewModelScope.launch {
            if (existingItemId != null) {
                val existing = _uiState.value.itemsWithPhotos
                    .firstOrNull { it.item.id == existingItemId }
                    ?.item
                if (existing != null) {
                    repository.upsertItem(
                        existing.copy(
                            name = trimmedName,
                            quantity = quantity.coerceAtLeast(1),
                            category = category.trim().ifEmpty { "General" },
                            notes = notes.trim()
                        )
                    )
                }
            } else {
                repository.createItem(
                    boxId = boxId,
                    name = trimmedName,
                    quantity = quantity.coerceAtLeast(1),
                    category = category.trim().ifEmpty { "General" },
                    notes = notes.trim()
                )
            }
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteItem(itemId)
        }
    }

    fun attachPhotoToBox(imageBytes: ByteArray) {
        if (imageBytes.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.attachPhotoToBox(boxId = boxId, imageBytes = imageBytes)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to attach photo.") }
            }
        }
    }

    fun attachPhotoToItem(itemId: String, imageBytes: ByteArray) {
        if (imageBytes.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.attachPhotoToItem(
                    boxId = boxId,
                    itemId = itemId,
                    imageBytes = imageBytes
                )
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to attach item photo.") }
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
        }
    }

    fun deleteBox(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteBox(boxId)
            _uiState.update { it.copy(isBoxDeleted = true) }
            onDeleted()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val repository: BoxVaultRepository,
        private val boxId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BoxDetailViewModel::class.java)) {
                return BoxDetailViewModel(repository, boxId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
