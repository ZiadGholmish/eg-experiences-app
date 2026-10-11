package eg.bahr.feature.settings.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.settings.data.SettingsRepository
import eg.bahr.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Koin graph Settings runs on: `settingsModule` over the real [AppSettingsStore], with only what
 * the app module provides (the preferences file, the error controller) stubbed.
 */
class SettingsKoinGraphTest {
    @Test
    fun `every Settings binding resolves - and a pick reaches the real store`() =
        runKoinGraphTest(
            listOf(
                settingsModule,
                module {
                    single<DataStore<Preferences>> { InMemoryPreferences() }
                    single { AppSettingsStore(get()) }
                    single { AppErrorController() }
                },
            ),
        ) { koin ->
            koin.get<SettingsViewModel>()
            val repository = koin.get<SettingsRepository>()
            repository.setLanguage(AppLanguage.ENGLISH)
            assertEquals(AppLanguage.ENGLISH, koin.get<AppSettingsStore>().language.first())
        }
}

/** A DataStore without a file, so the test runs the same on the JVM and on iOS. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
