package eg.bahr.core.network.auth

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.SessionStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

/**
 * Host mode's sign-in (M5-M1): the email-and-password session of a host (openapi `signInWithPassword`, body mode), kept
 * apart from the traveller's [AuthSession] in every way that matters:
 *
 * - its own secure item (core:common's `HostSessionStore`, bound by core:datastore);
 * - its own [AuthSession] instance, so its refreshes are serialised among themselves and a refused or lost host refresh,
 *   or a host 401 nothing could cure, drops the host's tokens only;
 * - its own HTTP client ([HostHttpClient]), whose bearer is the host's: the traveller's token never goes to
 *   the `/api/v1/host/` routes, and the host's never to a traveller route.
 *
 * Signing out of one never touches the other, and the traveller's features keep working while host mode is open.
 * The same [AuthSession] rules apply (see its doc): a body refresh token is single-use, so refreshes are serialised.
 */
class HostSession(
    store: SessionStore,
    clock: Clock = Clock.System,
) {
    internal val session = AuthSession(store, clock)

    /** Unknown until the secure item is read; then signed in or out. [SessionState.SignedIn.accountName] is the host's name. */
    val state: StateFlow<SessionState> get() = session.state

    /** Reads the secure item once; later calls return at once. */
    suspend fun load(): SessionState = session.load()

    /**
     * A successful password sign-in or password change (`refreshTokenIn: BODY`): keeps the host's tokens. [hostName] is
     * what the app shows offline until `getHostMe` answers; null when not known yet.
     */
    suspend fun signedIn(
        tokens: AuthTokens,
        hostName: String? = null,
    ) = session.signedIn(tokens, accountName = hostName, profileComplete = true, providers = emptyList())

    /** The host's name as `getHostMe` last said it; ignored when signed out meanwhile. */
    suspend fun updateName(hostName: String?) =
        session.updateAccount(accountName = hostName, profileComplete = true, providers = emptyList())

    /** Revokes the host's refresh token (best effort) and forgets the host's tokens; the traveller's stay. */
    suspend fun signOut() = session.signOut()
}

/**
 * The client host mode's requests go through (M5-M1): the app's JSON, base URL, timeouts and `Accept-Language`, with
 * the host's bearer ([HostSession]) instead of the traveller's. A type of its own, so a feature asks for it by name
 * and the traveller's `HttpClient` stays the only unqualified one. Like the traveller's client, it never retries a
 * request on its own: a manual booking must not be sent twice without the host asking (openapi `createManualBooking`).
 */
class HostHttpClient(
    val client: HttpClient,
)
