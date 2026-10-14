package eg.bahr.feature.auth.model

import kotlinx.serialization.Serializable

/** openapi `SignInRequest`. [name] is Apple's first-sign-in name; left out (not null) when absent. */
@Serializable
internal data class SignInRequest(
    val idToken: String,
    val nonce: String,
    val name: String? = null,
)

/**
 * openapi `SignIn`: `Tokens` plus [newAccount] and [account]. The tokens are handed straight to
 * core:network's `AuthSession`; [roles] is not used on the device.
 */
@Serializable
internal data class SignInDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val roles: List<String> = emptyList(),
    val newAccount: Boolean = false,
    val account: AccountDto,
)

/** openapi `Account`. [name], [phone] and [email] are absent until set (email may never be). */
@Serializable
internal data class AccountDto(
    val id: String,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val locale: String = "ar",
    val providers: List<String> = emptyList(),
    val profileComplete: Boolean = false,
)

/** openapi `UpdateAccountRequest`: absent fields stay as they are on the server. */
@Serializable
internal data class UpdateAccountRequest(
    val name: String? = null,
    val phone: String? = null,
    val locale: String? = null,
)

/** openapi `DeleteAccountRequest`. */
@Serializable
internal data class DeleteAccountRequest(
    val appleAuthorizationCode: String? = null,
)

/** The `{provider}` of `POST /auth/{provider}` (openapi `signInWithProvider`). */
internal enum class AuthProvider(
    val wire: String,
) {
    Apple("apple"),
    Google("google"),
}
