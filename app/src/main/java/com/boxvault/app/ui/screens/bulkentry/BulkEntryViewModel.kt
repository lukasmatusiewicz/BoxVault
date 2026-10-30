package com.boxvault.app.ui.screens.bulkentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.repository.BoxVaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BulkEntryUiState(
    val boxId: String = "",
    val box: BoxEntity? = null,
    val itemNameInput: String = "",
    val quantityInput: Int = 1,
    val categoryInput: String = "General",
    val notesInput: String = "",
    val showNotesField: Boolean = false,
    val sessionAddedItems: List<ItemEntity> = emptyList(),
    val totalBoxItemCount: Int = 0,
    val totalBoxQuantity: Int = 0,
    val submissionCount: Int = 0,
    val lastAddedItemName: String? = null
)

class BulkEntryViewModel(
    private val repository: BoxVaultRepository,
    val boxId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulkEntryUiState(boxId = boxId))
    val uiState: StateFlow<BulkEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeBoxWithItemsAndPhotos(boxId),
                repository.observeItemsForBox(boxId)
            ) { boxWithDetails, allItems ->
                Pair(boxWithDetails?.box, allItems)
            }.collect { (box, allItems) ->
                val itemsById = allItems.associateBy { it.id }
                _uiState.update { current ->
                    // Sync session items with latest quantities/deletions in DB
                    val syncedSessionItems = current.sessionAddedItems.mapNotNull { sessionItem ->
                        itemsById[sessionItem.id]
                    }
                    current.copy(
                        box = box,
                        sessionAddedItems = syncedSessionItems,
                        totalBoxItemCount = allItems.size,
                        totalBoxQuantity = allItems.sumOf { it.quantity }
                    )
                }
            }
        }
    }

    fun onItemNameChange(name: String) {
        _uiState.update { it.copy(itemNameInput = name) }
    }

    fun onQuantityChange(quantity: Int) {
        _uiState.update { it.copy(quantityInput = quantity.coerceAtLeast(1)) }
    }

    fun incrementDraftQuantity() {
        _uiState.update { it.copy(quantityInput = it.quantityInput + 1) }
    }

    fun decrementDraftQuantity() {
        _uiState.update { it.copy(quantityInput = (it.quantityInput - 1).coerceAtLeast(1)) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(categoryInput = category) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notesInput = notes) }
    }

    fun toggleNotesField() {
        _uiState.update { it.copy(showNotesField = !it.showNotesField) }
    }

    /**
     * Immediately resets the input state (clearing [BulkEntryUiState.itemNameInput] and
     * resetting [BulkEntryUiState.quantityInput] to 1) while persisting the item via
     * [BoxVaultRepository.createItem] so the user can type the next item with zero latency
     * and without the soft keyboard ever closing.
     */
    fun submitCurrentItem(): Boolean {
        val snapshot = _uiState.value
        val trimmedName = snapshot.itemNameInput.trim()
        if (trimmedName.isEmpty()) {
            return false
        }

        val quantityToInsert = snapshot.quantityInput.coerceAtLeast(1)
        val categoryToInsert = snapshot.categoryInput.trim().ifEmpty { "General" }
        val notesToInsert = snapshot.notesInput.trim()

        // Immediately reset input fields synchronously so rapid typing never drops keystrokes
        _uiState.update { current ->
            current.copy(
                itemNameInput = "",
                quantityInput = 1,
                notesInput = "",
                submissionCount = current.submissionCount + 1,
                lastAddedItemName = trimmedName
            )
        }

        viewModelScope.launch {
            val created = repository.createItem(
                boxId = boxId,
                name = trimmedName,
                quantity = quantityToInsert,
                category = categoryToInsert,
                notes = notesToInsert
            )
            _uiState.update { current ->
                val withoutDuplicate = current.sessionAddedItems.filterNot { it.id == created.id }
                current.copy(
                    sessionAddedItems = listOf(created) + withoutDuplicate
                )
            }
        }
        return true
    }

    fun bumpSessionItemQuantity(itemId: String) {
        val target = _uiState.value.sessionAddedItems.firstOrNull { it.id == itemId } ?: return
        val bumped = target.copy(quantity = target.quantity + 1)

        _uiState.update { current ->
            current.copy(
                sessionAddedItems = current.sessionAddedItems.map {
                    if (it.id == itemId) bumped else it
                }
            )
        }

        viewModelScope.launch {
            repository.upsertItem(bumped)
        }
    }

    fun undoSessionItem(itemId: String) {
        _uiState.update { current ->
            current.copy(
                sessionAddedItems = current.sessionAddedItems.filterNot { it.id == itemId }
            )
        }
        viewModelScope.launch {
            repository.deleteItem(itemId)
        }
    }

    class Factory(
        private val repository: BoxVaultRepository,
        private val boxId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BulkEntryViewModel::class.java)) {
                return BulkEntryViewModel(repository, boxId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
