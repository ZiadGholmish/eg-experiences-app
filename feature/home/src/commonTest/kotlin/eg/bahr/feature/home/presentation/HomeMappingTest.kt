package eg.bahr.feature.home.presentation

import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.HomeActionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeNoteDto
import eg.bahr.feature.home.model.HomeSectionDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.navigation.HomeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeMappingTest {
    @Test
    fun `trip and category actions carry their value`() {
        assertEquals(HomeAction.OpenTrip("burullus-dawn"), HomeActionDto("trip", "burullus-dawn").toHomeAction())
        assertEquals(HomeAction.OpenCategory("birds"), HomeActionDto("category", "birds").toHomeAction())
    }

    @Test
    fun `no url banner is tappable until the app has an in-app browser`() {
        assertNull(HomeActionDto("url", "https://bahr.eg/about").toHomeAction())
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

    @Test
    fun `the forward circle shows only on a slide that asks for it - and that a tap opens`() {
        fun slide(
            action: HomeActionDto,
            showForward: Boolean,
        ) = HomeBannerDto("b", null, ImageDto("https://cdn.invalid/b.png", 900, 560), action, showForward = showForward)

        assertTrue(slide(HomeActionDto("trip", "burullus-dawn"), showForward = true).showsForward())
        assertTrue(slide(HomeActionDto("category", "birds"), showForward = true).showsForward())
        assertFalse(slide(HomeActionDto("trip", "burullus-dawn"), showForward = false).showsForward())
        // Not until the app opens links (M4-M1a review #5b): the slide is not a button.
        assertFalse(slide(HomeActionDto("url", "https://bahr.eg/about"), showForward = true).showsForward())
        assertFalse(slide(HomeActionDto("none"), showForward = true).showsForward())
    }

    private fun note(id: String) = NoteSectionDto(id = id, type = "note", items = listOf(HomeNoteDto("Note $id")))

    private fun banners(id: String) =
        BannersSectionDto(
            id = id,
            type = "banners",
            layout = "carousel",
            items = listOf(HomeBannerDto("b", null, ImageDto("https://cdn.invalid/b.png", 900, 560), HomeActionDto("none"))),
        )

    private fun blocks(vararg sections: HomeSectionDto) = parseHomeSections(sections.toList()) {}

    @Test
    fun `the notes that end Home go after the trip list - a note between sections stays in place`() {
        val placed = HomePlacement.of(blocks(banners("hero"), note("middle"), banners("rows"), note("footer"), note("also-footer")))

        assertEquals(listOf("hero", "middle", "rows"), placed.inline.map { it.id })
        assertEquals(listOf("footer", "also-footer"), placed.trailing.map { it.id })
    }

    @Test
    fun `no trailing note - nothing after the list - and Home of notes only is all footer`() {
        val noteThenHero = HomePlacement.of(blocks(note("a"), banners("hero")))
        assertEquals(listOf("a", "hero"), noteThenHero.inline.map { it.id })
        assertEquals(emptyList(), noteThenHero.trailing)
        val notesOnly = HomePlacement.of(blocks(note("a")))
        assertEquals(emptyList(), notesOnly.inline)
        assertEquals(listOf("a"), notesOnly.trailing.map { it.id })
        assertEquals(HomePlacement(emptyList(), emptyList()), HomePlacement.of(emptyList()))
    }
}
