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
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripDetailPayloads
import eg.bahr.feature.trips.data.TripFixtures.trip
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
        stub: FakeTripRepository.() -> Unit,
    ) {
        val viewModel = TripDetailViewModel(SLUG, FakeTripRepository().apply(stub))
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
            compose.runOnUiThread { viewModel.selectDeparture(select) }
            compose.onNodeWithTag(TRIP_PAGE_TAG).performScrollToKey(TripSection.Availability.name)
            compose.waitForIdle()
            // Let the button's colour change to the disabled fill finish (the clock is paused).
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
