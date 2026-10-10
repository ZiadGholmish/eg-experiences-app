package eg.bahr.feature.trips.presentation

import eg.bahr.feature.trips.model.HomeActionDto
import eg.bahr.feature.trips.navigation.HomeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeMappingTest {
    @Test
    fun `trip and category actions carry their value`() {
        assertEquals(HomeAction.OpenTrip("burullus-dawn"), HomeActionDto("trip", "burullus-dawn").toHomeAction())
        assertEquals(HomeAction.OpenCategory("birds"), HomeActionDto("category", "birds").toHomeAction())
    }

    @Test
    fun `only an https url opens`() {
        assertEquals(HomeAction.OpenUrl("https://bahr.eg/about"), HomeActionDto("url", "https://bahr.eg/about").toHomeAction())
        assertNull(HomeActionDto("url", "http://bahr.eg/about").toHomeAction())
        assertNull(HomeActionDto("url", "javascript:alert(1)").toHomeAction())
    }

    @Test
    fun `none - a missing value and an unknown type are not tappable`() {
        assertNull(HomeActionDto("none").toHomeAction())
        assertNull(HomeActionDto("trip").toHomeAction())
        assertNull(HomeActionDto("trip", "  ").toHomeAction())
        assertNull(HomeActionDto("share", "x").toHomeAction())
    }

    @Test
    fun `aspect ratio is width over height - null when unusable`() {
        assertEquals(16f / 9f, parseAspectRatio("16:9"))
        assertEquals(45f / 28f, parseAspectRatio("45:28"))
        assertNull(parseAspectRatio(null))
        assertNull(parseAspectRatio("16x9"))
        assertNull(parseAspectRatio("0:9"))
        assertNull(parseAspectRatio("16:9:1"))
    }
}
