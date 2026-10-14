package eg.bahr.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.account.PersonName
import eg.bahr.core.common.phone.MobileProblem
import eg.bahr.core.common.phone.PhoneNumbers
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.settings.data.SettingsRepository
import eg.bahr.feature.settings.model.AccountDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class EditPersonalInfoUiState(
    val loading: Boolean = true,
    /** The account could not be read: the form cannot start. */
    val loadError: AppError? = null,
    val name: String = "",
    /** As typed; normalised only to check and to send. */
    val phone: String = "",
    val email: String? = null,
    val provider: String? = null,
    /** Off until the first Save; then every edit re-checks (handoff §2: "on Save, then live"). */
    val checked: Boolean = false,
    val saving: Boolean = false,
    val saveError: AppError? = null,
    val saved: Boolean = false,
) {
    val nameInvalid: Boolean get() = checked && !PersonName.isValid(name)
    val phoneProblem: MobileProblem? get() = if (checked) PhoneNumbers.mobileProblem(PhoneNumbers.normalizeMobile(phone)) else null
    val digits: Int get() = PhoneNumbers.normalizeMobile(phone).length
}

/**
 * Edit personal info (M3-M0b, handoff §2): the name (2–60) and the mobile, checked on Save and then live
 * as the person fixes them, sent normalised (`updateMe`, so the BE's `Phone` rule is checked again
 * there); the email is read-only. A save goes back to Profile, which says "Saved".
 *
 * Starts from the account Profile already read, if it still matches the session; reads it otherwise.
 */
internal class EditPersonalInfoViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditPersonalInfoUiState())
    val uiState: StateFlow<EditPersonalInfoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // The repository's account only while it still matches the session (M3-M0b review #2).
            val known = repository.account.first()
            if (known != null) fill(known) else load()
        }
    }

    /** "Try again" after a failed read. */
    fun load() {
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.refreshAccount()) {
                is AppResult.Success -> fill(result.data)
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
            }
        }
    }

    private fun fill(account: AccountDto) =
        _uiState.update {
            it.copy(
                loading = false,
                loadError = null,
                name = account.name.orEmpty(),
                // A number stored before M3-M0b may be "+20…": shown, and checked, in its national form.
                phone = account.phone?.let(PhoneNumbers::normalizeMobile).orEmpty(),
                email = account.email,
                provider = account.providers.firstOrNull(),
            )
        }

    fun setName(name: String) = _uiState.update { it.copy(name = name, saveError = null) }

    fun setPhone(phone: String) = _uiState.update { it.copy(phone = phone, saveError = null) }

    fun save() {
        val state = _uiState.value
        if (state.loading || state.saving || state.saved) return
        val checked = state.copy(checked = true)
        _uiState.value = checked
        if (checked.nameInvalid || checked.phoneProblem != null) return
        _uiState.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = repository.updateProfile(PersonName.clean(state.name), PhoneNumbers.normalizeMobile(state.phone))) {
                is AppResult.Success -> {
                    repository.setSavedNotice(true)
                    _uiState.update { it.copy(saving = false, saved = true) }
                }
                is AppResult.Failure -> _uiState.update { it.copy(saving = false, saveError = result.error) }
            }
        }
    }
}
