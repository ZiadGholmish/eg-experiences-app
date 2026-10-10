package eg.bahr.feature.trips.navigation

/**
 * What a tap on Home asks the app to do: a banner (openapi `HomeAction`), a category chip, or a trip
 * row's "See all" (`HomeSeeAll`, M4-M1b). Public because the app's NavHost decides where each goes; a
 * `none` banner (or one whose value is unusable) is simply not tappable and never produces one of these.
 */
sealed interface HomeAction {
    /** `trip`: open the trip page of [slug]. */
    data class OpenTrip(
        val slug: String,
    ) : HomeAction

    /**
     * `category`: the category page for [key] (the `category` filter of `GET /trips`). [title] is the
     * label the opener already shows (a chip's, a row's), drawn until the page's own facets arrive;
     * null from a banner, which only knows the key.
     */
    data class OpenCategory(
        val key: String,
        val title: String? = null,
    ) : HomeAction

    /**
     * A trip row's "See all" for a row that is not one category: every trip of section [sectionId]
     * (`GET /home/sections/{id}/trips`). [title] is the row's, which that endpoint does not return.
     */
    data class OpenSection(
        val sectionId: String,
        val title: String? = null,
    ) : HomeAction

    /** `url`: an https link, for an in-app browser. Anything not https never gets here. */
    data class OpenUrl(
        val url: String,
    ) : HomeAction
}
