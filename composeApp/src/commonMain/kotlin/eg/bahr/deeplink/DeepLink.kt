package eg.bahr.deeplink

import eg.bahr.core.common.locale.AppLanguage

/** Where a link from outside the app (App Link, Universal Link) asks to go. */
internal sealed interface DeepLink {
    /**
     * A trip's share link, `https://<host>/t/{slug}`.
     *
     * [language] is the link's `?lang=`, the language it was shared in. It is carried, not applied: the
     * reader's stored language decides what the app shows, the sharer's does not.
     */
    data class Trip(
        val slug: String,
        val language: AppLanguage? = null,
    ) : DeepLink

    /** Anything else, including a malformed trip link: the app opens on the trip list. */
    data object Unknown : DeepLink
}

/**
 * Turns a URL the platform hands over into a [DeepLink].
 *
 * Deliberately strict, and a hand-written grammar rather than a general URL parser, so that what is
 * accepted is exactly what is written here: the configured [host] (any port), `https` (plus `http`
 * when [allowsHttp], the local flavor only), and the path `/t/{slug}` with at most one trailing slash.
 * Query parameters other than `lang` and the fragment are ignored.
 */
internal class DeepLinkParser(
    host: String,
    private val allowsHttp: Boolean,
) {
    private val host = host.lowercase()

    fun parse(url: String): DeepLink {
        val match = UrlShape.matchEntire(url.trim()) ?: return DeepLink.Unknown
        val (scheme, authority, path, query) = match.destructured
        if (!isAllowedScheme(scheme.lowercase()) || !isOurHost(authority)) return DeepLink.Unknown

        val slug = TripPath.matchEntire(path)?.groupValues?.get(1) ?: return DeepLink.Unknown
        if (slug.length > SLUG_MAX_LENGTH || !Slug.matches(slug)) return DeepLink.Unknown

        return DeepLink.Trip(slug = slug, language = languageOf(query))
    }

    private fun isAllowedScheme(scheme: String): Boolean = scheme == "https" || (allowsHttp && scheme == "http")

    /** User-info is refused outright: `https://bahr.eg@evil.example/` is a link to evil.example. */
    private fun isOurHost(authority: String): Boolean {
        if ('@' in authority) return false
        val hostAndPort = HostAndPort.matchEntire(authority) ?: return false
        return hostAndPort.groupValues[1].lowercase() == host
    }

    /** Exactly `ar` or `en`, as the backend's `PageLanguage` reads it; anything else is no language. */
    private fun languageOf(query: String): AppLanguage? {
        val value =
            query
                .split('&')
                .firstOrNull { it.substringBefore('=') == LANG_PARAM }
                ?.substringAfter('=', missingDelimiterValue = "")
                ?.trim()
                ?.lowercase()
                ?: return null
        return AppLanguage.entries.firstOrNull { it.tag == value }
    }

    private companion object {
        /** scheme :// authority, then an optional path, query and fragment (RFC 3986, appendix B). */
        val UrlShape = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#.*)?$""")

        val HostAndPort = Regex("""^([^:]+)(?::\d{1,5})?$""")

        /** `/t/{slug}` or `/t/{slug}/`. Nothing else under `/t/`, and `/T/` is not `/t/` (nor is it on the web). */
        val TripPath = Regex("""^/t/([^/]+)/?$""")

        /**
         * The backend's slug rule, `chk_trip_slug` in bahr-be `V3__trip.sql`. Matched against the raw path,
         * so a percent-encoded character is simply not a slug.
         */
        val Slug = Regex("""^[a-z0-9]+(-[a-z0-9]+)*$""")

        /** `trip.slug varchar(160)`. */
        const val SLUG_MAX_LENGTH = 160

        const val LANG_PARAM = "lang"
    }
}
