package eg.bahr.feature.mytrips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.mytrips.data.BookingList
import eg.bahr.feature.mytrips.data.MyBookingsPagingSource
import eg.bahr.feature.mytrips.data.MyTripsApiService
import eg.bahr.feature.mytrips.data.MyTripsRepository
import eg.bahr.feature.mytrips.data.allBookings
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** A waiting-list join made on this device while signed out; [title] when a card on this device showed it. */
internal data class DeviceJoin(
    val departureId: String,
    val tripSlug: String,
    val title: String?,
    val date: LocalDate,
    val partySize: Int,
)

internal data class MyTripsUiState(
    val session: SessionState = SessionState.Unknown,
    val upcoming: SectionLoad<MyBookingDto> = SectionLoad.Loading,
    val waitlist: SectionLoad<MyWaitlistEntryDto> = SectionLoad.Loading,
    /** Past's `totalItems` from its first page, for the header; null until then or after sign-out. */
    val pastTotal: Long? = null,
    /** A pull-to-refresh is under way: until Upcoming and the waiting list are read again. */
    val refreshing: Boolean = false,
    val deviceJoins: List<DeviceJoin> = emptyList(),
)

/**
 * My trips (M3-M0; one list since MV-M2). Signed out: a sign-in prompt and the device's own waiting-list joins
 * (shown until sign-in attaches them to the account, M3-B1). Signed in: one list of three sections, Upcoming and the
 * Waiting list each read whole (they are small) and Past paged at the end ([pastBookings]), each loading and failing
 * on its own, so the list never waits on Past.
 *
 * Signing in or out anywhere (Settings, the sheet, a refused refresh) reaches this screen through the
 * session, so it never needs telling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MyTripsViewModel(
    private val repository: MyTripsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyTripsUiState())
    val uiState: StateFlow<MyTripsUiState> = _uiState.asStateFlow()

    private var upcomingJob: Job? = null
    private var waitlistJob: Job? = null
    private var refreshJob: Job? = null

    /** Past's current source, so a visit or a pull can ask it for a fresh read. */
    private var pastSource: MyBookingsPagingSource? = null

    /** Past while signed in, page by page as the list reaches its end; empty otherwise. */
    val pastBookings: Flow<PagingData<MyBookingDto>> =
        repository.session
            .map { it is SessionState.SignedIn }
            .distinctUntilChanged()
            .flatMapLatest { signedIn ->
                if (!signedIn) {
                    flowOf(PagingData.empty())
                } else {
                    Pager(PAGING) {
                        MyBookingsPagingSource(repository, BookingList.Past, onTotal = ::onPastTotal).also { pastSource = it }
                    }.flow
                }
            }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch { repository.loadSession() }
        repository.session
            .onEach { session ->
                val wasSignedIn = _uiState.value.session is SessionState.SignedIn
                if (session !is SessionState.SignedIn) {
                    // The account's lists go with its sign-in: the next person on this phone must never see
                    // them, not even while their own read is under way (M3-M0 review #5).
                    upcomingJob?.cancel()
                    waitlistJob?.cancel()
                    refreshJob?.cancel()
                    _uiState.update {
                        it.copy(
                            session = session,
                            upcoming = SectionLoad.Loading,
                            waitlist = SectionLoad.Loading,
                            pastTotal = null,
                            refreshing = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(session = session) }
                    if (!wasSignedIn) {
                        loadUpcoming()
                        loadWaitlist()
                    }
                }
            }.launchIn(viewModelScope)
        repository.deviceJoins
            .onEach { joins ->
                _uiState.update { state ->
                    state.copy(
                        deviceJoins =
                            joins
                                .sortedBy { it.date }
                                .map {
                                    DeviceJoin(
                                        it.departureId,
                                        it.tripSlug,
                                        repository.cachedTitle(it.tripSlug),
                                        it.date,
                                        it.partySize,
                                    )
                                },
                    )
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Each visit after the first (M3-M0 review #11): all three sections are read again, so a hold placed on the
     * booking screen, a review just written or a booking made on another phone shows. What is shown stays until
     * the new read arrives.
     */
    fun refresh() {
        if (_uiState.value.session !is SessionState.SignedIn) return
        reloadAll()
    }

    /** Pull-to-refresh: all three again; the indicator stays until Upcoming and the waiting list are back. */
    fun pullToRefresh() {
        if (_uiState.value.session !is SessionState.SignedIn) return
        refreshJob?.cancel()
        _uiState.update { it.copy(refreshing = true) }
        reloadAll()
        val reads = listOfNotNull(upcomingJob, waitlistJob)
        refreshJob =
            viewModelScope.launch {
                reads.joinAll()
                _uiState.update { it.copy(refreshing = false) }
            }
    }

    /** "Try again" in the Upcoming section: only that section is read again. */
    fun retryUpcoming() = loadUpcoming()

    /** "Try again" in the Waiting list section. */
    fun retryWaitlist() = loadWaitlist()

    private fun reloadAll() {
        loadUpcoming()
        loadWaitlist()
        pastSource?.invalidate()
    }

    private fun onPastTotal(total: Long) {
        _uiState.update { it.copy(pastTotal = total) }
    }

    private fun loadUpcoming() {
        if (_uiState.value.session !is SessionState.SignedIn) return
        upcomingJob?.cancel()
        // Keep what is shown while re-reading; only a first read (or one after a failure) shows the skeleton.
        if (_uiState.value.upcoming !is SectionLoad.Loaded) _uiState.update { it.copy(upcoming = SectionLoad.Loading) }
        upcomingJob =
            viewModelScope.launch {
                val load =
                    when (val result = repository.allBookings(BookingList.Upcoming)) {
                        is AppResult.Success -> SectionLoad.Loaded(result.data)
                        is AppResult.Failure -> SectionLoad.Failed(result.error)
                    }
                _uiState.update { it.copy(upcoming = it.upcoming.after(load)) }
            }
    }

    private fun loadWaitlist() {
        if (_uiState.value.session !is SessionState.SignedIn) return
        waitlistJob?.cancel()
        if (_uiState.value.waitlist !is SectionLoad.Loaded) _uiState.update { it.copy(waitlist = SectionLoad.Loading) }
        waitlistJob =
            viewModelScope.launch {
                val load =
                    when (val result = repository.waitlist()) {
                        is AppResult.Success -> SectionLoad.Loaded(result.data)
                        is AppResult.Failure -> SectionLoad.Failed(result.error)
                    }
                _uiState.update { it.copy(waitlist = it.waitlist.after(load)) }
            }
    }

    /**
     * A failed re-read keeps the rows shown (offline on a return or a pull), as Past's paged list does; only a first
     * read, or a retry of a failed one, shows the error.
     */
    private fun <T> SectionLoad<T>.after(read: SectionLoad<T>): SectionLoad<T> =
        if (this is SectionLoad.Loaded && read is SectionLoad.Failed) this else read

    private companion object {
        val PAGING =
            PagingConfig(
                pageSize = MyTripsApiService.PAGE_SIZE,
                prefetchDistance = MyTripsApiService.PAGE_SIZE / 2,
                initialLoadSize = MyTripsApiService.PAGE_SIZE,
                enablePlaceholders = false,
            )
    }
}
