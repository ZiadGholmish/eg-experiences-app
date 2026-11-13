package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.TripLikeDto
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MP-M1: the hearts' store (optimistic, debounced, one request at a time, settled or rolled back). */
@OptIn(ExperimentalCoroutinesApi::class)
class TripLikeStoreTest {
    private val signedIn = MutableStateFlow(true)
    private val errors = AppErrorController()
    private val calls = mutableListOf<Pair<String, Boolean>>()

    /** The server: answers each call with [answer], or waits on [gate] first when it is set. */
    private var answer: (String, Boolean) -> AppResult<TripLikeDto> = { slug, liked ->
        AppResult.Success(TripLikeDto(slug, likeCount = if (liked) 13 else 12, likedByMe = liked))
    }
    private var gate: CompletableDeferred<Unit>? = null

    private fun TestScope.store(): TripLikeStore =
        TripLikeStore(
            remote = { slug, liked ->
                calls += slug to liked
                gate?.await()
                answer(slug, liked)
            },
            errors = errors,
            scope = backgroundScope,
            signedIn = signedIn,
        ).also { runCurrent() }

    /**
     * Runs the store's work, which lives in `backgroundScope` (the app's scope in the app): `advanceUntilIdle` does not
     * wait for background work, so the clock is moved past any debounce instead.
     */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    private val unliked = LikeState(liked = false, count = 12)

    private fun TripLikeStore.heart(slug: String = SLUG): LikeState = likes.value.stateOf(slug, likeCount = 12, likedByMe = false)

    @Test
    fun `a tap flips the heart and its count at once - then settles on the server's values`() =
        runTest {
            val store = store()
            assertTrue(store.toggle(SLUG, unliked))
            assertEquals(LikeState(liked = true, count = 13), store.heart())
            assertTrue(calls.isEmpty(), "nothing is sent before the debounce")

            answer = { slug, _ -> AppResult.Success(TripLikeDto(slug, likeCount = 40, likedByMe = true)) }
            settle()
            assertEquals(listOf(SLUG to true), calls)
            assertEquals(LikeState(liked = true, count = 40), store.heart())
        }

    @Test
    fun `a double tap sends nothing`() =
        runTest {
            val store = store()
            store.toggle(SLUG, unliked)
            advanceTimeBy(TripLikeStore.LIKE_DEBOUNCE_MILLIS / 2)
            store.toggle(SLUG, store.heart())
            settle()
            assertTrue(calls.isEmpty())
            assertEquals(unliked, store.heart())
        }

    @Test
    fun `a failed call rolls back and says why`() =
        runTest {
            val store = store()
            val limited = AppError.Api(code = "RATE_LIMITED", message = null, httpStatus = 429)
            answer = { _, _ -> AppResult.Failure(limited) }
            store.toggle(SLUG, unliked)
            settle()
            assertEquals(unliked, store.heart())
            assertEquals(limited, errors.current.first()?.error)
        }

    @Test
    fun `taps while a call is out are sent after it`() =
        runTest {
            val store = store()
            val held = CompletableDeferred<Unit>()
            gate = held
            store.toggle(SLUG, unliked)
            advanceTimeBy(TripLikeStore.LIKE_DEBOUNCE_MILLIS + 1)
            assertEquals(listOf(SLUG to true), calls)
            // Taken back while the like is still out: shown at once, sent only once the like has answered.
            store.toggle(SLUG, store.heart())
            advanceTimeBy(TripLikeStore.LIKE_DEBOUNCE_MILLIS + 1)
            assertEquals(1, calls.size)
            assertFalse(store.heart().liked)
            gate = null
            held.complete(Unit)
            settle()
            assertEquals(listOf(SLUG to true, SLUG to false), calls)
            assertEquals(LikeState(liked = false, count = 12), store.heart())
        }

    @Test
    fun `a guest's tap changes nothing`() =
        runTest {
            signedIn.value = false
            val store = store()
            assertFalse(store.toggle(SLUG, unliked))
            settle()
            assertTrue(calls.isEmpty())
            assertEquals(unliked, store.heart())
        }

    @Test
    fun `signed out - no heart is filled - whatever a card loaded says`() {
        val likes = TripLikes(signedIn = false)
        assertEquals(LikeState(liked = false, count = 5), likes.stateOf(SLUG, likeCount = 5, likedByMe = true))
        assertEquals(LikeState(liked = true, count = 5), TripLikes(signedIn = true).stateOf(SLUG, likeCount = 5, likedByMe = true))
        // An older server sends neither field: 0, not liked.
        assertEquals(LikeState(liked = false, count = 0), TripLikes(signedIn = true).stateOf(SLUG, likeCount = 0, likedByMe = null))
    }

    @Test
    fun `signing out forgets the run's taps - and an answer for them is dropped`() =
        runTest {
            val store = store()
            gate = CompletableDeferred()
            store.toggle(SLUG, unliked)
            advanceTimeBy(TripLikeStore.LIKE_DEBOUNCE_MILLIS + 1)
            signedIn.value = false
            runCurrent()
            gate?.complete(Unit)
            settle()
            assertNull(store.likes.value.entry(SLUG))
            assertFalse(store.likes.value.signedIn)
        }

    @Test
    fun `a 401 that signs the account out still says so`() =
        runTest {
            val store = store()
            val unauthorized = AppError.Api(code = "UNAUTHORIZED", message = null, httpStatus = 401)
            answer = { _, _ ->
                // core:network's bahrAuth drops the tokens before the answer comes back.
                signedIn.value = false
                AppResult.Failure(unauthorized)
            }
            store.toggle(SLUG, unliked)
            settle()
            assertFalse(store.likes.value.signedIn)
            assertEquals(unauthorized, errors.current.first()?.error)
            assertEquals(unliked, store.heart())
        }

    @Test
    fun `a fresh read becomes every screen's heart - but not over a pending tap`() =
        runTest {
            val store = store()
            store.seen(SLUG, likeCount = 30, likedByMe = true)
            assertEquals(LikeState(liked = true, count = 30), store.heart())

            store.toggle(SLUG, store.heart())
            store.seen(SLUG, likeCount = 30, likedByMe = true)
            assertEquals(LikeState(liked = false, count = 29), store.heart())
            settle()
            assertNotNull(store.likes.value.entry(SLUG))
        }

    private companion object {
        const val SLUG = "burullus-dawn"
        const val SETTLE_MILLIS = 10_000L
    }
}
