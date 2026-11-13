package eg.bahr.feature.settings.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.settings.data.SettingsRepository
import eg.bahr.feature.settings.presentation.EditPersonalInfoViewModel
import eg.bahr.feature.settings.presentation.ProfileViewModel
import eg.bahr.feature.settings.presentation.PublicProfileSettingsViewModel
import eg.bahr.feature.settings.presentation.TravellerProfileViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Koin graph Profile (Settings before M3-M0b) runs on: `settingsModule` over the real [AppSettingsStore], with only what
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
                    // The sign-in the account row reads (M3-M0), over a store in memory.
                    single { AuthSession(NoSessionStore) }
                    // Profile's `getMe` / `updateMe` (M3-M0b); never called here.
                    single { HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }) }
                },
            ),
        ) { koin ->
            koin.get<ProfileViewModel>()
            koin.get<EditPersonalInfoViewModel>()
            // MP-M2: a traveller's profile (by its public id) and Account → Public profile.
            koin.get<TravellerProfileViewModel> { parametersOf("3f9c2a7e5b1d4c8e9a0f6b2d7c4e1a95") }
            koin.get<PublicProfileSettingsViewModel>()
            val repository = koin.get<SettingsRepository>()
            repository.setLanguage(AppLanguage.ENGLISH)
            assertEquals(AppLanguage.ENGLISH, koin.get<AppSettingsStore>().language.first())
        }
}

private object NoSessionStore : SessionStore {
    override suspend fun read(): StoredSession? = null

    override suspend fun write(session: StoredSession?) = Unit
}

/** A DataStore without a file, so the test runs the same on the JVM and on iOS. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
