package eg.bahr.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.catalog.presentation.Throttle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.settings.data.PublicProfileRepository
import eg.bahr.feature.settings.data.SettingsRepository
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.model.UpdateProfileVisibilityRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The five switches of Account → Public profile (openapi `ProfileVisibility`), in the order the page shows them. */
internal enum class ProfileSwitch { Public, PastTrips, Likes, Reviews, FullNameAndPhoto }

/**
 * Account → Public profile (MP-M2).
 *
 * [shown] is what the switches draw: the server's last answer ([confirmed]) with every change still on its way laid
 * over it (optimistic). A change that fails goes back to the server's value and [saveFailed] asks for the "couldn't
 * save" note. [error] is a failed first read (the page shows it with Retry). [signedOut]: the sign-in ended (a 401
 * nothing could cure drops it in core:network; a deleted account answers 401 too), so the page has nothing to show.
 */
internal data class PublicProfileSettingsUiState(
    val shown: ProfileVisibilityDto? = null,
    val confirmed: ProfileVisibilityDto? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val saveFailed: Boolean = false,
    val signedOut: Boolean = false,
    /** The master switch has a change on its way: "View my public profile" waits for the server's answer. */
    val publicPending: Boolean = false,
) {
    /**
     * Past trips, liked trips and the full name only mean something on a public profile, so they wait for the master
     * switch. Reviews do not: `showReviews` also decides how the reviews are signed on every trip page, whether or not
     * the profile is public (openapi `ProfileVisibility.showReviews`), so it stays usable while private.
     */
    fun enabled(switch: ProfileSwitch): Boolean =
        when (switch) {
            ProfileSwitch.Public, ProfileSwitch.Reviews -> true
            ProfileSwitch.PastTrips, ProfileSwitch.Likes, ProfileSwitch.FullNameAndPhoto -> shown?.isPublic == true
        }

    /**
     * "View my public profile" opens only a profile the server says is public: while private (or while turning public is
     * still on its way) `getTraveller` answers 404, so the button waits with a note instead.
     */
    val canViewProfile: Boolean get() = confirmed?.isPublic == true && shown?.isPublic == true && !publicPending
}

internal class PublicProfileSettingsViewModel(
    private val repository: PublicProfileRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PublicProfileSettingsUiState())
    val uiState: StateFlow<PublicProfileSettingsUiState> = _uiState.asStateFlow()

    /** A burst of Retry taps is one request (M4-M6). */
    private val retries = Throttle(viewModelScope)

    /** Each PATCH's number, in the order they were sent. */
    private var sent = 0

    /** The newest answer laid into [PublicProfileSettingsUiState.confirmed]: an older one landing later is not. */
    private var applied = 0

    /** Per switch, the newest change still on its way: its request's number and the value asked for. */
    private val pending = mutableMapOf<ProfileSwitch, Pending>()

    private var load: Job? = null

    init {
        load()
        viewModelScope.launch {
            settings.session.collect { session ->
                if (session is SessionState.SignedOut) _uiState.update { it.copy(signedOut = true) }
            }
        }
    }

    fun retry() = retries.attempt { load() }

    /**
     * Turns [switch] [on] at once on screen and sends only that field. Each change is its own request: the answer is
     * the whole state, so only the newest answer is kept, and switches still on their way stay as the user set them.
     */
    fun toggle(
        switch: ProfileSwitch,
        on: Boolean,
    ) {
        val state = _uiState.value
        if (state.shown == null || !state.enabled(switch)) return
        val number = ++sent
        pending[switch] = Pending(number, on)
        publish()
        viewModelScope.launch {
            val result = repository.updateVisibility(switch.request(on))
            // Only this switch's newest change settles it; an older one's answer still counts as the server's state.
            if (pending[switch]?.number == number) pending.remove(switch)
            when (result) {
                is AppResult.Success -> {
                    if (number > applied) {
                        applied = number
                        _uiState.update { it.copy(confirmed = result.data) }
                    }
                    publish()
                }
                // The switch goes back to the server's value (pending removed above), and the page says so.
                is AppResult.Failure -> {
                    publish()
                    _uiState.update { it.copy(saveFailed = true) }
                }
            }
        }
    }

    /** The "couldn't save" note has been shown. */
    fun saveFailedShown() = _uiState.update { it.copy(saveFailed = false) }

    private fun load() {
        load?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        load =
            viewModelScope.launch {
                when (val result = repository.visibility()) {
                    is AppResult.Success -> {
                        _uiState.update { it.copy(confirmed = result.data, isLoading = false) }
                        publish()
                    }
                    is AppResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
                }
            }
    }

    /** [PublicProfileSettingsUiState.shown]: the server's state with the changes still on their way laid over it. */
    private fun publish() =
        _uiState.update { state ->
            val confirmed = state.confirmed
            state.copy(
                shown = confirmed?.let { pending.entries.fold(it) { shown, (switch, change) -> shown.with(switch, change.on) } },
                publicPending = ProfileSwitch.Public in pending,
            )
        }

    private data class Pending(
        val number: Int,
        val on: Boolean,
    )
}

internal fun ProfileVisibilityDto.isOn(switch: ProfileSwitch): Boolean =
    when (switch) {
        ProfileSwitch.Public -> isPublic
        ProfileSwitch.PastTrips -> showPastTrips
        ProfileSwitch.Likes -> showLikes
        ProfileSwitch.Reviews -> showReviews
        ProfileSwitch.FullNameAndPhoto -> showFullNameAndPhoto
    }

private fun ProfileVisibilityDto.with(
    switch: ProfileSwitch,
    on: Boolean,
): ProfileVisibilityDto =
    when (switch) {
        ProfileSwitch.Public -> copy(isPublic = on)
        ProfileSwitch.PastTrips -> copy(showPastTrips = on)
        ProfileSwitch.Likes -> copy(showLikes = on)
        ProfileSwitch.Reviews -> copy(showReviews = on)
        ProfileSwitch.FullNameAndPhoto -> copy(showFullNameAndPhoto = on)
    }

/** Only the one field changed: absent fields stay as they are on the server. */
private fun ProfileSwitch.request(on: Boolean): UpdateProfileVisibilityRequest =
    when (this) {
        ProfileSwitch.Public -> UpdateProfileVisibilityRequest(isPublic = on)
        ProfileSwitch.PastTrips -> UpdateProfileVisibilityRequest(showPastTrips = on)
        ProfileSwitch.Likes -> UpdateProfileVisibilityRequest(showLikes = on)
        ProfileSwitch.Reviews -> UpdateProfileVisibilityRequest(showReviews = on)
        ProfileSwitch.FullNameAndPhoto -> UpdateProfileVisibilityRequest(showFullNameAndPhoto = on)
    }
