package eg.bahr.feature.host.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.host.model.HostProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/**
 * The host page (split from feature:trips, M3-M16). The host's trips are `GET /trips?host=`, core:catalog's
 * `TripListApiService`. Needs no token: the page is public.
 */
internal class HostApiService(
    private val client: HttpClient,
) {
    /**
     * `getHost` (M4-M10): a host's public page, opened from the trip page's host card. NOT_FOUND for an
     * unknown slug and for a host who is not approved, though the trip card always carries the slug.
     */
    suspend fun hostBySlug(slug: String): AppResult<HostProfileDto> =
        callApi {
            client.get("hosts/$slug")
        }
}
