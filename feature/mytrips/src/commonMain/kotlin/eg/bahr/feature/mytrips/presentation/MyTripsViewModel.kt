package eg.bahr.feature.mytrips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.mytrips.data.BookingList
import eg.bahr.feature.mytrips.data.MyBookingsPagingSource
import eg.bahr.feature.mytrips.data.MyTripsApiService
import eg.bahr.feature.mytrips.data.MyTripsRepository
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** The three segments of My trips (user decision 2026-10-12), in reading order. */
internal enum class MyTripsSegment { Upcoming, Waitlist, Past }

/** The signed-in waiting list: its own small load (the endpoint is not paged). */
internal sealed interface WaitlistLoad {
    data object Loading : WaitlistLoad

    data class Loaded(
        val entries: List<MyWaitlistEntryDto>,
    ) : WaitlistLoad

    data class Failed(
        val error: AppError,
    ) : WaitlistLoad
}

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
    val segment: MyTripsSegment = MyTripsSegment.Upcoming,
    val waitlist: WaitlistLoad = WaitlistLoad.Loading,
    val deviceJoins: List<DeviceJoin> = emptyList(),
)

/**
 * My trips (M3-M0). Signed out: a sign-in prompt and the device's own waiting-list joins (shown until
 * sign-in attaches them to the account, M3-B1). Signed in: Upcoming and Past as paged lists
 * ([bookings], one flow following the selected segment) and the Waiting list as one read.
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

    private var waitlistJob: Job? = null

    /** Upcoming or Past for the selected segment while signed in; empty otherwise (and on Waiting list). */
    val bookings: Flow<PagingData<MyBookingDto>> =
        combine(
            repository.session.map { it is SessionState.SignedIn }.distinctUntilChanged(),
            _uiState.map { it.segment }.distinctUntilChanged(),
        ) { signedIn, segment -> if (signedIn) segment.list() else null }
            .distinctUntilChanged()
            .flatMapLatest { list ->
                if (list == null) {
                    flowOf(PagingData.empty())
                } else {
                    Pager(PAGING) { MyBookingsPagingSource(repository, list) }.flow
                }
            }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch { repository.loadSession() }
        repository.session
            .onEach { session ->
                val wasSignedIn = _uiState.value.session is SessionState.SignedIn
                if (session !is SessionState.SignedIn) {
                    // The account's list goes with its sign-in: the next person on this phone must never
                    // see it, not even while their own read is under way (M3-M0 review #5).
                    waitlistJob?.cancel()
                    _uiState.update { it.copy(session = session, waitlist = WaitlistLoad.Loading) }
                } else {
                    _uiState.update { it.copy(session = session) }
                    if (!wasSignedIn) loadWaitlist()
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

    fun selectSegment(segment: MyTripsSegment) {
        if (segment == _uiState.value.segment) return
        _uiState.update { it.copy(segment = segment) }
        // The list may have changed since (a place opened, an entry was attached at sign-in).
        if (segment == MyTripsSegment.Waitlist) loadWaitlist()
    }

    /** "Try again" on a failed waiting list. */
    fun retryWaitlist() = loadWaitlist()

    private fun loadWaitlist() {
        if (_uiState.value.session !is SessionState.SignedIn) return
        waitlistJob?.cancel()
        // Keep what is shown while re-reading; only a first read shows the loading state.
        if (_uiState.value.waitlist !is WaitlistLoad.Loaded) _uiState.update { it.copy(waitlist = WaitlistLoad.Loading) }
        waitlistJob =
            viewModelScope.launch {
                val load =
                    when (val result = repository.waitlist()) {
                        is AppResult.Success -> WaitlistLoad.Loaded(result.data)
                        is AppResult.Failure -> WaitlistLoad.Failed(result.error)
                    }
                _uiState.update { it.copy(waitlist = load) }
            }
    }

    private fun MyTripsSegment.list(): BookingList? =
        when (this) {
            MyTripsSegment.Upcoming -> BookingList.Upcoming
            MyTripsSegment.Past -> BookingList.Past
            MyTripsSegment.Waitlist -> null
        }

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
