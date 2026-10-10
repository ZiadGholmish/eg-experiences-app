package eg.bahr.feature.trips.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.feature.trips.data.JoinedWaitlist
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.model.TripCardDto
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Which list cards carry the "Waiting list" tag (M4-M5): the stored joins, as (trip slug, date).
 *
 * A card knows only its trip's `nextDeparture`, which has a date but no departure id, so a card is
 * tagged exactly when:
 * - its `nextDeparture.soldOut` is true (D3: only a sold-out date has a list; and by the contract it
 *   is true only when every upcoming open date of the trip is full), and
 * - the device joined the waiting list of that trip on that same date.
 *
 * A join for another date of the trip does not tag the card: the card shows one date, and the tag
 * speaks about that one. No clock is needed: a passed or bookable date is never a sold-out next
 * departure. Matched by date, so two buses of one trip on the same day count as one (the contract's
 * `nextDeparture` has no id).
 */
@Immutable
internal data class WaitlistTags(
    private val joined: Set<Pair<String, LocalDate>> = emptySet(),
) {
    fun shows(trip: TripCardDto): Boolean {
        val next = trip.nextDeparture ?: return false
        val date = next.date ?: return false
        return next.soldOut == true && (trip.slug to date) in joined
    }

    companion object {
        val None = WaitlistTags()

        fun of(joins: List<JoinedWaitlist>): WaitlistTags = WaitlistTags(joins.mapTo(mutableSetOf()) { it.tripSlug to it.date })
    }
}

/**
 * Keeps a list's [WaitlistTags] in step with the stored joins for as long as the view model lives,
 * so a join made on a trip page tags its card when the user comes back to the list.
 */
internal fun ViewModel.collectWaitlistTags(
    waitlists: WaitlistMemory,
    onTags: (WaitlistTags) -> Unit,
) {
    viewModelScope.launch {
        waitlists.joins
            .map(WaitlistTags::of)
            .distinctUntilChanged()
            .collect(onTags)
    }
}
