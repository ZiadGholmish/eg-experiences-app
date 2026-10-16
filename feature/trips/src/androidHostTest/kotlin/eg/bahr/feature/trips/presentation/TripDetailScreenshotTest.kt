package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.RatingDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.core.testing.storedJoin
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripDetailPayloads
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.HostDto
import eg.bahr.feature.trips.model.ReviewsDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.presentation.components.SEE_ALL_REVIEWS_TAG
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.DateTimeUnit
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
 * Record: `./gradlew :feature:trips:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
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

    /** Joined: the confirmation, with the date and the email (left to right in Arabic too). */
    @Test
    fun waitlistJoinedArabic() =
        snap("trip_detail_waitlist_joined", AppLanguage.ARABIC, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
            joinWaitlist = { _, _ -> AppResult.Success(Unit) }
        }

    @Test
    fun waitlistJoinedEnglish() =
        snap("trip_detail_waitlist_joined", AppLanguage.ENGLISH, select = SOLD_OUT_ID, act = ::fillAndJoin) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
            joinWaitlist = { _, _ -> AppResult.Success(Unit) }
        }

    /**
     * M4-M5: the device joined this sold-out date's list on an earlier visit (a restart: a new view
     * model over the stored join). Its date card reads "Waiting list" and the notice says so with
     * the email it was left on, instead of offering Join again.
     */
    @Test
    fun waitlistRememberedArabic() =
        snap("trip_detail_waitlist_remembered", AppLanguage.ARABIC, select = SOLD_OUT_ID, waitlists = rememberedJoin()) {
            serve(TripDetailPayloads.arabic, TripDetailPayloads.arabicDepartures)
        }

    @Test
    fun waitlistRememberedEnglish() =
        snap("trip_detail_waitlist_remembered", AppLanguage.ENGLISH, select = SOLD_OUT_ID, waitlists = rememberedJoin()) {
            serve(TripDetailPayloads.english, TripDetailPayloads.englishDepartures)
        }

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

    private fun FakeTripRepository.cancelOnNotOpen(
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

    private fun FakeTripRepository.reopenOnConflict(
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

    private fun FakeTripRepository.serve(
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
        stub: FakeTripRepository.() -> Unit,
    ) {
        val viewModel = TripDetailViewModel(SLUG, FakeTripRepository().apply(stub), waitlists)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
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
        const val SLUG = "burullus-dawn"
        const val SETTLE_MS = 1_000L
        val UNREACHABLE_PHOTOS = (1..3).map { ImageDto(url = "http://127.0.0.1:9/photo-$it.png", width = 900, height = 560) }
        const val SOLD_OUT_ID = "0199c3a0-5eed-7000-8000-000000000603"
        const val OPEN_ID = "0199c3a0-5eed-7000-8000-000000000601"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** burullus-dawn's average as the server derives it from its review rows (M4-B10), as shown. */
private const val DERIVED_AVERAGE = "4.8"

/** The seed's Arabic name for the Burullus host (M4-B10), served on an English page when the English one is blank. */
private const val ARABIC_HOST_NAME = "أشرف البحر"
