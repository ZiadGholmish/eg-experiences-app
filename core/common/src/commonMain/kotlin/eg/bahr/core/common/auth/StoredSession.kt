package eg.bahr.core.common.auth

import kotlinx.serialization.Serializable

/**
 * Our own tokens (openapi `Tokens`, M3-B1): a JWT access token and an opaque, single-use refresh
 * token, each with its expiry as the server sent it (ISO-8601). Infrastructure, not business: the
 * shape every signed-in request needs, here because both core:network (which sends and refreshes them)
 * and core:datastore (which keeps them in secure storage) need it without depending on each other.
 */
@Serializable
data class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
)

/**
 * What the device keeps about a sign-in: the tokens, and the little of the account the app shows
 * without a network (Settings' "signed in as …"): [accountName] (absent until set),
 * [profileComplete] (name and phone both set) and [providers] (`apple`, `google`; `apple` means
 * account deletion re-authenticates with Apple first). Personal data: secure storage only.
 */
@Serializable
data class StoredSession(
    val tokens: AuthTokens,
    val accountName: String? = null,
    val profileComplete: Boolean = false,
    val providers: List<String> = emptyList(),
)

/** Where [StoredSession] is kept (Keychain on iOS, Keystore-encrypted on Android; core:datastore). */
interface SessionStore {
    suspend fun read(): StoredSession?

    /** Replaces what is stored; null forgets the session. */
    suspend fun write(session: StoredSession?)
}

/**
 * A random UUID made once per install (never a hardware id), sent as `X-Device-Id` on sign-in, holds
 * and waiting-list joins (openapi `DeviceId`, M3-B1), so the server can attach this device's anonymous
 * holds and entries to the account it signs in to.
 */
fun interface DeviceIdSource {
    suspend fun deviceId(): String
}
