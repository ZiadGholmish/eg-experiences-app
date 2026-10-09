package eg.bahr.feature.trips.presentation.components

import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.feature.trips.model.NextDepartureDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripCardMappingTest {
    @Test
    fun `every contract tone maps to its badge tone`() {
        assertEquals(BahrBadge.Tone.Primary, badgeTone("primary"))
        assertEquals(BahrBadge.Tone.Secondary, badgeTone("secondary"))
        assertEquals(BahrBadge.Tone.Tertiary, badgeTone("tertiary"))
        assertEquals(BahrBadge.Tone.Quaternary, badgeTone("quaternary"))
        assertEquals(BahrBadge.Tone.Success, badgeTone("success"))
    }

    @Test
    fun `an unknown or missing tone is primary`() {
        assertEquals(BahrBadge.Tone.Primary, badgeTone("ultraviolet"))
        assertEquals(BahrBadge.Tone.Primary, badgeTone("SECONDARY"))
        assertEquals(BahrBadge.Tone.Primary, badgeTone(null))
    }

    @Test
    fun `sold out shows zero seats whatever the count says`() {
        assertEquals(0, NextDepartureDto(seatsRemaining = 3, soldOut = true).seatsLeft())
        assertEquals(6, NextDepartureDto(seatsRemaining = 6, soldOut = false).seatsLeft())
        assertEquals(6, NextDepartureDto(seatsRemaining = 6).seatsLeft())
        assertNull(NextDepartureDto().seatsLeft())
    }
}
