package eg.bahr.feature.trips.data

import eg.bahr.core.datastore.MAX_RECENT_SEARCHES
import eg.bahr.core.datastore.RecentSearchesStore
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-written fake of core:datastore's store, with the same newest-first, de-duplicated, capped list. */
internal class FakeRecentSearchesStore(
    initial: List<String> = emptyList(),
) : RecentSearchesStore {
    override val searches = MutableStateFlow(initial)

    override suspend fun add(query: String) {
        searches.value = (listOf(query) + searches.value.filter { it != query }).take(MAX_RECENT_SEARCHES)
    }

    override suspend fun clear() {
        searches.value = emptyList()
    }
}
