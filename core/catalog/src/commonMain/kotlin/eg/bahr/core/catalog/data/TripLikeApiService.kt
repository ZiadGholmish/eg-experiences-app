package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.TripLikeDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.put

/**
 * The heart (MP-B1): `PUT /me/likes/{tripSlug}` (`likeTrip`) and `DELETE` (`unlikeTrip`). Both are idempotent and
 * answer the trip's like state after the call. They go with the signed-in account's token, as every request does
 * (core:network's `bahrAuth`); a 401 nothing can cure drops the tokens there.
 */
internal class TripLikeApiService(
    private val client: HttpClient,
) {
    /** [liked] true likes the trip, false takes the like back. */
    suspend fun setLiked(
        slug: String,
        liked: Boolean,
    ): AppResult<TripLikeDto> =
        callApi {
            val path = "me/likes/$slug"
            if (liked) client.put(path) else client.delete(path)
        }
}
