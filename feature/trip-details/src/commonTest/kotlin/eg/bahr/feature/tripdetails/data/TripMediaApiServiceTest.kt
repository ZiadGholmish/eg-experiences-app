package eg.bahr.feature.tripdetails.data

import eg.bahr.core.catalog.presentation.media.ViewerMedia
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.tripdetails.presentation.galleryMedia
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The trip page's gallery since M5-B4 (`TripDetail.media`, `TripMedia`), drawn since MV-M3: decoded through the app's
 * real client from the seeded `burullus-dawn`'s shape (a photo, the 8 s demo video, more photos), and turned into the
 * slider's and the viewer's items in the server's order, a kind this build does not know skipped.
 */
class TripMediaApiServiceTest {
    private fun service(body: String) =
        TripDetailApiService(
            apiHttpClient(
                MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) },
                ApiConfig(BASE_URL, isDebug = false),
            ) { "en" },
        )

    private suspend fun trip(body: String) =
        service(body).tripBySlug("burullus-dawn").let {
            assertIs<AppResult.Success<*>>(it)
            (it as AppResult.Success).data
        }

    @Test
    fun `decodes photos and a video in the server's order`() =
        runTest {
            val trip = trip(withMedia)

            val media = trip.media.orEmpty()
            assertEquals(listOf("photo", "video", "panorama", "photo"), media.map { it.kind })
            val video = media[1]
            assertEquals("http://localhost:9000/bahr-assets/seed/burullus-dawn.mp4", video.url)
            assertEquals(640, video.width)
            assertEquals(360, video.height)
            assertEquals(8, video.durationSeconds)
            assertEquals("http://localhost:9000/bahr-assets/seed/joy-boats.png", video.poster?.url)
            assertEquals("Video: dawn on the lake", video.alt)
            assertEquals(1, media[0].variants.size)
        }

    @Test
    fun `the slider and the viewer get the media in order and without a kind they do not know`() =
        runTest {
            val items = trip(withMedia).galleryMedia()

            assertEquals(3, items.size)
            assertIs<ViewerMedia.Photo>(items[0])
            val video = assertIs<ViewerMedia.Video>(items[1])
            assertEquals(8, video.durationSeconds)
            assertEquals(640f / 360f, video.aspectRatio)
            assertEquals("data:image/png;base64,poster", video.lqip, "a video's LQIP is its poster's")
            assertEquals("http://localhost:9000/bahr-assets/seed/joy-market.png", (items[2] as ViewerMedia.Photo).image.url)
        }

    @Test
    fun `a server before M5-B4 sends no media and the photo gallery is used`() =
        runTest {
            val trip = trip(galleryOnly)

            assertNull(trip.media)
            val items = trip.galleryMedia()
            assertEquals(listOf("http://localhost:9000/bahr-assets/seed/joy-birds.png"), items.map { it.key })
        }

    @Test
    fun `with no gallery at all the hero image is the one item`() =
        runTest {
            val items = trip(heroOnly).galleryMedia()

            assertEquals(listOf("http://localhost:9000/bahr-assets/seed/card-dawn.png"), items.map { it.key })
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"

        const val HEAD = """{"success":true,"error":null,"data":{"slug":"burullus-dawn","title":"Dawn on Lake Burullus",
            "durationLabel":"17h","price":{"amount":450,"currency":"EGP"},
            "heroImage":{"url":"http://localhost:9000/bahr-assets/seed/card-dawn.png","width":600,"height":600}"""

        val withMedia =
            HEAD +
                """,
            "gallery":[{"url":"http://localhost:9000/bahr-assets/seed/joy-boats.png","width":900,"height":560}],
            "media":[
              {"kind":"photo","url":"http://localhost:9000/bahr-assets/seed/joy-boats.png","width":900,"height":560,
               "alt":"Fishing boats on Lake Burullus","lqip":"data:image/png;base64,boats",
               "variants":[{"url":"http://localhost:9000/bahr-assets/seed/joy-boats.png?w=390&fm=webp","width":390,"format":"webp"}]},
              {"kind":"video","url":"http://localhost:9000/bahr-assets/seed/burullus-dawn.mp4","width":640,"height":360,
               "alt":"Video: dawn on the lake","durationSeconds":8,
               "poster":{"url":"http://localhost:9000/bahr-assets/seed/joy-boats.png","width":900,"height":560,
                         "alt":"Video: dawn on the lake","lqip":"data:image/png;base64,poster"}},
              {"kind":"panorama","url":"http://localhost:9000/bahr-assets/seed/pano.jpg","width":4000,"height":1000},
              {"kind":"photo","url":"http://localhost:9000/bahr-assets/seed/joy-market.png","width":900,"height":560}
            ]}}"""

        val galleryOnly =
            HEAD +
                """,
            "gallery":[{"url":"http://localhost:9000/bahr-assets/seed/joy-birds.png","width":900,"height":560}]}}"""

        val heroOnly = "$HEAD}}"
    }
}
