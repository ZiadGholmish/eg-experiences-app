package eg.bahr.feature.triplisting.presentation

import androidx.paging.testing.asSnapshot
import eg.bahr.core.catalog.data.LikeState
import eg.bahr.core.catalog.data.TripHearts
import eg.bahr.core.catalog.data.TripLikes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.triplisting.data.FakeTripListingRepository
import eg.bahr.feature.triplisting.data.TripFixtures.sectionPage
import eg.bahr.feature.triplisting.data.TripFixtures.trip
import eg.bahr.feature.triplisting.data.waitlistMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Account → Liked trips (MP-M1). */
class LikedTripsViewModelTest {
    private val signedIn = SessionState.SignedIn(accountName = "Mona", profileComplete = true, providers = setOf("google"))

    @Test
    fun `signed in - the liked trips page by page with the total`() =
        runViewModelTest {
            val repository =
                FakeTripListingRepository(likedTrips = { p -> sectionPage(listOf(trip(p)), page = p, totalPages = 2, totalItems = 2) })
            val vm = LikedTripsViewModel(repository, waitlistMemory(), MutableStateFlow(signedIn))

            val shown = vm.trips.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf("trip-0", "trip-1"), shown.map { it.slug })
            assertEquals(listOf(0, 1), repository.likedRequests)
            assertEquals(2L, vm.uiState.value.totalItems)
            assertIs<SessionState.SignedIn>(vm.uiState.value.session)
        }

    @Test
    fun `signed out - nothing is asked of the server and the page offers sign-in`() =
        runViewModelTest {
            val repository = FakeTripListingRepository()
            val vm = LikedTripsViewModel(repository, waitlistMemory(), MutableStateFlow(SessionState.SignedOut))
            backgroundScope.launch { vm.trips.collect {} }
            advanceUntilIdle()

            assertTrue(repository.likedRequests.isEmpty())
            assertEquals(SessionState.SignedOut, vm.uiState.value.session)
        }

    @Test
    fun `signing in from the page loads the list`() =
        runViewModelTest {
            val session = MutableStateFlow<SessionState>(SessionState.SignedOut)
            val repository = FakeTripListingRepository(likedTrips = { sectionPage(listOf(trip(1))) })
            val vm = LikedTripsViewModel(repository, waitlistMemory(), session)

            session.value = signedIn
            val shown = vm.trips.asSnapshot()

            assertEquals(listOf("trip-1"), shown.map { it.slug })
        }

    @Test
    fun `a heart tap goes to the app's hearts with the heart as drawn`() =
        runViewModelTest {
            val hearts = RecordingHearts()
            val vm = LikedTripsViewModel(FakeTripListingRepository(), waitlistMemory(), MutableStateFlow(signedIn), hearts)
            advanceUntilIdle()

            assertTrue(vm.toggleLike(trip(3).copy(likeCount = 4, likedByMe = true)))
            assertEquals(listOf("trip-3" to LikeState(liked = true, count = 4)), hearts.taps)
        }

    @Test
    fun `a guest's tap is refused - so the screen opens the sign-in sheet`() =
        runViewModelTest {
            val vm = LikedTripsViewModel(FakeTripListingRepository(), waitlistMemory(), MutableStateFlow(SessionState.SignedOut))
            advanceUntilIdle()
            assertFalse(vm.toggleLike(trip(3)))
        }

    /** Signed in, records each tap. */
    private class RecordingHearts : TripHearts {
        val taps = mutableListOf<Pair<String, LikeState>>()
        override val likes: StateFlow<TripLikes> = MutableStateFlow(TripLikes(signedIn = true))

        override fun toggle(
            slug: String,
            shown: LikeState,
        ): Boolean {
            taps += slug to shown
            return true
        }

        override fun seen(
            slug: String,
            likeCount: Int,
            likedByMe: Boolean?,
        ) = Unit
    }
}
