package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.FakeWaitlistJoinsStore
import eg.bahr.feature.trips.data.TripDetailPayloads
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.data.storedJoin
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.BadgeDto
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.ImageDto
import eg.bahr.feature.trips.model.RatingDto
import eg.bahr.feature.trips.model.TripDetailDto
import kotlinx.coroutines.awaitCancellation
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
 * Record: `./gradlew :feature:trips:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
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

    /** M2-M3: the waiting-list form open under the sold-out date, a number typed and a party of two. */
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

    /** Joined: the confirmation, with the date and the number (left to right in Arabic too). */
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
     * model over the stored join). Its date card reads "On the waiting list" and the notice says so with
     * the phone it was left on, instead of offering Join again.
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

    private fun fillForm(vm: TripDetailViewModel) {
        vm.openWaitlist()
        vm.setWaitlistPhone("+201001234567")
        vm.increaseWaitlistParty()
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
    ) {
        val detail = withoutPhotos(json.decodeFromString<Envelope<TripDetailDto>>(trip).data).copy(gallery = photos)
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
        .copy(slug = SLUG, subtitle = subtitle, rating = RatingDto(4.8, 37))

    private fun snap(
        prefix: String,
        language: AppLanguage,
        select: String? = null,
        act: (TripDetailViewModel) -> Unit = {},
        waitlists: WaitlistMemory = waitlistMemory(),
        stub: FakeTripRepository.() -> Unit,
    ) {
        val viewModel = TripDetailViewModel(SLUG, FakeTripRepository().apply(stub), waitlists)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        TripDetailScreen(slug = SLUG, onBack = {}, onContinue = { _, _ -> }, viewModel = viewModel)
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
        val json = Json { ignoreUnknownKeys = true }
    }
}
