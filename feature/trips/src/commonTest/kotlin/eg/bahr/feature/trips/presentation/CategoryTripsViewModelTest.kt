package eg.bahr.feature.trips.presentation

import androidx.paging.testing.ErrorRecovery
import androidx.paging.testing.LoadErrorHandler
import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.TripQuery
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.FacetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The category page (M4-M1b): `GET /trips?category=&filter=`, paged, with its filter chips. */
class CategoryTripsViewModelTest {
    /** Answers every query with one trip named after it, and the chips for that query. */
    private fun repository(totalPages: Int = 1) =
        FakeTripRepository(
            listFiltered = { q ->
                page(
                    listOf(trip(q.page, title = "${q.category}/${q.filter}")),
                    page = q.page,
                    totalPages = totalPages,
                    facets = facets(category = q.category, filter = q.filter),
                    totalItems = 7,
                )
            },
        )

    @Test
    fun `the page lists the category's trips and names itself from its facet`() =
        runViewModelTest {
            val repository = repository()
            val vm = CategoryTripsViewModel("birds", title = "Birds (from Home)", repository = repository, waitlists = waitlistMemory())
            assertEquals("Birds (from Home)", vm.uiState.value.fallbackTitle, "the opener's label shows until the facets arrive")

            val shown = vm.trips.asSnapshot()

            assertEquals(listOf("birds/null"), shown.map { it.title })
            assertEquals(listOf(TripQuery(0, "birds", null)), repository.requestedQueries)
            val header = vm.uiState.value.categoryFacet
            assertEquals("Birds", header?.label)
            assertEquals("flutter_dash", header?.icon)
            assertEquals("secondary", header?.tone)
            assertEquals(7L, vm.uiState.value.totalItems)
        }

    @Test
    fun `only the filter chips are drawn - in the served order - with All selected at first`() =
        runViewModelTest {
            val vm = CategoryTripsViewModel("birds", null, repository(), waitlistMemory())
            vm.trips.asSnapshot()

            val chips = vm.uiState.value.filterChips
            assertEquals(listOf("all", "weekend", "under_400", "half_day"), chips.map { it.key })
            assertEquals(listOf(true, false, false, false), chips.map(vm.uiState.value::isSelected))
            assertEquals(0, chips.first { it.key == "under_400" }.count, "zero-count chips stay, to be drawn dimmed")
        }

    @Test
    fun `a chip is matched on type and key - a category that shares a filter's key is not a filter`() {
        val shared = FacetDto(type = "category", key = "weekend", label = "Weekend trips", tone = "primary", count = 2)
        val state = CategoryTripsUiState(category = "weekend", facets = facets() + shared)

        assertEquals(shared, state.categoryFacet)
        assertEquals(1, state.filterChips.count { it.key == "weekend" })
    }

    @Test
    fun `a filter chip starts the list over with that filter - tapping it again clears it`() =
        runViewModelTest {
            val repository = repository()
            val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())
            vm.trips.asSnapshot()

            vm.selectFilter("weekend")
            val filtered = vm.trips.asSnapshot()
            assertEquals(listOf("birds/weekend"), filtered.map { it.title })
            assertTrue(
                vm.uiState.value.filterChips
                    .first { it.key == "weekend" }
                    .let(vm.uiState.value::isSelected),
            )

            vm.selectFilter("weekend")
            assertNull(vm.uiState.value.filter, "the active chip again clears the filter")
            assertEquals(listOf("birds/null"), vm.trips.asSnapshot().map { it.title })
            assertEquals(
                listOf(TripQuery(0, "birds", null), TripQuery(0, "birds", "weekend"), TripQuery(0, "birds", null)),
                repository.requestedQueries,
            )
        }

    @Test
    fun `the All chip clears the filter`() =
        runViewModelTest {
            val vm = CategoryTripsViewModel("birds", null, repository(), waitlistMemory())
            vm.selectFilter("half_day")

            vm.selectFilter("all")

            assertNull(vm.uiState.value.filter)
        }

    @Test
    fun `a refused key falls back to every trip once - not an error`() =
        runViewModelTest {
            val refused = AppResult.Failure(AppError.Api(ApiErrorCodes.VALIDATION_FAILED, "Unknown category", 400))
            val repository =
                FakeTripRepository(
                    listFiltered = { q -> if (q.category != null) refused else page(listOf(trip(1)), facets = facets()) },
                )
            val vm = CategoryTripsViewModel("gone", "Old banner", repository, waitlistMemory())

            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })
            val shown = vm.trips.asSnapshot()

            assertEquals(listOf("trip-1"), shown.map { it.slug })
            assertNull(vm.uiState.value.category, "the page is now every trip")
            assertNull(vm.uiState.value.categoryFacet)
            assertEquals(listOf(TripQuery(0, "gone", null), TripQuery(0, null, null)), repository.requestedQueries)
        }

    @Test
    fun `when every query is refused the fallback stops after one retry`() =
        runViewModelTest {
            val refused = AppResult.Failure(AppError.Api(ApiErrorCodes.VALIDATION_FAILED, null, 400))
            val repository = FakeTripRepository(listFiltered = { refused })
            val vm = CategoryTripsViewModel("gone", null, repository, waitlistMemory())

            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })
            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })

            assertEquals(listOf(TripQuery(0, "gone", null), TripQuery(0, null, null)), repository.requestedQueries)
        }

    @Test
    fun `a refused filter on a category that has loaded drops only the filter`() =
        runViewModelTest {
            val refused = AppResult.Failure(AppError.Api(ApiErrorCodes.VALIDATION_FAILED, null, 400))
            val repository =
                FakeTripRepository(
                    listFiltered = { q ->
                        val refusesFilter = q.filter != null
                        if (refusesFilter) refused else page(listOf(trip(1)), facets = facets(category = q.category))
                    },
                )
            val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())
            vm.trips.asSnapshot()

            vm.selectFilter("weekend")
            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })
            val shown = vm.trips.asSnapshot()

            assertEquals("birds", vm.uiState.value.category, "the category page stays the category page")
            assertNull(vm.uiState.value.filter)
            assertEquals(listOf("trip-1"), shown.map { it.slug })
            assertEquals(
                listOf(TripQuery(0, "birds", null), TripQuery(0, "birds", "weekend"), TripQuery(0, "birds", null)),
                repository.requestedQueries,
            )
        }

    @Test
    fun `a new filter forgets the old count until it answers`() =
        runViewModelTest {
            val vm = CategoryTripsViewModel("birds", null, repository(), waitlistMemory())
            vm.trips.asSnapshot()

            vm.selectFilter("weekend")

            assertNull(vm.uiState.value.totalItems)
        }

    @Test
    fun `any other failure is the list's error - the category stays`() =
        runViewModelTest {
            val repository = FakeTripRepository(listFiltered = { AppResult.Failure(AppError.Network) })
            val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())

            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })

            assertEquals("birds", vm.uiState.value.category)
            assertEquals(1, repository.requestedQueries.size, "no fallback query for a network error")
        }

    @Test
    fun `facets stay from the previous query until the new one answers`() =
        runViewModelTest {
            val vm = CategoryTripsViewModel("birds", null, repository(), waitlistMemory())
            vm.trips.asSnapshot()
            val before = vm.uiState.value.facets

            vm.selectFilter("weekend")

            assertEquals(before, vm.uiState.value.facets, "the chips do not blink out on a tap")
            assertFalse(
                vm.uiState.value.facets
                    .isEmpty(),
            )
        }

    @Test
    fun `the category's trips page on to the end`() =
        runViewModelTest {
            val repository = repository(totalPages = 3)
            val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())

            vm.trips.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf(0, 1, 2), repository.requestedQueries.map { it.page })
            assertTrue(repository.requestedQueries.all { it.category == "birds" })
        }
}
