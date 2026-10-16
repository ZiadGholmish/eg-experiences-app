package eg.bahr.feature.home.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eg.bahr.core.catalog.model.HomeCategoryDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.feature.home.data.FakeTripRepository
import eg.bahr.feature.home.data.TripFixtures.facets
import eg.bahr.feature.home.data.TripFixtures.page
import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.data.waitlistMemory
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.CategoriesSectionDto
import eg.bahr.feature.home.model.HomeActionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeDto
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.components.HOME_CAROUSEL_TAG
import eg.bahr.feature.home.presentation.components.categoryTileSharedKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M4-M1 on Home: a carousel dot goes to its slide (HANDOFF Home: "tappable"), a category tile opens
 * its category, and a `url` banner is not a button until the app can open one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
class HomeSectionsTest {
    private companion object {
        /** Outside the dot's own cell, inside the 48dp minimum touch target around it. */
        val BESIDE = BahrSpacing.md

        /** A pager page scroll is a spring of well under a second. */
        const val SLIDE_SETTLE_MILLIS = 2_000L
        const val FRAME_MILLIS = 16L
    }

    @get:Rule
    val compose = createComposeRule()

    private val image = ImageDto(url = "https://cdn.invalid/banner.png", width = 900, height = 560)
    private val banners =
        BannersSectionDto(
            id = "hero",
            type = "banners",
            layout = "carousel",
            aspectRatio = "16:9",
            items =
                listOf(
                    HomeBannerDto("b1", "First", image, HomeActionDto("trip", "trip-1")),
                    HomeBannerDto("b2", "Second", image, HomeActionDto("none")),
                    HomeBannerDto("b3", "About us", image, HomeActionDto("url", "https://bahr.eg/about")),
                ),
        )
    private val categories =
        CategoriesSectionDto(
            id = "kinds",
            type = "categories",
            title = "Browse by kind",
            layout = "row",
            items = listOf(HomeCategoryDto("birds", "Birds", "flutter_dash", "secondary")),
        )

    @Test
    fun `a dot goes to its slide`() {
        show()
        compose.onNodeWithContentDescription("Slide 1 of 3").assertIsSelected()

        // A real touch at the dot's centre: the dot is drawn 7dp wide, its target is the touch minimum.
        compose.onNodeWithContentDescription("Slide 3 of 3").performTouchInput { click(center) }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide 3 of 3").assertIsSelected()
        compose.onNodeWithContentDescription("Slide 1 of 3").assertIsNotSelected()
    }

    /**
     * M4-M1 review #3: the dot is drawn 7dp wide, but a tap beside it still lands on it: Compose
     * widens a target smaller than the touch minimum. The first dot, tapped past its start edge
     * (nothing else is there), in LTR.
     */
    @Test
    fun `a tap beside a dot still goes to its slide`() {
        show()
        compose.onNodeWithContentDescription("Slide 3 of 3").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide 1 of 3").performTouchInput {
            click(Offset(-BESIDE.toPx(), centerY))
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide 1 of 3").assertIsSelected()
    }

    /**
     * M4-M9: from the last slide the timer goes on to the first going forward, as from any other
     * slide: the first slide comes in from the end side and only ever moves toward the start. Going
     * back to it instead would bring it in from the start side, across the second slide.
     */
    @Test
    fun `after the last slide the first comes next - going forward`() {
        show()
        // The clock moves only when the test moves it: the timer and the slides' animation, frame by frame.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Slide 3 of 3").performClick()
        settle()
        compose.onNodeWithContentDescription("Slide 3 of 3").assertIsSelected()

        // Frame by frame through the wait and the slide, wherever the timer stood when slide 3 settled.
        val lefts = mutableListOf<Float>()
        val frames = (BahrMotion.CarouselAdvance + SLIDE_SETTLE_MILLIS) / FRAME_MILLIS
        repeat(frames.toInt()) {
            compose.mainClock.advanceTimeByFrame()
            firstSlideLeft()?.let { lefts += it }
        }
        // No settle here: the window ends before the timer's next step (to slide 2).
        compose.waitForIdle()
        val rest = firstSlideLeft()

        compose.onNodeWithContentDescription("Slide 1 of 3").assertIsSelected()
        assertTrue(lefts.distinct().size > 2, "the first slide was seen coming in: $lefts")
        assertTrue(lefts.first() > lefts.last(), "it came in from the end side: ${lefts.distinct()}")
        assertTrue(lefts.zipWithNext().all { (before, after) -> after <= before }, "it only moved toward the start: ${lefts.distinct()}")
        assertEquals(rest, lefts.last(), "it ended where a slide rests: ${lefts.distinct()}")
    }

    /**
     * M4-M9 review #3: the endless pager holds many laps of pages, but screen readers are told how many
     * banners there are (its collection info), not how many pages.
     */
    @Test
    fun `screen readers hear the banner count - not the pages`() {
        show()

        val info = compose.onNodeWithTag(HOME_CAROUSEL_TAG).fetchSemanticsNode().config[SemanticsProperties.CollectionInfo]

        assertEquals(1, info.rowCount)
        assertEquals(banners.items.size, info.columnCount)
    }

    /** A finger resting on a slide holds the carousel still; once it lifts, the timer starts over. */
    @Test
    fun `a finger on the carousel holds it - lifting it lets it go on`() {
        show()
        // The clock moves only when the test moves it: the timer and the slides' animation, frame by frame.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("First").performTouchInput { down(center) }

        compose.mainClock.advanceTimeBy(BahrMotion.CarouselAdvance * 2)
        compose.onNodeWithContentDescription("Slide 1 of 3").assertIsSelected()

        compose.onNodeWithText("First").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(BahrMotion.CarouselAdvance)
        settle()
        compose.onNodeWithContentDescription("Slide 2 of 3").assertIsSelected()
    }

    @Test
    fun `a category tile opens its category - with its label as the title`() {
        val actions = show()

        compose.onNodeWithText("Birds").performClick()

        val opened = actions.single()
        assertEquals(HomeAction.OpenCategory("birds", "Birds", categoryTileSharedKey("kinds", "birds")), opened)
    }

    @Test
    fun `a url banner is a picture - not a button`() {
        val actions = show()
        compose.onNodeWithContentDescription("Slide 3 of 3").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("About us").performClick()
        compose.waitForIdle()

        assertEquals(emptyList(), actions)
    }

    /** Past a slide's animation (and the dots' recomposition). */
    private fun settle() {
        compose.mainClock.advanceTimeBy(SLIDE_SETTLE_MILLIS)
        compose.waitForIdle()
    }

    /** Where the first slide's title starts on screen, in dp; null while it is not composed. */
    private fun firstSlideLeft(): Float? =
        compose
            .onAllNodesWithText("First")
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.let {
                with(compose.density) {
                    it.boundsInRoot.left
                        .toDp()
                        .value
                }
            }

    private fun show(): List<HomeAction> {
        val actions = mutableListOf<HomeAction>()
        val repository =
            FakeTripRepository(
                listTrips = { page(listOf(trip(1)), facets = facets()) },
                home = { AppResult.Success(HomeDto(listOf(banners, categories))) },
            )
        val viewModel = TripListViewModel(repository, waitlistMemory())
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    TripListScreen(onTripClick = {}, onAction = { actions += it }, viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()
        return actions
    }
}
