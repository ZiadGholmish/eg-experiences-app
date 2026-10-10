package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
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
import eg.bahr.feature.trips.model.HomeSectionDto
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripsSectionDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home: the server-driven [sections] (M4-M1a: banners, trip rows, categories, in the server's order,
 * only the ones this build draws) above the full list of trips, which is paged
 * ([TripListViewModel.trips], M4-M1b) and so carries its own loading and error states.
 *
 * The two reads are independent. Home's sections are extra, so a failed `/home` only leaves them out
 * (and is logged), and never takes the list down with it.
 */
internal data class TripListUiState(
    val awaitingHome: Boolean = true,
    val sections: List<HomeSectionDto> = emptyList(),
    /** Which cards carry the "Waiting list" tag (M4-M5), in the rows and the list alike. */
    val waitlistTags: WaitlistTags = WaitlistTags.None,
)

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

internal class TripListViewModel(
    private val repository: TripRepository,
    waitlists: WaitlistMemory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripListUiState())
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    /** Lets [refresh] start the list over from page 0: each invalidation makes a fresh source. */
    private val listSources = InvalidatingPagingSourceFactory { TripListPagingSource(repository) }

    /**
     * "All trips", a page at a time (AndroidX Paging). Its own flow rather than a field of [uiState]:
     * Paging's presenter, in the screen, is what turns it into items and load states.
     */
    val trips: Flow<PagingData<TripCardDto>> =
        // A lambda, not the factory itself: common metadata does not see the factory as a function type.
        Pager(config = TripPagingConfig, pagingSourceFactory = { listSources() })
            .flow
            .distinctTrips()
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

    /** Retry, from the screen's error view: reads Home again and starts the list over. Throttled. */
    fun refresh() =
        retries.attempt {
            listSources.invalidate()
            loadHome()
        }

    private fun loadHome() {
        homeRead?.cancel()
        homeWait?.cancel()
        _uiState.update { it.copy(awaitingHome = true) }
        homeWait =
            viewModelScope.launch {
                delay(HOME_WAIT_MILLIS)
                _uiState.update { it.copy(awaitingHome = false) }
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
