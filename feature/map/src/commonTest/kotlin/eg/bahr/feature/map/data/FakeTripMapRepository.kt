package eg.bahr.feature.map.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.map.model.TripMapDto

/** Hand-written fake: answers from [tripMap] and counts the reads. */
internal class FakeTripMapRepository(
    var tripMap: suspend () -> AppResult<TripMapDto> = { AppResult.Failure(AppError.Unknown("unset")) },
) : TripMapRepository {
    var reads = 0
        private set

    override suspend fun tripMap(): AppResult<TripMapDto> {
        reads++
        return tripMap.invoke()
    }
}
