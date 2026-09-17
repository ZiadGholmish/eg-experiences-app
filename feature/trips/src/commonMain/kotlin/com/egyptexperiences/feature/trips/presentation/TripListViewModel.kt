package com.egyptexperiences.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egyptexperiences.core.common.result.AppError
import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.feature.trips.data.TripRepository
import com.egyptexperiences.feature.trips.model.TripSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val trips: List<TripSummaryDto> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val error: AppError? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
) {
    /** Category filtering is client-side: the list endpoint takes no filter yet. */
    val visibleTrips: List<TripSummaryDto>
        get() = selectedCategory?.let { category -> trips.filter { it.category == category } } ?: trips
}

class TripListViewModel(
    private val repository: TripRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripListUiState())
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        load(page = 0, append = false)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        load(page = state.page + 1, append = true)
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private fun load(
        page: Int,
        append: Boolean,
    ) {
        viewModelScope.launch {
            when (val result = repository.listTrips(page)) {
                is AppResult.Success -> {
                    val items = result.data.items
                    _uiState.update { state ->
                        val trips = if (append) state.trips + items else items
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            trips = trips,
                            categories = trips.mapNotNull { it.category }.distinct(),
                            page = result.data.page,
                            hasMore = result.data.hasMore,
                            error = null,
                        )
                    }
                }

                is AppResult.Failure ->
                    _uiState.update {
                        // A failed "load more" keeps the pages already shown;
                        // only a failed first page empties the screen.
                        it.copy(isLoading = false, isLoadingMore = false, error = result.error)
                    }
            }
        }
    }
}
