package eg.bahr.feature.home.data

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.CategoriesSectionDto
import eg.bahr.feature.home.model.DeparturesSectionDto
import eg.bahr.feature.home.model.HomeDto
import eg.bahr.feature.home.model.HomeSectionsSerializer
import eg.bahr.feature.home.model.HomeSeeAllDto
import eg.bahr.feature.home.model.HostsSectionDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.model.ReviewsSectionDto
import eg.bahr.feature.home.model.SkippedSectionDto
import eg.bahr.feature.home.model.TripsSectionDto
import eg.bahr.feature.home.model.key
import eg.bahr.feature.home.presentation.AssetsBlock
import eg.bahr.feature.home.presentation.CardsBlock
import eg.bahr.feature.home.presentation.HomeBlock
import eg.bahr.feature.home.presentation.HomeCards
import eg.bahr.feature.home.presentation.HomeLayout
import eg.bahr.feature.home.presentation.parseHomeSections
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/v1/home` through the app's real client on a MockEngine: the sections decode by `type`, in
 * the server's order, and what this build cannot draw is kept as a [SkippedSectionDto] instead of
 * failing the whole Home.
 */
class HomeApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        language: String = "en",
    ): HomeApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(content = body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        return HomeApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { language })
    }

    private suspend fun home(body: String = HomePayloads.ENGLISH): HomeDto {
        val result = service(body).home()
        assertIs<AppResult.Success<HomeDto>>(result)
        return result.data
    }

    @Test
    fun `asks for home with the stored language`() =
        runTest {
            service(HomePayloads.ENGLISH, language = "ar").home()

            val request = requests.single()
            assertEquals("/api/v1/home", request.url.encodedPath)
            assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
        }

    @Test
    fun `sections decode by type in the server's order`() =
        runTest {
            val sections = home().sections

            assertEquals(6, sections.size)
            assertIs<BannersSectionDto>(sections[0])
            assertIs<TripsSectionDto>(sections[1])
            assertIs<SkippedSectionDto>(sections[2])
            assertIs<CategoriesSectionDto>(sections[3])
            assertIs<TripsSectionDto>(sections[4])
            assertIs<SkippedSectionDto>(sections[5])
        }

    @Test
    fun `banners keep their ratio - image and action - and ignore fields a newer server adds`() =
        runTest {
            val banners = assertIs<BannersSectionDto>(home().sections[0])

            assertEquals("carousel", banners.layout)
            assertEquals("45:28", banners.aspectRatio)
            assertNull(banners.title)
            assertEquals(listOf("trip", "category", "none"), banners.items.map { it.action.type })
            assertEquals("burullus-dawn", banners.items[0].action.value)
            assertEquals(900, banners.items[0].image.width)
            assertNull(banners.items[2].title)
            assertNull(banners.items[2].action.value)
        }

    @Test
    fun `a hero slide reads its badge - from price - meta and forward flag`() =
        runTest {
            val (hero, _, url) = assertIs<BannersSectionDto>(home(HomePayloads.HERO_AND_NOTES).sections[0]).items

            assertEquals("الأكثر حجزًا", hero.badge?.label)
            assertEquals("secondary", hero.badge?.tone)
            assertEquals(450L, hero.price?.amount)
            assertEquals("EGP", hero.price?.currencyCode)
            assertEquals("بحيرة البرلس · يوم واحد", hero.meta)
            assertTrue(hero.showForward)
            assertTrue(url.showForward)
        }

    @Test
    fun `a slide without the hero fields - as an older server sends it - has none and no forward button`() =
        runTest {
            val plain = assertIs<BannersSectionDto>(home(HomePayloads.HERO_AND_NOTES).sections[0]).items[1]
            val older = assertIs<BannersSectionDto>(home().sections[0]).items[0]

            listOf(plain, older).forEach { slide ->
                assertNull(slide.badge)
                assertNull(slide.price)
                assertNull(slide.meta)
                assertFalse(slide.showForward)
            }
        }

    @Test
    fun `a note reads its body - icon and title - in the server's order`() =
        runTest {
            val sections = home(HomePayloads.HERO_AND_NOTES).sections

            val footer = assertIs<NoteSectionDto>(sections[1])
            assertNull(footer.title)
            val note = footer.items.single()
            assertTrue(note.body.startsWith("كل الرحلات بتتحرك من موقف عبد المنعم رياض"))
            assertEquals("directions_bus", note.icon)

            val titled = assertIs<NoteSectionDto>(sections[2])
            assertEquals("قبل ما تحجز", titled.title)
            assertEquals("a_symbol_this_build_lacks", titled.items.single().icon)
        }

    @Test
    fun `a note whose body is blank is unreadable - and its section is dropped as empty`() =
        runTest {
            val sections = home(HomePayloads.HERO_AND_NOTES).sections
            val blank = assertIs<NoteSectionDto>(sections[3])
            assertEquals(0, blank.items.size)
            assertEquals(1, blank.droppedItems)

            val drawn = parseHomeSections(sections) {}
            assertEquals(listOf(sections[0], sections[1], sections[2], sections[4]).map { it.key }, drawn.map { it.key })
        }

    @Test
    fun `a note without an icon key reads as no icon`() =
        runTest {
            val note = assertIs<NoteSectionDto>(home(HomePayloads.HERO_AND_NOTES).sections[4]).items.single()

            assertEquals("Bring a hat.", note.body)
            assertNull(note.icon)
        }

    @Test
    fun `a trip row reads nights and the return date - with absent nights as a day trip`() =
        runTest {
            val row = assertIs<TripsSectionDto>(home().sections[1])

            assertEquals("Featured trips", row.title)
            val (dawn, desert) = row.items
            assertEquals(0, dawn.nights)
            assertEquals(1, desert.nights)
            assertEquals("2 days · 1 night", desert.durationLabel)
            assertEquals(LocalDate(2026, 10, 16), desert.nextDeparture?.returnDate)

            val oldServer =
                """{"success":true,"data":{"sections":[{"id":"x","type":"trips","layout":"row",
                "items":[{"slug":"a","title":"A","durationLabel":"","price":{"amount":1,"currency":"EGP"}}]}]}}"""
            val oldCard = assertIs<TripsSectionDto>(home(oldServer).sections.single()).items.single()
            assertEquals(0, oldCard.nights)
        }

    @Test
    fun `an unknown type and an unreadable section are skipped - not fatal`() =
        runTest {
            val sections = home().sections

            val unknown = assertIs<SkippedSectionDto>(sections[2])
            assertEquals("stories", unknown.type)
            val unreadable = assertIs<SkippedSectionDto>(sections[5])
            assertEquals("trips", unreadable.type)
            assertTrue(unreadable.reason.startsWith("unreadable"), unreadable.reason)
        }

    @Test
    fun `an unreadable card costs only that card - not its row`() =
        runTest {
            val row = assertIs<TripsSectionDto>(home().sections[4])

            assertEquals(listOf("reed-kayak"), row.items.map { it.slug })
            assertEquals(1, row.droppedItems)
            // A row read whole says so.
            assertEquals(0, assertIs<TripsSectionDto>(home().sections[1]).droppedItems)
        }

    @Test
    fun `a row whose every card is unreadable is left empty - and dropped as empty`() =
        runTest {
            val body =
                """{"success":true,"data":{"sections":[{"id":"x","type":"trips","layout":"row",
                "items":[{"title":"No slug","durationLabel":"","price":{"amount":1,"currency":"EGP"}}]}]}}"""
            val row = assertIs<TripsSectionDto>(home(body).sections.single())
            assertEquals(emptyList(), row.items)
            assertEquals(1, row.droppedItems)

            val logged = mutableListOf<String>()
            assertEquals(emptyList<HomeBlock>(), parseHomeSections(listOf(row)) { logged += it })
            assertEquals(2, logged.size, logged.toString())
        }

    @Test
    fun `category chips keep key - label - icon and tone`() =
        runTest {
            val categories = assertIs<CategoriesSectionDto>(home().sections[3])

            assertEquals("Browse by kind", categories.title)
            val boat = categories.items.first()
            assertEquals("on_the_boat", boat.key)
            assertEquals("On the boat", boat.label)
            assertEquals("sailing", boat.icon)
            assertEquals("primary", boat.tone)
        }

    @Test
    fun `a trip row carries its whole list's length and where See all leads - older rows carry neither`() =
        runTest {
            val rows = home().sections.filterIsInstance<TripsSectionDto>()

            val featured = rows.first { it.title == "Featured trips" }
            assertEquals(12, featured.totalItems)
            assertEquals(HomeSeeAllDto(type = "section", value = "0199c3a0-5eed-7000-8000-000000000702"), featured.seeAll)

            // An older server (before M4-B1b) sends neither: no "See all" then.
            val json = Json { ignoreUnknownKeys = true }
            val older =
                HomeSectionsSerializer.decodeSection(
                    json,
                    json.parseToJsonElement(
                        """{"id":"r","type":"trips","layout":"row","items":[{"slug":"a","title":"A","durationLabel":"",
                        "price":{"amount":1,"currency":"EGP"}}]}""",
                    ),
                )
            assertNull(assertIs<TripsSectionDto>(older).totalItems)
            assertNull(older.seeAll)
        }

    @Test
    fun `the repository remembers row cards - so a trip opened from a row is text-first`() =
        runTest {
            // The app's one card cache (core:catalog), which the trip page (feature:trip-details) reads (M4-M1c).
            val cards = TripCardCache()
            val repository = DefaultHomeRepository(service(HomePayloads.ENGLISH), lists = FakeTripRepository(), cards = cards)

            repository.home()

            assertEquals("The White Desert, overnight", cards.card("white-desert-overnight")?.title)
        }

    @Test
    fun `hosts - departures and reviews decode field for field - a bad date costs only its card`() =
        runTest {
            val sections = home(HomePayloads.CARD_SECTIONS).sections

            val hosts = assertIs<HostsSectionDto>(sections[0])
            val ashraf = hosts.items.first()
            assertEquals("ashraf-el-bahr", ashraf.slug)
            assertEquals("person", ashraf.kind)
            assertTrue(ashraf.verified)
            assertEquals(42, ashraf.tripsRun)
            assertEquals(4.8, ashraf.rating?.value)
            assertEquals(37, ashraf.rating?.count)
            assertEquals("https://cdn.invalid/ashraf.jpg", ashraf.avatar?.url)
            assertNull(hosts.items[1].rating, "a host with no reviews has no rating")

            val departures = assertIs<DeparturesSectionDto>(sections[1])
            assertEquals(2, departures.columns)
            val dawn = departures.items.single()
            assertEquals(LocalDate(2026, 11, 14), dawn.date)
            assertEquals(LocalTime(5, 0), dawn.departTime)
            assertEquals(3, dawn.seatsRemaining)
            assertEquals("burullus-dawn", dawn.trip.slug)
            assertEquals(1, departures.droppedItems)

            val review = assertIs<ReviewsSectionDto>(sections[2]).items.single()
            assertEquals(5, review.rating)
            assertEquals("Mona S.", review.author)
            assertEquals(LocalDate(2026, 10, 20), review.dateISO)
            assertTrue(review.verified)
            assertNull(review.trip.image)
        }

    @Test
    fun `the parser draws the card sections by layout - clamps columns - and drops a mismatch and an unknown layout`() =
        runTest {
            val logged = mutableListOf<String>()
            val blocks = parseHomeSections(home(HomePayloads.CARD_SECTIONS).sections) { logged += it }

            assertEquals(
                listOf(HomeLayout.CardRail, HomeLayout.CardGrid, HomeLayout.CardList, HomeLayout.TileGrid),
                blocks.map { it.layout },
            )
            assertIs<HomeCards.Hosts>(assertIs<CardsBlock>(blocks[0]).cards)
            assertEquals(3, assertIs<AssetsBlock>(blocks[3]).columns, "5 columns clamp to 3")
            assertTrue(logged.any { "cannot draw" in it }, logged.toString())
            assertTrue(logged.any { "unknown layout 'mosaic'" in it }, logged.toString())
            assertTrue(logged.any { "unreadable" in it }, logged.toString())
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
    }
}
