package eg.bahr.di

import co.touchlab.kermit.Logger
import eg.bahr.core.common.locale.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The app language every Compose root draws in (M4-M8), one per process (a Koin single).
 *
 * Shared, and started eagerly, so that on iOS, where each tab is its own Compose root, a tab composed
 * late (its first visit) starts in the stored language rather than the default and then flips; and a
 * switch from Settings reaches all four roots from the one store. On Android there is one root, and it
 * reads the same value.
 *
 * [switchTo] writes in this object's own scope, not a screen's: a deep link's `?lang=` asks for it
 * from a NavHost that the switch itself is about to rebuild.
 */
class AppLanguageState(
    stored: Flow<AppLanguage>,
    private val store: suspend (AppLanguage) -> Unit,
    private val scope: CoroutineScope,
) {
    val language: StateFlow<AppLanguage> = stored.stateIn(scope, SharingStarted.Eagerly, AppLanguage.default)

    fun switchTo(language: AppLanguage) {
        scope.launch {
            try {
                store(language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed write keeps the language the reader had; the link still opens, in it.
                Logger.withTag(LOG_TAG).w(e) { "Could not store the language a link asked for" }
            }
        }
    }
}

private const val LOG_TAG = "AppLanguage"
