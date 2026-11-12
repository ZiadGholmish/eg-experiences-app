package eg.bahr.feature.host.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import co.touchlab.kermit.Logger
import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.GovernorateChipDto
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.model.HostKindKey
import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.catalog.model.HostPageDto
import eg.bahr.core.catalog.model.HostSortKey
import eg.bahr.core.catalog.presentation.Throttle
import eg.bahr.core.catalog.presentation.isValidationFailure
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.host.data.HostPagingConfig
import eg.bahr.feature.host.data.HostRepository
import eg.bahr.feature.host.data.HostsPagingSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long typing must rest before it is searched (search's own pause, M4-M3). */
internal const val HOSTS_SEARCH_DEBOUNCE_MILLIS = 300L

/** Fewer characters than this (outer spaces not counted) search nothing: the whole directory shows. */
internal const val HOSTS_MIN_QUERY_LENGTH = 2

/** `listHosts`' `q` maxLength: the field holds no more. */
internal const val HOSTS_MAX_QUERY_LENGTH = 80

/** `listHosts`' orders, in the sort menu's order; [TopRated] is the server's default. */
internal enum class HostSort(
    val key: String,
) {
    TopRated(HostSortKey.TOP_RATED),
    MostTrips(HostSortKey.MOST_TRIPS),
    Newest(HostSortKey.NEWEST),
    ;

    companion object {
        /** The order an opener asked for; anything else (or none) is the default. */
        fun of(key: String?): HostSort = entries.firstOrNull { it.key == key } ?: TopRated
    }
}

/** The person / company filter's values (openapi `HostKind`). */
internal enum class HostKindFilter(
    val key: String,
) {
    Person(HostKindKey.PERSON),
    Company(HostKindKey.COMPANY),
}

/**
 * The hosts directory (MV-M1). [text] is what the field holds; [q] the text searched (null: none, fewer than
 * [HOSTS_MIN_QUERY_LENGTH] characters). [category], [governorate] and [kind] are the chosen filters (null: none),
 * local so a tap shows at once.
 *
 * [categories] are the kind-of-trip chips (`listTrips`' category facets, Home's categories), loaded once;
 * [governorates] are the region chips of the current query's first page and [totalItems] its count, both kept
 * from the previous query until the new one answers, so the chips and the count do not blink on every tap.
 */
internal data class HostsUiState(
    val text: String = "",
    val q: String? = null,
    val sort: HostSort = HostSort.TopRated,
    val category: String? = null,
    val governorate: String? = null,
    val kind: HostKindFilter? = null,
    val categories: List<FacetDto> = emptyList(),
    val governorates: List<GovernorateChipDto> = emptyList(),
    val totalItems: Long? = null,
) {
    /** What the list asks `GET /hosts` for now. */
    val query: HostListQuery
        get() = HostListQuery(q = q, sort = sort.key, category = category, governorate = governorate, kind = kind?.key)

    /** Any filter chip on: "Clear all" shows. The search text and the order are not filters. */
    val hasFilters: Boolean get() = category != null || governorate != null || kind != null

    /** Narrowed by a search or a filter: an empty answer says "no hosts match", not "no hosts yet". */
    val narrowed: Boolean get() = q != null || hasFilters
}

/** One load of a query: [attempt] goes up on Retry, so the same query loads again from page 0. */
private data class HostsLoad(
    val query: HostListQuery,
    val attempt: Int,
)

/**
 * The hosts directory (MV-M1): `GET /hosts`, paged, searched by name, sorted and filtered. Any change of the
 * text (debounced), the sort or a filter is a new query, and a new query is a new Pager: the list starts over at
 * page 0. The same query twice is not asked again (`distinctUntilChanged`); a newer one cancels the one in flight
 * (`flatMapLatest`).
 *
 * The state lives with the back-stack entry, so the filters are still set when the user comes back from a host's
 * page. [initialQ] and [initialSort] are what the opener carried (search's "See all hosts" brings its text).
 *
 * A 400 VALIDATION_FAILED does not say which key it refused (and the message is never parsed). The keys come from
 * the server's own chips, so only one that went away since can be stale: the region is dropped first, then the
 * kind of trip; with neither left the error shows with its retry. Each step drops something, so it ends.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class HostsViewModel(
    initialQ: String?,
    initialSort: String?,
    private val repository: HostRepository,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            initialQ.orEmpty().let { text ->
                HostsUiState(text = text, q = hostSearchable(text), sort = HostSort.of(initialSort))
            },
        )
    val uiState: StateFlow<HostsUiState> = _uiState.asStateFlow()

    /** The typed text, debounced into [HostsUiState.q]. */
    private val typed = MutableStateFlow(initialQ.orEmpty())

    /** Bumped by [retry]: the failed first page loads again. */
    private val attempts = MutableStateFlow(0)

    /** A burst of Retry taps is one request (M4-M6). */
    private val retries = Throttle(viewModelScope)

    val hosts: Flow<PagingData<HostCardDto>> =
        combine(_uiState.map { it.query }, attempts, ::HostsLoad)
            .distinctUntilChanged()
            .flatMapLatest { (query, _) ->
                Pager(HostPagingConfig) { HostsPagingSource(repository, query) { onFirstPage(query, it) } }.flow
            }.map { generation ->
                // A host who joined between two page reads shifts the next page by one, so a card can repeat;
                // the list keys cards by slug, and a repeated key crashes it. Once per generation.
                val seen = mutableSetOf<String>()
                generation.filter { seen.add(it.slug) }
            }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            typed
                // Too short searches nothing, at once: the whole list comes back as soon as the text goes.
                .debounce { if (hostSearchable(it) == null) 0L else HOSTS_SEARCH_DEBOUNCE_MILLIS }
                .collect { text -> _uiState.update { it.copy(q = hostSearchable(text)) } }
        }
        loadCategories()
    }

    /**
     * The field changed. The field already cuts a paste to the contract's limit (MV-M1 review #1); longer text from
     * anywhere else is cut here too, never at the middle of a character, so the request is never refused for it.
     */
    fun onTextChange(text: String) {
        val kept = text.cutTo(HOSTS_MAX_QUERY_LENGTH)
        _uiState.update { it.copy(text = kept) }
        typed.value = kept
    }

    /** The keyboard's search key: search what is typed now, without waiting for the pause. */
    fun search() {
        _uiState.update { it.copy(q = hostSearchable(it.text)) }
    }

    fun selectSort(sort: HostSort) = _uiState.update { it.copy(sort = sort) }

    /** A kind-of-trip chip. Tapping the chosen one clears it. */
    fun selectCategory(key: String) = _uiState.update { state -> state.copy(category = key.takeUnless { key == state.category }) }

    /** A region chip, its key as served. Tapping the chosen one clears it. */
    fun selectGovernorate(key: String) = _uiState.update { state -> state.copy(governorate = key.takeUnless { key == state.governorate }) }

    /** Individuals or companies. Tapping the chosen one clears it. */
    fun selectKind(kind: HostKindFilter) = _uiState.update { state -> state.copy(kind = kind.takeUnless { kind == state.kind }) }

    /** "Clear all": every filter chip off. The search text and the order stay: they are not filters. */
    fun clearFilters() = _uiState.update { it.copy(category = null, governorate = null, kind = null) }

    /** Retry, from the first page's error: loads the current query again, and the kind chips if they failed. Throttled. */
    fun retry() =
        retries.attempt {
            attempts.update { it + 1 }
            if (_uiState.value.categories.isEmpty()) loadCategories()
        }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val result = repository.tripCategories()) {
                is AppResult.Success -> _uiState.update { it.copy(categories = result.data) }
                // The directory works without them: the list, the regions and the kinds still show.
                is AppResult.Failure -> log.w { "Kinds of trip not loaded: ${result.error}" }
            }
        }
    }

    private fun onFirstPage(
        query: HostListQuery,
        result: AppResult<HostPageDto>,
    ) {
        _uiState.update { state ->
            // An answer for a query the user has already moved on from changes nothing.
            if (state.query != query) return@update state
            when (result) {
                is AppResult.Success -> state.copy(governorates = result.data.governorates, totalItems = result.data.totalItems)
                is AppResult.Failure ->
                    when {
                        !result.error.isValidationFailure() -> state
                        query.governorate != null -> {
                            log.w { "Region ${query.governorate} refused; dropped" }
                            state.copy(governorate = null)
                        }
                        query.category != null -> {
                            log.w { "Kind of trip ${query.category} refused; dropped" }
                            state.copy(category = null)
                        }
                        // Nothing left to drop: the list's error, never another request.
                        else -> state
                    }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("Hosts")
    }
}

/** The first [max] characters, moved one back rather than splitting a surrogate pair. */
internal fun String.cutTo(max: Int): String {
    if (length <= max) return this
    val end = if (max > 0 && this[max - 1].isHighSurrogate()) max - 1 else max
    return substring(0, end)
}

/**
 * [text] as it is searched: control characters out (the server refuses them with 400), outer spaces dropped, or
 * null when what is left is too short. Tab and line feed are allowed by the contract and folded to spaces there.
 */
internal fun hostSearchable(text: String): String? =
    text
        .filterNot { it.isISOControl() && it != '\t' && it != '\n' }
        .trim()
        .takeIf { it.length >= HOSTS_MIN_QUERY_LENGTH }
