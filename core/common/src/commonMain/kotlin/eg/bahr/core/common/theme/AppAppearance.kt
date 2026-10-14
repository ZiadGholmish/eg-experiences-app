package eg.bahr.core.common.theme

/**
 * Light or dark, as the person picked it in Profile → Appearance (user decision 2026-10-14). [default]
 * is Light: dark is opt-in, and the app follows the phone's setting only when [SYSTEM] is picked.
 * Stored per install by its [tag]; the app root turns it into the theme on both platforms.
 */
enum class AppAppearance(
    val tag: String,
) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system"),
    ;

    /** Whether to draw dark, given whether the phone is in dark mode now. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            LIGHT -> false
            DARK -> true
            SYSTEM -> systemDark
        }

    companion object {
        val default: AppAppearance = LIGHT

        /** An unknown or missing value (a first launch) is [default]. */
        fun fromTag(tag: String?): AppAppearance = entries.firstOrNull { it.tag == tag } ?: default
    }
}
