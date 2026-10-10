package eg.bahr.feature.map.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.map.model.TripMapDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/** `getTripMap`: public, like the trip list; localised by the client's `Accept-Language`. */
internal class TripMapApiService(
    private val client: HttpClient,
) {
    /** Every published trip as a pin, the places the buses leave from, and the legend. Not paged. */
    suspend fun tripMap(): AppResult<TripMapDto> =
        callApi {
            client.get("trips/map")
        }
}
