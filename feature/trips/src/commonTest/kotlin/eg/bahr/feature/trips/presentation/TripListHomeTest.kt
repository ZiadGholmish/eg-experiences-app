package eg.bahr.feature.trips.presentation

import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.HomeActionDto
import eg.bahr.feature.trips.model.HomeBannerDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.ImageDto
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripsSectionDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's server-driven sections on the list view model (M4-M1a). */
class TripListHomeTest {
    private val errors = AppErrorController()

    private val banners =
        BannersSectionDto(
            id = "b",
            type = "banners",
            layout = "carousel",
            aspectRatio = "16:9",
            items = listOf(HomeBannerDto("b1", "Dawn", ImageDto("https://x/1.png", 900, 560), HomeActionDto("trip", "trip-1"))),
        )
    private val row = TripsSectionDto(id = "r", type = "trips", title = "Featured", layout = "row", items = listOf(trip(1)))

    @Test
    fun `sections show above the list in the server's order - unknown ones dropped`() =
        runViewModelTest {
            val sections = listOf(row, SkippedSectionDto("stories", "unknown type"), banners)
            val vm =
                TripListViewModel(
                    FakeTripRepository(listTrips = { page(listOf(trip(2))) }, home = { AppResult.Success(HomeDto(sections)) }),
                    errors,
                )

            advanceUntilIdle()

            assertEquals(listOf(row, banners), vm.uiState.value.sections)
            assertEquals(
                listOf("trip-2"),
                vm.uiState.value.trips
                    .map { it.slug },
            )
        }

    @Test
    fun `an empty section is dropped`() {
        val empty = row.copy(items = emptyList())
        val logged = mutableListOf<String>()

        assertEquals(listOf(banners), drawableSections(listOf(empty, banners)) { logged += it })
        assertEquals(1, logged.size)
    }

    @Test
    fun `a failed home leaves the list alone and is not shown as an error`() =
        runViewModelTest {
            val vm =
                TripListViewModel(
                    FakeTripRepository(listTrips = { page(listOf(trip(1))) }, home = { AppResult.Failure(AppError.Network) }),
                    errors,
                )

            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.showsLoading)
            assertNull(state.error)
            assertTrue(state.sections.isEmpty())
            assertEquals(1, state.trips.size)
            assertNull(errors.current.first(), "a missing Home is not a message: the list is the page")
        }

    @Test
    fun `a slow home holds the list back only until the wait runs out - then sections insert above it`() =
        runViewModelTest {
            val home = CompletableDeferred<AppResult<HomeDto>>()
            val vm =
                TripListViewModel(
                    FakeTripRepository(listTrips = { page(listOf(trip(1))) }, home = { home.await() }),
                    errors,
                )

            runCurrent()
            assertFalse(vm.uiState.value.isLoading, "the list is in")
            assertTrue(vm.uiState.value.showsLoading, "Home gets a short wait, so its sections usually land with the list")

            advanceTimeBy(HOME_WAIT_MILLIS - 1)
            runCurrent()
            assertTrue(vm.uiState.value.showsLoading)

            advanceTimeBy(1)
            runCurrent()
            assertFalse(vm.uiState.value.showsLoading, "the wait is capped: the list shows without Home")
            assertTrue(
                vm.uiState.value.sections
                    .isEmpty(),
            )

            home.complete(AppResult.Success(HomeDto(listOf(banners))))
            runCurrent()
            assertEquals(listOf(banners), vm.uiState.value.sections, "late sections are still shown")
            assertFalse(vm.uiState.value.showsLoading)
        }

    @Test
    fun `a home that never answers does not keep the list behind the spinner`() =
        runViewModelTest {
            val vm =
                TripListViewModel(
                    FakeTripRepository(listTrips = { page(listOf(trip(1))) }, home = { awaitCancellation() }),
                    errors,
                )

            advanceTimeBy(HOME_WAIT_MILLIS)
            runCurrent()

            assertFalse(vm.uiState.value.showsLoading)
            assertEquals(
                listOf("trip-1"),
                vm.uiState.value.trips
                    .map { it.slug },
            )
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `a home that answers first ends the wait at once`() =
        runViewModelTest {
            val vm =
                TripListViewModel(
                    FakeTripRepository(listTrips = { page(listOf(trip(1))) }, home = { AppResult.Success(HomeDto(listOf(row))) }),
                    errors,
                )

            runCurrent()

            assertEquals(0L, currentTime, "no virtual time passed")
            assertFalse(vm.uiState.value.showsLoading)
            assertEquals(listOf(row), vm.uiState.value.sections)
        }

    @Test
    fun `retry reads home again - and a failed retry keeps the sections already shown`() =
        runViewModelTest {
            var homeAnswer: AppResult<HomeDto> = AppResult.Success(HomeDto(listOf(banners)))
            val repository = FakeTripRepository(listTrips = { page(listOf(trip(1))) }, home = { homeAnswer })
            val vm = TripListViewModel(repository, errors)
            advanceUntilIdle()

            homeAnswer = AppResult.Failure(AppError.Timeout)
            vm.refresh()
            advanceUntilIdle()

            assertEquals(2, repository.homeReads)
            assertEquals(listOf(banners), vm.uiState.value.sections)
        }
}
