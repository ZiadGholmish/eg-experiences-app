package eg.bahr.feature.home.presentation

import eg.bahr.core.catalog.model.HomeCategoryDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.CategoriesSectionDto
import eg.bahr.feature.home.model.HomeActionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeHostDto
import eg.bahr.feature.home.model.HomeNoteDto
import eg.bahr.feature.home.model.HomeSectionDto
import eg.bahr.feature.home.model.HostsSectionDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.model.SkippedSectionDto
import eg.bahr.feature.home.model.TripsSectionDto
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.components.homeLayoutRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MH-M1: the layout registry and the parser that turns Home's sections into drawable blocks. */
class HomeLayoutTest {
    private val image = ImageDto("https://cdn.invalid/b.png", 600, 600)

    private fun banners(
        layout: String,
        aspectRatio: String? = null,
        columns: Int? = null,
        ids: List<String> = listOf("b1"),
    ) = BannersSectionDto(
        id = "banners-$layout",
        type = "banners",
        layout = layout,
        aspectRatio = aspectRatio,
        columns = columns,
        items = ids.map { HomeBannerDto(it, null, image, HomeActionDto("none")) },
    )

    private fun trips(layout: String) = TripsSectionDto(id = "trips-$layout", type = "trips", layout = layout, items = listOf(trip(1)))

    private fun hosts(layout: String) =
        HostsSectionDto(
            id = "hosts-$layout",
            type = "hosts",
            layout = layout,
            items = listOf(HomeHostDto("ashraf", "person", "Ashraf", verified = true, tripsRun = 3)),
        )

    private fun categories(layout: String) =
        CategoriesSectionDto(
            id = "kinds",
            type = "categories",
            layout = layout,
            items = listOf(HomeCategoryDto("birds", "Birds", "flutter_dash", "secondary")),
        )

    private fun note(layout: String?) =
        NoteSectionDto(id = "note", type = "note", layout = layout, items = listOf(HomeNoteDto("Bring a hat.")))

    private fun parse(
        vararg sections: HomeSectionDto,
        log: MutableList<String> = mutableListOf(),
    ) = parseHomeSections(sections.toList()) { log += it }

    @Test
    fun `every layout's renderer draws the layout's shape`() {
        HomeLayout.entries.forEach { layout ->
            assertEquals(layout.shape, homeLayoutRenderer(layout).shape, "$layout")
        }
    }

    @Test
    fun `the contract's eleven layouts resolve by their lowercase key`() {
        val keys =
            listOf(
                "hero",
                "strip",
                "poster_rail",
                "tile_rail",
                "tile_grid",
                "circles",
                "card_rail",
                "card_grid",
                "card_list",
                "chips",
                "note_card",
            )
        assertEquals(keys, HomeLayout.entries.map { it.key })
        HomeLayout.entries.forEach { assertEquals(it, HomeLayout.resolve(it.key, it.shape)) }
        assertEquals(HomeLayout.TileGrid, HomeLayout.resolve(" TILE_GRID ", HomeShape.Assets))
    }

    @Test
    fun `legacy carousel and row map by shape as the server's migration did`() {
        assertEquals(HomeLayout.Hero, HomeLayout.resolve("carousel", HomeShape.Assets))
        assertEquals(HomeLayout.TileRail, HomeLayout.resolve("row", HomeShape.Assets))
        assertEquals(HomeLayout.CardRail, HomeLayout.resolve("carousel", HomeShape.Cards))
        assertEquals(HomeLayout.CardRail, HomeLayout.resolve("row", HomeShape.Cards))
        assertEquals(HomeLayout.Chips, HomeLayout.resolve("row", HomeShape.Chips))
        assertEquals(HomeLayout.NoteCard, HomeLayout.resolve("row", HomeShape.Text))
        // A note never needed a layout; the old app drew it the one way.
        assertEquals(HomeLayout.NoteCard, HomeLayout.resolve(null, HomeShape.Text))
        assertNull(HomeLayout.resolve(null, HomeShape.Assets))
    }

    @Test
    fun `columns clamp to two to three - two when absent`() {
        assertEquals(2, HomeLayout.gridColumns(null))
        assertEquals(2, HomeLayout.gridColumns(1))
        assertEquals(2, HomeLayout.gridColumns(2))
        assertEquals(3, HomeLayout.gridColumns(3))
        assertEquals(3, HomeLayout.gridColumns(5))

        val grid = assertIs<AssetsBlock>(parse(banners("tile_grid", columns = 9)).single())
        assertEquals(3, grid.columns)
    }

    @Test
    fun `legacy sections still draw - a trip row as a card rail - categories as chips - a note without layout`() {
        val blocks = parse(banners("carousel"), trips("row"), categories("row"), note(null))

        assertEquals(listOf(HomeLayout.Hero, HomeLayout.CardRail, HomeLayout.Chips, HomeLayout.NoteCard), blocks.map { it.layout })
        assertIs<HomeCards.Trips>(assertIs<CardsBlock>(blocks[1]).cards)
    }

    @Test
    fun `an unknown type and an unknown layout are dropped and logged`() {
        val log = mutableListOf<String>()
        val blocks = parse(SkippedSectionDto("stories", "unknown type"), banners("mosaic"), hosts("card_rail"), log = log)

        assertEquals(listOf("hosts-card_rail"), blocks.map { it.id })
        assertEquals(2, log.size, log.toString())
        assertTrue(log[0].contains("stories"))
        assertTrue(log[1].contains("unknown layout 'mosaic'"))
    }

    @Test
    fun `a layout that cannot draw the content's shape drops the section`() {
        val log = mutableListOf<String>()
        val blocks = parse(banners("card_rail"), trips("hero"), hosts("chips"), categories("tile_grid"), note("card_list"), log = log)

        assertEquals(emptyList(), blocks)
        assertEquals(5, log.size, log.toString())
        assertTrue(log.all { "cannot draw" in it }, log.toString())
    }

    @Test
    fun `the ratio is the server's aspect ratio - else the layout's own`() {
        assertEquals(16f / 9f, assertIs<AssetsBlock>(parse(banners("hero", aspectRatio = "16:9")).single()).ratio)
        assertEquals(2f, assertIs<AssetsBlock>(parse(banners("hero")).single()).ratio)
        assertEquals(4f, assertIs<AssetsBlock>(parse(banners("strip", aspectRatio = "nonsense")).single()).ratio)
        assertEquals(.8f, assertIs<AssetsBlock>(parse(banners("poster_rail")).single()).ratio)
    }

    @Test
    fun `a repeated item key is dropped - a lazy row would crash on it`() {
        val log = mutableListOf<String>()
        val block = assertIs<AssetsBlock>(parse(banners("tile_rail", ids = listOf("a", "b", "a")), log = log).single())

        assertEquals(listOf("a", "b"), block.banners.map { it.id })
        assertTrue(log.single().contains("repeated"))
    }

    /**
     * A trips row's "See all" is the server's (none here: no total, no target); a hosts row's is the app's own, the
     * hosts directory (MV-M1: the contract gives hosts sections no `seeAll`).
     */
    @Test
    fun `a hosts section sees all hosts - and each card section has its own content type`() {
        val blocks = parse(trips("card_rail"), hosts("card_rail"))

        assertEquals(listOf("section-card_rail-trip", "section-card_rail-host"), blocks.map { it.contentType })
        assertNull(assertIs<CardsBlock>(blocks[0]).seeAll)
        assertEquals(HomeAction.OpenHosts, assertIs<CardsBlock>(blocks[1]).seeAll)
    }
}
