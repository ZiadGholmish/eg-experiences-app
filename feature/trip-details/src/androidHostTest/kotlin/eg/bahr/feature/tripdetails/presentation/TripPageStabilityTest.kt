package eg.bahr.feature.tripdetails.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.model.RatingDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.feature.tripdetails.data.FakeTripDetailRepository
import eg.bahr.feature.tripdetails.data.TripDetailPayloads
import eg.bahr.feature.tripdetails.data.TripFixtures.trip
import eg.bahr.feature.tripdetails.data.waitlistMemory
import eg.bahr.feature.tripdetails.model.DepartureDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.presentation.components.TRIP_BAR_PRICE_TAG
import eg.bahr.feature.tripdetails.presentation.components.TRIP_CTA_TAG
import eg.bahr.feature.tripdetails.presentation.components.TRIP_DATE_ROW_TAG
import eg.bahr.feature.tripdetails.presentation.components.TRIP_FACTS_TAG
import eg.bahr.feature.tripdetails.presentation.components.TRIP_HERO_TAG
import eg.bahr.feature.tripdetails.presentation.components.TRIP_PRICE_FACT_TAG
import eg.bahr.feature.tripdetails.presentation.components.TRIP_TITLE_BLOCK_TAG
import eg.bahr.feature.tripdetails.presentation.components.TRIP_TITLE_TAG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * MV-M7: the trip page does not jump while it loads. One composition is driven through the loading stages (the list
 * card only → the trip landed → the live dates landed) by holding the fake's two reads open, and after each stage the
 * hero, the title, the facts grid's price cell and the sticky bar's price must sit exactly where they sat before. The
 * scrolled case is the "scroll never jumps" rule: with the facts scrolled to the top, landing data must not move them.
 *
 * Vertical edges are compared: a shimmer bar is narrower than the text that replaces it, which is not a jump. A card
 * from an older server has no deck, so the page reserves two lines for it; those trips carry a two-line deck (the
 * seeded deck's first sentence), and a deck of another length moves what is under the title by the difference. Since
 * MV-M13 a card from an MV-B3 server carries the deck and the bus/place cells: then the whole title block and the
 * whole facts grid hold still whatever the deck's length, or with none.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class TripPageStabilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val trip = CompletableDeferred<AppResult<TripDetailDto>>()
    private val dates = CompletableDeferred<AppResult<List<DepartureDto>>>()

    /** How many times the page asked to continue to the booking. */
    private var continued = 0

    @Before
    fun pauseClock() {
        // Shimmers animate forever; with the clock paused the composition can go idle.
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun fromCardNothingMovesArabic() = fromCardNothingMoves(AppLanguage.ARABIC)

    @Test
    fun fromCardNothingMovesEnglish() = fromCardNothingMoves(AppLanguage.ENGLISH)

    private fun fromCardNothingMoves(language: AppLanguage) {
        open(language, withCard = true)
        val cardOnly = bounds(TRIP_HERO_TAG, TRIP_TITLE_TAG, TRIP_PRICE_FACT_TAG, TRIP_BAR_PRICE_TAG)

        land(trip, AppResult.Success(detail(language).withTwoLineDeck()))
        assertEquals(cardOnly, bounds(TRIP_HERO_TAG, TRIP_TITLE_TAG, TRIP_PRICE_FACT_TAG, TRIP_BAR_PRICE_TAG), "trip landed")

        land(dates, AppResult.Success(departures(language)))
        assertEquals(cardOnly, bounds(TRIP_HERO_TAG, TRIP_TITLE_TAG, TRIP_PRICE_FACT_TAG, TRIP_BAR_PRICE_TAG), "dates landed")
    }

    /**
     * A cold open (a shared link: no card) has nothing to draw as text, so the title block's lines are guesses; the hero
     * and the sticky bar must still hold still.
     */
    @Test
    fun coldOpenHeroAndBarHoldStill() {
        open(AppLanguage.ARABIC, withCard = false)
        val cold = bounds(TRIP_HERO_TAG, TRIP_BAR_PRICE_TAG)

        land(trip, AppResult.Success(detail(AppLanguage.ARABIC)))
        land(dates, AppResult.Success(departures(AppLanguage.ARABIC)))
        assertEquals(cold, bounds(TRIP_HERO_TAG, TRIP_BAR_PRICE_TAG))
    }

    /** The reader has scrolled the facts to the top before anything landed: they stay exactly there. */
    @Test
    fun scrolledPageStaysPut() {
        open(AppLanguage.ARABIC, withCard = true)
        compose.onNodeWithTag(TRIP_PAGE_TAG).performScrollToKey(TripSection.Facts.name)
        compose.waitForIdle()
        val scrolled = bounds(TRIP_PRICE_FACT_TAG)

        land(trip, AppResult.Success(detail(AppLanguage.ARABIC).withTwoLineDeck()))
        assertEquals(scrolled, bounds(TRIP_PRICE_FACT_TAG), "trip landed")

        land(dates, AppResult.Success(departures(AppLanguage.ARABIC)))
        assertEquals(scrolled, bounds(TRIP_PRICE_FACT_TAG), "dates landed")
    }

    /**
     * A card from an older server (no deck, no places): the deck's length is not known until the trip lands, so a
     * three-line deck, or none at all, moves the facts under the title by the difference from the two lines reserved.
     * What is above it holds still.
     */
    @Test
    fun aLongerDeckMovesOnlyWhatIsUnderTheTitle() = deckOfOtherLength { it }

    @Test
    fun noDeckMovesOnlyWhatIsUnderTheTitle() = deckOfOtherLength { it.copy(deck = null) }

    private fun deckOfOtherLength(deck: (TripDetailDto) -> TripDetailDto) {
        open(AppLanguage.ARABIC, withCard = true)
        val cardOnly = bounds(TRIP_HERO_TAG, TRIP_TITLE_TAG, TRIP_BAR_PRICE_TAG)

        land(trip, AppResult.Success(deck(detail(AppLanguage.ARABIC))))
        land(dates, AppResult.Success(departures(AppLanguage.ARABIC)))
        assertEquals(cardOnly, bounds(TRIP_HERO_TAG, TRIP_TITLE_TAG, TRIP_BAR_PRICE_TAG))
    }

    /*
     * MV-M13: a card from an MV-B3 server carries the deck and the bus/place cells (`TripCard.deck/departure/
     * destination`), so the title block and the facts grid are drawn as text at their final size from the first frame,
     * and nothing in them, or under them, moves when the trip lands: the seeded deck in full (three lines on this
     * phone), and a trip with no deck at all.
     */

    @Test
    fun cardWithPageFieldsNothingMovesArabic() = cardWithPageFieldsNothingMoves(AppLanguage.ARABIC) { it }

    @Test
    fun cardWithPageFieldsNothingMovesEnglish() = cardWithPageFieldsNothingMoves(AppLanguage.ENGLISH) { it }

    @Test
    fun cardWithPageFieldsAndNoDeckNothingMoves() = cardWithPageFieldsNothingMoves(AppLanguage.ARABIC) { it.copy(deck = null) }

    private fun cardWithPageFieldsNothingMoves(
        language: AppLanguage,
        trip: (TripDetailDto) -> TripDetailDto,
    ) {
        val detail = trip(detail(language))
        open(language, withCard = true, cardFrom = detail)
        compose.onNodeWithTag(TRIP_TITLE_BLOCK_TAG).assertIsDisplayed()
        val tags = arrayOf(TRIP_HERO_TAG, TRIP_TITLE_BLOCK_TAG, TRIP_FACTS_TAG, TRIP_PRICE_FACT_TAG, TRIP_BAR_PRICE_TAG)
        val cardOnly = bounds(*tags)
        detail.deck?.let { compose.onNodeWithText(it).assertIsDisplayed() }

        land(this.trip, AppResult.Success(detail))
        assertEquals(cardOnly, bounds(*tags), "trip landed")

        land(dates, AppResult.Success(departures(language)))
        assertEquals(cardOnly, bounds(*tags), "dates landed")
    }

    /**
     * Opened on a linked date (M3-M5) whose live dates answer before the trip: the page waits for the trip before it
     * brings the dates into view, or the sections the trip brings above the band would push it away again.
     */
    @Test
    fun linkedDateIsRevealedOnceTheTripHasLanded() {
        open(AppLanguage.ARABIC, withCard = true, link = TripLink(date = linkedDate()))
        land(dates, AppResult.Success(departures(AppLanguage.ARABIC)))
        settle()
        assertEquals(0f, bounds(TRIP_HERO_TAG).getValue(TRIP_HERO_TAG).first, "nothing moves before the trip")

        land(trip, AppResult.Success(detail(AppLanguage.ARABIC)))
        settle()
        compose.onNodeWithTag(TRIP_DATE_ROW_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    /** The reader scrolled before the dates landed: the page is theirs, and the reveal does not move it. */
    @Test
    fun linkedDateDoesNotMoveAPageTheReaderHasScrolled() {
        open(AppLanguage.ARABIC, withCard = true, link = TripLink(date = linkedDate()))
        compose.onNodeWithTag(TRIP_PAGE_TAG).performTouchInput { swipeUp(startY = centerY, endY = centerY - SWIPE_PX) }
        settle()
        val scrolled = bounds(TRIP_PRICE_FACT_TAG)

        land(trip, AppResult.Success(detail(AppLanguage.ARABIC).withTwoLineDeck()))
        land(dates, AppResult.Success(departures(AppLanguage.ARABIC)))
        settle()
        assertEquals(scrolled, bounds(TRIP_PRICE_FACT_TAG))
    }

    /**
     * MV-M9: a trip whose card already says it is full shows a disabled "Sold out" from the first frame, and the button
     * keeps its place and size through the loading stages (only its fill would change).
     */
    @Test
    fun soldOutFromTheFirstFrameArabic() = soldOutFromTheFirstFrame(AppLanguage.ARABIC)

    @Test
    fun soldOutFromTheFirstFrameEnglish() = soldOutFromTheFirstFrame(AppLanguage.ENGLISH)

    private fun soldOutFromTheFirstFrame(language: AppLanguage) {
        open(language, withCard = true, cardSoldOut = true)
        val soldOut = if (language == AppLanguage.ARABIC) "كامل العدد" else "Sold out"
        val cta = compose.onNodeWithTag(TRIP_CTA_TAG)
        cta.assertIsNotEnabled().assertTextEquals(soldOut)
        val first = bounds(TRIP_CTA_TAG, TRIP_BAR_PRICE_TAG)

        land(trip, AppResult.Success(detail(language).let { it.copy(dates = full(it.dates)) }))
        cta.assertIsNotEnabled().assertTextEquals(soldOut)
        assertEquals(first, bounds(TRIP_CTA_TAG, TRIP_BAR_PRICE_TAG), "trip landed")

        land(dates, AppResult.Success(full(departures(language))))
        cta.assertIsNotEnabled().assertTextEquals(soldOut)
        assertEquals(first, bounds(TRIP_CTA_TAG, TRIP_BAR_PRICE_TAG), "dates landed")
    }

    /** MV-M9: before the dates are known, "Choose a date" is disabled at the size it will have once enabled. */
    @Test
    fun chooseADateWaitsForTheDatesWithoutMoving() {
        open(AppLanguage.ARABIC, withCard = true)
        val cta = compose.onNodeWithTag(TRIP_CTA_TAG)
        cta.assertIsNotEnabled()
        val loading = bounds(TRIP_CTA_TAG)

        land(trip, AppResult.Success(detail(AppLanguage.ARABIC)))
        cta.assertIsNotEnabled()

        land(dates, AppResult.Success(departures(AppLanguage.ARABIC)))
        cta.assertIsEnabled()
        assertEquals(loading, bounds(TRIP_CTA_TAG))
    }

    /** MV-M9: a tap on the disabled "Sold out" neither continues nor scrolls the page. */
    @Test
    fun tappingSoldOutDoesNothing() {
        open(AppLanguage.ARABIC, withCard = true, cardSoldOut = true)
        land(trip, AppResult.Success(detail(AppLanguage.ARABIC).let { it.copy(dates = full(it.dates)) }))
        land(dates, AppResult.Success(full(departures(AppLanguage.ARABIC))))
        val before = bounds(TRIP_HERO_TAG, TRIP_TITLE_TAG)

        compose.onNodeWithTag(TRIP_CTA_TAG).performClick()
        settle()

        assertEquals(0, continued)
        assertEquals(before, bounds(TRIP_HERO_TAG, TRIP_TITLE_TAG), "the page did not scroll to the dates")
    }

    /** The first date the page can be opened on: bookable or sold out. */
    private fun linkedDate() = departures(AppLanguage.ARABIC).first { it.isSelectable() }.date

    /** Lets an animated scroll (or a fling) run to its end on the paused clock. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun dateRowKeepsItsHeightArabic() = dateRowKeepsItsHeight(AppLanguage.ARABIC)

    /** English wraps "6 seats left" on the narrow card: the skeleton card's seats line must wrap the same. */
    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi")
    @Test
    fun dateRowKeepsItsHeightEnglish() = dateRowKeepsItsHeight(AppLanguage.ENGLISH)

    /*
     * MV-M11: at a larger text size the date cards widen and grow (the Pixel runs 1.3); the skeleton cards must grow with
     * them, or the row would jump when the dates land.
     */

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi", fontScale = FONT_SCALE_PIXEL)
    @Test
    fun dateRowKeepsItsHeightAtFontScale13Arabic() = dateRowKeepsItsHeight(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi", fontScale = FONT_SCALE_PIXEL)
    @Test
    fun dateRowKeepsItsHeightAtFontScale13English() = dateRowKeepsItsHeight(AppLanguage.ENGLISH)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi", fontScale = FONT_SCALE_LARGE)
    @Test
    fun dateRowKeepsItsHeightAtFontScale15Arabic() = dateRowKeepsItsHeight(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h3200dp-xhdpi", fontScale = FONT_SCALE_LARGE)
    @Test
    fun dateRowKeepsItsHeightAtFontScale15English() = dateRowKeepsItsHeight(AppLanguage.ENGLISH)

    /** Skeleton cards → the trip's own dates with their seat counts shimmering → the live dates: one height throughout. */
    private fun dateRowKeepsItsHeight(language: AppLanguage) {
        open(language, withCard = true)
        val skeleton = height(TRIP_DATE_ROW_TAG)

        land(trip, AppResult.Success(detail(language)))
        assertEquals(skeleton, height(TRIP_DATE_ROW_TAG), "trip landed, seats loading")

        land(dates, AppResult.Success(departures(language)))
        assertEquals(skeleton, height(TRIP_DATE_ROW_TAG), "dates landed")
    }

    private fun height(tag: String): Float {
        // The band is the page's last section: bring it into view, where it is laid out.
        compose.onNodeWithTag(TRIP_PAGE_TAG).performScrollToKey(TripSection.Availability.name)
        compose.waitForIdle()
        return compose
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.height
    }

    private fun open(
        language: AppLanguage,
        withCard: Boolean,
        link: TripLink = TripLink(),
        cardSoldOut: Boolean = false,
        cardFrom: TripDetailDto? = null,
    ) {
        val repository =
            FakeTripDetailRepository(
                tripBySlug = { trip.await() },
                departuresFor = { dates.await() },
                cards = if (withCard) mapOf(SLUG to card(language, cardSoldOut, cardFrom)) else emptyMap(),
            )
        val viewModel = TripDetailViewModel(SLUG, repository, waitlistMemory(), link)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        TripDetailScreen(slug = SLUG, onBack = {}, onContinue = { _, _ -> continued++ }, viewModel = viewModel)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun <T> land(
        read: CompletableDeferred<T>,
        value: T,
    ) {
        compose.runOnUiThread { read.complete(value) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** Each node's top and bottom edge in the root, in px. */
    private fun bounds(vararg tags: String): Map<String, Pair<Float, Float>> =
        tags.associateWith {
            val rect = compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            rect.top to rect.bottom
        }

    /** The deck's first sentence: two lines on this phone in both languages, the room the page reserves for it. */
    private fun TripDetailDto.withTwoLineDeck() = copy(deck = deck?.substringBefore('.')?.plus('.'))

    /**
     * The list card of the seeded trip, as Home or search would have cached it. [pageFieldsFrom]: as an MV-B3 server
     * sends it, with that trip's deck and bus/place cells; else as an older server's card, without them.
     */
    private fun card(
        language: AppLanguage,
        soldOut: Boolean = false,
        pageFieldsFrom: TripDetailDto? = null,
    ) = detail(language).let { d ->
        trip(1, title = d.title, priceEgp = d.price.amount, durationLabel = d.durationLabel)
            .copy(slug = SLUG, subtitle = d.subtitle, durationMinutes = d.durationMinutes, rating = d.rating ?: RatingDto(4.8, 5))
            .copy(nextDeparture = if (soldOut) NextDepartureDto(seatsRemaining = 0, capacity = 18, soldOut = true) else null)
            .copy(
                deck = pageFieldsFrom?.deck,
                departure = pageFieldsFrom?.departure,
                destination = pageFieldsFrom?.destination,
            )
    }

    private fun full(dates: List<DepartureDto>) =
        dates.map { it.copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "SOLD_OUT") }

    private fun detail(language: AppLanguage): TripDetailDto =
        json
            .decodeFromString<Envelope<TripDetailDto>>(
                if (language == AppLanguage.ARABIC) TripDetailPayloads.arabic else TripDetailPayloads.english,
            ).data
            // No photos: Coil is not what is under test.
            .copy(heroImage = null, cardImage = null, gallery = emptyList(), media = null)

    private fun departures(language: AppLanguage): List<DepartureDto> =
        json
            .decodeFromString<Envelope<List<DepartureDto>>>(
                if (language == AppLanguage.ARABIC) TripDetailPayloads.arabicDepartures else TripDetailPayloads.englishDepartures,
            ).data

    @Serializable
    private data class Envelope<T>(
        val data: T,
    )

    private companion object {
        const val SLUG = "burullus-dawn"
        const val SETTLE_MS = 2_000L
        const val SWIPE_PX = 200f
        const val FONT_SCALE_PIXEL = 1.3f
        const val FONT_SCALE_LARGE = 1.5f
        val json = Json { ignoreUnknownKeys = true }
    }
}
