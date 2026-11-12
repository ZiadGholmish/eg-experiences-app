package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.catalog.model.HostPageDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * `GET /hosts`, the hosts directory (MV-B1). One owner here because two features read it (MV-M1): feature:host's
 * directory and the hosts above feature:search's results. Public like the host page: no token.
 */
internal class HostListApiService(
    private val client: HttpClient,
) {
    /**
     * `listHosts`: one page of hosts narrowed by [query]. A null choice is left out of the request (the contract's
     * "clear"), never sent empty. An unknown sort, kind, category or governorate is 400 VALIDATION_FAILED.
     */
    suspend fun listHosts(
        query: HostListQuery,
        page: Int,
        size: Int,
    ): AppResult<HostPageDto> =
        callApi {
            client.get("hosts") {
                // Ktor's `parameter` skips a null value, so a cleared choice is simply absent.
                parameter("q", query.q)
                parameter("sort", query.sort)
                parameter("category", query.category)
                parameter("governorate", query.governorate)
                parameter("kind", query.kind)
                parameter("page", page)
                parameter("size", size)
            }
        }
}

/** The directory's page size: the server's default (20), inside `listHosts`' 1..50. */
const val HOST_PAGE_SIZE = 20

/** How many hosts the search screen shows above the trips (the contract's `size=3`). */
const val SEARCH_HOSTS_SIZE = 3
