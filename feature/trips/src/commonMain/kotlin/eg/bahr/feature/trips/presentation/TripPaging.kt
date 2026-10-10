package eg.bahr.feature.trips.presentation

import androidx.paging.PagingData
import androidx.paging.filter
import eg.bahr.feature.trips.model.TripCardDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Drops a card already shown earlier in the same list. A trip published between two page reads
 * shifts the next page by one, so a card can repeat, and the lists key their items by slug: a
 * repeated key crashes a LazyColumn. Paging does not de-duplicate, so this does it once per
 * generation (a refresh brings a new [PagingData], and with it a fresh set).
 *
 * Paging applies the filter to pages in the order they arrive, one at a time, so the set needs no lock.
 */
internal fun Flow<PagingData<TripCardDto>>.distinctTrips(): Flow<PagingData<TripCardDto>> =
    map { generation ->
        val seen = mutableSetOf<String>()
        generation.filter { seen.add(it.slug) }
    }
