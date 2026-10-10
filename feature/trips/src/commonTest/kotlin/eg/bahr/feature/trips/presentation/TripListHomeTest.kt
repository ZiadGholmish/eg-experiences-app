package eg.bahr.feature.trips.presentation

import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.BannersSectionDto
import eg.bahr.feature.trips.model.HomeActionDto
import eg.bahr.feature.trips.model.HomeBannerDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.ImageDto
import eg.bahr.feature.trips.model.SkippedSectionDto
import eg.bahr.feature.trips.model.TripsSectionDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Home's server-driven sections on the list view model (M4-M1a). */
class TripListHomeTest {
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
    fun `sections show in the server's order - unknown ones dropped`() =
        runViewModelTest {
            val sections = listOf(row, SkippedSectionDto("stories", "unknown type"), banners)
            val vm = TripListViewModel(FakeTripRepository(home = { AppResult.Success(HomeDto(sections)) }), waitlistMemory())

            advanceUntilIdle()

            assertEquals(listOf(row, banners), vm.uiState.value.sections)
            assertFalse(vm.uiState.value.awaitingHome)
        }

    @Test
    fun `an empty section is dropped`() {
        val empty = row.copy(items = emptyList())
        val logged = mutableListOf<String>()

        assertEquals(listOf(banners), drawableSections(listOf(empty, banners)) { logged += it })
        assertEquals(1, logged.size)
    }

    @Test
    fun `a failed home ends the wait and is not shown as an error`() =
        runViewModelTest {
            val repository = FakeTripRepository(listTrips = { page(listOf(trip(1))) }, home = { AppResult.Failure(AppError.Network) })
            val vm = TripListViewModel(repository, waitlistMemory())

            advanceUntilIdle()

            assertFalse(vm.uiState.value.awaitingHome)
            assertTrue(
                vm.uiState.value.sections
                    .isEmpty(),
            )
            assertEquals(listOf("trip-1"), vm.trips.asSnapshot().map { it.slug }, "the list is the page, whatever Home does")
        }

    @Test
    fun `a slow home holds the list back only until the wait runs out - then sections insert above it`() =
        runViewModelTest {
            val home = CompletableDeferred<AppResult<HomeDto>>()
            val vm = TripListViewModel(FakeTripRepository(home = { home.await() }), waitlistMemory())

            runCurrent()
            assertTrue(vm.uiState.value.awaitingHome, "Home gets a short wait, so its sections usually land with the list")

            advanceTimeBy(HOME_WAIT_MILLIS - 1)
            runCurrent()
            assertTrue(vm.uiState.value.awaitingHome)

            advanceTimeBy(1)
            runCurrent()
            assertFalse(vm.uiState.value.awaitingHome, "the wait is capped: the list shows without Home")
            assertTrue(
                vm.uiState.value.sections
                    .isEmpty(),
            )

            home.complete(AppResult.Success(HomeDto(listOf(banners))))
            runCurrent()
            assertEquals(listOf(banners), vm.uiState.value.sections, "late sections are still shown")
            assertFalse(vm.uiState.value.awaitingHome)
        }

    @Test
    fun `a home that never answers does not keep the list behind the spinner`() =
        runViewModelTest {
            val vm = TripListViewModel(FakeTripRepository(home = { awaitCancellation() }), waitlistMemory())

            advanceTimeBy(HOME_WAIT_MILLIS)
            runCurrent()

            assertFalse(vm.uiState.value.awaitingHome)
        }

    @Test
    fun `a home that answers first ends the wait at once - and cancels the timer`() =
        runViewModelTest {
            val vm = TripListViewModel(FakeTripRepository(home = { AppResult.Success(HomeDto(listOf(row))) }), waitlistMemory())

            // If the wait's timer were still pending, running everything would move the virtual clock to it.
            advanceUntilIdle()

            assertEquals(0L, currentTime, "the timer was cancelled: no virtual time passed")
            assertFalse(vm.uiState.value.awaitingHome)
            assertEquals(listOf(row), vm.uiState.value.sections)
        }

    @Test
    fun `a refresh cancels the home read still in flight - a stale answer never lands last`() =
        runViewModelTest {
            val answers = ArrayDeque(listOf(CompletableDeferred<AppResult<HomeDto>>(), CompletableDeferred()))
            val pending = answers.toList()
            val vm = TripListViewModel(FakeTripRepository(home = { answers.removeFirst().await() }), waitlistMemory())
            runCurrent()

            vm.refresh()
            runCurrent()
            pending[1].complete(AppResult.Success(HomeDto(listOf(row))))
            runCurrent()
            pending[0].complete(AppResult.Success(HomeDto(listOf(banners))))
            advanceUntilIdle()

            assertEquals(listOf(row), vm.uiState.value.sections, "the first read's answer came last, and was dropped")
        }

    @Test
    fun `retry reads home again - and a failed retry keeps the sections already shown`() =
        runViewModelTest {
            var homeAnswer: AppResult<HomeDto> = AppResult.Success(HomeDto(listOf(banners)))
            val repository = FakeTripRepository(home = { homeAnswer })
            val vm = TripListViewModel(repository, waitlistMemory())
            advanceUntilIdle()

            homeAnswer = AppResult.Failure(AppError.Timeout)
            vm.refresh()
            advanceUntilIdle()

            assertEquals(2, repository.homeReads)
            assertEquals(listOf(banners), vm.uiState.value.sections)
        }
}
