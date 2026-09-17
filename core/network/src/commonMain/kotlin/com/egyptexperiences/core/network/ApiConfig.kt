package com.egyptexperiences.core.network

/**
 * Everything the HTTP client needs that only the app module knows.
 *
 * [baseUrl] comes from a `BuildConfig` field on Android and from the Xcode
 * configuration on iOS, so it cannot be a constant in this module.
 */
data class ApiConfig(
    val baseUrl: String,
    val isDebug: Boolean,
)

/**
 * Supplies the language tag for the `Accept-Language` header on every request.
 *
 * A function rather than a value because the user can switch language inside
 * the app, and the next request has to carry the new one.
 */
fun interface LanguageTagProvider {
    fun currentLanguageTag(): String
}
