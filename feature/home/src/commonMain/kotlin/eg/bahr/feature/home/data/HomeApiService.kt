package eg.bahr.feature.home.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.home.model.HomeDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/**
 * Home's own read. Public like the catalogue: no token needed, but the signed-in account's goes with it (core:network's
 * `bahrAuth`), so the cards say `likedByMe` (MP-B1). "All trips" is core:catalog's `GET /trips`.
 */
internal class HomeApiService(
    private val client: HttpClient,
) {
    /**
     * `getHome`: the server-driven sections above the list (PLAN §5c), localised by the client's
     * `Accept-Language`. `GET /trips` still serves the full list under them.
     */
    suspend fun home(): AppResult<HomeDto> =
        callApi {
            client.get("home")
        }
}
