package eg.bahr.feature.booking.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The booking feature's navigation functions on a real back stack (a NavHost with empty
 * destinations): a trip opened by a link over a live hold, Continue from it, and the way back to
 * date + party when a hold ends. The trip and list routes are stand-ins, as this feature may not
 * import `feature:trips`; `:composeApp` pushes the linked trip the same way (no `popUpTo`).
 */
@RunWith(RobolectricTestRunner::class)
class BookingNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private val hold =
        HoldRoute(
            ref = "BRL-7K4M2X9P",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
            totalAmount = 900,
            totalCurrency = "EGP",
            guestPhone = "01012345678",
        )

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = ListStub) {
                composable<ListStub> {}
                composable<TripStub> {}
                composable<BookingRoute> {}
                composable<HoldRoute> {}
            }
        }
        compose.waitForIdle()
    }

    private fun onNav(block: NavHostController.() -> Unit) {
        compose.runOnUiThread { nav.block() }
        compose.waitForIdle()
    }

    private inline fun <reified T : Any> onTop(): Boolean = nav.currentBackStackEntry?.destination?.hasRoute<T>() == true

    /** List → trip → date + party → held seats → a trip opened by a link on top. */
    private fun holdUnderLinkedTrip() {
        onNav {
            navigate(TripStub("burullus-dawn"))
            navigateToBooking("burullus-dawn", "dep-2")
            navigateToHold(hold)
            navigate(TripStub("burullus-murals"))
        }
    }

    @Test
    fun `nothing in progress on the list`() {
        assertFalse(nav.hasBookingInProgress())
        onNav { navigateToBooking("burullus-dawn", "dep-2") }
        assertTrue(nav.hasBookingInProgress())
    }

    @Test
    fun `back from a trip opened over a hold returns to the countdown`() {
        holdUnderLinkedTrip()
        assertTrue(nav.hasBookingInProgress())

        onNav { popBackStack() }

        assertTrue(onTop<HoldRoute>())
    }

    @Test
    fun `continue from a trip opened over a hold goes back to that hold instead of a second booking`() {
        holdUnderLinkedTrip()

        onNav { navigateToBooking("burullus-murals", "dep-9") }

        assertTrue(onTop<HoldRoute>())
        assertEquals(true, nav.currentBackStackEntry?.savedStateHandle?.get<Boolean>(ALREADY_HELD_KEY))
        // Only the one date + party, the one under the hold.
        val bookings = nav.currentBackStack.value.count { it.destination.hasRoute<BookingRoute>() }
        assertEquals(1, bookings)
    }

    @Test
    fun `without a hold continue opens date and party`() {
        onNav {
            navigate(TripStub("burullus-dawn"))
            navigateToBooking("burullus-dawn", "dep-2")
        }

        assertTrue(onTop<BookingRoute>())
    }

    @Test
    fun `a stored hold handed back instead of a second one replaces that date and party`() {
        onNav {
            navigate(TripStub("burullus-murals"))
            navigateToBooking("burullus-murals", "dep-9")
            navigateToHold(hold, alreadyHeld = true)
        }

        assertTrue(onTop<HoldRoute>())
        assertEquals(true, nav.currentBackStackEntry?.savedStateHandle?.get<Boolean>(ALREADY_HELD_KEY))
        // The date + party that was about to place a second hold is gone: it belonged to another booking.
        assertEquals(0, nav.currentBackStack.value.count { it.destination.hasRoute<BookingRoute>() })

        // When the hold ends the user lands on the trip they were on.
        onNav { returnToDateSelection(holdExpired = false) }
        assertTrue(onTop<TripStub>())
    }

    @Test
    fun `the Home card opens the hold once however often it is tapped and its end returns to Home`() {
        onNav {
            navigateToHold(hold)
            navigateToHold(hold)
        }

        assertTrue(onTop<HoldRoute>())
        assertEquals(1, nav.currentBackStack.value.count { it.destination.hasRoute<HoldRoute>() })
        assertEquals(null, nav.currentBackStackEntry?.savedStateHandle?.get<Boolean>(ALREADY_HELD_KEY))

        onNav { returnToDateSelection(holdExpired = true) }
        assertTrue(onTop<ListStub>())
    }

    @Test
    fun `a hold that ran out returns to its date and party and says so`() {
        onNav {
            navigateToBooking("burullus-dawn", "dep-2")
            navigateToHold(hold)
            returnToDateSelection(holdExpired = true)
        }

        assertTrue(onTop<BookingRoute>())
        assertEquals(HOLD_ENDED_EXPIRED, nav.currentBackStackEntry?.savedStateHandle?.get<String>(HOLD_ENDED_KEY))
    }

    @Test
    fun `a released hold returns to date and party marked released`() {
        onNav {
            navigateToBooking("burullus-dawn", "dep-2")
            navigateToHold(hold)
            returnToDateSelection(holdExpired = false)
        }

        assertTrue(onTop<BookingRoute>())
        assertEquals(HOLD_ENDED_RELEASED, nav.currentBackStackEntry?.savedStateHandle?.get<String>(HOLD_ENDED_KEY))
    }
}

/** Stand-in for the trip list (navigation reflects on the route class, so it is not private). */
@Serializable
internal data object ListStub

/** Stand-in for a trip page. */
@Serializable
internal data class TripStub(
    val slug: String,
)
