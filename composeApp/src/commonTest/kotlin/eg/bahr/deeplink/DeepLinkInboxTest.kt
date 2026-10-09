package eg.bahr.deeplink

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

    @Test
    fun `a linked trip goes on top of a booking under way and above the list otherwise`() {
        assertEquals(TripLinkPlacement.OnTop, tripLinkPlacement(bookingInProgress = true))
        assertEquals(TripLinkPlacement.AboveList, tripLinkPlacement(bookingInProgress = false))
    }

    private class RecordingDestinations : DeepLinkDestinations {
        val calls = mutableListOf<String>()

        override fun openTrip(slug: String) {
            calls += "trip:$slug"
        }

        override fun openList() {
            calls += "list"
        }
    }
}
