package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import co.touchlab.kermit.Logger
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.data.TripListPagingSource
import eg.bahr.feature.trips.data.TripPagingConfig
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.CategoriesSectionDto
import eg.bahr.feature.trips.model.FILTER_ALL
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.model.FacetType
import eg.bahr.feature.trips.model.HomeSectionDto
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripPageDto
import eg.bahr.feature.trips.model.TripsSectionDto
import eg.bahr.feature.trips.model.key
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home: the server-driven [sections] (M4-M1a: banners, trip rows, categories, in the server's order,
 * only the ones this build draws) above the full list of trips, which is paged
 * ([TripListViewModel.trips], M4-M1b) and so carries its own loading and error states.
 *
 * The two reads are independent. Home's sections are extra, so a failed `/home` only leaves them out
 * (and is logged), and never takes the list down with it.
 *
 * M4-M1: the list has the handoff's filter chips. [filter] is the active chip's key (null for "all");
 * [facets] and [totalItems] come from the current filter's first page, and stay from the previous one
 * until the new one answers, so the chips do not blink out on every tap. [listAnswered] is true once a
 * first page has loaded: from then on a loading or failed list is drawn inside the page (under the
 * chips, so a filter can always be changed back), never as the whole screen.
 */
internal data class TripListUiState(
    val awaitingHome: Boolean = true,
    val sections: List<HomeSectionDto> = emptyList(),
    /** Which cards carry the "Waiting list" tag (M4-M5), in the rows and the list alike. */
    val waitlistTags: WaitlistTags = WaitlistTags.None,
    val filter: String? = null,
    val facets: List<FacetDto> = emptyList(),
    val totalItems: Long? = null,
    val listAnswered: Boolean = false,
) {
    /** The filter chips, in the served order. Category facets are Home's category tiles, not chips. */
    val filterChips: List<FacetDto>
        get() = facets.filter { it.type == FacetType.FILTER && !it.key.isNullOrBlank() }

    /** Whether [chip] is the active filter. Local, so a tap shows at once, before the server confirms it. */
    fun isSelected(chip: FacetDto): Boolean = chip.key == (filter ?: FILTER_ALL)
}

/**
 * How long a loaded list waits for Home's sections before it shows without them. Long enough for
 * `/home` on a normal connection (it is a small read run alongside `/trips`), short next to the
 * request timeout a slow one would otherwise cost.
 *
 * One loading state covers both reads, which run in parallel, so sections usually land with the list
 * rather than pushing it down after it shows; [TripListUiState.awaitingHome] holds it for at most
 * this long, so a slow `/home` cannot keep a list that has arrived behind a spinner (text first on a
 * slow network). Sections that come later are inserted above the list.
 */
internal const val HOME_WAIT_MILLIS = 1_200L

/** One load of the list: [filter] (null = all), [attempt] going up on Retry so the same filter loads again. */
private data class HomeListLoad(
    val filter: String?,
    val attempt: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class TripListViewModel(
    private val repository: TripRepository,
    waitlists: WaitlistMemory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripListUiState())
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    /** Bumped by [refresh]: the list starts over from page 0, on the same filter. */
    private val attempts = MutableStateFlow(0)

    /**
     * "All trips", a page at a time (AndroidX Paging). Its own flow rather than a field of [uiState]:
     * Paging's presenter, in the screen, is what turns it into items and load states.
     *
     * A new filter (a chip tap) starts a new Pager, so the list starts over at page 0. The same load
     * twice is not asked again (`distinctUntilChanged`); taps faster than the answers collapse to the
     * last one, whose Pager replaces (and cancels) the one before (`flatMapLatest`), as on the
     * category page (M4-M6).
     */
    val trips: Flow<PagingData<TripCardDto>> =
        combine(_uiState.map { it.filter }, attempts, ::HomeListLoad)
            .distinctUntilChanged()
            .flatMapLatest { load ->
                Pager(TripPagingConfig) {
                    TripListPagingSource(repository, filter = load.filter) { onFirstPage(load.filter, it) }
                }.flow
            }.distinctTrips()
            .cachedIn(viewModelScope)

    /** Ends the wait for `/home` after [HOME_WAIT_MILLIS]; cancelled when `/home` answers first. */
    private var homeWait: Job? = null

    /** The `/home` read in flight. A newer [refresh] cancels it, so a stale answer cannot land last. */
    private var homeRead: Job? = null

    init {
        loadHome()
        viewModelScope.launch {
            // Home opens on every start, so a phone left on a date that has since run is not kept on
            // the device just because its trip page was never opened again.
            waitlists.forgetPassed()
        }
        collectWaitlistTags(waitlists) { tags -> _uiState.update { it.copy(waitlistTags = tags) } }
    }

    /** A burst of Retry taps is one reload (M4-M6). */
    private val retries = Throttle(viewModelScope)

    /**
     * Retry, from the list's error: reads Home again and starts the list over, on the same filter.
     * Throttled.
     */
    fun refresh() =
        retries.attempt {
            attempts.update { it + 1 }
            loadHome()
        }

    /**
     * A filter chip tapped. Tapping the active one (or "all") clears the filter. Only the list reloads:
     * Home's sections stay as they are, and the list does not wait for `/home` again.
     */
    fun selectFilter(key: String) {
        _uiState.update { state ->
            val cleared = key == FILTER_ALL || key == state.filter
            val filter = if (cleared) null else key
            // The count belongs to the filter; the chips stay (with their old counts) until it answers.
            if (filter == state.filter) state else state.copy(filter = filter, totalItems = null)
        }
    }

    private fun onFirstPage(
        filter: String?,
        result: AppResult<TripPageDto>,
    ) {
        _uiState.update { state ->
            // An answer for a filter the user has already moved on from changes nothing.
            if (state.filter != filter) return@update state
            when (result) {
                is AppResult.Success ->
                    state.copy(facets = result.data.facets, totalItems = result.data.totalItems, listAnswered = true)

                // A filter key the server no longer knows (chips come from its own facets, so only
                // across a deploy): show every trip rather than an error. Without a filter there is
                // nothing to drop, and the error stays the list's.
                is AppResult.Failure ->
                    if (filter != null && result.error.isValidationFailure()) {
                        log.w { "Filter $filter refused; showing every trip" }
                        state.copy(filter = null, totalItems = null)
                    } else {
                        state
                    }
            }
        }
    }

    private fun loadHome() {
        homeRead?.cancel()
        homeWait?.cancel()
        // The list waits for `/home` only before it has first shown. After that a reload (Retry under
        // the chips) keeps the page as it is, and the sections are swapped in when they arrive.
        if (_uiState.value.listAnswered) {
            homeWait = null
        } else {
            _uiState.update { it.copy(awaitingHome = true) }
            homeWait = launchHomeWait()
        }
        homeRead =
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

    private fun launchHomeWait(): Job =
        viewModelScope.launch {
            delay(HOME_WAIT_MILLIS)
            _uiState.update { it.copy(awaitingHome = false) }
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
        val dropped =
            when (section) {
                is BannersSectionDto -> section.droppedItems
                is TripsSectionDto -> section.droppedItems
                is CategoriesSectionDto -> section.droppedItems
                is SkippedSectionDto -> 0
            }
        if (dropped > 0) log("Dropped $dropped unreadable item(s) from Home section ${section.key}")
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
