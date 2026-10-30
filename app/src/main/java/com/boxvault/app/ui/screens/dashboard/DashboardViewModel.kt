package com.boxvault.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalBoxes: Int = 0,
    val totalItemQuantity: Int = 0,
    val distinctItemCount: Int = 0,
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
)

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
        viewModelScope.launch {
            combine(
                repository.observeTotalBoxCount(),
                repository.observeTotalItemQuantity(),
                repository.observeDistinctItemCount(),
                repository.observeDistinctLocations(),
                repository.observeBoxSummaries()
            ) { totalBoxes, totalQty, distinctItems, locations, boxSummaries ->
                DashboardStatsAndBoxes(
                    totalBoxes = totalBoxes,
                    totalItemQuantity = totalQty,
                    distinctItemCount = distinctItems,
                    locations = locations,
                    boxes = boxSummaries
                )
            }.combine(_selectedLocation) { stats, selectedLoc ->
                val activeLoc = selectedLoc?.takeIf { it in stats.locations }
                val filtered = if (activeLoc == null) {
                    stats.boxes
                } else {
                    stats.boxes.filter { it.box.location.equals(activeLoc, ignoreCase = true) }
                }
                Triple(stats, activeLoc, filtered)
            }.collect { (stats, activeLoc, filtered) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        totalBoxes = stats.totalBoxes,
                        totalItemQuantity = stats.totalItemQuantity,
                        distinctItemCount = stats.distinctItemCount,
                        locations = stats.locations,
                        selectedLocation = activeLoc,
                        allBoxes = stats.boxes,
                        filteredBoxes = filtered
                    )
                }
            }
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
        _uiState.update { current ->
            val filtered = if (nextLocation == null) {
                current.allBoxes
            } else {
                current.allBoxes.filter { it.box.location.equals(nextLocation, ignoreCase = true) }
            }
            current.copy(
                selectedLocation = nextLocation,
                filteredBoxes = filtered
            )
        }
    }

    fun deleteBox(boxId: String) {
        viewModelScope.launch {
            repository.deleteBox(boxId)
        }
    }

    private data class DashboardStatsAndBoxes(
        val totalBoxes: Int,
        val totalItemQuantity: Int,
        val distinctItemCount: Int,
        val locations: List<String>,
        val boxes: List<BoxSummary>
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
