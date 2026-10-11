package eg.bahr.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.onFailure
import eg.bahr.feature.settings.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [language] is the stored one, null until the store has answered (the choice is then drawn with
 * nothing picked rather than a guess that could flip).
 */
internal data class SettingsUiState(
    val language: AppLanguage? = null,
)

/**
 * Settings (M4-M8). Picking a language only writes it to the store: the app's Compose roots read the
 * store and re-theme (script, RTL/LTR, strings, `Accept-Language`) on their own, so this view model
 * never tells anything else to change.
 */
internal class SettingsViewModel(
    private val repository: SettingsRepository,
    private val errors: AppErrorController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        repository.language
            .onEach { language -> _uiState.update { it.copy(language = language) } }
            .launchIn(viewModelScope)
    }

    fun selectLanguage(language: AppLanguage) {
        // The language already shown: nothing to write, and no rebuild of every screen.
        if (language == _uiState.value.language) return
        viewModelScope.launch {
            repository.setLanguage(language).onFailure(errors::show)
        }
    }
}
