package eg.bahr.core.catalog.presentation

import eg.bahr.core.catalog.data.JoinedWaitlist
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a card shows the "Waiting list" tag (M4-M5). Moved here from feature:trips (M4-M7 / M4-M1c
 * review #6); that every list reads the stored joins stays with the lists' view models, in
 * feature:trips' `WaitlistTagsTest` and feature:home's `WaitlistTagsHomeTest`.
 */
class WaitlistTagsTest {
    private val saturday = LocalDate.parse("2026-10-24")

    private fun joined(
        slug: String = "trip-1",
        date: LocalDate = saturday,
    ) = WaitlistTags.of(listOf(JoinedWaitlist("dep-3", slug, date, partySize = 2, email = "sara@example.com")))

    private fun trip(nextDeparture: NextDepartureDto? = null) =
        TripCardDto(
            slug = "trip-1",
            title = "Trip 1",
            durationLabel = "05:00 → 22:00",
            price = MoneyDto(amount = 450, currencyCode = "EGP"),
            nextDeparture = nextDeparture,
        )

    private fun card(
        date: LocalDate? = saturday,
        soldOut: Boolean? = true,
    ) = trip(NextDepartureDto(date = date, seatsRemaining = 0, capacity = 18, soldOut = soldOut))

    @Test
    fun `a sold-out next departure the device joined is tagged`() {
        assertTrue(joined().shows(card()))
    }

    @Test
    fun `not tagged when the next departure has seats - or no sold-out flag`() {
        assertFalse(joined().shows(card(soldOut = false)))
        assertFalse(joined().shows(card(soldOut = null)))
    }

    @Test
    fun `not tagged for a join on another date or another trip`() {
        assertFalse(joined(date = LocalDate.parse("2026-10-31")).shows(card()))
        assertFalse(joined(slug = "trip-2").shows(card()))
    }

    @Test
    fun `not tagged without a next departure or its date`() {
        assertFalse(joined().shows(trip()))
        assertFalse(joined().shows(card(date = null)))
        assertFalse(WaitlistTags.None.shows(card()))
    }
}
