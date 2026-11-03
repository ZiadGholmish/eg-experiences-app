package eg.bahr.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.push.PushChannel
import eg.bahr.core.datastore.PushPromptStore
import eg.bahr.feature.notifications.data.PushPermissionStatus
import eg.bahr.feature.notifications.data.PushPermissions
import eg.bahr.feature.notifications.navigation.PushPromptReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [Deciding] until the view model knows whether to ask; then the sheet ([Showing]); [Waiting] while the OS dialog is up
 * (the sheet is gone, but the prompt stays placed: Android's result comes back to its launcher); [Done] once answered
 * or not needed.
 */
internal enum class PushPromptUiState { Deciding, Showing, Waiting, Done }

/**
 * The push pre-prompt (M3-M5, handoff §3), offered right after a waiting-list join, and (M3-M7, [reason]) right after a
 * hold is placed:
 * - not shown for a waiting list when this build has no push ([PushChannel.isAvailable]: no Firebase config, M3-M19a),
 *   when notifications are already allowed, or after two "Not now"s for that reason ([MAX_DECLINES]; the person said
 *   no twice);
 * - "Yes, notify me": the OS dialog if it can still be shown, else (refused for good, or turned off) the app's
 *   notification settings; either way the push channel is read again, so a grant registers the token;
 * - "Not now" (and swiping the sheet away, which says the same) is counted, and asked again at the next join.
 *
 * Everything "Yes" needs is read when the sheet is offered, so the tap acts at once, inside the click: a read that
 * suspended there would let the sheet leave (and Android unregister its permission launcher) before the dialog opened.
 */
internal class PushPromptViewModel(
    private val push: PushChannel,
    private val store: PushPromptStore,
    private val reason: PushPromptReason = PushPromptReason.WaitingList,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PushPromptUiState.Deciding)
    val uiState: StateFlow<PushPromptUiState> = _uiState.asStateFlow()

    /** What [offer] found, for [allow]. */
    private var status = PushPermissionStatus.Granted

    /** A new join: decide afresh whether to ask. */
    fun offer(permissions: PushPermissions) {
        _uiState.value = PushPromptUiState.Deciding
        viewModelScope.launch {
            if (!needsPush() || declines() >= MAX_DECLINES) {
                _uiState.value = PushPromptUiState.Done
                return@launch
            }
            status = permissions.status(store.systemDialogShown())
            _uiState.value = if (status == PushPermissionStatus.Granted) PushPromptUiState.Done else PushPromptUiState.Showing
        }
    }

    fun allow(permissions: PushPermissions) {
        if (_uiState.value != PushPromptUiState.Showing) return
        when (status) {
            PushPermissionStatus.Granted -> {
                push.refresh()
                _uiState.value = PushPromptUiState.Done
            }
            PushPermissionStatus.CanAsk -> {
                _uiState.value = PushPromptUiState.Waiting
                viewModelScope.launch { store.recordSystemDialogShown() }
                permissions.requestSystemDialog {
                    push.refresh()
                    _uiState.value = PushPromptUiState.Done
                }
            }
            PushPermissionStatus.Denied -> {
                permissions.openSettings()
                _uiState.value = PushPromptUiState.Done
            }
        }
    }

    fun notNow() {
        if (_uiState.value != PushPromptUiState.Showing) return
        _uiState.value = PushPromptUiState.Done
        viewModelScope.launch {
            when (reason) {
                PushPromptReason.WaitingList -> store.recordDecline()
                PushPromptReason.HoldReminder -> store.recordHoldReminderDecline()
            }
        }
    }

    /**
     * Whether asking makes sense in this build. A seat alert is a server push: none without Firebase config. The hold
     * reminder is a local notification (M3-M7), which needs only the permission (the gate the M3-M5 review asked to lift).
     */
    private fun needsPush(): Boolean =
        when (reason) {
            PushPromptReason.WaitingList -> push.isAvailable
            PushPromptReason.HoldReminder -> true
        }

    private suspend fun declines(): Int =
        when (reason) {
            PushPromptReason.WaitingList -> store.declines()
            PushPromptReason.HoldReminder -> store.holdReminderDeclines()
        }

    /**
     * The placing screen has been told: back to [PushPromptUiState.Deciding], so the next placement (another join on
     * the same page, which keeps this view model) does not read the old [PushPromptUiState.Done] and leave at once.
     */
    fun finished() {
        if (_uiState.value == PushPromptUiState.Done) _uiState.value = PushPromptUiState.Deciding
    }

    internal companion object {
        /** The handoff: "Stop asking after 2 'Not now's." */
        const val MAX_DECLINES = 2
    }
}
