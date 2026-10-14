package eg.bahr.core.network.auth

import co.touchlab.kermit.Logger
import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Whether someone is signed in on this device, and the little the app shows about them offline. */
sealed interface SessionState {
    /** The secure store has not been read yet (the first frames after launch). */
    data object Unknown : SessionState

    data object SignedOut : SessionState

    data class SignedIn(
        val accountName: String?,
        val profileComplete: Boolean,
        val providers: Set<String>,
    ) : SessionState
}

/**
 * The device's sign-in (M3-M0): our tokens in secure storage ([SessionStore]) and the rules for using
 * them, in one app-wide object (a Koin single) that every request goes through.
 *
 * - Every request carries the access token while one is held ([BahrAuth]); it is refreshed shortly
 *   before it expires, or once after a 401.
 * - **Refreshes are serialised** ([mutex]): a refresh token is single-use, and sending a used one again
 *   revokes the whole sign-in (openapi `refreshTokens`, M3-B1 review #4). A caller that waited while
 *   another refreshed gets the new token instead of refreshing again.
 * - A refresh that is refused (401 or 400) signs out. So does one whose answer was lost after it was
 *   sent (a timeout, a 5xx, a body that is not ours): the server may already have retired the token, and
 *   only a new sign-in is safe. One made ahead of expiry that never left the device (no connection) keeps
 *   the tokens; the next request tries again, and if that turns out to be a replay the server's 401
 *   signs out the same.
 * - A 401 that a refresh cannot cure drops the tokens (openapi `getMe`: also the answer for a deleted
 *   account): the refresh was refused or lost, could not be sent, or the retry with the new token was
 *   refused again. So "You've been signed out" (the UNAUTHORIZED message) is then true.
 * - A 429 on refresh keeps the tokens: the server refused before reading the token.
 */
class AuthSession(
    private val store: SessionStore,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private var loaded = false
    private var current: StoredSession? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** The server side of refresh and sign-out; set by the HTTP client factory, which owns the client. */
    internal var remote: SessionRemote? = null

    /** Reads the secure store once; later calls return at once. */
    suspend fun load(): SessionState =
        mutex.withLock {
            ensureLoaded()
            _state.value
        }

    /** A successful sign-in (`signInWithProvider`): keeps the tokens and the account summary. */
    suspend fun signedIn(
        tokens: AuthTokens,
        accountName: String?,
        profileComplete: Boolean,
        providers: List<String>,
    ) = mutex.withLock {
        loaded = true
        save(StoredSession(tokens, accountName, profileComplete, providers))
    }

    /** A fresh read or update of the account (`getMe`, `updateMe`); ignored when signed out meanwhile. */
    suspend fun updateAccount(
        accountName: String?,
        profileComplete: Boolean,
        providers: List<String>,
    ) = mutex.withLock {
        ensureLoaded()
        current?.let { save(it.copy(accountName = accountName, profileComplete = profileComplete, providers = providers)) }
    }

    /**
     * Signs out: asks the server to revoke the refresh token (best effort: openapi `signOut` is
     * idempotent and the access token simply expires), then forgets both tokens on the device whatever
     * the answer, so sign-out always works offline too.
     */
    suspend fun signOut() {
        val refreshToken =
            mutex.withLock {
                ensureLoaded()
                current?.tokens?.refreshToken.also { save(null) }
            } ?: return
        try {
            remote?.revoke(refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Sign-out not confirmed by the server; the token is forgotten here anyway" }
        }
    }

    /** Forgets the sign-in without telling the server (after `deleteMe`, which revoked everything). */
    suspend fun clear() =
        mutex.withLock {
            loaded = true
            save(null)
        }

    /** The access token to send, refreshed first when it is about to expire; null when signed out. */
    internal suspend fun accessTokenForRequest(): String? =
        mutex.withLock {
            ensureLoaded()
            val session = current ?: return@withLock null
            when {
                session.tokens.refreshTokenExpiresAt.hasPassed(margin = 0.seconds) -> {
                    save(null)
                    null
                }
                session.tokens.accessTokenExpiresAt.hasPassed(margin = REFRESH_MARGIN) -> refreshLocked(session.tokens.accessToken)
                else -> session.tokens.accessToken
            }
        }

    /**
     * After a 401 to a request sent with [rejected]: the token to retry with, or null when there is none
     * (signed out now, or the same token: the refresh could not reach the server).
     */
    internal suspend fun refreshAfterRejection(rejected: String): String? =
        mutex.withLock {
            ensureLoaded()
            refreshLocked(rejected)?.takeUnless { it == rejected }
        }

    /**
     * Forgets the sign-in if [rejected] is still its access token: a 401 nothing could cure. Keyed, so a
     * late 401 to an old token cannot sign out a session that has been refreshed (or signed in) since.
     */
    internal suspend fun dropIfCurrent(rejected: String) =
        mutex.withLock {
            ensureLoaded()
            if (current?.tokens?.accessToken == rejected) save(null)
        }

    /** Holding [mutex]. [seen] is the access token the caller has; a different one means a refresh already ran. */
    private suspend fun refreshLocked(seen: String): String? {
        val session = current ?: return null
        if (session.tokens.accessToken != seen) return session.tokens.accessToken
        val remote = remote ?: return session.tokens.accessToken
        return when (val outcome = remote.refresh(session.tokens.refreshToken)) {
            is RefreshOutcome.Refreshed -> {
                save(session.copy(tokens = outcome.tokens))
                outcome.tokens.accessToken
            }
            RefreshOutcome.Rejected, RefreshOutcome.Lost -> {
                save(null)
                null
            }
            RefreshOutcome.NotSent -> session.tokens.accessToken
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        current =
            try {
                store.read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Stored sign-in not readable; signed out: ${e.message}" }
                null
            }
        loaded = true
        _state.value = current.toState()
    }

    /** Memory first (so a failed write still takes effect for this run), then the secure store. */
    private suspend fun save(session: StoredSession?) {
        current = session
        _state.value = session.toState()
        try {
            store.write(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Sign-in not written to secure storage: ${e.message}" }
        }
    }

    private fun String.hasPassed(margin: kotlin.time.Duration): Boolean {
        // An expiry that does not parse is left to the server to judge (a 401 then refreshes).
        val at = runCatching { Instant.parse(this) }.getOrNull() ?: return false
        return clock.now() >= at - margin
    }

    private fun StoredSession?.toState(): SessionState =
        if (this == null) {
            SessionState.SignedOut
        } else {
            SessionState.SignedIn(accountName, profileComplete, providers.toSet())
        }

    private companion object {
        /** Refresh this long before the access token's expiry, so a request in flight does not carry a dying one. */
        val REFRESH_MARGIN = 30.seconds
        val log = Logger.withTag("AuthSession")
    }
}

/** How a refresh ended, as [AuthSession] needs to know it. */
internal sealed interface RefreshOutcome {
    data class Refreshed(
        val tokens: AuthTokens,
    ) : RefreshOutcome

    /** The server refused the token (401/400): it is retired, revoked or unknown. */
    data object Rejected : RefreshOutcome

    /** Sent, but no usable answer came back: the token may already be retired. */
    data object Lost : RefreshOutcome

    /** It never reached the server (no connection): the token is still good. */
    data object NotSent : RefreshOutcome
}

internal interface SessionRemote {
    suspend fun refresh(refreshToken: String): RefreshOutcome

    suspend fun revoke(refreshToken: String)
}
