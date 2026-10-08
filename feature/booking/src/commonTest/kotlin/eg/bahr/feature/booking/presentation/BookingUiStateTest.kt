package eg.bahr.feature.booking.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookingUiStateTest {
    private val valid =
        BookingUiState(departureId = 1, partySize = 2, guestName = "Mona", guestPhone = "+201001234567")

    @Test
    fun `a complete form can place a hold`() {
        assertTrue(valid.canPlaceHold)
    }

    @Test
    fun `a hold in flight blocks a second one`() {
        // The button renders this as loading (coral + progress), not as disabled.
        assertFalse(valid.copy(isPlacingHold = true).canPlaceHold)
    }

    @Test
    fun `a blank name or short phone cannot place a hold`() {
        assertFalse(valid.copy(guestName = " ").canPlaceHold)
        assertFalse(valid.copy(guestPhone = "123").canPlaceHold)
    }
}
