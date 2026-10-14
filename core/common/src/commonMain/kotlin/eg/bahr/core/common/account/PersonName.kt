package eg.bahr.core.common.account

/**
 * The account's name as the handoff asks for it (M3-M0b, §2: required, 2–60 characters), for the two
 * forms that take it: the first-sign-in sheet (feature:auth) and Edit personal info (feature:settings).
 * The contract allows 1–80 (openapi `UpdateAccountRequest.name`); this stays inside it, and the server
 * stays the authority.
 */
object PersonName {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 60

    /** What is sent: the name without the spaces around it. */
    fun clean(typed: String): String = typed.trim()

    fun isValid(typed: String): Boolean = clean(typed).length in MIN_LENGTH..MAX_LENGTH
}
