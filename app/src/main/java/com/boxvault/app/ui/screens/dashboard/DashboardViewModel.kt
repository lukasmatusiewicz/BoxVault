package com.boxvault.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boxvault.app.data.local.entity.RoomEntity
import com.boxvault.app.data.local.model.BoxSummary
import com.boxvault.app.data.local.model.GlobalSearchResult
import com.boxvault.app.data.repository.BoxVaultRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomSectionUiModel(
    val room: RoomEntity,
    val boxes: List<BoxSummary>,
    val totalItems: Int,
    val totalQuantity: Int
) {
    val boxCount: Int
        get() = boxes.size
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalBoxes: Int = 0,
    val totalItemQuantity: Int = 0,
    val distinctItemCount: Int = 0,
    val totalRooms: Int = 0,
    val rooms: List<RoomEntity> = emptyList(),
    val roomSections: List<RoomSectionUiModel> = emptyList(),
    val unassignedBoxes: List<BoxSummary> = emptyList(),
    val locations: List<String> = emptyList(),
    val selectedLocation: String? = null,
    val allBoxes: List<BoxSummary> = emptyList(),
    val filteredBoxes: List<BoxSummary> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResult: GlobalSearchResult = GlobalSearchResult(
        query = "",
        matchingBoxes = emptyList(),
        matchingItems = emptyList()
    )
) {
    val hasRooms: Boolean
        get() = rooms.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: BoxVaultRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLocation = MutableStateFlow<String?>(null)
    val selectedLocation: StateFlow<String?> = _selectedLocation.asStateFlow()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        val statsFlow = combine(
            repository.observeTotalBoxCount(),
            repository.observeTotalItemQuantity(),
            repository.observeDistinctItemCount(),
            repository.observeDistinctLocations()
        ) { totalBoxes, totalQty, distinctItems, locations ->
            DashboardStats(
                totalBoxes = totalBoxes,
                totalItemQuantity = totalQty,
                distinctItemCount = distinctItems,
                locations = locations
            )
        }

        val contentFlow = combine(
            repository.observeAllRooms(),
            repository.observeBoxSummaries(),
            _selectedLocation
        ) { rooms, boxSummaries, selectedLoc ->
            DashboardContent(
                rooms = rooms,
                boxes = boxSummaries,
                selectedLocation = selectedLoc
            )
        }

        viewModelScope.launch {
            combine(statsFlow, contentFlow) { stats, content ->
                val activeLoc = content.selectedLocation?.takeIf { it in stats.locations }
                val filtered = if (activeLoc == null) {
                    content.boxes
                } else {
                    content.boxes.filter { it.box.location.equals(activeLoc, ignoreCase = true) }
                }

                val sections = content.rooms.map { room ->
                    val roomBoxes = filtered.filter { it.box.roomId == room.id }
                    RoomSectionUiModel(
                        room = room,
                        boxes = roomBoxes,
                        totalItems = roomBoxes.sumOf { it.itemCount },
                        totalQuantity = roomBoxes.sumOf { it.totalQuantity }
                    )
                }
                val unassigned = filtered.filter { it.box.roomId == null }

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        totalBoxes = stats.totalBoxes,
                        totalItemQuantity = stats.totalItemQuantity,
                        distinctItemCount = stats.distinctItemCount,
                        totalRooms = content.rooms.size,
                        rooms = content.rooms,
                        locations = stats.locations,
                        selectedLocation = activeLoc,
                        allBoxes = content.boxes,
                        filteredBoxes = filtered,
                        roomSections = sections,
                        unassignedBoxes = unassigned
                    )
                }
            }.collect {}
        }

        viewModelScope.launch {
            _searchQuery
                .flatMapLatest { query ->
                    repository.observeGlobalSearch(query)
                }
                .collect { result ->
                    _uiState.update { current ->
                        current.copy(
                            searchResult = result,
                            isSearchActive = current.searchQuery.trim().isNotEmpty()
                        )
                    }
                }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        val trimmed = newQuery.trim()
        _uiState.update { current ->
            current.copy(
                searchQuery = newQuery,
                isSearchActive = trimmed.isNotEmpty()
            )
        }
    }

    fun onClearSearch() {
        onSearchQueryChange("")
    }

    fun onSelectLocation(location: String?) {
        val nextLocation = if (_selectedLocation.value == location) null else location
        _selectedLocation.value = nextLocation
    }

    fun createRoom(
        name: String,
        colorHex: String = "#3B82F6",
        iconName: String = "room",
        onCreated: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val created = repository.createRoom(name = name, colorHex = colorHex, iconName = iconName)
            onCreated?.invoke(created.id)
        }
    }

    fun updateRoom(roomId: String, name: String, colorHex: String, iconName: String = "room") {
        viewModelScope.launch {
            val existing = repository.getRoomById(roomId) ?: return@launch
            repository.upsertRoom(
                existing.copy(
                    name = name,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
        }
    }

    fun moveBoxToRoom(boxId: String, newRoomId: String?) {
        viewModelScope.launch {
            repository.assignBoxToRoom(boxId, newRoomId)
        }
    }

    fun deleteBox(boxId: String) {
        viewModelScope.launch {
            repository.deleteBox(boxId)
        }
    }

    private data class DashboardStats(
        val totalBoxes: Int,
        val totalItemQuantity: Int,
        val distinctItemCount: Int,
        val locations: List<String>
    )

    private data class DashboardContent(
        val rooms: List<RoomEntity>,
        val boxes: List<BoxSummary>,
        val selectedLocation: String?
    )

    class Factory(
        private val repository: BoxVaultRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
