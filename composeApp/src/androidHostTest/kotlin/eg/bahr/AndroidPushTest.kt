package eg.bahr

import android.app.Application
import android.content.Intent
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.deeplink.DeepLinkDestinations
import eg.bahr.deeplink.DeepLinkParser
import eg.bahr.push.PUSH_LINK
import eg.bahr.push.PUSH_TYPE
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M3-M5 review #5: a tap on a push the system showed in the background reaches MainActivity as the launcher intent
 * with the push's data as string extras ([AndroidPush.openFromExtras]): its link waits in the deep-link inbox, and a
 * "place opened" push opens the trip saying so. An intent without our link is not a push.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidPushTest {
    private class Opened : DeepLinkDestinations {
        val trips = mutableListOf<Triple<String, String?, Boolean>>()
        override val language = AppLanguage.ARABIC
        override val bookingInProgress = false

        override fun switchLanguage(language: AppLanguage) = Unit

        override fun openTrip(
            slug: String,
            date: String?,
            placeOpened: Boolean,
        ) {
            trips += Triple(slug, date, placeOpened)
        }

        override fun openList() = Unit
    }

    private val parser = DeepLinkParser(host = HOST, allowsHttp = false)

    /** The inbox is one per process: whatever a test left in it is opened (and so dropped) before the next. */
    @After
    fun drainInbox() = AppDeepLinkInbox.dispatch(parser, Opened())

    @Test
    fun `a tapped place-opened push opens its trip on that date - saying a place opened`() {
        val tap =
            Intent(Intent.ACTION_MAIN)
                .putExtra(PUSH_LINK, "https://$HOST/t/burullus-dawn?date=2026-10-24")
                .putExtra(PUSH_TYPE, "waitlist_seat_opened")

        assertTrue(AndroidPush.openFromExtras(tap))

        val opened = Opened()
        AppDeepLinkInbox.dispatch(parser, opened)
        assertEquals(listOf<Triple<String, String?, Boolean>>(Triple("burullus-dawn", "2026-10-24", true)), opened.trips)
    }

    @Test
    fun `another push type opens its link without the claim`() {
        AndroidPush.openFromExtras(
            Intent().putExtra(PUSH_LINK, "https://$HOST/t/burullus-dawn?date=2026-10-24").putExtra(PUSH_TYPE, "broadcast"),
        )

        val opened = Opened()
        AppDeepLinkInbox.dispatch(parser, opened)
        assertEquals(listOf<Triple<String, String?, Boolean>>(Triple("burullus-dawn", "2026-10-24", false)), opened.trips)
    }

    @Test
    fun `an intent without our link is not a push`() {
        assertFalse(AndroidPush.openFromExtras(Intent(Intent.ACTION_MAIN)))
        assertNull(AppDeepLinkInbox.pending.value)
    }

    private companion object {
        const val HOST = "bahr.example"
    }
}
