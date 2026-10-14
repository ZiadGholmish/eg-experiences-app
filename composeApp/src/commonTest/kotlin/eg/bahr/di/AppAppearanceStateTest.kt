package eg.bahr.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.datastore.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M3-M0b review R4-1: a stored Dark is what the first frame reads. The state holds the seeded value at
 * once, before its own collector has run, and the entry points' helper seeds it from the store.
 */
class AppAppearanceStateTest {
    /** A scope that never runs anything: the collector cannot have emitted when the value is read. */
    private val idle = CoroutineScope(Job().apply { cancel() })

    @Test
    fun `a seeded value is there at once - before the stored flow says anything`() {
        val state = AppAppearanceState(stored = emptyFlow(), scope = idle)
        assertEquals(AppAppearance.LIGHT, state.appearance.value, "Light until seeded")

        state.seed(AppAppearance.DARK)

        assertEquals(AppAppearance.DARK, state.appearance.value)
    }

    @Test
    fun `the entry points' helper seeds the state with what the store holds`() {
        val preferences = InMemoryPreferences()
        val koin =
            koinApplication {
                modules(
                    module {
                        single { AppSettingsStore(preferences) }
                        single { AppAppearanceState(stored = emptyFlow(), scope = idle) }
                    },
                )
            }.koin
        runBlocking { koin.get<AppSettingsStore>().setAppearance(AppAppearance.DARK) }

        assertEquals(AppAppearance.DARK, koin.seedStoredAppearance())
        assertEquals(AppAppearance.DARK, koin.get<AppAppearanceState>().appearance.value, "the first frame draws Dark")
    }
}

private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
