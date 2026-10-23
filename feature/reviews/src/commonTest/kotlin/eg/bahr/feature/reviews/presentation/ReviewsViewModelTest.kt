package eg.bahr.feature.reviews.presentation

import androidx.paging.testing.ErrorRecovery
import androidx.paging.testing.LoadErrorHandler
import androidx.paging.testing.asSnapshot
import eg.bahr.core.catalog.model.RatingSummaryDto
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.catalog.model.StarCountDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.reviews.data.FakeReviewsRepository
import eg.bahr.feature.reviews.data.ReviewRequest
import eg.bahr.feature.reviews.data.TripFixtures.page
import eg.bahr.feature.reviews.data.TripFixtures.trip
import eg.bahr.feature.reviews.model.ReviewPageDto
import eg.bahr.feature.reviews.model.ReviewSort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "See all reviews" (M4-M11): `GET /reviews?trip=` or `?host=`, paged (Paging 3, checked with
 * paging-testing), with the sort and star chips, and on a host's reviews the Trip filter.
 */
class ReviewsViewModelTest {
    /** Answers every request with one review naming it, and a summary per scope (a trip's has 2 reviews, a host's 5). */
    private fun repository(
        totalPages: Int = 1,
        hostTrips: List<String> = listOf("burullus-dawn", "burullus-sunset"),
    ) = FakeReviewsRepository(
        reviews = { r ->
            AppResult.Success(
                ReviewPageDto(
                    items = listOf(ReviewDto(author = "${r.trip ?: r.host}/${r.sort.wire}/${r.stars}/${r.page}", rating = r.stars ?: 5)),
                    page = r.page,
                    size = 1,
                    totalItems = totalPages.toLong(),
                    totalPages = totalPages,
                    rating = summary(if (r.trip != null) 2 else 5),
                ),
            )
        },
        listFiltered = { page(hostTrips.mapIndexed { i, slug -> trip(i).copy(slug = slug, title = "Title of $slug") }) },
    )

    private fun tripVm(repository: FakeReviewsRepository) =
        ReviewsViewModel(trip = TRIP, host = null, title = "Dawn on the lake", repository)

    private fun hostVm(repository: FakeReviewsRepository) = ReviewsViewModel(trip = null, host = HOST, title = "Ashraf El Bahr", repository)

    @Test
    fun `a trip's reviews are paged from the trip - newest first - with the summary from the answer`() =
        runViewModelTest {
            val repository = repository(totalPages = 2)
            val vm = tripVm(repository)
            assertNull(vm.uiState.value.rating, "no summary before the first answer: never the trip page's authored count")

            val shown = vm.reviews.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf("$TRIP/newest/null/0", "$TRIP/newest/null/1"), shown.map { it.author })
            assertEquals(
                listOf(ReviewRequest(TRIP, null, ReviewSort.NEWEST, null, 0), ReviewRequest(TRIP, null, ReviewSort.NEWEST, null, 1)),
                repository.reviewRequests,
            )
            assertEquals(
                2,
                vm.uiState.value.rating
                    ?.count,
            )
            assertEquals("Dawn on the lake", vm.uiState.value.subtitle)
            assertTrue(repository.requestedQueries.isEmpty(), "a trip's reviews have no Trip filter to fill")
            assertTrue(
                vm.uiState.value.tripFilter
                    .isEmpty(),
            )
        }

    @Test
    fun `a sort chip starts the list over in that order - the summary stays`() =
        runViewModelTest {
            val repository = repository()
            val vm = tripVm(repository)
            vm.reviews.asSnapshot()
            val before = vm.uiState.value.rating

            vm.selectSort(ReviewSort.LOWEST)
            val shown = vm.reviews.asSnapshot()

            assertEquals(listOf("$TRIP/lowest/null/0"), shown.map { it.author })
            assertEquals(ReviewSort.LOWEST, vm.uiState.value.sort)
            assertEquals(before, vm.uiState.value.rating)
        }

    @Test
    fun `a star chip narrows the list - tapping it again clears it`() =
        runViewModelTest {
            val repository = repository()
            val vm = tripVm(repository)
            vm.reviews.asSnapshot()

            vm.selectStars(4)
            assertEquals(listOf("$TRIP/newest/4/0"), vm.reviews.asSnapshot().map { it.author })
            assertTrue(vm.uiState.value.isFiltered)

            vm.selectStars(4)
            assertNull(vm.uiState.value.stars)
            assertFalse(vm.uiState.value.isFiltered)
            vm.reviews.asSnapshot()

            assertEquals(listOf(null, 4, null), repository.reviewRequests.map { it.stars })
        }

    @Test
    fun `one star chip at a time - another replaces it`() =
        runViewModelTest {
            val vm = tripVm(repository())

            vm.selectStars(5)
            vm.selectStars(2)

            assertEquals(2, vm.uiState.value.stars)
        }

    @Test
    fun `rapid chip taps send one request for the last - the same query is not asked again`() =
        runViewModelTest {
            val repository = repository()
            val vm = tripVm(repository)
            vm.reviews.asSnapshot()

            vm.selectStars(5)
            vm.selectStars(3)
            vm.selectSort(ReviewSort.HIGHEST)
            // Back to the same query as the last one: nothing new.
            vm.selectSort(ReviewSort.HIGHEST)
            vm.reviews.asSnapshot()

            assertEquals(
                listOf(ReviewRequest(TRIP, null, ReviewSort.NEWEST, null, 0), ReviewRequest(TRIP, null, ReviewSort.HIGHEST, 3, 0)),
                repository.reviewRequests,
            )
        }

    @Test
    fun `a host's reviews come from the host - with the host's trips as the Trip filter`() =
        runViewModelTest {
            val repository = repository()
            val vm = hostVm(repository)

            val shown = vm.reviews.asSnapshot()
            advanceUntilIdle()

            assertEquals(listOf("$HOST/newest/null/0"), shown.map { it.author })
            assertEquals(HOST, repository.reviewRequests.single().host)
            assertNull(repository.reviewRequests.single().trip)
            assertEquals(listOf(HOST), repository.requestedQueries.map { it.host })
            assertEquals(
                listOf("burullus-dawn", "burullus-sunset"),
                vm.uiState.value.tripFilter
                    .map { it.slug },
            )
            assertNull(vm.uiState.value.tripSlug, "All trips at first")
            assertEquals(
                5,
                vm.uiState.value.rating
                    ?.count,
            )
        }

    @Test
    fun `the Trip filter switches the list to that trip - its summary and title - and All trips goes back`() =
        runViewModelTest {
            val repository = repository()
            val vm = hostVm(repository)
            vm.reviews.asSnapshot()
            advanceUntilIdle()
            vm.selectStars(5)
            vm.reviews.asSnapshot()

            vm.selectTrip("burullus-sunset")
            val shown = vm.reviews.asSnapshot()

            // The star filter stays across a trip switch.
            assertEquals(listOf("burullus-sunset/newest/5/0"), shown.map { it.author })
            assertEquals(
                2,
                vm.uiState.value.rating
                    ?.count,
                "the summary is the trip's now",
            )
            assertEquals("Title of burullus-sunset", vm.uiState.value.subtitle)
            assertTrue(vm.uiState.value.isFiltered)

            vm.selectTrip(null)
            vm.reviews.asSnapshot()

            assertEquals(ReviewRequest(null, HOST, ReviewSort.NEWEST, 5, 0), repository.reviewRequests.last())
            assertEquals(
                5,
                vm.uiState.value.rating
                    ?.count,
            )
            assertEquals("Ashraf El Bahr", vm.uiState.value.subtitle)
        }

    @Test
    fun `a host with one trip gets no Trip filter - and a failed trip list just leaves it out`() =
        runViewModelTest {
            val one = hostVm(repository(hostTrips = listOf("burullus-dawn")))
            advanceUntilIdle()
            assertTrue(
                one.uiState.value.tripFilter
                    .isEmpty(),
            )

            val failing = repository().apply { listFiltered = { AppResult.Failure(AppError.Network) } }
            val vm = hostVm(failing)
            val shown = vm.reviews.asSnapshot()
            advanceUntilIdle()

            assertTrue(
                vm.uiState.value.tripFilter
                    .isEmpty(),
            )
            assertEquals(1, shown.size, "the reviews do not need the filter")
            assertFalse(vm.uiState.value.notAvailable)
        }

    @Test
    fun `the summary stays from the previous answer until the new one lands`() =
        runViewModelTest {
            val gate = CompletableDeferred<Unit>()
            val repository = repository()
            val answer = repository.reviews
            repository.reviews = { r -> if (r.trip != null) gate.await().let { answer(r) } else answer(r) }
            val vm = hostVm(repository)
            vm.reviews.asSnapshot()
            advanceUntilIdle()

            vm.selectTrip("burullus-dawn")
            advanceUntilIdle()

            assertEquals(
                5,
                vm.uiState.value.rating
                    ?.count,
                "the host's summary while the trip's is out",
            )
            gate.complete(Unit)
            vm.reviews.asSnapshot()
            assertEquals(
                2,
                vm.uiState.value.rating
                    ?.count,
            )
        }

    @Test
    fun `NOT_FOUND is not available - not an error to retry`() =
        runViewModelTest {
            val vm = tripVm(FakeReviewsRepository(reviews = { NOT_FOUND }))

            vm.reviews.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })

            assertTrue(vm.uiState.value.notAvailable)
        }

    @Test
    fun `a filtered trip gone since the filter was offered drops back to the host's reviews`() =
        runViewModelTest {
            val repository = repository(hostTrips = listOf("a", "b", "gone"))
            val answer = repository.reviews
            repository.reviews = { r -> if (r.trip == "gone") NOT_FOUND else answer(r) }
            val vm = hostVm(repository)
            vm.reviews.asSnapshot()
            advanceUntilIdle()

            vm.selectTrip("gone")
            vm.reviews.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })
            val shown = vm.reviews.asSnapshot()

            assertFalse(vm.uiState.value.notAvailable)
            assertNull(vm.uiState.value.tripSlug)
            assertEquals(
                listOf("a", "b"),
                vm.uiState.value.tripFilter
                    .map { it.slug },
            )
            assertEquals(listOf("$HOST/newest/null/0"), shown.map { it.author })
        }

    @Test
    fun `any other failure is the list's error - a burst of Retry taps reads it once more`() =
        runViewModelTest {
            var answer: AppResult<ReviewPageDto> = AppResult.Failure(AppError.Network)
            val repository = FakeReviewsRepository(reviews = { answer })
            val vm = tripVm(repository)
            vm.reviews.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })
            assertFalse(vm.uiState.value.notAvailable)
            assertEquals(1, repository.reviewRequests.size)

            answer = AppResult.Success(ReviewPageDto(items = listOf(ReviewDto(author = "Back")), totalPages = 1, rating = summary(1)))
            vm.retry()
            vm.retry()
            vm.retry()
            val shown = vm.reviews.asSnapshot()
            advanceTimeBy(THROTTLE_PASSED_MS)

            assertEquals(listOf("Back"), shown.map { it.author })
            assertEquals(2, repository.reviewRequests.size)
        }

    @Test
    fun `the chips' order follows reading order - trip row then sort then 5 down to 1 stars`() {
        val trips = listOf(ReviewTrip("a", "A"), ReviewTrip("b", "B"))
        val base = ReviewsUiState(tripSlug = null, hostSlug = HOST, hostTrips = trips)

        val orders =
            listOf(
                base,
                base.copy(stars = 5),
                base.copy(stars = 1),
                base.copy(sort = ReviewSort.HIGHEST),
                base.copy(tripSlug = "a"),
                base.copy(tripSlug = "b"),
            ).map { it.chipOrder }

        assertEquals(orders.sorted(), orders)
        assertEquals(orders.distinct(), orders)
    }

    private fun summary(count: Int) =
        RatingSummaryDto(
            average = 4.5,
            count = count,
            distribution = (5 downTo 1).map { StarCountDto(it, if (it == 5) count else 0) },
        )

    private companion object {
        const val TRIP = "burullus-dawn"
        const val HOST = "ashraf-el-bahr"
        const val THROTTLE_PASSED_MS = 2_000L
        val NOT_FOUND = AppResult.Failure(AppError.Api("NOT_FOUND", "Not found", 404))
    }
}
