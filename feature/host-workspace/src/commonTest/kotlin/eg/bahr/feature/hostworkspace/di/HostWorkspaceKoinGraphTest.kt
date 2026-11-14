package eg.bahr.feature.hostworkspace.di

import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.HostSessionStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.di.networkModule
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.hostworkspace.presentation.HostDepartureViewModel
import eg.bahr.feature.hostworkspace.presentation.HostHomeViewModel
import eg.bahr.feature.hostworkspace.presentation.HostSignInViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test

/**
 * The Koin graph host mode runs on (M5-M1): `hostWorkspaceModule` over core:network's real module, so the host session
 * and client it asks for are the ones the app binds, next to the traveller's, without a type bound twice.
 */
class HostWorkspaceKoinGraphTest {
    @Test
    fun `every host mode binding resolves beside the travellers session`() =
        runKoinGraphTest(
            listOf(
                networkModule,
                hostWorkspaceModule,
                module {
                    single { ApiConfig(baseUrl = TEST_BASE_URL, isDebug = false) }
                    single { LanguageTagProvider { "ar" } }
                    single<SessionStore> { NoStore }
                    single<HostSessionStore> { NoHostStore }
                    single { DeviceIdSource { "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e" } }
                    single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.NotFound) } }
                },
            ),
        ) { koin ->
            koin.get<HostSignInViewModel>()
            koin.get<HostHomeViewModel>()
            koin.get<HostDepartureViewModel> { parametersOf("3c1e5a7b-9d2f-4b6a-8c0e-2f4a6b8c0d1e") }
        }
}

private object NoStore : SessionStore {
    override suspend fun read(): StoredSession? = null

    override suspend fun write(session: StoredSession?) = Unit
}

private object NoHostStore : HostSessionStore {
    override suspend fun read(): StoredSession? = null

    override suspend fun write(session: StoredSession?) = Unit
}
