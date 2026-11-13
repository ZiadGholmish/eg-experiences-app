package eg.bahr.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.catalog.presentation.Throttle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.settings.data.PublicProfileRepository
import eg.bahr.feature.settings.model.TravellerProfileDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A traveller's public profile (MP-M2).
 *
 * [notAvailable]: `getTraveller` answered NOT_FOUND, which is one answer for a private profile, an unknown id and a
 * deleted account (so a probe cannot tell them apart): "This profile isn't public", with no retry, as asking again
 * gives the same. [error] is any other failure (no connection, RATE_LIMITED), shown with Retry when one may help.
 */
internal data class TravellerProfileUiState(
    val profile: TravellerProfileDto? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val notAvailable: Boolean = false,
)

internal class TravellerProfileViewModel(
    private val publicId: String,
    private val repository: PublicProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TravellerProfileUiState())
    val uiState: StateFlow<TravellerProfileUiState> = _uiState.asStateFlow()

    /** A burst of Retry taps is one request (M4-M6). */
    private val retries = Throttle(viewModelScope)

    private var load: Job? = null

    init {
        load()
    }

    /** Retry, from the page's error. Throttled. */
    fun retry() = retries.attempt { load() }

    private fun load() {
        // A retry while a read is still out replaces it, so an old answer cannot land after a new one.
        load?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        load =
            viewModelScope.launch {
                when (val result = repository.traveller(publicId)) {
                    is AppResult.Success -> _uiState.update { it.copy(profile = result.data, isLoading = false, notAvailable = false) }
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

/** NOT_FOUND by its code (never its message or status): there is no public profile behind this id. */
private fun AppError.isNotFound(): Boolean = this is AppError.Api && code == ApiErrorCodes.NOT_FOUND
