package eg.bahr.feature.search.presentation

import eg.bahr.core.catalog.data.SEARCH_HOSTS_SIZE
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.search.data.FakeHostListRepository
import eg.bahr.feature.search.data.FakeHostListRepository.Companion.hosts
import eg.bahr.feature.search.data.FakeRecentSearchesStore
import eg.bahr.feature.search.data.FakeTripListRepository
import eg.bahr.feature.search.data.HostRequest
import eg.bahr.feature.search.data.LastSearch
import eg.bahr.feature.search.data.TripFixtures.page
import eg.bahr.feature.search.data.TripFixtures.trip
import eg.bahr.feature.search.data.waitlistMemory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MV-M1: the hosts above search's results, `GET /hosts?q=<text>&size=3` beside `GET /trips?q=` (MV-B1: one contract,
 * no combined endpoint). A failure costs the hosts block only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchHostsTest {
    private val trips = FakeTripListRepository(listFiltered = { q -> page(listOf(trip(1, title = "${q.q}"))) })

    private fun TestScope.vm(hosts: FakeHostListRepository) =
        SearchTripsViewModel(trips, FakeRecentSearchesStore(), waitlistMemory(), LastSearch(), hosts).also { runCurrent() }

    @Test
    fun `a search asks for its first three hosts by the same text`() =
        runViewModelTest {
            val hostLists = FakeHostListRepository(listHosts = { hosts(card("hoda-eissa", "Hoda Eissa")) })
            val search = vm(hostLists)

            search.onTextChange("hoda")
            advanceUntilIdle()

            assertEquals(listOf(HostRequest(HostListQuery(q = "hoda"), page = 0, size = SEARCH_HOSTS_SIZE)), hostLists.requests)
            assertEquals(
                listOf("hoda-eissa"),
                search.uiState.value.hosts
                    .map { it.slug },
            )
        }

    @Test
    fun `no search asks for no hosts - and clearing the text hides them`() =
        runViewModelTest {
            val hostLists = FakeHostListRepository(listHosts = { hosts(card("hoda-eissa", "Hoda Eissa")) })
            val search = vm(hostLists)
            advanceUntilIdle()
            assertTrue(hostLists.requests.isEmpty())

            search.onTextChange("hoda")
            advanceUntilIdle()
            search.onTextChange("")
            advanceUntilIdle()

            assertTrue(
                search.uiState.value.hosts
                    .isEmpty(),
            )
            assertEquals(1, hostLists.requests.size)
        }

    @Test
    fun `a failed hosts read hides the block - the trips still search`() =
        runViewModelTest {
            val search = vm(FakeHostListRepository(listHosts = { AppResult.Failure(AppError.Network) }))

            search.onTextChange("hoda")
            advanceUntilIdle()

            assertTrue(
                search.uiState.value.hosts
                    .isEmpty(),
            )
            assertEquals("hoda", search.uiState.value.query)
        }

    @Test
    fun `a trips filter chip does not ask for the hosts again`() =
        runViewModelTest {
            val hostLists = FakeHostListRepository(listHosts = { hosts(card("hoda-eissa", "Hoda Eissa")) })
            val search = vm(hostLists)
            search.onTextChange("hoda")
            advanceUntilIdle()

            search.selectFilter("weekend")
            advanceUntilIdle()

            assertEquals(1, hostLists.requests.size)
        }

    @Test
    fun `Retry reads the hosts again with the trips`() =
        runViewModelTest {
            var fail = true
            val hostLists =
                FakeHostListRepository(listHosts = { if (fail) AppResult.Failure(AppError.Network) else hosts(card("a", "A")) })
            val search = vm(hostLists)
            search.onTextChange("hoda")
            advanceUntilIdle()

            fail = false
            search.retry()
            advanceUntilIdle()

            assertEquals(
                listOf("a"),
                search.uiState.value.hosts
                    .map { it.slug },
            )
        }

    private fun card(
        slug: String,
        name: String,
    ) = HostCardDto(slug = slug, name = name, publishedTrips = 1)
}
