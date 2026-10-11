package eg.bahr.feature.settings.data

import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * The stored language in memory. [stored] null = the store has not answered yet. With [failWith], a
 * write fails and the stored language stays as it was.
 */
internal class FakeSettingsRepository(
    initial: AppLanguage? = AppLanguage.ARABIC,
    var failWith: AppError? = null,
) : SettingsRepository {
    val stored = MutableStateFlow(initial)
    val writes = mutableListOf<AppLanguage>()

    override val language: Flow<AppLanguage> = stored.filterNotNull()

    override suspend fun setLanguage(language: AppLanguage): AppResult<Unit> {
        writes += language
        failWith?.let { return AppResult.Failure(it) }
        stored.value = language
        return AppResult.Success(Unit)
    }
}
