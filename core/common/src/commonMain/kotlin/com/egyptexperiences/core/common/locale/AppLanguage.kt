package com.egyptexperiences.core.common.locale

/**
 * The two locales phase 1 ships.
 *
 * Arabic is [default] because the backend's locale resolver falls back to
 * Arabic when `Accept-Language` says nothing — the client must not disagree
 * with the server about what an unconfigured user sees.
 */
enum class AppLanguage(
    val tag: String,
    val isRtl: Boolean,
) {
    ARABIC("ar", isRtl = true),
    ENGLISH("en", isRtl = false),
    ;

    companion object {
        val default: AppLanguage = ARABIC

        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag.equals(tag?.take(2), ignoreCase = true) } ?: default
    }
}
