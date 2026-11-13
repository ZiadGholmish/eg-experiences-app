package eg.bahr.feature.tripdetails.presentation

import eg.bahr.core.catalog.data.LikeState
import eg.bahr.core.catalog.data.TripLikeStore
import eg.bahr.core.catalog.model.TripLikeDto
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.tripdetails.data.FakeTripDetailRepository
import eg.bahr.feature.tripdetails.data.TripFixtures.detail
import eg.bahr.feature.tripdetails.data.TripFixtures.saturdays
import eg.bahr.feature.tripdetails.data.TripFixtures.trip
import eg.bahr.feature.tripdetails.data.waitlistMemory
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MP-M1: the trip page's heart, through the app's one heart store. */
class TripDetailLikeTest {
    private val signedIn = MutableStateFlow(true)
    private val sent = mutableListOf<Boolean>()

    private fun TestScope.store() =
        TripLikeStore(
            remote = { slug, liked ->
                sent += liked
                AppResult.Success(TripLikeDto(slug, likeCount = if (liked) 8 else 7, likedByMe = liked))
            },
            errors = AppErrorController(),
            scope = backgroundScope,
            signedIn = signedIn,
        )

    /** The store's work is in `backgroundScope`, which `advanceUntilIdle` does not wait for. */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    @Test
    fun `the heart is drawn from the list card before the trip arrives`() =
        runViewModelTest {
            val repo =
                FakeTripDetailRepository(
                    tripBySlug = { awaitCancellation() },
                    departuresFor = { awaitCancellation() },
                    cards = mapOf("burullus-dawn" to trip(1).copy(slug = "burullus-dawn", likeCount = 7, likedByMe = true)),
                )
            val vm = TripDetailViewModel("burullus-dawn", repo, waitlistMemory(), hearts = store())
            settle()

            assertEquals(LikeState(liked = true, count = 7), vm.uiState.value.like)
        }

    @Test
    fun `a cold open has no heart until the trip arrives`() =
        runViewModelTest {
            val repo = FakeTripDetailRepository(tripBySlug = { awaitCancellation() }, departuresFor = { awaitCancellation() })
            val vm = TripDetailViewModel("burullus-dawn", repo, waitlistMemory(), hearts = store())
            settle()

            assertNull(vm.uiState.value.like)
        }

    @Test
    fun `a tap likes the trip - and the store settles it for every screen`() =
        runViewModelTest {
            val store = store()
            val repo =
                FakeTripDetailRepository(
                    tripBySlug = { AppResult.Success(detail().copy(likeCount = 7, likedByMe = false)) },
                    departuresFor = { AppResult.Success(saturdays()) },
                )
            val vm = TripDetailViewModel("burullus-dawn", repo, waitlistMemory(), hearts = store)
            settle()
            assertEquals(LikeState(liked = false, count = 7), vm.uiState.value.like)

            assertTrue(vm.toggleLike())
            runCurrent()
            assertEquals(LikeState(liked = true, count = 8), vm.uiState.value.like)
            settle()

            assertEquals(listOf(true), sent)
            // A card of the same trip, loaded earlier as not liked, shows the like too.
            assertEquals(LikeState(liked = true, count = 8), store.likes.value.stateOf("burullus-dawn", likeCount = 7, likedByMe = false))
        }

    @Test
    fun `a guest's tap is refused - for the sign-in sheet`() =
        runViewModelTest {
            signedIn.value = false
            val repo =
                FakeTripDetailRepository(tripBySlug = { AppResult.Success(detail()) }, departuresFor = { AppResult.Success(saturdays()) })
            val vm = TripDetailViewModel("burullus-dawn", repo, waitlistMemory(), hearts = store())
            settle()

            assertFalse(vm.toggleLike())
            settle()
            assertTrue(sent.isEmpty())
        }

    private companion object {
        const val SETTLE_MILLIS = 10_000L
    }
}
