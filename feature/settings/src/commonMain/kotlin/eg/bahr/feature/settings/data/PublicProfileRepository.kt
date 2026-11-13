package eg.bahr.feature.settings.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.model.TravellerProfileDto
import eg.bahr.feature.settings.model.UpdateProfileVisibilityRequest

/**
 * A traveller's public profile and the account's own profile switches (MP-M2). An interface so the view-model tests
 * hand in a fake (hand-written fakes, no mocking library).
 */
internal interface PublicProfileRepository {
    suspend fun traveller(publicId: String): AppResult<TravellerProfileDto>

    suspend fun visibility(): AppResult<ProfileVisibilityDto>

    suspend fun updateVisibility(request: UpdateProfileVisibilityRequest): AppResult<ProfileVisibilityDto>
}

/** A pass-through to [api]: nothing is cached, since a switch takes effect on the server at once. */
internal class DefaultPublicProfileRepository(
    private val api: PublicProfileApiService,
) : PublicProfileRepository {
    override suspend fun traveller(publicId: String) = api.traveller(publicId)

    override suspend fun visibility() = api.visibility()

    override suspend fun updateVisibility(request: UpdateProfileVisibilityRequest) = api.updateVisibility(request)
}
