package eg.bahr.feature.trips.presentation

import androidx.paging.testing.ErrorRecovery
import androidx.paging.testing.LoadErrorHandler
import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeRecentSearchesStore
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripApiService
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.TripQuery
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.FacetDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Search (M4-M3). The clock is the test scheduler's virtual one: the debounce is a real `delay`
 * on it, so `advanceTimeBy` moves through it without waiting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchTripsViewModelTest {
    /** Answers every search with one trip named after it, and the chips for its filter. */
    private fun repository() =
        FakeTripRepository(
            listFiltered = { q ->
                page(listOf(trip(q.page, title = "${q.q}/${q.filter}")), facets = facets(filter = q.filter), totalItems = 3)
            },
        )

    private fun TestScope.searchVm(
        repository: FakeTripRepository = repository(),
        recents: FakeRecentSearchesStore = FakeRecentSearchesStore(),
    ) = SearchTripsViewModel(repository, recents, waitlistMemory()).also { runCurrent() }

    @Test
    fun `typing is searched once it rests for the debounce - not before`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)

            vm.onTextChange("felucca")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
            runCurrent()
            assertNull(vm.uiState.value.query, "still inside the debounce")

            advanceTimeBy(1)
            runCurrent()
            assertEquals("felucca", vm.uiState.value.query)
            assertEquals(listOf("felucca/null"), vm.trips.asSnapshot().map { it.title })
            assertEquals(listOf(TripQuery(0, q = "felucca")), repository.requestedQueries)
        }

    @Test
    fun `fast typing is one search for the last text`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)

            listOf("fe", "fel", "felu", "feluc").forEach { text ->
                vm.onTextChange(text)
                advanceTimeBy(SEARCH_DEBOUNCE_MILLIS / 2)
            }
            advanceUntilIdle()
            vm.trips.asSnapshot()

            assertEquals(listOf(TripQuery(0, q = "feluc")), repository.requestedQueries)
        }

    @Test
    fun `fewer than two letters search nothing - outer spaces do not count`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)

            listOf("f", " f ", "   ").forEach { text ->
                vm.onTextChange(text)
                advanceUntilIdle()
                assertNull(vm.uiState.value.query, "\"$text\" is too short to search")
            }
            vm.trips.asSnapshot()

            assertTrue(repository.requestedQueries.isEmpty(), "nothing is sent below $MIN_QUERY_LENGTH letters")
        }

    @Test
    fun `going below two letters ends the search at once - no debounce`() =
        runViewModelTest {
            val vm = searchVm()
            vm.onTextChange("birds")
            advanceUntilIdle()
            val before = currentTime

            vm.onTextChange("b")
            runCurrent()

            assertNull(vm.uiState.value.query)
            assertEquals(before, currentTime)
        }

    @Test
    fun `a new search cancels the request of the one before`() =
        runViewModelTest {
            var firstCancelled = false
            val repository =
                FakeTripRepository(
                    listFiltered = { q ->
                        if (q.q == "fel") {
                            try {
                                awaitCancellation()
                            } finally {
                                firstCancelled = true
                            }
                        }
                        page(listOf(trip(1, title = "${q.q}")))
                    },
                )
            val vm = searchVm(repository)
            vm.onTextChange("fel")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            // A presenter collecting the list, as the screen's would; it waits on the first search.
            backgroundScope.launch { vm.trips.asSnapshot() }
            runCurrent()
            assertEquals(listOf("fel"), repository.requestedQueries.map { it.q }, "the first search is in flight")

            vm.onTextChange("felucca")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            runCurrent()

            assertTrue(firstCancelled, "the stale request is cancelled, so it cannot answer late")
            assertEquals(listOf("felucca"), vm.trips.asSnapshot().map { it.title })
            assertEquals(listOf("fel", "felucca"), repository.requestedQueries.map { it.q })
        }

    @Test
    fun `the text goes as typed - arabic and its marks included - only outer spaces dropped`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)

            vm.onTextChange("  فُلوكة  البرلس ")
            advanceUntilIdle()
            vm.trips.asSnapshot()

            assertEquals(listOf("فُلوكة  البرلس"), repository.requestedQueries.map { it.q })
            assertEquals("  فُلوكة  البرلس ", vm.uiState.value.text, "the field keeps exactly what was typed")
        }

    @Test
    fun `control characters never go on the wire - the server refuses them`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)

            vm.onTextChange("bi\u0000rd\u0007s\u0085 lake\u007F")
            advanceUntilIdle()
            vm.trips.asSnapshot()

            assertEquals(listOf("birds lake"), repository.requestedQueries.map { it.q })
        }

    @Test
    fun `control characters do not count towards the two-letter minimum`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)

            vm.onTextChange("b\u0001")
            advanceUntilIdle()

            assertNull(vm.uiState.value.query)
        }

    @Test
    fun `text past the contract's limit is refused - never sent`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)
            val longest = "ب".repeat(TripApiService.MAX_QUERY_LENGTH)

            vm.onTextChange(longest)
            vm.onTextChange(longest + "ب")
            advanceUntilIdle()
            vm.trips.asSnapshot()

            assertEquals(longest, vm.uiState.value.text)
            assertEquals(listOf(longest), repository.requestedQueries.map { it.q })
        }

    @Test
    fun `the search key runs at once and remembers the search`() =
        runViewModelTest {
            val recents = FakeRecentSearchesStore()
            val vm = searchVm(recents = recents)

            vm.onTextChange("فلوكة")
            vm.search()
            runCurrent()

            assertEquals("فلوكة", vm.uiState.value.query)
            assertEquals(0L, currentTime, "no debounce on the search key")
            assertEquals(listOf("فلوكة"), vm.uiState.value.recents)
        }

    @Test
    fun `a pause in typing is not remembered - opening a result is`() =
        runViewModelTest {
            val recents = FakeRecentSearchesStore()
            val vm = searchVm(recents = recents)

            vm.onTextChange("birds")
            advanceUntilIdle()
            assertTrue(recents.searches.value.isEmpty())

            vm.onResultOpened()
            runCurrent()
            assertEquals(listOf("birds"), recents.searches.value)
        }

    @Test
    fun `a recent search fills the field - runs at once - and moves to the top`() =
        runViewModelTest {
            val repository = repository()
            val recents = FakeRecentSearchesStore(listOf("birds", "felucca"))
            val vm = searchVm(repository, recents)
            assertEquals(listOf("birds", "felucca"), vm.uiState.value.recents)

            vm.searchRecent("felucca")
            runCurrent()

            assertEquals("felucca", vm.uiState.value.text)
            assertEquals("felucca", vm.uiState.value.query)
            assertEquals(listOf("felucca", "birds"), vm.uiState.value.recents)
            assertEquals(listOf("felucca/null"), vm.trips.asSnapshot().map { it.title })
        }

    @Test
    fun `clear all forgets the recent searches`() =
        runViewModelTest {
            val vm = searchVm(recents = FakeRecentSearchesStore(listOf("birds")))

            vm.clearRecents()
            runCurrent()

            val recents = vm.uiState.value.recents
            assertTrue(recents.isEmpty())
        }

    @Test
    fun `a filter chip narrows the search - a new search keeps it - clearing the text drops it`() =
        runViewModelTest {
            val repository = repository()
            val vm = searchVm(repository)
            vm.onTextChange("birds")
            advanceUntilIdle()
            vm.trips.asSnapshot()
            val chipKeys =
                vm.uiState.value.filterChips
                    .map(FacetDto::key)
            assertEquals(listOf("all", "weekend", "under_400", "half_day"), chipKeys)

            vm.selectFilter("weekend")
            assertEquals(listOf("birds/weekend"), vm.trips.asSnapshot().map { it.title })
            vm.onTextChange("lake")
            advanceUntilIdle()
            assertEquals(listOf("lake/weekend"), vm.trips.asSnapshot().map { it.title })

            vm.onTextChange("")
            runCurrent()
            assertNull(vm.uiState.value.filter)
            val chips = vm.uiState.value.facets
            assertTrue(chips.isEmpty())
            assertEquals(
                listOf(
                    TripQuery(0, q = "birds"),
                    TripQuery(0, filter = "weekend", q = "birds"),
                    TripQuery(0, filter = "weekend", q = "lake"),
                ),
                repository.requestedQueries,
            )
        }

    @Test
    fun `facets and count come from the search's first page`() =
        runViewModelTest {
            val vm = searchVm()
            vm.onTextChange("birds")
            advanceUntilIdle()

            vm.trips.asSnapshot()

            assertEquals(3L, vm.uiState.value.totalItems)
            vm.onTextChange("birdsx")
            advanceUntilIdle()
            assertNull(vm.uiState.value.totalItems, "the count belongs to the search, until the new one answers")
        }

    @Test
    fun `a refused search is the list's error - the text and the filter stay`() =
        runViewModelTest {
            val refused = AppResult.Failure(AppError.Api(ApiErrorCodes.VALIDATION_FAILED, null, 400))
            val repository = FakeTripRepository(listFiltered = { refused })
            val vm = searchVm(repository)
            vm.onTextChange("birds")
            vm.selectFilter("weekend")
            advanceUntilIdle()

            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })
            advanceUntilIdle()

            assertEquals("birds", vm.uiState.value.query)
            assertEquals("weekend", vm.uiState.value.filter)
            assertEquals(1, repository.requestedQueries.size, "no fallback query: nothing is dropped behind the user's back")
        }

    @Test
    fun `the results page on - best match first - in the server's order`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    listFiltered = { q -> page(listOf(trip(q.page * 2 + 1), trip(q.page * 2)), page = q.page, totalPages = 2) },
                )
            val vm = searchVm(repository)
            vm.onTextChange("lake")
            advanceUntilIdle()

            val shown = vm.trips.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf("trip-1", "trip-0", "trip-3", "trip-2"), shown.map { it.slug }, "never re-sorted")
            assertTrue(repository.requestedQueries.all { it.q == "lake" })
        }
}
