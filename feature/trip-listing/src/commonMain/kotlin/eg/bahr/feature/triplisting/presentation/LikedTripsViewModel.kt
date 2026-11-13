package eg.bahr.feature.triplisting.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import eg.bahr.core.catalog.data.TripHearts
import eg.bahr.core.catalog.data.TripLikes
import eg.bahr.core.catalog.data.TripPagingConfig
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.presentation.Throttle
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.catalog.presentation.collectTripLikes
import eg.bahr.core.catalog.presentation.collectWaitlistTags
import eg.bahr.core.catalog.presentation.distinctTrips
import eg.bahr.core.catalog.presentation.toggle
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.triplisting.data.LikedTripsPagingSource
import eg.bahr.feature.triplisting.data.TripListingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Account → Liked trips (MP-M1). [session] says which state the page is in: nothing until the secure store has been
 * read (so a signed-in reader never sees the sign-in card flash), the sign-in card when signed out, else the list.
 */
internal data class LikedTripsUiState(
    val session: SessionState = SessionState.Unknown,
    val totalItems: Long? = null,
    val waitlistTags: WaitlistTags = WaitlistTags.None,
    val likes: TripLikes = TripLikes.None,
)

/**
 * The signed-in account's liked trips, paged, newest like first. A heart taken back here stays on its card, empty,
 * until the page is read again (on coming back to it, [refresh]): a card vanishing under the finger would leave no way
 * to undo a slip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LikedTripsViewModel(
    repository: TripListingRepository,
    waitlists: WaitlistMemory,
    session: Flow<SessionState>,
    private val hearts: TripHearts = TripHearts.None,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LikedTripsUiState())
    val uiState: StateFlow<LikedTripsUiState> = _uiState.asStateFlow()

    /** Lets [retry] and [refresh] load the list again from page 0: each invalidation makes a fresh source. */
    private val sources =
        InvalidatingPagingSourceFactory {
            LikedTripsPagingSource(repository) { result ->
                if (result is AppResult.Success) _uiState.update { it.copy(totalItems = result.data.totalItems) }
            }
        }

    /** A burst of Retry taps is one request. */
    private val retries = Throttle(viewModelScope)

    /** Signed in: the list, read again on each sign-in; otherwise nothing is asked of the server (it would say 401). */
    val trips: Flow<PagingData<TripCardDto>> =
        session
            .map { it is SessionState.SignedIn }
            .distinctUntilChanged()
            .flatMapLatest { signedIn ->
                if (signedIn) {
                    Pager(TripPagingConfig, pagingSourceFactory = { sources() }).flow.distinctTrips()
                } else {
                    flowOf(PagingData.empty())
                }
            }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            session.collect { now ->
                _uiState.update { it.copy(session = now, totalItems = it.totalItems.takeIf { now is SessionState.SignedIn }) }
            }
        }
        collectWaitlistTags(waitlists) { tags -> _uiState.update { it.copy(waitlistTags = tags) } }
        collectTripLikes(hearts) { likes -> _uiState.update { it.copy(likes = likes) } }
    }

    /** Retry, from the first page's error. Throttled. */
    fun retry() = retries.attempt { sources.invalidate() }

    /** Back on the page: read the list again, so trips liked elsewhere join it and those taken back leave. */
    fun refresh() {
        if (_uiState.value.session is SessionState.SignedIn) sources.invalidate()
    }

    /** A tap on [trip]'s heart; false for a guest (the page shows the sign-in card then anyway). */
    fun toggleLike(trip: TripCardDto): Boolean = hearts.toggle(trip, _uiState.value.likes)
}
