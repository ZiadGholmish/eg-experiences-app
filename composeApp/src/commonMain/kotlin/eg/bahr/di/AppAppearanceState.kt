package eg.bahr.di

import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.datastore.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import kotlin.coroutines.cancellation.CancellationException

/**
 * The appearance every Compose root draws in (M3-M0b, user decision 2026-10-14: Light by default, the
 * phone's setting only when picked), one per process (a Koin single), as [AppLanguageState] is for the
 * language: shared, so iOS's tab roots and its SwiftUI tab bar change together.
 *
 * Its first value must be the stored one, or a stored Dark paints a light first frame and then flips
 * (M3-M0b review R4-1): the platform entry points [seed] it before any Compose root exists
 * ([seedStoredAppearance]); the store's own flow keeps it current after that.
 */
class AppAppearanceState(
    stored: Flow<AppAppearance>,
    scope: CoroutineScope,
) {
    private val current = MutableStateFlow(AppAppearance.default)
    val appearance: StateFlow<AppAppearance> = current.asStateFlow()

    init {
        scope.launch { stored.collect { current.value = it } }
    }

    /** The value read once at launch, before the first frame. */
    fun seed(stored: AppAppearance) {
        current.value = stored
    }
}

/**
 * Reads the stored appearance once and seeds [AppAppearanceState] with it, for the platform entry points
 * to call right after Koin starts and before any Compose root (Android's and iOS's
 * `startApp`). A small DataStore read, once per launch, on purpose blocking: the first frame must not
 * wait for a coroutine. An unreadable store is the default, Light.
 */
fun Koin.seedStoredAppearance(): AppAppearance {
    val stored =
        runBlocking {
            try {
                get<AppSettingsStore>().appearance.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppAppearance.default
            }
        }
    get<AppAppearanceState>().seed(stored)
    return stored
}
