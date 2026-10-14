package eg.bahr.feature.settings.model

import kotlinx.serialization.Serializable

/**
 * openapi `Account` (`getMe`, `updateMe`), as Profile reads it (M3-M0b). Its own copy rather than
 * feature:auth's: a feature never imports another's types. [name], [phone] and [email] are absent until
 * set (email may never be).
 */
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
)
