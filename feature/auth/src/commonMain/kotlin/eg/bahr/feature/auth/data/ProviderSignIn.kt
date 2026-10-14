package eg.bahr.feature.auth.data

import androidx.compose.runtime.Composable
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.feature.auth.model.AuthProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the provider's own sign-in UI (Credential Manager, AuthenticationServices, Google Sign-In) gave back. */
internal sealed interface ProviderResult {
    /**
     * [idToken] for `signInWithProvider`; [fullName] Apple sends once, on the first sign-in;
     * [authorizationCode] Apple's, which `deleteMe` takes to revoke the user's Apple tokens.
     */
    data class Success(
        val idToken: String,
        val fullName: String? = null,
        val authorizationCode: String? = null,
    ) : ProviderResult

    /** The person closed the provider's sheet: no error to show. */
    data object Cancelled : ProviderResult

    data class Failed(
        val reason: String?,
    ) : ProviderResult
}

/**
 * The platform's sign-in with [providers]: Google only on Android (Apple on Android needs a web flow
 * with a `form_post` HTTPS callback, not built in M3), Apple and Google on iOS. Empty when the build
 * has no client ids and no fake sign-in; the sheet then says sign-in is not set up.
 */
internal interface ProviderSignIn {
    val providers: List<AuthProvider>

    /** Runs the provider's UI. [hashedNonce] is `sha256hex(rawNonce)` (openapi `signInWithProvider`). */
    suspend fun signIn(
        provider: AuthProvider,
        hashedNonce: String,
    ): ProviderResult
}

/**
 * The platform's [ProviderSignIn], bound to the current screen: Android's Credential Manager needs the
 * Activity it shows over.
 */
@Composable
internal expect fun rememberProviderSignIn(): ProviderSignIn

/** SHA-256 of [text]'s UTF-8 bytes, as lowercase hex: what the provider SDK is given as its nonce. */
internal expect fun sha256Hex(text: String): String

/**
 * A fresh nonce per sign-in (openapi: at least 16 characters): two random UUIDs as 64 hex digits. Kotlin's
 * `Uuid.random()` draws from the platform's cryptographically secure generator on Android and iOS.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun newRawNonce(): String = Uuid.random().toHexString() + Uuid.random().toHexString()

/**
 * Local runs only (M3-B1: the `local` profile's fake verifier): makes the token the backend's
 * `FakeIdTokenVerifier` accepts, `fake.` + base64url (no padding) of the claims, with no provider UI.
 * The subject is per install (`local-<provider>-<device id>`), so one phone keeps one account across
 * sign-ins and two phones are two people. Never used where real client ids are configured.
 */
internal class FakeProviderSignIn(
    private val deviceIds: DeviceIdSource,
    override val providers: List<AuthProvider>,
) : ProviderSignIn {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun signIn(
        provider: AuthProvider,
        hashedNonce: String,
    ): ProviderResult {
        val device = deviceIds.deviceId()
        val claims =
            buildJsonObject {
                put("sub", "local-${provider.wire}-$device")
                put("email", "local-${device.take(DEVICE_PREFIX)}@example.com")
                put("email_verified", true)
                put("nonce", hashedNonce)
                put("provider", provider.wire)
            }
        val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(claims.toString().encodeToByteArray())
        return ProviderResult.Success(idToken = FAKE_PREFIX + encoded)
    }

    private companion object {
        const val FAKE_PREFIX = "fake."
        const val DEVICE_PREFIX = 8
    }
}

/** A build with no client ids and no fake sign-in: no buttons, and the sheet says so. */
internal object NoProviderSignIn : ProviderSignIn {
    override val providers = emptyList<AuthProvider>()

    override suspend fun signIn(
        provider: AuthProvider,
        hashedNonce: String,
    ): ProviderResult = ProviderResult.Failed("sign-in not configured")
}
