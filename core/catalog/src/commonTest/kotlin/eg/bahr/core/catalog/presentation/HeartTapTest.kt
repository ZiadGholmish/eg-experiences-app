package eg.bahr.core.catalog.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

/** MP-M1: a screen's heart tap goes to its view model, or to the sign-in sheet for a guest. */
class HeartTapTest {
    @Test
    fun `a signed-in tap does not open the sign-in sheet`() {
        var signIns = 0
        val taps = mutableListOf<String>()
        heartTap<String>(toggle = {
            taps += it
            true
        }, onSignIn = { signIns++ })("trip-1")
        assertEquals(listOf("trip-1"), taps)
        assertEquals(0, signIns)
    }

    @Test
    fun `a guest's tap opens the sign-in sheet`() {
        var signIns = 0
        heartTap<String>(toggle = { false }, onSignIn = { signIns++ })("trip-1")
        assertEquals(1, signIns)
    }
}
