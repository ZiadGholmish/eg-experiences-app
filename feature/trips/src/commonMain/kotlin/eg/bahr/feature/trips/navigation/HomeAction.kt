package eg.bahr.feature.trips.navigation

/**
 * What a tap on a Home banner asks the app to do (openapi `HomeAction`). Public because the app's
 * NavHost decides where each goes; a `none` banner (or one whose value is unusable) is simply not
 * tappable and never produces one of these.
 */
sealed interface HomeAction {
    /** `trip`: open the trip page of [slug]. */
    data class OpenTrip(
        val slug: String,
    ) : HomeAction

    /** `category`: the trip list filtered by [key] (the `category` filter of `GET /trips`). */
    data class OpenCategory(
        val key: String,
    ) : HomeAction

    /** `url`: an https link, for an in-app browser. Anything not https never gets here. */
    data class OpenUrl(
        val url: String,
    ) : HomeAction
}
