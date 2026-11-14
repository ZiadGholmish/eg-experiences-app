package eg.bahr.core.network.auth

import co.touchlab.kermit.Logger
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import io.ktor.client.HttpClient
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** openapi `UpdateAccountRequest` with only the language: absent fields stay as they are (`updateMe`). */
@Serializable
internal data class AccountLocaleRequest(
    val locale: String,
)

/** The one field of openapi `Account` this needs. */
@Serializable
internal data class AccountLocaleDto(
    val locale: String? = null,
)

/**
 * Keeps the signed-in account's language (`app_user.locale`, openapi `updateMe {locale}`) the app's (M3-M21). The server
 * writes the account's in-app and push notifications in that language, and set it only once, at sign-up: without this,
 * an app switched to Arabic kept receiving English notifications.
 *
 * App-wide and for the life of the process, as [eg.bahr.core.network.install.InstallRegistrar] is (not Profile's, which
 * is only alive while Profile is open): it sends one `PATCH /me` whenever someone is signed in and the account's
 * language, as last heard from the server ([AuthSession.accountLocale]), is not the app's. That covers a switch in
 * Profile or from a link's `?lang=`, a sign-in to an account in the other language, and a restored sign-in whose
 * account language is unknown (kept by an older build) or still differs. Signed out nothing is sent: the install's own
 * registration already carries its locale.
 *
 * A failure is logged and left: the kept locale still differs, so the next app start sends it again (as does the next
 * switch or sign-in). It never blocks a screen; nothing waits for it.
 */
class AccountLocaleSync internal constructor(
    clientProvider: () -> HttpClient,
    private val session: AuthSession,
    private val coalesce: Duration = COALESCE,
) {
    private val client: HttpClient by lazy(clientProvider)

    /**
     * Starts following [language], which must begin with the stored language (not a default standing in for it until
     * it is read), or an English app would first tell an English account it is Arabic.
     */
    @OptIn(FlowPreview::class)
    fun start(
        scope: CoroutineScope,
        language: Flow<AppLanguage>,
    ): Job =
        scope.launch {
            launch { session.load() }
            val signedIn =
                session.state
                    .filter { it != SessionState.Unknown }
                    .map { it is SessionState.SignedIn }
                    .distinctUntilChanged()
            combine(signedIn, session.accountLocale, language.map { it.tag }) { inAccount, accountLocale, tag ->
                // What the account should be told now; null when it already follows the app (or no one is signed in).
                tag.takeIf { inAccount && accountLocale != tag }
            }.distinctUntilChanged()
                // A sign-in's tokens and its account locale land a moment apart; switches back and forth fold into one.
                .debounce(coalesce)
                .filterNotNull()
                // One request at a time, and only for the latest language once one is under way.
                .conflate()
                .collect { tag -> sync(tag) }
        }

    /** One `PATCH /me {locale}`; on success the account's answer is kept as its language, if still signed in to it. */
    internal suspend fun sync(locale: String): AppResult<Unit> {
        // The answer belongs to this sign-in only: a sign-out and another sign-in while it is under way drop it.
        val signIn = session.signInGeneration()
        return when (val result = callApi<AccountLocaleDto> { client.patch("me") { setBody(AccountLocaleRequest(locale)) } }) {
            is AppResult.Success -> {
                session.updateAccountLocale(result.data.locale ?: locale, forSignIn = signIn)
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> {
                log.i { "Account language not updated (${result.error}); the next start tries again" }
                result
            }
        }
    }

    internal companion object {
        /** As the install registration's: long enough to fold a sign-in's two writes, short enough to go unnoticed. */
        val COALESCE: Duration = 500.milliseconds

        private val log = Logger.withTag("AccountLocaleSync")
    }
}
