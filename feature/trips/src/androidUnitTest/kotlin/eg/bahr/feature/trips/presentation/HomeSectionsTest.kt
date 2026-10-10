package eg.bahr.feature.trips.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.CategoriesSectionDto
import eg.bahr.feature.trips.model.HomeActionDto
import eg.bahr.feature.trips.model.HomeBannerDto
import eg.bahr.feature.trips.model.HomeCategoryDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.ImageDto
import eg.bahr.feature.trips.navigation.HomeAction
import eg.bahr.feature.trips.presentation.components.categoryTileSharedKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

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
