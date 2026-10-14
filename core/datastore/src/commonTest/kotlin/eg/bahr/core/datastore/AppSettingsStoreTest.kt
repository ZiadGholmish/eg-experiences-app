package eg.bahr.core.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.theme.AppAppearance
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

    /** M3-M0b (user decision 2026-10-14): light until picked otherwise, and the pick is kept. */
    @Test
    fun `the appearance is Light on a first launch - a stored pick wins`() =
        runTest {
            val store = AppSettingsStore(InMemoryPreferences())
            assertEquals(AppAppearance.LIGHT, store.appearance.first())

            store.setAppearance(AppAppearance.SYSTEM)

            assertEquals(AppAppearance.SYSTEM, store.appearance.first())
            assertEquals(AppLanguage.ARABIC, store.language.first(), "the language is a separate setting")
        }
}
