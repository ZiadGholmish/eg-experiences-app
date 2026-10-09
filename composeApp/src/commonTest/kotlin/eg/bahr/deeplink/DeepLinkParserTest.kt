package eg.bahr.deeplink

import eg.bahr.core.common.locale.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinkParserTest {
    private val prod = DeepLinkParser(host = "bahr.eg", allowsHttp = false)
    private val local = DeepLinkParser(host = "localhost", allowsHttp = true)

    private fun assertTrip(
        slug: String,
        url: String,
        parser: DeepLinkParser = prod,
        language: AppLanguage? = null,
    ) = assertEquals(DeepLink.Trip(slug, language), parser.parse(url), url)

    private fun assertUnknown(
        url: String,
        parser: DeepLinkParser = prod,
    ) = assertEquals(DeepLink.Unknown, parser.parse(url), url)

    @Test
    fun `a share link opens its trip`() {
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn")
        assertTrip("a", "https://bahr.eg/t/a")
        assertTrip("trip-2-day-3", "https://bahr.eg/t/trip-2-day-3")
        assertTrip("123", "https://bahr.eg/t/123")
    }

    @Test
    fun `a trailing slash is the same trip`() {
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn/")
        assertUnknown("https://bahr.eg/t/burullus-dawn//")
    }

    @Test
    fun `slugs the backend would refuse are not trips`() {
        // chk_trip_slug: ^[a-z0-9]+(-[a-z0-9]+)*$
        listOf(
            "Burullus-Dawn",
            "burullus_dawn",
            "-burullus",
            "burullus-",
            "burullus--dawn",
            "burullus.dawn",
            "burullus%20dawn",
            "%62urullus",
            "بحر",
            "x;end",
            "x\"y",
        ).forEach { assertUnknown("https://bahr.eg/t/$it") }
    }

    @Test
    fun `a slug is at most 160 characters like the column`() {
        val longest = "a".repeat(160)
        assertTrip(longest, "https://bahr.eg/t/$longest")
        assertUnknown("https://bahr.eg/t/${"a".repeat(161)}")
    }

    @Test
    fun `other paths open the list`() {
        listOf(
            "https://bahr.eg",
            "https://bahr.eg/",
            "https://bahr.eg/t",
            "https://bahr.eg/t/",
            "https://bahr.eg/t/a/b",
            "https://bahr.eg/T/burullus-dawn",
            "https://bahr.eg/b/BHR-7K2Q",
            "https://bahr.eg/trips/burullus-dawn",
            "https://bahr.eg/x/t/burullus-dawn",
            "https://bahr.eg/.well-known/assetlinks.json",
        ).forEach { assertUnknown(it) }
    }

    @Test
    fun `lang is read while every other query parameter and the fragment are ignored`() {
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn?lang=en", language = AppLanguage.ENGLISH)
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn?lang=ar", language = AppLanguage.ARABIC)
        assertTrip(
            "burullus-dawn",
            "https://bahr.eg/t/burullus-dawn/?utm_source=fb&lang=EN&fbclid=x#dates",
            language = AppLanguage.ENGLISH,
        )
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn?utm_source=fb&fbclid=IwAR0")
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn#top")
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn?")
    }

    @Test
    fun `a lang the backend does not carry is no language rather than Arabic`() {
        listOf("fr", "english", "", "e", "ar-EG").forEach {
            assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn?lang=$it")
        }
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn?lang")
        assertTrip("burullus-dawn", "https://bahr.eg/t/burullus-dawn?language=en")
    }

    @Test
    fun `https only unless the flavor allows http`() {
        assertUnknown("http://bahr.eg/t/burullus-dawn")
        assertUnknown("ftp://bahr.eg/t/burullus-dawn")
        assertUnknown("intent://bahr.eg/t/burullus-dawn#Intent;scheme=https;end")
        assertTrip("burullus-dawn", "HTTPS://bahr.eg/t/burullus-dawn")

        assertTrip("burullus-dawn", "http://localhost:8084/t/burullus-dawn", local)
        assertTrip("burullus-dawn", "https://localhost/t/burullus-dawn", local)
    }

    @Test
    fun `another host is not ours`() {
        listOf(
            "https://evil.example/t/burullus-dawn",
            "https://bahr.eg.evil.example/t/burullus-dawn",
            "https://www.bahr.eg/t/burullus-dawn",
            "https://bahr.egx/t/burullus-dawn",
            "https://xbahr.eg/t/burullus-dawn",
            "https:///t/burullus-dawn",
            "https://bahr.eg:/t/burullus-dawn",
            "https://bahr.eg:abc/t/burullus-dawn",
        ).forEach { assertUnknown(it) }
    }

    @Test
    fun `user-info in front of our host is a link to another host`() {
        assertUnknown("https://bahr.eg@evil.example/t/burullus-dawn")
        assertUnknown("https://x@bahr.eg/t/burullus-dawn")
        assertUnknown("https://x;end@bahr.eg/t/burullus-dawn")
    }

    @Test
    fun `the host matches without case and with any port`() {
        assertTrip("burullus-dawn", "https://BAHR.EG/t/burullus-dawn")
        assertTrip("burullus-dawn", "https://bahr.eg:443/t/burullus-dawn")
        assertEquals(
            DeepLink.Trip("burullus-dawn"),
            DeepLinkParser(host = "Bahr.EG", allowsHttp = false).parse("https://bahr.eg/t/burullus-dawn"),
        )
    }

    @Test
    fun `text that is not an absolute url opens the list`() {
        listOf(
            "",
            "   ",
            "/t/burullus-dawn",
            "bahr.eg/t/burullus-dawn",
            "//bahr.eg/t/burullus-dawn",
            "https:/bahr.eg/t/burullus-dawn",
            "burullus-dawn",
        ).forEach { assertUnknown(it) }
    }

    @Test
    fun `surrounding whitespace is not part of the link`() {
        assertTrip("burullus-dawn", "  https://bahr.eg/t/burullus-dawn\n")
    }
}
