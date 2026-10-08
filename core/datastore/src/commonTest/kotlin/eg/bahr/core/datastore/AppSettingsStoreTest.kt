package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import eg.bahr.core.common.locale.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The stored language is the one source of truth for the app language: theme, layout direction,
 * string resources and `Accept-Language` all read [AppSettingsStore.language]. These pin what it
 * says before the user has ever chosen.
 */
class AppSettingsStoreTest {
    @Test
    fun `first launch with nothing stored is Arabic - never the device language`() =
        runTest {
            assertEquals(AppLanguage.ARABIC, AppSettingsStore(InMemoryPreferences()).language.first())
        }

    @Test
    fun `a stored choice wins`() =
        runTest {
            val store = AppSettingsStore(InMemoryPreferences())

            store.setLanguage(AppLanguage.ENGLISH)

            assertEquals(AppLanguage.ENGLISH, store.language.first())
        }

    @Test
    fun `an unreadable stored value falls back to Arabic`() =
        runTest {
            val corrupt = mutablePreferencesOf(stringPreferencesKey("language") to "xx")
            assertEquals(AppLanguage.ARABIC, AppSettingsStore(InMemoryPreferences(corrupt)).language.first())
        }
}

/** A DataStore without a file, so the test runs the same on the JVM and on iOS. */
private class InMemoryPreferences(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
