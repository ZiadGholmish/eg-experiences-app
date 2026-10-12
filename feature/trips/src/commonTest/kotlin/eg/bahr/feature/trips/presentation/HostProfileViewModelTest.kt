package eg.bahr.feature.trips.presentation

import androidx.paging.testing.ErrorRecovery
import androidx.paging.testing.LoadErrorHandler
import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.HostProfileDto
import eg.bahr.feature.trips.model.RatingSummaryDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The host page (M4-M10): the profile from `getHost` and the host's trips paged from
 * `GET /trips?host=`, read side by side; a NOT_FOUND from either is the one "not available" state
 * (M4-B7 review #2), any other failure the error with its retry.
 */
class HostProfileViewModelTest {
    private fun vm(repository: FakeTripRepository) = HostProfileViewModel(SLUG, "Red Sea Days", AVATAR, repository, waitlistMemory())

    @Test
    fun `the card's name and photo show before the profile answers - text first`() =
        runViewModelTest {
            val profile = CompletableDeferred<AppResult<HostProfileDto>>()
            val vm = vm(FakeTripRepository(hostBySlug = { profile.await() }))
            advanceUntilIdle()

            assertEquals("Red Sea Days", vm.uiState.value.name)
            assertEquals(AVATAR, vm.uiState.value.avatar)
            assertTrue(vm.uiState.value.isLoading)

            profile.complete(AppResult.Success(host(name = "Red Sea Days Co.")))
            advanceUntilIdle()

            assertEquals("Red Sea Days Co.", vm.uiState.value.name)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `the host's trips are paged from the list narrowed to the host - with the list's total`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    hostBySlug = { AppResult.Success(host()) },
                    listFiltered = { q -> page(listOf(trip(q.page)), page = q.page, totalPages = 2, totalItems = 2) },
                )
            val vm = vm(repository)

            val shown = vm.trips.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf("trip-0", "trip-1"), shown.map { it.slug })
            assertEquals(listOf(SLUG, SLUG), repository.requestedQueries.map { it.host })
            // Only the host narrows it: no category, filter or search goes with it.
            assertTrue(repository.requestedQueries.all { it.category == null && it.filter == null && it.q == null })
            assertEquals(2L, vm.uiState.value.totalTrips)
            assertEquals(listOf(SLUG), repository.hostReads)
        }

    @Test
    fun `a host with no reviews yet is shown - with no average`() =
        runViewModelTest {
            val vm = vm(FakeTripRepository(hostBySlug = { AppResult.Success(host(rating = RatingSummaryDto(count = 0))) }))
            advanceUntilIdle()

            assertNull(
                vm.uiState.value.profile
                    ?.rating
                    ?.average,
            )
            assertFalse(vm.uiState.value.notAvailable)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `a host the server does not show - NOT_FOUND - is not available - not an error to retry`() =
        runViewModelTest {
            val vm = vm(FakeTripRepository(hostBySlug = { NOT_FOUND }))
            advanceUntilIdle()

            assertTrue(vm.uiState.value.notAvailable)
            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `a NOT_FOUND from the host's trip list is not available too`() =
        runViewModelTest {
            // Approved when the profile was read, suspended by the time the list was.
            val repository = FakeTripRepository(hostBySlug = { AppResult.Success(host()) }, listFiltered = { NOT_FOUND })
            val vm = vm(repository)

            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })

            assertTrue(vm.uiState.value.notAvailable)
        }

    @Test
    fun `any other failure is the error - and retry reads the profile and the trips again`() =
        runViewModelTest {
            var profileAnswer: AppResult<HostProfileDto> = AppResult.Failure(AppError.Network)
            val repository =
                FakeTripRepository(
                    hostBySlug = { profileAnswer },
                    listFiltered = { q -> page(listOf(trip(1)), page = q.page) },
                )
            val vm = vm(repository)
            vm.trips.asSnapshot()
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.error)
            assertFalse(vm.uiState.value.notAvailable)
            val listReads = repository.requestedQueries.size

            profileAnswer = AppResult.Success(host())
            vm.retry()
            // A burst of taps is one request (M4-M6).
            vm.retry()
            val shown = vm.trips.asSnapshot()
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertEquals(
                "Red Sea Days",
                vm.uiState.value.profile
                    ?.name,
            )
            assertEquals(listOf(SLUG, SLUG), repository.hostReads)
            assertTrue(repository.requestedQueries.size > listReads)
            assertEquals(listOf("trip-1"), shown.map { it.slug })
        }

    @Test
    fun `retrying the trips alone leaves the profile as it is`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    hostBySlug = { AppResult.Success(host()) },
                    listFiltered = { q -> page(listOf(trip(1)), page = q.page) },
                )
            val vm = vm(repository)
            vm.trips.asSnapshot()
            advanceTimeBy(THROTTLE_PASSED_MS)

            vm.retryTrips()
            vm.trips.asSnapshot()
            advanceUntilIdle()

            assertEquals(listOf(SLUG), repository.hostReads)
        }

    private fun host(
        name: String = "Red Sea Days",
        rating: RatingSummaryDto = RatingSummaryDto(average = 4.5, count = 2),
    ) = HostProfileDto(slug = SLUG, kind = "company", name = name, tripsRun = 2, rating = rating)

    private companion object {
        const val SLUG = "red-sea-days"
        const val AVATAR = "http://127.0.0.1:9/host.png"
        const val THROTTLE_PASSED_MS = 2_000L
        val NOT_FOUND = AppResult.Failure(AppError.Api("NOT_FOUND", "Host not found", 404))
    }
}
