package eg.bahr.feature.booking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.ActiveHoldStore
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal data class OpenHoldUiState(
    val isLoading: Boolean = true,
    /** The read got no answer: the screen offers to try again. */
    val error: AppError? = null,
    /** The hold is live: the held seats open with this. */
    val open: HoldRoute? = null,
    /** The hold is over (or not this device's): the screen leaves, and the app says the seats were released. */
    val ended: Boolean = false,
)

/**
 * A tap on the "your hold is about to expire" reminder (M3-M7) names only the hold's [ref]. The held seats need the
 * guest's phone (an anonymous `GET /bookings/{ref}` proves ownership with it) and a fresh deadline, so this reads the
 * device's stored hold and the booking again, with the same "still held" rule as Home's card ([liveDeadline],
 * [reopenedHold]), before the hold screen opens.
 *
 * - Stored hold is [ref] and the server says held → [OpenHoldUiState.open].
 * - No stored hold, another one, not held, or NOT_FOUND → [OpenHoldUiState.ended]; the seats were released, said once
 *   through [errors] (the hold screen's `HOLD_EXPIRED` message), and a stored hold found over is forgotten with its
 *   reminder.
 * - No answer → [OpenHoldUiState.error] with a retry: the seats are never assumed gone.
 */
internal class OpenHoldViewModel(
    private val ref: String,
    private val repository: BookingRepository,
    private val activeHold: ActiveHoldStore,
    private val errors: AppErrorController,
    private val reminders: HoldReminderSchedule = NoHoldReminderSchedule,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OpenHoldUiState())
    val uiState: StateFlow<OpenHoldUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val stored = activeHold.hold.first()
            if (stored == null || stored.ref != ref) {
                // Paid, released or replaced since the reminder was set: nothing of it is left on this device.
                reminders.onEnded(ref)
                end()
                return@launch
            }
            when (val result = repository.heldBooking(stored.ref, stored.guestPhone)) {
                is AppResult.Success -> {
                    val booking = result.data
                    val holdExpiresAt = booking.holdExpiresAt
                    if (holdExpiresAt != null && booking.liveDeadline(clock.now()) != null) {
                        _uiState.update { it.copy(isLoading = false, open = reopenedHold(stored, booking, holdExpiresAt)) }
                    } else {
                        activeHold.clear(stored.ref)
                        reminders.onEnded(stored.ref)
                        end()
                    }
                }
                is AppResult.Failure ->
                    if (result.error.isNotFound()) {
                        activeHold.clear(stored.ref)
                        reminders.onEnded(stored.ref)
                        end()
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = result.error) }
                    }
            }
        }
    }

    /** The screen has passed the result on to navigation; a re-run of its effect must not do so twice. */
    fun onHandled() = _uiState.update { it.copy(open = null, ended = false) }

    private fun end() {
        errors.show(AppError.Api(code = ApiErrorCodes.HOLD_EXPIRED, message = null, httpStatus = null))
        _uiState.update { it.copy(isLoading = false, ended = true) }
    }
}
