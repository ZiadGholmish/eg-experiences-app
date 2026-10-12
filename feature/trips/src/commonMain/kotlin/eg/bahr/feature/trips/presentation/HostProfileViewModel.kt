package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import eg.bahr.core.catalog.data.TripListPagingSource
import eg.bahr.core.catalog.data.TripPagingConfig
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.presentation.Throttle
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.catalog.presentation.collectWaitlistTags
import eg.bahr.core.catalog.presentation.distinctTrips
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.model.HostProfileDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The host page (M4-M10). [previewName] and [previewAvatar] are what the trip page's host card showed,
 * carried through navigation so the header's text (and the card's morph into it) is there on the first
 * frame, before `getHost` answers (text first).
 *
 * [notAvailable]: `getHost` or the host's trip list answered NOT_FOUND (an unknown slug, or a host who
 * is not approved: the trip card carries the slug either way, M4-B7 review #2). One whole-page state,
 * with no retry: asking again gives the same answer. [error] is any other failure of the profile, which
 * a retry may fix.
 */
internal data class HostProfileUiState(
    val previewName: String? = null,
    val previewAvatar: String? = null,
    val profile: HostProfileDto? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val notAvailable: Boolean = false,
    /** How many published trips the host's list holds, from its first page. */
    val totalTrips: Long? = null,
    val waitlistTags: WaitlistTags = WaitlistTags.None,
) {
    val name: String? get() = profile?.name ?: previewName
    val avatar: String? get() = profile?.avatar?.url ?: previewAvatar
}

/**
 * A host's public page: `GET /hosts/{slug}` for the header, rating and recent reviews, and the host's
 * trips paged from `GET /trips?host={slug}` (core:catalog's list, like the category page). The two are
 * read side by side, so a slow profile does not hold the trips back, or the other way round.
 */
internal class HostProfileViewModel(
    private val slug: String,
    previewName: String?,
    previewAvatar: String?,
    private val repository: TripRepository,
    waitlists: WaitlistMemory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HostProfileUiState(previewName = previewName, previewAvatar = previewAvatar))
    val uiState: StateFlow<HostProfileUiState> = _uiState.asStateFlow()

    /** Lets [retry] read the list again from page 0: each invalidation makes a fresh source. */
    private val sources =
        InvalidatingPagingSourceFactory {
            TripListPagingSource(repository, host = slug) { result ->
                when (result) {
                    is AppResult.Success -> _uiState.update { it.copy(totalTrips = result.data.totalItems) }
                    is AppResult.Failure -> if (result.error.isNotFound()) _uiState.update { it.copy(notAvailable = true) }
                }
            }
        }

    /** A burst of Retry taps is one request (M4-M6). */
    private val retries = Throttle(viewModelScope)

    private var profileLoad: Job? = null

    val trips: Flow<PagingData<TripCardDto>> =
        // A lambda, not the factory itself: common metadata does not see the factory as a function type.
        Pager(TripPagingConfig, pagingSourceFactory = { sources() })
            .flow
            .distinctTrips()
            .cachedIn(viewModelScope)

    init {
        loadProfile()
        collectWaitlistTags(waitlists) { tags -> _uiState.update { it.copy(waitlistTags = tags) } }
    }

    /**
     * Retry, from the page's error: reads the profile again and the trip list from its first page (a
     * network failure most likely failed both). Throttled.
     */
    fun retry() =
        retries.attempt {
            loadProfile()
            sources.invalidate()
        }

    /** Retry, from the trip list's own first-page error (the profile is showing): the list only. Throttled. */
    fun retryTrips() = retries.attempt { sources.invalidate() }

    private fun loadProfile() {
        // A retry while a read is still out replaces it, so an old answer cannot land after a new one.
        profileLoad?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        profileLoad =
            viewModelScope.launch {
                when (val result = repository.hostBySlug(slug)) {
                    is AppResult.Success -> _uiState.update { it.copy(profile = result.data, isLoading = false) }
                    is AppResult.Failure ->
                        _uiState.update {
                            if (result.error.isNotFound()) {
                                it.copy(isLoading = false, notAvailable = true)
                            } else {
                                it.copy(isLoading = false, error = result.error)
                            }
                        }
                }
            }
    }
}

/** NOT_FOUND by its code (never its message or status): this host has no public page. */
private fun AppError.isNotFound(): Boolean = this is AppError.Api && code == ApiErrorCodes.NOT_FOUND
