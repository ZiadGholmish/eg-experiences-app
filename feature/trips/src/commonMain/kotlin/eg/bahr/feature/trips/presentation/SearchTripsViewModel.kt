package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import co.touchlab.kermit.Logger
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.RecentSearchesStore
import eg.bahr.feature.trips.data.TripApiService
import eg.bahr.feature.trips.data.TripListPagingSource
import eg.bahr.feature.trips.data.TripPagingConfig
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.model.FILTER_ALL
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.model.FacetType
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripPageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long the typed text must rest before it is searched. Short enough to feel live, long enough
 * that a word typed at normal speed is one request rather than one per letter.
 */
internal const val SEARCH_DEBOUNCE_MILLIS = 300L

/** Fewer characters than this (outer spaces not counted) search nothing: one letter matches almost every trip. */
internal const val MIN_QUERY_LENGTH = 2

/**
 * Search (M4-M3): `GET /trips?q=<text>&filter=<key>`, paged.
 *
 * [text] is what the field holds. [query] is the text being searched, null while there is none
 * (fewer than [MIN_QUERY_LENGTH] characters): then the screen shows the [recents] instead of results.
 * [filter] is the active filter chip's key, null for "all". [facets] and [totalItems] come from the
 * current search's first page; the chips stay from the previous search until the new one answers,
 * so they do not blink out while typing.
 */
internal data class SearchTripsUiState(
    val text: String = "",
    val query: String? = null,
    val filter: String? = null,
    val facets: List<FacetDto> = emptyList(),
    val totalItems: Long? = null,
    val recents: List<String> = emptyList(),
    val waitlistTags: WaitlistTags = WaitlistTags.None,
) {
    /** The filter chips, in the served order. The category chips are not drawn on the search page. */
    val filterChips: List<FacetDto>
        get() = facets.filter { it.type == FacetType.FILTER && !it.key.isNullOrBlank() }

    /** Whether [chip] is the active filter. Local, so a tap shows at once, before the server confirms it. */
    fun isSelected(chip: FacetDto): Boolean = chip.key == (filter ?: FILTER_ALL)
}

/** What one search asks `GET /trips` for. */
private data class SearchQuery(
    val text: String?,
    val filter: String?,
)

/**
 * The text is sent as typed: the server folds Arabic spelling, case and digits, in both languages,
 * and matches word by word (every word somewhere in the trip, any order). Only outer spaces and
 * control characters are left out, so "felucca " is the same search as "felucca" and is not sent again.
 *
 * Typing is debounced ([SEARCH_DEBOUNCE_MILLIS]); a new search replaces the one before it, and its
 * request in flight is cancelled (`flatMapLatest`). The keyboard's search key and a recent search
 * run at once, without waiting.
 *
 * A search is remembered in [recentSearches] when the user commits to it (the keyboard's search key,
 * opening one of its trips, or a recent search tapped again), not on every pause in typing, which
 * would fill the list with "fe", "fel", "felu".
 *
 * A 400 (VALIDATION_FAILED) is the list's error like any other failure: the text and the filter stay
 * as the user left them, never dropped behind their back. The field cannot hold more than
 * [TripApiService.MAX_QUERY_LENGTH] characters and the filter keys come from the server's own chips,
 * so it is not expected; unlike the category page there is no stale key to fall back from.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class SearchTripsViewModel(
    private val repository: TripRepository,
    private val recentSearches: RecentSearchesStore,
    waitlists: WaitlistMemory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchTripsUiState())
    val uiState: StateFlow<SearchTripsUiState> = _uiState.asStateFlow()

    /** The typed text, debounced into [SearchTripsUiState.query]. */
    private val typed = MutableStateFlow("")

    /**
     * The results of the current search, a page at a time, best match first as the server orders
     * them. No search is an empty list that reads as not loaded yet, so a search that has just
     * started never flashes "no trips match" before its first page.
     */
    val trips: Flow<PagingData<TripCardDto>> =
        _uiState
            .map { SearchQuery(it.query, it.filter) }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                val text = query.text ?: return@flatMapLatest flowOf(PagingData.empty(NOT_SEARCHED))
                Pager(TripPagingConfig) {
                    TripListPagingSource(repository, filter = query.filter, q = text) { onFirstPage(query, it) }
                }.flow
            }.distinctTrips()
            .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            typed
                // Too short searches nothing, and says so at once: the results go as soon as the text does.
                .debounce { if (searchable(it) == null) 0L else SEARCH_DEBOUNCE_MILLIS }
                .collect { setQuery(searchable(it)) }
        }
        viewModelScope.launch {
            recentSearches.searches
                .catch { log.w(it) { "Recent searches not read" } }
                .collect { recents -> _uiState.update { it.copy(recents = recents) } }
        }
        collectWaitlistTags(waitlists) { tags -> _uiState.update { it.copy(waitlistTags = tags) } }
    }

    /** The field changed. Longer than the contract allows is refused, never cut (a cut can split a character). */
    fun onTextChange(text: String) {
        if (text.length > TripApiService.MAX_QUERY_LENGTH) return
        _uiState.update { it.copy(text = text) }
        typed.value = text
    }

    /** The keyboard's search key: search what is typed now, and remember it. */
    fun search() {
        val query = searchable(_uiState.value.text) ?: return
        setQuery(query)
        remember(query)
    }

    /** A recent search tapped: it fills the field and runs at once. */
    fun searchRecent(recent: String) {
        val query = searchable(recent) ?: return
        _uiState.update { it.copy(text = recent) }
        typed.value = recent
        setQuery(query)
        remember(query)
    }

    /** A trip opened from the results: the search found something, so it is worth offering again. */
    fun onResultOpened() {
        _uiState.value.query?.let(::remember)
    }

    fun clearRecents() {
        viewModelScope.launch {
            try {
                recentSearches.clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                log.w(failed) { "Recent searches not cleared" }
            }
        }
    }

    /** A filter chip tapped. Tapping the active one (or "all") clears the filter. */
    fun selectFilter(key: String) {
        _uiState.update { state ->
            val cleared = key == FILTER_ALL || key == state.filter
            val filter = if (cleared) null else key
            // The count belongs to the search; the chips stay (with their old counts) until it answers.
            if (filter == state.filter) state else state.copy(filter = filter, totalItems = null)
        }
    }

    /**
     * A new search keeps the filter the user chose (the chips stay on screen while typing). Going
     * back to no search starts afresh: no filter, no chips, no count.
     */
    private fun setQuery(query: String?) {
        _uiState.update { state ->
            when {
                state.query == query -> state
                query == null -> state.copy(query = null, filter = null, facets = emptyList(), totalItems = null)
                else -> state.copy(query = query, totalItems = null)
            }
        }
    }

    private fun onFirstPage(
        query: SearchQuery,
        result: AppResult<TripPageDto>,
    ) {
        if (result !is AppResult.Success) return
        _uiState.update { state ->
            // An answer for a search the user has already moved on from changes nothing.
            if (SearchQuery(state.query, state.filter) != query) return@update state
            state.copy(facets = result.data.facets, totalItems = result.data.totalItems)
        }
    }

    private fun remember(query: String) {
        viewModelScope.launch {
            try {
                recentSearches.add(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // Only the list of recent searches misses it; the search itself goes on.
                log.w(failed) { "Recent search not stored" }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("SearchTrips")

        /** Not loading, not failed, not at the end: the list's status reads it as "loading", never "empty". */
        val NOT_SEARCHED =
            LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = false),
            )
    }
}

/**
 * [text] as it is searched: control characters out (the server refuses them with 400), outer spaces
 * dropped, or null when what is left is too short to search. A lone one-letter word would match
 * almost every trip, which is what the minimum is for.
 */
internal fun searchable(text: String): String? = text.withoutControlCharacters().trim().takeIf { it.length >= MIN_QUERY_LENGTH }

/**
 * [this] without C0/C1 control characters (and DEL), which `listTrips` answers with 400
 * VALIDATION_FAILED. Tab and newline are allowed by the contract and kept. The field strips them as
 * they are typed or pasted (see `SearchTripsScreen`); this keeps a stray one off the wire anyway.
 */
internal fun String.withoutControlCharacters(): String = filterNot { it.isISOControl() && it != '\t' && it != '\n' }
