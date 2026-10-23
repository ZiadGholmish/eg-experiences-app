package eg.bahr.feature.tripdetails.presentation

/**
 * The server's rule for a waiting-list email (bahr-be `WaitlistEntry.requireValid`, M4-B9), mirrored
 * here because the server refuses a bad address with `VALIDATION_FAILED` and no `violations[]` entry
 * to point at the field: the form has to catch it inline, before the request.
 *
 * Trimmed first, as the server does; then at most [MAX_LENGTH] characters and the WHATWG
 * `input type=email` shape with at least one dot in the domain. ASCII only, as on the server, so an
 * address in Arabic letters is refused here rather than by the server.
 */
internal object WaitlistEmail {
    /** openapi `WaitlistRequest.email.maxLength`: the longest address SMTP delivers to (RFC 5321). */
    const val MAX_LENGTH = 254

    // Character for character the server's pattern, so the two never disagree on an address.
    private val PATTERN =
        Regex(
            "^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+@" +
                "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
                "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$",
        )

    /** What is sent and remembered: the address as typed, without the spaces around it. */
    fun normalised(typed: String): String = typed.trim()

    fun isValid(typed: String): Boolean {
        val email = normalised(typed)
        return email.length <= MAX_LENGTH && PATTERN.matches(email)
    }
}
