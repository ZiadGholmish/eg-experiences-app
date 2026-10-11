package eg.bahr.deeplink

import eg.bahr.core.common.locale.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepLinkInboxTest {
    private val parser = DeepLinkParser(host = "bahr.eg", allowsHttp = false)
    private val inbox = DeepLinkInbox()
    private val opened = RecordingDestinations()

    @Test
    fun `a trip link opens the trip once`() {
        inbox.offer("https://bahr.eg/t/burullus-dawn")

        inbox.dispatch(parser, opened)
        inbox.dispatch(parser, opened)

        assertEquals(listOf("trip:burullus-dawn"), opened.calls)
        assertNull(inbox.pending.value)
    }

    @Test
    fun `a link waits in the inbox until it is dispatched as on a cold start behind splash`() {
        inbox.offer("https://bahr.eg/t/burullus-dawn")

        assertEquals("https://bahr.eg/t/burullus-dawn", inbox.pending.value)
        assertTrue(opened.calls.isEmpty())
    }

    @Test
    fun `an unknown link opens the list`() {
        inbox.offer("https://bahr.eg/b/BHR-7K2Q")

        inbox.dispatch(parser, opened)

        assertEquals(listOf("list"), opened.calls)
    }

    @Test
    fun `a newer link replaces one not yet opened`() {
        inbox.offer("https://bahr.eg/t/first")
        inbox.offer("https://bahr.eg/t/second")

        inbox.dispatch(parser, opened)

        assertEquals(listOf("trip:second"), opened.calls)
    }

    @Test
    fun `the same link tapped again after it was opened opens again on a warm start`() {
        inbox.offer("https://bahr.eg/t/burullus-dawn")
        inbox.dispatch(parser, opened)
        inbox.offer("https://bahr.eg/t/burullus-dawn")
        inbox.dispatch(parser, opened)

        assertEquals(listOf("trip:burullus-dawn", "trip:burullus-dawn"), opened.calls)
    }

    @Test
    fun `nothing pending opens nothing`() {
        inbox.dispatch(parser, opened)

        assertTrue(opened.calls.isEmpty())
    }

    // ---- ?lang= (M4-M8) ----

    @Test
    fun `a link in the other language switches the language first - and stays pending for the rebuilt NavHost`() {
        inbox.offer("https://bahr.eg/t/burullus-dawn?lang=en")

        inbox.dispatch(parser, opened)

        assertEquals(listOf("language:en"), opened.calls)
        assertEquals("https://bahr.eg/t/burullus-dawn?lang=en", inbox.pending.value)

        // The switch rebuilt the NavHost, now in English; it dispatches again.
        opened.language = AppLanguage.ENGLISH
        inbox.dispatch(parser, opened)

        assertEquals(listOf("language:en", "trip:burullus-dawn"), opened.calls)
        assertNull(inbox.pending.value)
    }

    @Test
    fun `a link in the language already shown opens at once`() {
        opened.language = AppLanguage.ENGLISH
        inbox.offer("https://bahr.eg/t/burullus-dawn?lang=en")

        inbox.dispatch(parser, opened)

        assertEquals(listOf("trip:burullus-dawn"), opened.calls)
    }

    @Test
    fun `a link with no lang leaves the language alone`() {
        opened.language = AppLanguage.ENGLISH
        inbox.offer("https://bahr.eg/t/burullus-dawn")

        inbox.dispatch(parser, opened)

        assertEquals(listOf("trip:burullus-dawn"), opened.calls)
    }

    @Test
    fun `a language switch that never lands does not hold the link - the second pass opens it`() {
        inbox.offer("https://bahr.eg/t/burullus-dawn?lang=en")

        inbox.dispatch(parser, opened)
        // Still Arabic (the write failed): the next dispatch opens the trip anyway, and asks only once.
        inbox.dispatch(parser, opened)

        assertEquals(listOf("language:en", "trip:burullus-dawn"), opened.calls)
        assertNull(inbox.pending.value)
    }

    @Test
    fun `with a booking under way a link in the other language opens at once - in the reader's language`() {
        opened.bookingInProgress = true
        inbox.offer("https://bahr.eg/t/burullus-dawn?lang=en")

        inbox.dispatch(parser, opened)

        // No switch: it would rebuild the NavHost and drop the booking the trip goes on top of.
        assertEquals(listOf("trip:burullus-dawn"), opened.calls)
        assertNull(inbox.pending.value)
    }

    @Test
    fun `the same link with lang tapped again later asks for its language again`() {
        inbox.offer("https://bahr.eg/t/burullus-dawn?lang=en")
        inbox.dispatch(parser, opened)
        inbox.dispatch(parser, opened)

        // The reader switched back to Arabic in Settings, then tapped the same link again.
        inbox.offer("https://bahr.eg/t/burullus-dawn?lang=en")
        inbox.dispatch(parser, opened)

        assertEquals(listOf("language:en", "trip:burullus-dawn", "language:en"), opened.calls)
    }

    @Test
    fun `a linked trip goes on top of a booking under way and above the list otherwise`() {
        assertEquals(TripLinkPlacement.OnTop, tripLinkPlacement(bookingInProgress = true))
        assertEquals(TripLinkPlacement.AboveList, tripLinkPlacement(bookingInProgress = false))
    }

    private class RecordingDestinations : DeepLinkDestinations {
        val calls = mutableListOf<String>()

        override var language: AppLanguage = AppLanguage.ARABIC

        override var bookingInProgress: Boolean = false

        override fun switchLanguage(language: AppLanguage) {
            calls += "language:${language.tag}"
        }

        override fun openTrip(slug: String) {
            calls += "trip:$slug"
        }

        override fun openList() {
            calls += "list"
        }
    }
}
