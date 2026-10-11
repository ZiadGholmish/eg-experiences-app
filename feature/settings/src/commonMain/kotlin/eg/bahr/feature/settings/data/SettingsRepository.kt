package eg.bahr.feature.settings.data

import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.AppSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/** What Settings reads and changes: the stored app language. */
internal interface SettingsRepository {
    val language: Flow<AppLanguage>

    /** Stores [language]; the whole app follows it (App re-themes every Compose root from the store). */
    suspend fun setLanguage(language: AppLanguage): AppResult<Unit>
}

internal class DefaultSettingsRepository(
    private val store: AppSettingsStore,
) : SettingsRepository {
    override val language: Flow<AppLanguage> = store.language

    /**
     * A failed write (a full disk, a broken file) leaves the old language in place; it becomes a
     * failure the screen can report rather than a crash. Not network, so not `callApi`'s to catch.
     */
    override suspend fun setLanguage(language: AppLanguage): AppResult<Unit> =
        try {
            store.setLanguage(language)
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message))
        }
}
