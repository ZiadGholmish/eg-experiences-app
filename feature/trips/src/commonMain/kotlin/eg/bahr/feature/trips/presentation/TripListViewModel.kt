package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.CategoriesSectionDto
import eg.bahr.feature.trips.model.HomeSectionDto
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripsSectionDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home: the server-driven [sections] (M4-M1a: banners, trip rows, categories, in the server's order,
 * only the ones this build draws) above the full list of [trips].
 *
 * The two reads are independent. [error] is the list's: Home's sections are extra, so a failed
 * `/home` only leaves them out (and is logged), and never takes the list down with it.
 */
internal data class TripListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val trips: List<TripCardDto> = emptyList(),
    val error: AppError? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val awaitingHome: Boolean = true,
    val sections: List<HomeSectionDto> = emptyList(),
) {
    /**
     * One loading state for both reads, which run in parallel, so sections usually land with the
     * list rather than pushing it down after it shows. [awaitingHome] is held for at most
     * [HOME_WAIT_MILLIS]: a slow `/home` must not keep a list that has arrived behind a spinner
     * (text first on a slow network). Sections that come later are inserted above the list.
     */
    val showsLoading: Boolean get() = isLoading || awaitingHome
}

/**
 * How long a loaded list waits for Home's sections before it shows without them. Long enough for
 * `/home` on a normal connection (it is a small read run alongside `/trips`), short next to the
 * request timeout a slow one would otherwise cost.
 */
internal const val HOME_WAIT_MILLIS = 1_200L

internal class TripListViewModel(
    private val repository: TripRepository,
    private val errors: AppErrorController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripListUiState())
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    /** Ends the wait for `/home` after [HOME_WAIT_MILLIS]; cancelled when `/home` answers first. */
    private var homeWait: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, awaitingHome = true, error = null) }
        load(page = 0, append = false)
        loadHome()
    }

    private fun loadHome() {
        homeWait?.cancel()
        homeWait =
            viewModelScope.launch {
                delay(HOME_WAIT_MILLIS)
                _uiState.update { it.copy(awaitingHome = false) }
            }
        viewModelScope.launch {
            val result = repository.home()
            homeWait?.cancel()
            when (result) {
                is AppResult.Success -> {
                    val sections = drawableSections(result.data.sections) { log.w { it } }
                    _uiState.update { it.copy(awaitingHome = false, sections = sections) }
                }

                is AppResult.Failure -> {
                    // Not shown to the user: the list below is the page, the sections are extra.
                    // Whatever sections were already on screen stay.
                    log.w { "Home sections not loaded: ${result.error}" }
                    _uiState.update { it.copy(awaitingHome = false) }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        load(page = state.page + 1, append = true)
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
                        // A trip published between two page reads shifts the pages, so the next
                        // page can repeat a card. The list is keyed by slug, and a repeated key
                        // crashes a LazyColumn.
                        val trips = if (append) (state.trips + items).distinctBy { it.slug } else items
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            trips = trips,
                            page = result.data.page,
                            hasMore = result.data.hasMore,
                            error = null,
                        )
                    }
                }

                is AppResult.Failure ->
                    if (append) {
                        // A failed "load more" keeps the pages already shown and stays
                        // usable, so it is a transient message, not screen state.
                        _uiState.update { it.copy(isLoadingMore = false) }
                        errors.show(result.error)
                    } else {
                        // Only a failed first page empties the screen.
                        _uiState.update { it.copy(isLoading = false, error = result.error) }
                    }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("Home")
    }
}

/**
 * The sections this build draws, in the server's order. A section of an unknown `type` (or one that
 * did not decode) is dropped and logged, as the contract asks, and so is an empty one (the server
 * omits those, so one would be a server bug, not something to draw a bare heading for).
 */
internal fun drawableSections(
    sections: List<HomeSectionDto>,
    log: (String) -> Unit,
): List<HomeSectionDto> =
    sections.filter { section ->
        val empty =
            when (section) {
                is BannersSectionDto -> section.items.isEmpty()
                is TripsSectionDto -> section.items.isEmpty()
                is CategoriesSectionDto -> section.items.isEmpty()
                is SkippedSectionDto -> {
                    log("Dropped a Home section of type ${section.type}: ${section.reason}")
                    return@filter false
                }
            }
        if (empty) log("Dropped an empty Home section")
        !empty
    }
