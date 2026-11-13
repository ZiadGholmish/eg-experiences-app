package eg.bahr.feature.tripdetails.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import eg.bahr.core.catalog.data.LikeState
import eg.bahr.core.catalog.data.TripHearts
import eg.bahr.core.catalog.data.TripLikes
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.RatingDto
import eg.bahr.core.catalog.presentation.components.SEE_ALL_REVIEWS_TAG
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.core.testing.storedJoin
import eg.bahr.feature.tripdetails.data.FakeTripDetailRepository
import eg.bahr.feature.tripdetails.data.TripDetailPayloads
import eg.bahr.feature.tripdetails.data.TripFixtures.trip
import eg.bahr.feature.tripdetails.data.waitlistMemory
import eg.bahr.feature.tripdetails.model.DepartureDto
import eg.bahr.feature.tripdetails.model.HostDto
import eg.bahr.feature.tripdetails.model.ReviewsDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.model.TripMediaDto
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The trip page × {ar, en} × {loading, loaded, sold-out date selected} (this is also M1-M3's set),
 * plus the waiting list under the sold-out notice (M2-M3: form, list full, joined, seats opened, date closed),
 * driven through the real view model with a fake repository holding the seeded `burullus-dawn` in
 * each language. Photos are stripped: a golden must not depend on Coil or a running MinIO.
 *
 * `loaded` is shot on a tall screen so the whole page is in one golden; the other two use a phone
 * viewport, `sold_out_selected` scrolled to the availability band.
 *
 * Record: `./gradlew :feature:trip-details:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class TripDetailScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        // Shimmers animate forever; a paused clock makes their frame deterministic.
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    /** Text first: the list card's title, location and price, everything else shimmering. */
    @Test
    fun loadingArabic() =
        snap("trip_detail_loading", AppLanguage.ARABIC) {
            tripBySlug = { awaitCancellation() }
            departuresFor = { awaitCancellation() }
            cards = mapOf(SLUG to card("الفجر على بحيرة البرلس", "بحيرة البرلس · كفر الشيخ", "الأكثر حجزًا"))
        }

    @Test
    fun loadingEnglish() =
        snap("trip_detail_loading", AppLanguage.ENGLISH) {
            tripBySlug = { awaitCancellation() }
            departuresFor = { awaitCancellation() }
            cards = mapOf(SLUG to card("Dawn on Lake Burullus", "Lake Burullus · Kafr El Sheikh", "Most booked"))
        }

    /** The same skeleton in dark mode (M3-M14): the placeholders must still read on the dark page. */
    @Test
    fun loadingDarkArabic() =
        snap("trip_detail_loading_dark", AppLanguage.ARABIC, theme = BahrThemeName.Dark) {
            tripBySlug = { awaitCancellation() }
            departuresFor = { awaitCancellation() }
            cards = mapOf(SLUG to card("الفجر على بحيرة البرلس", "بحيرة البرلس · كفر الشيخ", "الأكثر حجزًا"))
        }

    @Test
    fun loadingDarkEnglish() =
        snap("trip_detail_loading_dark", AppLanguage.ENGLISH, theme = BahrThemeName.Dark) {
            tripBySlug = { awaitCancellation() }
            departuresFor = { awaitCancellation() }
            cards = mapOf(SLUG to card("Dawn on Lake Burullus", "Lake Burullus · Kafr El Sheikh", "Most booked"))
        }

    /*
     * MV-M7: the page's loading stages, each the whole page on one tall screen so the sections line up from shot to shot:
     * opened from a list (the card's text, the rest shimmering), opened cold from a link (nothing known), and loaded. The
     * stage between (the trip landed, the live seat counts on their way) differs only in the dates band, below this
     * screen, so the band_* shots below carry it. The trip carries a deck of the two lines the page
     * reserves (the seeded deck's first sentence), so the shots differ only where content replaced its skeleton.
     */

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageCardArabic() = stage("trip_detail_stage_card", AppLanguage.ARABIC, card = true, trip = false, dates = false)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageCardEnglish() = stage("trip_detail_stage_card", AppLanguage.ENGLISH, card = true, trip = false, dates = false)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageColdArabic() = stage("trip_detail_stage_cold", AppLanguage.ARABIC, card = false, trip = false, dates = false)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageColdEnglish() = stage("trip_detail_stage_cold", AppLanguage.ENGLISH, card = false, trip = false, dates = false)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageLoadedArabic() = stage("trip_detail_stage_loaded", AppLanguage.ARABIC, card = true, trip = true, dates = true)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageLoadedEnglish() = stage("trip_detail_stage_loaded", AppLanguage.ENGLISH, card = true, trip = true, dates = true)

    /*
     * MV-M13: opened from the card of an MV-B3 server, which carries the deck and the bus/place cells: the title block
     * and the facts grid are text from the first frame (no deck lines or place cells shimmering), and the trip's whole
     * deck is shown, so this stage lines up with trip_detail_loaded down to the end of the facts.
     */

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageCardWithPageFieldsArabic() =
        stage("trip_detail_stage_card_fields", AppLanguage.ARABIC, card = true, trip = false, dates = false, pageFields = true)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun stageCardWithPageFieldsEnglish() =
        stage("trip_detail_stage_card_fields", AppLanguage.ENGLISH, card = true, trip = false, dates = false, pageFields = true)

    /*
     * The same stages at the dates band (on a phone, scrolled to it): skeleton cards; the trip's own dates with only their
     * seat counts shimmering (HANDOFF §loading: "live seat counts shimmer"); the live dates. One band height throughout.
     */

    @Test
    fun bandCardArabic() = stage("trip_detail_band_card", AppLanguage.ARABIC, card = true, trip = false, dates = false, band = true)

    @Test
    fun bandCardEnglish() = stage("trip_detail_band_card", AppLanguage.ENGLISH, card = true, trip = false, dates = false, band = true)

    @Test
    fun bandDatesLoadingArabic() =
        stage("trip_detail_band_dates_loading", AppLanguage.ARABIC, card = true, trip = true, dates = false, band = true)

    @Test
    fun bandDatesLoadingEnglish() =
        stage("trip_detail_band_dates_loading", AppLanguage.ENGLISH, card = true, trip = true, dates = false, band = true)

    @Test
    fun bandLoadedArabic() = stage("trip_detail_band_loaded", AppLanguage.ARABIC, card = true, trip = true, dates = true, band = true)

    @Test
    fun bandLoadedEnglish() = stage("trip_detail_band_loaded", AppLanguage.ENGLISH, card = true, trip = true, dates = true, band = true)

    private fun stage(
        prefix: String,
        language: AppLanguage,
        card: Boolean,
        trip: Boolean,
        dates: Boolean,
        band: Boolean = false,
        pageFields: Boolean = false,
    ) = snap(prefix, language, scrollTo = if (band) TripSection.Availability else null) {
        // An older server's card has no deck: the trip's is cut to the two lines the page reserves. A card with the page's
        // fields (MV-M13) needs no such fitting, so the deck stays whole.
        val fitted: (TripDetailDto) -> TripDetailDto =
            if (pageFields) ({ it }) else ({ it.copy(deck = it.deck?.substringBefore('.')?.plus('.')) })
        serve(payload(language), departures(language), detail = fitted)
        if (card) cards = mapOf(SLUG to cardOf(language, pageFields))
        if (!trip) tripBySlug = { awaitCancellation() }
        if (!dates) departuresFor = { awaitCancellation() }
    }

    /**
     * The list card of the seeded trip as Home or search caches it: the trip's own title, place, price and duration, and
     * with [pageFields] (an MV-B3 server, MV-M13) its deck and bus/place cells too.
     */
    private fun cardOf(
        language: AppLanguage,
        pageFields: Boolean = false,
    ) = json.decodeFromString<Envelope<TripDetailDto>>(payload(language)).data.let { d ->
        trip(1, title = d.title, priceEgp = d.price.amount, durationLabel = d.durationLabel)
            .copy(slug = SLUG, subtitle = d.subtitle, durationMinutes = d.durationMinutes, rating = d.rating)
            .let { if (pageFields) it.copy(deck = d.deck, departure = d.departure, destination = d.destination) else it }
    }

    /*
     * MP-M1: the hero's heart at the top end, level with Back, with the trip's like count: liked (signed in), and a
     * guest's (never filled, the count still shown). Light and dark. An overlay on the photo's box: the page under it is
     * the loaded page's, unmoved.
     */

    @Test
    fun heartLikedArabic() = heart("trip_detail_heart_liked", AppLanguage.ARABIC, signedIn = true)

    @Test
    fun heartLikedEnglish() = heart("trip_detail_heart_liked", AppLanguage.ENGLISH, signedIn = true)

    @Test
    fun heartLikedDarkArabic() = heart("trip_detail_heart_liked_dark", AppLanguage.ARABIC, signedIn = true, theme = BahrThemeName.Dark)

    @Test
    fun heartLikedDarkEnglish() = heart("trip_detail_heart_liked_dark", AppLanguage.ENGLISH, signedIn = true, theme = BahrThemeName.Dark)

    @Test
    fun heartGuestArabic() = heart("trip_detail_heart_guest", AppLanguage.ARABIC, signedIn = false)

    @Test
    fun heartGuestEnglish() = heart("trip_detail_heart_guest", AppLanguage.ENGLISH, signedIn = false)

    private fun heart(
        prefix: String,
        language: AppLanguage,
        signedIn: Boolean,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) = snap(prefix, language, theme = theme, hearts = FixedHearts(signedIn)) {
        serve(payload(language), departures(language), detail = { it.copy(likeCount = LIKES, likedByMe = signedIn) })
    }

    /** Hearts as the store would have them, signed in or not; taps change nothing. */
    private class FixedHearts(
        signedIn: Boolean,
    ) : TripHearts {
        override val likes: StateFlow<TripLikes> = MutableStateFlow(TripLikes(signedIn = signedIn))

        override fun toggle(
            slug: String,
            shown: LikeState,
        ) = false

        override fun seen(
            slug: String,
            likeCount: Int,
            likedByMe: Boolean?,
        ) = Unit
    }

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun loadedArabic() =
        snap("trip_detail_loaded", AppLanguage.ARABIC) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun loadedEnglish() =
        snap("trip_detail_loaded", AppLanguage.ENGLISH) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    @Test
    fun soldOutSelectedArabic() =
        snap("trip_detail_sold_out_selected", AppLanguage.ARABIC, select = SOLD_OUT_ID) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Test
    fun soldOutSelectedEnglish() =
        snap("trip_detail_sold_out_selected", AppLanguage.ENGLISH, select = SOLD_OUT_ID) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /**
     * D3: one date of each kind side by side — cancelled (also full), open, sold out (picked, with
     * its notice) and booking closed — each drawn by its reason. Wide enough for all four cards.
     */
    @Config(qualifiers = "en-w480dp-h640dp-xhdpi")
    @Test
    fun dateReasonsArabic() =
        snap("trip_detail_date_reasons", AppLanguage.ARABIC, select = SOLD_OUT_ID) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures, dates = ::withEveryReason)
        }

    @Config(qualifiers = "en-w480dp-h640dp-xhdpi")
    @Test
    fun dateReasonsEnglish() =
        snap("trip_detail_date_reasons", AppLanguage.ENGLISH, select = SOLD_OUT_ID) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures, dates = ::withEveryReason)
        }

    /**
     * Multi-day (M4-B0b, M4-M1a review #7): every date is an overnight, so each card names the day it
     * is back, and the picked one too. Wide enough for the cards to show side by side.
     */
    @Config(qualifiers = "en-w480dp-h640dp-xhdpi")
    @Test
    fun multiDayDatesArabic() =
        snap("trip_detail_multi_day_dates", AppLanguage.ARABIC, select = OPEN_ID) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures, dates = ::overnight)
        }

    @Config(qualifiers = "en-w480dp-h640dp-xhdpi")
    @Test
    fun multiDayDatesEnglish() =
        snap("trip_detail_multi_day_dates", AppLanguage.ENGLISH, select = OPEN_ID) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures, dates = ::overnight)
        }

    /** One night away: back the day after each departure. */
    private fun overnight(dates: List<DepartureDto>) = dates.map { it.copy(returnDate = it.date.plus(1, DateTimeUnit.DAY)) }

    /*
     * MV-M11: the date cards at a larger text size (the Pixel runs 1.3; 1.5 for headroom). Robolectric's font scale goes
     * through the platform's non-linear text scaling, as on the phone. Every kind of card side by side (cancelled, a
     * two-digit count, a waiting list the device is on, sold out, booking closed) on November and December dates, whose
     * Arabic months the fixed-width card clipped ("21 نوفم"): each must show its whole date, and its status on the lines
     * it takes at the default size. Wide enough for the five cards at 1.5.
     */

    @Config(qualifiers = WIDE_BAND, fontScale = FONT_SCALE_PIXEL)
    @Test
    fun dateCardsFontScale13Arabic() = dateCardsAtFontScale("trip_detail_date_cards_fs13", AppLanguage.ARABIC)

    @Config(qualifiers = WIDE_BAND, fontScale = FONT_SCALE_PIXEL)
    @Test
    fun dateCardsFontScale13English() = dateCardsAtFontScale("trip_detail_date_cards_fs13", AppLanguage.ENGLISH)

    @Config(qualifiers = WIDE_BAND, fontScale = FONT_SCALE_LARGE)
    @Test
    fun dateCardsFontScale15Arabic() = dateCardsAtFontScale("trip_detail_date_cards_fs15", AppLanguage.ARABIC)

    @Config(qualifiers = WIDE_BAND, fontScale = FONT_SCALE_LARGE)
    @Test
    fun dateCardsFontScale15English() = dateCardsAtFontScale("trip_detail_date_cards_fs15", AppLanguage.ENGLISH)

    /*
     * MV-M13 (the MV-M11 follow-up): the itinerary at a larger text size on a phone, tall enough for every stop. The time
     * column widens with the text (as the date cards do), so "≈12:15" stays on one line at the Pixel's 1.3 and at 1.5.
     */

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi", fontScale = FONT_SCALE_PIXEL)
    @Test
    fun itineraryFontScale13Arabic() = itineraryAtFontScale("trip_detail_itinerary_fs13", AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi", fontScale = FONT_SCALE_PIXEL)
    @Test
    fun itineraryFontScale13English() = itineraryAtFontScale("trip_detail_itinerary_fs13", AppLanguage.ENGLISH)

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi", fontScale = FONT_SCALE_LARGE)
    @Test
    fun itineraryFontScale15Arabic() = itineraryAtFontScale("trip_detail_itinerary_fs15", AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi", fontScale = FONT_SCALE_LARGE)
    @Test
    fun itineraryFontScale15English() = itineraryAtFontScale("trip_detail_itinerary_fs15", AppLanguage.ENGLISH)

    private fun itineraryAtFontScale(
        prefix: String,
        language: AppLanguage,
    ) = snap(prefix, language, scrollTo = TripSection.Itinerary) {
        serve(payload(language), departures(language))
    }

    private fun dateCardsAtFontScale(
        prefix: String,
        language: AppLanguage,
    ) = snap(prefix, language, scrollTo = TripSection.Availability, waitlists = joinedOn(WINTER_JOINED_DATE)) {
        serve(payload(language), departures(language), dates = ::winterDatesOfEveryKind)
    }

    /** The seeded dates moved to November and December, one of each kind, and a fifth (sold out, not joined). */
    private fun winterDatesOfEveryKind(dates: List<DepartureDto>): List<DepartureDto> {
        val (cancelled, open, soldOut, closed) = dates
        return listOf(
            cancelled.copy(
                date = LocalDate.parse("2026-11-21"),
                seatsRemaining = 0,
                soldOut = true,
                bookable = false,
                unavailableReason = "CANCELLED",
            ),
            open.copy(date = LocalDate.parse("2026-11-28"), seatsRemaining = TWO_DIGIT_SEATS),
            soldOut.copy(date = LocalDate.parse(WINTER_JOINED_DATE)),
            soldOut.copy(id = EXTRA_SOLD_OUT_ID, date = LocalDate.parse("2026-12-12")),
            closed.copy(date = LocalDate.parse("2026-12-19"), bookable = false, unavailableReason = "CLOSED"),
        )
    }

    private fun joinedOn(date: String) =
        waitlistMemory(FakeWaitlistJoinsStore(listOf(storedJoin(departureId = SOLD_OUT_ID, tripSlug = SLUG, date = date))))

    /** M4-M13: the waiting-list form just opened under the sold-out date: nothing typed, the button disabled. */
    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFormEmptyArabic() =
        snap("trip_detail_waitlist_form_empty", AppLanguage.ARABIC, select = SOLD_OUT_ID, act = ::openForm) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFormEmptyEnglish() =
        snap("trip_detail_waitlist_form_empty", AppLanguage.ENGLISH, select = SOLD_OUT_ID, act = ::openForm) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /**
     * M4-M13: an address the server's rule refuses (no dot in the domain), the field not focused: it is
     * flagged inline with its sentence and the button stays disabled. The server's own refusal would
     * name no field.
     */
    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFormInvalidArabic() =
        snap("trip_detail_waitlist_form_invalid", AppLanguage.ARABIC, select = SOLD_OUT_ID, act = ::fillInvalid) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFormInvalidEnglish() =
        snap("trip_detail_waitlist_form_invalid", AppLanguage.ENGLISH, select = SOLD_OUT_ID, act = ::fillInvalid) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /** M2-M3: the waiting-list form open under the sold-out date, an email typed (M4-M13) and a party of two. */
    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFormArabic() =
        snap("trip_detail_waitlist_form", AppLanguage.ARABIC, select = SOLD_OUT_ID, act = ::fillForm) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFormEnglish() =
        snap("trip_detail_waitlist_form", AppLanguage.ENGLISH, select = SOLD_OUT_ID, act = ::fillForm) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /** The date's list is full (`RATE_LIMITED` on this endpoint): its own sentence, the form stays. */
    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFullArabic() =
        snap("trip_detail_waitlist_full", AppLanguage.ARABIC, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
            joinWaitlist = { _, _ -> AppResult.Failure(AppError.Api(ApiErrorCodes.RATE_LIMITED, "full", 429)) }
        }

    @Config(qualifiers = "en-w360dp-h900dp-xhdpi")
    @Test
    fun waitlistFullEnglish() =
        snap("trip_detail_waitlist_full", AppLanguage.ENGLISH, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
            joinWaitlist = { _, _ -> AppResult.Failure(AppError.Api(ApiErrorCodes.RATE_LIMITED, "full", 429)) }
        }

    /**
     * Joined: the confirmation, with the date and the email (left to right in Arabic too). Its tick
     * (BahrLottie's success check, M3-M8b) is shot still under reduced motion, in light and dark.
     */
    @Test
    fun waitlistJoinedArabic() = waitlistJoined(AppLanguage.ARABIC)

    @Test
    fun waitlistJoinedEnglish() = waitlistJoined(AppLanguage.ENGLISH)

    @Test
    fun waitlistJoinedDarkArabic() = waitlistJoined(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun waitlistJoinedDarkEnglish() = waitlistJoined(AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun waitlistJoined(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) = snap(
        if (theme == BahrThemeName.Dark) "trip_detail_waitlist_joined_dark" else "trip_detail_waitlist_joined",
        language,
        select = SOLD_OUT_ID,
        act = ::fillAndJoin,
        theme = theme,
        lottie = true,
    ) {
        val arabic = language == AppLanguage.ARABIC
        serve(
            if (arabic) TripDetailPayloads.arabic else TripDetailPayloads.english,
            if (arabic) TripDetailPayloads.arabicDepartures else TripDetailPayloads.englishDepartures,
        )
        joinWaitlist = { _, _ -> AppResult.Success(Unit) }
    }

    /**
     * M4-M5: the device joined this sold-out date's list on an earlier visit (a restart: a new view
     * model over the stored join). Its date card reads "Waiting list" and the notice says so with
     * the email it was left on, instead of offering Join again.
     */
    @Test
    fun waitlistRememberedArabic() =
        snap("trip_detail_waitlist_remembered", AppLanguage.ARABIC, select = SOLD_OUT_ID, waitlists = rememberedJoin(), lottie = true) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Test
    fun waitlistRememberedEnglish() =
        snap("trip_detail_waitlist_remembered", AppLanguage.ENGLISH, select = SOLD_OUT_ID, waitlists = rememberedJoin(), lottie = true) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /**
     * MV-M9: every date full (the seeded white-desert-overnight's shape). The button reads a disabled "Sold out" with
     * no date picked; picking one shows its notice with "Join the waiting list" (no other date to point to).
     */
    @Test
    fun allSoldOutArabic() =
        snap("trip_detail_all_sold_out", AppLanguage.ARABIC, select = SOLD_OUT_ID) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures, dates = ::allFull)
        }

    @Test
    fun allSoldOutEnglish() =
        snap("trip_detail_all_sold_out", AppLanguage.ENGLISH, select = SOLD_OUT_ID) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures, dates = ::allFull)
        }

    private fun allFull(dates: List<DepartureDto>) =
        dates.map { it.copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "SOLD_OUT") }

    private fun rememberedJoin() =
        waitlistMemory(FakeWaitlistJoinsStore(listOf(storedJoin(departureId = SOLD_OUT_ID, tripSlug = SLUG, date = "2026-10-24"))))

    /**
     * `CONFLICT`: seats opened up on the date while it showed as full. The dates are read again (here
     * the re-read answers it bookable), the notice goes and the band says so; the button reads Continue.
     */
    @Test
    fun waitlistSeatsOpenedArabic() =
        snap("trip_detail_waitlist_seats_opened", AppLanguage.ARABIC, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            reopenOnConflict(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Test
    fun waitlistSeatsOpenedEnglish() =
        snap("trip_detail_waitlist_seats_opened", AppLanguage.ENGLISH, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            reopenOnConflict(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /**
     * A tap on the "a place opened" push (M3-M5): the page opens on the push's date, which the fresh dates show
     * bookable, and the band says a place opened; the button reads Continue.
     */
    @Test
    fun placeOpenedArabic() =
        snap("trip_detail_place_opened", AppLanguage.ARABIC, link = placeOpened(), scrollTo = TripSection.Availability) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Test
    fun placeOpenedEnglish() =
        snap("trip_detail_place_opened", AppLanguage.ENGLISH, link = placeOpened(), scrollTo = TripSection.Availability) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /** The second Saturday, bookable in the payload. */
    private fun placeOpened() = TripLink(LocalDate.parse("2026-10-17"), placeOpened = true)

    /**
     * `DEPARTURE_NOT_OPEN`: the host cancelled the date while it showed as full. The re-read answers it
     * cancelled, the selection and the notice go, and the band says the date can't be booked.
     */
    @Test
    fun waitlistDateClosedArabic() =
        snap("trip_detail_waitlist_date_closed", AppLanguage.ARABIC, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            cancelOnNotOpen(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Test
    fun waitlistDateClosedEnglish() =
        snap("trip_detail_waitlist_date_closed", AppLanguage.ENGLISH, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            cancelOnNotOpen(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    private fun FakeTripDetailRepository.cancelOnNotOpen(
        trip: String,
        departures: String,
    ) {
        var cancelled = false
        serve(trip, departures)
        val first = departuresFor
        departuresFor = { slug ->
            val read = first(slug)
            if (cancelled && read is AppResult.Success) AppResult.Success(read.data.map(::cancel)) else read
        }
        joinWaitlist = { _, _ ->
            cancelled = true
            AppResult.Failure(AppError.Api(ApiErrorCodes.DEPARTURE_NOT_OPEN, "cancelled", 409))
        }
    }

    private fun cancel(d: DepartureDto) = if (d.id == SOLD_OUT_ID) d.copy(unavailableReason = "CANCELLED") else d

    private fun FakeTripDetailRepository.reopenOnConflict(
        trip: String,
        departures: String,
    ) {
        var reopened = false
        serve(trip, departures)
        val first = departuresFor
        departuresFor = { slug ->
            val read = first(slug)
            if (reopened && read is AppResult.Success) AppResult.Success(read.data.map(::reopen)) else read
        }
        joinWaitlist = { _, _ ->
            reopened = true
            AppResult.Failure(AppError.Api(ApiErrorCodes.CONFLICT, "has seats", 409))
        }
    }

    private fun reopen(d: DepartureDto) =
        if (d.id == SOLD_OUT_ID) d.copy(seatsRemaining = 2, soldOut = false, bookable = true, unavailableReason = null) else d

    private fun openForm(vm: TripDetailViewModel) = vm.openWaitlist()

    private fun fillForm(vm: TripDetailViewModel) {
        vm.openWaitlist()
        vm.setWaitlistEmail("sara@example.com")
        vm.increaseWaitlistParty()
    }

    private fun fillInvalid(vm: TripDetailViewModel) {
        vm.openWaitlist()
        vm.setWaitlistEmail("sara@localhost")
    }

    private fun fillAndJoin(vm: TripDetailViewModel) {
        fillForm(vm)
        vm.joinWaitlist()
    }

    private fun withEveryReason(dates: List<DepartureDto>) =
        dates.mapIndexed { i, d ->
            when (i) {
                0 -> d.copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "CANCELLED")
                dates.lastIndex -> d.copy(bookable = false, unavailableReason = "CLOSED")
                else -> d
            }
        }

    /**
     * M4-M10: with the host page wired, the host card's row is tappable, with a forward arrow (pointing
     * left in Arabic). Scrolled to the host card. M4-M15: the server sends the host's name in the page's
     * language (M4-B10), so the Arabic card reads "أشرف البحر".
     */
    @Test
    fun hostCardArabic() =
        snap("trip_detail_host_card", AppLanguage.ARABIC, onHostClick = {}, scrollTo = TripSection.Host) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Test
    fun hostCardEnglish() =
        snap("trip_detail_host_card", AppLanguage.ENGLISH, onHostClick = {}, scrollTo = TripSection.Host) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

    /**
     * M4-M15: a host with no English name gets the Arabic one on the English page (M4-B10's fallback):
     * the RTL name sits in the LTR card without pulling the role or the arrow out of place.
     */
    @Test
    fun hostCardArabicNameEnglish() =
        snap("trip_detail_host_card_arabic_name", AppLanguage.ENGLISH, onHostClick = {}, scrollTo = TripSection.Host) {
            serve(
                TripDetailPayloads.english,
                TripDetailPayloads.englishDepartures,
                detail = { it.copy(host = it.host?.copy(name = ARABIC_HOST_NAME)) },
            )
        }

    /**
     * M4-M11: the reviews with their stars, and "See all reviews" under them. M4-M15: the seed's derived
     * numbers (M4-B10): burullus-dawn counts 5 review rows and previews the server's full 3, so there is
     * more to see. The link carries no number. Taller than the class default: three review cards and the
     * link under them.
     */
    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun reviewsSeeAllArabic() = reviewsSeeAll(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun reviewsSeeAllEnglish() = reviewsSeeAll(AppLanguage.ENGLISH)

    private fun reviewsSeeAll(language: AppLanguage) {
        snap("trip_detail_reviews_see_all", language, onSeeAllReviews = {}, scrollTo = TripSection.Reviews) {
            serve(payload(language), departures(language))
        }
        compose.onNodeWithTag(SEE_ALL_REVIEWS_TAG).assertIsDisplayed()
    }

    /**
     * M4-M12/M4-M15: a trip whose 3 review rows are all in the preview (the count equals what is shown,
     * as the server now derives it): no "See all reviews", which would open the same three again.
     */
    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun reviewsAllShownArabic() = reviewsAllShown(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h1000dp-xhdpi")
    @Test
    fun reviewsAllShownEnglish() = reviewsAllShown(AppLanguage.ENGLISH)

    private fun reviewsAllShown(language: AppLanguage) {
        snap("trip_detail_reviews_all_shown", language, onSeeAllReviews = {}, scrollTo = TripSection.Reviews) {
            serve(payload(language), departures(language), detail = ::withEveryReviewShown)
        }
        compose.onNodeWithTag(SEE_ALL_REVIEWS_TAG).assertDoesNotExist()
    }

    /**
     * M4-M15: a trip with no reviews, as M4-B10 serves it (no `rating`, `reviews` counting 0 with no
     * items): no rating pill beside the location, no reviews section, no "See all reviews". The whole
     * page, so both places a pill could be are in the golden.
     */
    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun noReviewsArabic() = noReviews(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun noReviewsEnglish() = noReviews(AppLanguage.ENGLISH)

    private fun noReviews(language: AppLanguage) {
        snap("trip_detail_no_reviews", language, onSeeAllReviews = {}) {
            serve(payload(language), departures(language), detail = { it.copy(rating = null, reviews = ReviewsDto(count = 0)) })
        }
        // The average is the only place "4.8" appears on this page: neither pill is drawn.
        compose.onNodeWithText(DERIVED_AVERAGE, substring = true).assertDoesNotExist()
        compose.onNodeWithTag(SEE_ALL_REVIEWS_TAG).assertDoesNotExist()
    }

    /** The trip's reviews counted as exactly the ones previewed. */
    private fun withEveryReviewShown(trip: TripDetailDto) =
        trip.copy(
            rating = trip.rating?.copy(count = trip.reviews?.items?.size),
            reviews = trip.reviews?.let { r -> r.copy(count = r.items.size) },
        )

    private fun payload(language: AppLanguage) =
        if (language == AppLanguage.ARABIC) TripDetailPayloads.arabic else TripDetailPayloads.english

    private fun departures(language: AppLanguage) =
        if (language == AppLanguage.ARABIC) TripDetailPayloads.arabicDepartures else TripDetailPayloads.englishDepartures

    /**
     * The photo counter ("1 / 3") over a three-photo gallery, which must read left to right in Arabic
     * too (M1-M1 review #1). The URLs point at a closed local port, so Coil draws nothing and the
     * golden does not depend on the network; the counter and the ground still render.
     */
    @Test
    fun galleryArabic() =
        snap("trip_detail_gallery", AppLanguage.ARABIC) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures, photos = UNREACHABLE_PHOTOS)
        }

    @Test
    fun galleryEnglish() =
        snap("trip_detail_gallery", AppLanguage.ENGLISH) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures, photos = UNREACHABLE_PHOTOS)
        }

    /**
     * M5-M2 (MV-M3): a video first in the gallery (`media`), drawn as its poster with the play badge and its running time
     * ("0:08", left to right in Arabic too); the photo counter counts it with the photos. Unreachable URLs, as above.
     */
    @Test
    fun galleryVideoArabic() =
        snap("trip_detail_gallery_video", AppLanguage.ARABIC) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures, detail = ::withVideoFirst)
        }

    @Test
    fun galleryVideoEnglish() =
        snap("trip_detail_gallery_video", AppLanguage.ENGLISH) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures, detail = ::withVideoFirst)
        }

    private fun withVideoFirst(trip: TripDetailDto) =
        trip.copy(
            media =
                listOf(
                    TripMediaDto(
                        kind = "video",
                        url = "http://127.0.0.1:9/dawn.mp4",
                        width = 640,
                        height = 360,
                        alt = "Video: dawn on the lake",
                        durationSeconds = 8,
                        poster = UNREACHABLE_PHOTOS.first(),
                    ),
                ) + UNREACHABLE_PHOTOS.drop(1).map { TripMediaDto(kind = "photo", url = it.url, width = it.width, height = it.height) },
        )

    private fun FakeTripDetailRepository.serve(
        trip: String,
        departures: String,
        photos: List<ImageDto> = emptyList(),
        dates: (List<DepartureDto>) -> List<DepartureDto> = { it },
        detail: (TripDetailDto) -> TripDetailDto = { it },
    ) {
        val detail = detail(withoutPhotos(json.decodeFromString<Envelope<TripDetailDto>>(trip).data).copy(gallery = photos))
        val dates = dates(json.decodeFromString<Envelope<List<DepartureDto>>>(departures).data)
        tripBySlug = { AppResult.Success(detail) }
        departuresFor = { AppResult.Success(dates) }
    }

    private fun withoutPhotos(trip: TripDetailDto) =
        trip.copy(heroImage = null, cardImage = null, gallery = emptyList(), og = null, host = trip.host?.copy(avatar = null))

    private fun card(
        title: String,
        subtitle: String,
        badge: String,
    ) = trip(1, title = title, priceEgp = 450, badge = BadgeDto(badge, "primary"))
        .copy(slug = SLUG, subtitle = subtitle, rating = RatingDto(4.8, 5))

    private fun snap(
        prefix: String,
        language: AppLanguage,
        select: String? = null,
        act: (TripDetailViewModel) -> Unit = {},
        waitlists: WaitlistMemory = waitlistMemory(),
        onHostClick: ((HostDto) -> Unit)? = null,
        onSeeAllReviews: ((String) -> Unit)? = null,
        scrollTo: TripSection? = null,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        // The shot holds a BahrLottie (the joined tick): drawn still under reduced motion, once its file has loaded.
        lottie: Boolean = false,
        link: TripLink = TripLink(),
        hearts: TripHearts = TripHearts.None,
        stub: FakeTripDetailRepository.() -> Unit,
    ) {
        val viewModel = TripDetailViewModel(SLUG, FakeTripDetailRepository().apply(stub), waitlists, link, hearts)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    theme = theme,
                    reducedMotion = lottie,
                ) {
                    // The app's tab Scaffold sets onBackground as the content colour; a bare test root does not, so dark
                    // shots would draw uncoloured text black on the dark page (light goldens keep their recorded black).
                    CompositionLocalProvider(
                        LocalContentColor provides
                            if (theme == BahrThemeName.Dark) MaterialTheme.colorScheme.onBackground else LocalContentColor.current,
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            TripDetailScreen(slug = SLUG, onBack = {}, onContinue = {
                                _,
                                _,
                                ->
                            }, onHostClick = onHostClick, onSeeAllReviews = onSeeAllReviews, viewModel = viewModel)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        if (select != null) {
            compose.runOnUiThread {
                viewModel.selectDeparture(select)
                act(viewModel)
            }
            compose.waitForIdle()
            compose.onNodeWithTag(TRIP_PAGE_TAG).performScrollToKey(TripSection.Availability.name)
            compose.waitForIdle()
            // Let the button's colour change to the disabled fill finish (the clock is paused).
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            // Again, now that the band has grown by the notice (and the waiting list): the first
            // scroll measured the band before they were drawn and stopped short of them.
            compose.onNodeWithTag(TRIP_PAGE_TAG).performScrollToKey(TripSection.Availability.name)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MS)
        }
        if (lottie) {
            // Read off the main thread; with the clock paused, frames carry the parsed file in.
            compose.waitUntil(LOTTIE_LOAD_TIMEOUT_MS) {
                compose.mainClock.advanceTimeByFrame()
                // Unmerged: the joined note merges its children into one announcement.
                compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
        }
        if (scrollTo != null) {
            compose.onNodeWithTag(TRIP_PAGE_TAG).performScrollToKey(scrollTo.name)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MS)
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    @kotlinx.serialization.Serializable
    private data class Envelope<T>(
        val data: T,
    )

    private companion object {
        /** A two-digit count, so the heart's pill is at its usual width. */
        const val LIKES = 12

        const val SLUG = "burullus-dawn"
        const val SETTLE_MS = 1_000L
        const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
        val UNREACHABLE_PHOTOS = (1..3).map { ImageDto(url = "http://127.0.0.1:9/photo-$it.png", width = 900, height = 560) }
        const val SOLD_OUT_ID = "0199c3a0-5eed-7000-8000-000000000603"
        const val OPEN_ID = "0199c3a0-5eed-7000-8000-000000000601"
        const val EXTRA_SOLD_OUT_ID = "0199c3a0-5eed-7000-8000-000000000605"
        const val WINTER_JOINED_DATE = "2026-12-05"
        const val TWO_DIGIT_SEATS = 18
        const val WIDE_BAND = "en-w900dp-h640dp-xhdpi"
        const val FONT_SCALE_PIXEL = 1.3f
        const val FONT_SCALE_LARGE = 1.5f
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** burullus-dawn's average as the server derives it from its review rows (M4-B10), as shown. */
private const val DERIVED_AVERAGE = "4.8"

/** The seed's Arabic name for the Burullus host (M4-B10), served on an English page when the English one is blank. */
private const val ARABIC_HOST_NAME = "أشرف البحر"
