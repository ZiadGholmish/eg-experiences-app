package eg.bahr.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.account.PersonName
import eg.bahr.core.common.phone.MobileProblem
import eg.bahr.core.common.phone.PhoneNumbers
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.auth.data.AuthRepository
import eg.bahr.feature.auth.data.ProfileDraft
import eg.bahr.feature.auth.data.ProfilePrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CompleteProfileUiState(
    /** The ask the sheet answers; null: no sheet. */
    val prompt: ProfilePrompt? = null,
    /** As typed; the phone is normalised only to check and to send. */
    val name: String = "",
    val phone: String = "",
    /** Off until the first Save; then every edit re-checks (handoff §2: "on Save, then live"). */
    val checked: Boolean = false,
    val saving: Boolean = false,
    val error: AppError? = null,
) {
    val nameInvalid: Boolean get() = checked && !PersonName.isValid(name)
    val phoneProblem: MobileProblem? get() = if (checked) PhoneNumbers.mobileProblem(PhoneNumbers.normalizeMobile(phone)) else null
    val digits: Int get() = PhoneNumbers.normalizeMobile(phone).length
}

/**
 * "Complete your profile" (M3-M0b, handoff §2): the modal sheet shown after a sign-in that came back
 * without a name or phone, over whatever screen is showing. The name is prefilled from the provider.
 * Save checks the name (2–60) and the Egyptian mobile ([PhoneNumbers]) first, then sends them
 * normalised; Later closes it, and checkout asks again.
 *
 * Follows the repository's app-wide [AuthRepository.profilePrompt]: a new ask starts the form over.
 */
internal class CompleteProfileViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompleteProfileUiState())
    val uiState: StateFlow<CompleteProfileUiState> = _uiState.asStateFlow()

    init {
        // The ask and its draft are the repository's, so every sheet (one per iOS tab root, each with its
        // own long-lived view model) shows the same text, whichever tab it was typed in (review R3-1).
        combine(repository.profilePrompt, repository.profileDraft) { prompt, draft -> prompt to draft }
            .onEach { (prompt, draft) ->
                _uiState.update { state ->
                    when {
                        prompt == null -> CompleteProfileUiState()
                        // A new ask starts the form over.
                        prompt != state.prompt ->
                            CompleteProfileUiState(
                                prompt = prompt,
                                name = draft?.name ?: prompt.name,
                                phone = draft?.phone ?: PhoneNumbers.normalizeMobile(prompt.phone),
                            )
                        draft != null -> state.copy(name = draft.name, phone = draft.phone)
                        else -> state
                    }
                }
            }.launchIn(viewModelScope)
        // The session ended while asking (a refused refresh, a deletion): the ask and what was typed go
        // with it, never to be shown to the next account (review R3-2).
        repository.session
            .onEach { if (it is SessionState.SignedOut) repository.closeProfilePrompt() }
            .launchIn(viewModelScope)
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
        keepDraft()
    }

    fun setPhone(phone: String) {
        _uiState.update { it.copy(phone = phone, error = null) }
        keepDraft()
    }

    private fun keepDraft() {
        val state = _uiState.value
        if (state.prompt != null) repository.setProfileDraft(ProfileDraft(state.name, state.phone))
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.prompt == null) return
        val checked = state.copy(checked = true)
        _uiState.value = checked
        if (checked.nameInvalid || checked.phoneProblem != null) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.updateProfile(PersonName.clean(state.name), PhoneNumbers.normalizeMobile(state.phone))) {
                is AppResult.Success -> repository.closeProfilePrompt()
                is AppResult.Failure -> _uiState.update { it.copy(saving = false, error = result.error) }
            }
        }
    }

    /** "Later" (or the sheet swiped away): signed in without the details; checkout asks again. */
    fun later() {
        if (_uiState.value.saving) return
        repository.closeProfilePrompt()
    }
}
