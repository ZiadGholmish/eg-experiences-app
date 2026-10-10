package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.CategoriesSectionDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.HomeSectionsSerializer
import eg.bahr.feature.trips.model.HomeSeeAllDto
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripsSectionDto
import eg.bahr.feature.trips.presentation.drawableSections
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
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
    ): TripApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(content = body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        return TripApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { language })
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
            assertEquals(emptyList(), drawableSections(listOf(row)) { logged += it })
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
            val repository = DefaultTripRepository(service(HomePayloads.ENGLISH))

            repository.home()

            assertEquals("The White Desert, overnight", repository.cachedCard("white-desert-overnight")?.title)
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
    }
}
