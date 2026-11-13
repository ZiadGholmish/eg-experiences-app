package eg.bahr.feature.settings.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.model.TravellerProfileDto
import eg.bahr.feature.settings.model.UpdateProfileVisibilityRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart

/**
 * Traveller profiles (MP-B2): `getTraveller` (public; a token is ignored) and the signed-in account's own switches,
 * `getMyProfileVisibility` / `updateMyProfileVisibility` (bearer; UNAUTHORIZED drops the sign-in in core:network).
 */
internal class PublicProfileApiService(
    private val client: HttpClient,
) {
    /** NOT_FOUND for a private profile, an unknown or malformed id and a deleted account alike. */
    suspend fun traveller(publicId: String): AppResult<TravellerProfileDto> =
        // The id is opaque and comes from a payload: encoded, so it can only ever be one path segment.
        callApi { client.get("travellers/${publicId.encodeURLPathPart()}") }

    suspend fun visibility(): AppResult<ProfileVisibilityDto> = callApi { client.get("me/profile-visibility") }

    /** Sends only what [request] sets; the answer is the whole new state. */
    suspend fun updateVisibility(request: UpdateProfileVisibilityRequest): AppResult<ProfileVisibilityDto> =
        callApi { client.patch("me/profile-visibility") { setBody(request) } }
}
