package eg.bahr.feature.home.presentation

import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.model.HomeSeeAllDto
import eg.bahr.feature.home.model.TripsSectionDto
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.components.rowTitleSharedKey
import eg.bahr.feature.home.presentation.components.seeAllAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A Home row's "See all" (M4-M1b): when it shows, and where it leads. Moved from feature:trips'
 * `SectionTripsViewModelTest` with Home (M4-M1c); the list it opens is tested there.
 */
class SeeAllActionTest {
    @Test
    fun `see all shows only when the whole list is longer than the row`() {
        val row = row(totalItems = 2, seeAll = HomeSeeAllDto("section", "s-1"))

        assertNull(row.seeAllAction(), "two of two: nothing more to see")
        assertEquals(HomeAction.OpenSection("s-1", "Featured", rowTitleSharedKey(row.id)), row.copy(totalItems = 3).seeAllAction())
        assertNull(row.copy(totalItems = null).seeAllAction(), "an older server sends no total")
        assertNull(row.copy(totalItems = 3, seeAll = null).seeAllAction(), "nor a target")
    }

    @Test
    fun `a category row's see all opens the category page - an unknown target type shows no link`() {
        val row = row(totalItems = 9, seeAll = HomeSeeAllDto("category", "birds"))

        assertEquals(HomeAction.OpenCategory("birds", "Featured", rowTitleSharedKey(row.id)), row.seeAllAction())
        assertNull(row.copy(seeAll = HomeSeeAllDto("map", "x")).seeAllAction())
    }

    private fun row(
        totalItems: Int?,
        seeAll: HomeSeeAllDto?,
    ) = TripsSectionDto(
        id = "s-1",
        type = "trips",
        title = "Featured",
        layout = "row",
        items = listOf(trip(1), trip(2)),
        totalItems = totalItems,
        seeAll = seeAll,
    )
}
