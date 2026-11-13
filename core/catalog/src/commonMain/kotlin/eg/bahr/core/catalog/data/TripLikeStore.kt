package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.model.TripLikeDto
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A heart as drawn: filled or not, and the trip's public like count. */
data class LikeState(
    val liked: Boolean,
    val count: Int,
)

/**
 * What every heart in the app shows (MP-M1), one snapshot for all screens: the hearts tapped in this run (optimistic
 * until the server answers, then the server's values) and the fresh reads seen since, laid over the values a card or a
 * trip page was loaded with. So a like on the trip page shows on its card in Home, search or Liked trips when the reader
 * goes back, although those lists still hold the cards they loaded.
 *
 * Signed out ([signedIn] false), no heart is filled, whatever a card loaded while signed in still says (`likedByMe`
 * comes only with a token); the counts stay.
 */
data class TripLikes(
    val signedIn: Boolean = false,
    private val entries: Map<String, LikeState> = emptyMap(),
) {
    fun stateOf(trip: TripCardDto): LikeState = stateOf(trip.slug, trip.likeCount, trip.likedByMe)

    /** The heart for [slug], loaded as [likeCount] / [likedByMe] (null: a guest's read, "not liked"). */
    fun stateOf(
        slug: String,
        likeCount: Int,
        likedByMe: Boolean?,
    ): LikeState {
        val known = entries[slug] ?: LikeState(liked = likedByMe == true, count = likeCount.coerceAtLeast(0))
        return if (signedIn) known else known.copy(liked = false)
    }

    internal fun with(
        slug: String,
        state: LikeState,
    ) = copy(entries = entries + (slug to state))

    internal fun entry(slug: String): LikeState? = entries[slug]

    companion object {
        val None = TripLikes()
    }
}

/**
 * The hearts as a screen sees them (MP-M1): the app's one snapshot and the tap. [TripLikeStore] in the app; [None] (no
 * heart can be tapped, nobody signed in) where a test does not look at hearts.
 */
interface TripHearts {
    val likes: StateFlow<TripLikes>

    /** A tap on [slug]'s heart, drawn as [shown]; false when nobody is signed in (nothing changed). */
    fun toggle(
        slug: String,
        shown: LikeState,
    ): Boolean

    /** A fresh read of [slug]'s heart from the server (the trip page's), for every screen. */
    fun seen(
        slug: String,
        likeCount: Int,
        likedByMe: Boolean?,
    )

    object None : TripHearts {
        override val likes: StateFlow<TripLikes> = MutableStateFlow(TripLikes.None).asStateFlow()

        override fun toggle(
            slug: String,
            shown: LikeState,
        ) = false

        override fun seen(
            slug: String,
            likeCount: Int,
            likedByMe: Boolean?,
        ) = Unit
    }
}

/** Sends one heart's state to the server: `likeTrip` when [liked], else `unlikeTrip`. A seam for tests. */
fun interface TripLikeRemote {
    suspend fun setLiked(
        slug: String,
        liked: Boolean,
    ): AppResult<TripLikeDto>
}

/**
 * The hearts (MP-M1): one app-wide store (a Koin single) every screen with a heart reads ([likes]) and taps through
 * ([toggle]), so the heart is the same on every screen.
 *
 * - **Optimistic:** a tap flips the heart and its count at once.
 * - **Debounced:** the request goes [debounceMillis] after the last tap, with the heart's final state, and not at all
 *   when that is what the server already has (a double tap costs nothing: like and unlike share one rate-limit budget).
 * - **One request per trip at a time;** taps while one is out are sent after it, if they changed the state again.
 * - **Settled on the server's answer** (`TripLike`), or **rolled back** to the last state the server confirmed when the
 *   call fails, with the error said through [errors] (RATE_LIMITED "too many tries", UNAUTHORIZED "you've been signed
 *   out", which core:network's `bahrAuth` has then made true by dropping the tokens).
 * - The requests run in [scope], the app's, not a screen's: a like tapped just before Back still reaches the server.
 *
 * [signedIn] is the session's "is someone signed in". Any change of it forgets every tap and fresh read of the run: they
 * were the previous account's. Every call happens on the main thread (taps, view-model coroutines, and [scope] on the
 * main dispatcher), so the bookkeeping needs no lock.
 */
class TripLikeStore(
    private val remote: TripLikeRemote,
    private val errors: AppErrorController,
    private val scope: CoroutineScope,
    signedIn: Flow<Boolean>,
    private val debounceMillis: Long = LIKE_DEBOUNCE_MILLIS,
) : TripHearts {
    private val _likes = MutableStateFlow(TripLikes.None)
    override val likes: StateFlow<TripLikes> = _likes.asStateFlow()

    /** A trip whose heart was tapped and is not settled yet. [confirmed] is what the server last said (or the card did). */
    private class Pending(
        var confirmed: LikeState,
    ) {
        var debounce: Job? = null
        var inFlight = false
    }

    private val pending = mutableMapOf<String, Pending>()

    /** Bumped on every sign-in or sign-out: an answer for the previous account's tap is dropped. */
    private var generation = 0

    init {
        scope.launch {
            signedIn.distinctUntilChanged().collect { now ->
                generation++
                pending.values.forEach { it.debounce?.cancel() }
                pending.clear()
                _likes.value = TripLikes(signedIn = now)
            }
        }
    }

    /**
     * A tap on the heart of [slug], drawn as [shown] (from [likes]). False when nobody is signed in: nothing changes,
     * and the caller offers the sign-in sheet instead.
     */
    override fun toggle(
        slug: String,
        shown: LikeState,
    ): Boolean {
        if (!_likes.value.signedIn) return false
        val current = _likes.value.entry(slug) ?: shown
        val slot = pending.getOrPut(slug) { Pending(confirmed = current) }
        val next = !current.liked
        // The count follows the heart until the server's own count replaces it (not money: no rule against it).
        val count = (current.count + if (next) 1 else -1).coerceAtLeast(0)
        _likes.update { it.with(slug, LikeState(next, count)) }
        slot.debounce?.cancel()
        slot.debounce =
            scope.launch {
                delay(debounceMillis)
                slot.debounce = null
                // A request is out: its answer sends this state after it (see send).
                if (!slot.inFlight) send(slug, slot)
            }
        return true
    }

    /**
     * Fresh reads from the server (a list page, the trip page): what they say becomes every screen's heart, so a list
     * loaded later does not undo what an older one shows. A trip with a tap still pending keeps it.
     */
    fun seen(cards: List<TripCardDto>) = cards.forEach { seen(it.slug, it.likeCount, it.likedByMe) }

    override fun seen(
        slug: String,
        likeCount: Int,
        likedByMe: Boolean?,
    ) {
        // A guest's read says nothing about the heart, only the count; and a pending tap is newer than any read.
        if (slug in pending) return
        val state = _likes.value
        val liked = if (state.signedIn) likedByMe ?: return else false
        _likes.update { it.with(slug, LikeState(liked, likeCount.coerceAtLeast(0))) }
    }

    /** Stops the store's work: its scope ends with the Koin graph that made it. */
    fun close() = scope.cancel()

    /** Sends [slug]'s heart until the server agrees with it, or rolls it back on a failure. */
    private suspend fun send(
        slug: String,
        slot: Pending,
    ) {
        val started = generation
        while (true) {
            val wanted = _likes.value.entry(slug)?.liked ?: return
            if (wanted == slot.confirmed.liked) {
                // Tapped back to where the server is: nothing to send; its count stands.
                settle(slug, slot.confirmed)
                return
            }
            slot.inFlight = true
            val result = remote.setLiked(slug, wanted)
            slot.inFlight = false
            if (generation != started) {
                // Signed out meanwhile: the heart is already the guest's. A 401 is what signed out (bahrAuth dropped the
                // tokens: the account was deleted, or its sign-in ran out), so it is still said ("you've been signed out").
                if (result is AppResult.Failure) errors.show(result.error)
                return
            }
            when (result) {
                is AppResult.Success -> {
                    slot.confirmed = LikeState(result.data.likedByMe, result.data.likeCount.coerceAtLeast(0))
                    // Another tap is waiting out its debounce: it sends (or settles) when that ends.
                    if (slot.debounce != null) return
                    // Otherwise loop: settle if the heart still says what the server now has, else send the change.
                }
                is AppResult.Failure -> {
                    slot.debounce?.cancel()
                    settle(slug, slot.confirmed)
                    // Branches on nothing: RATE_LIMITED ("too many tries"), UNAUTHORIZED ("you've been signed out") and a lost
                    // connection have their own lines; anything else (NOT_FOUND: the trip is no longer published) the
                    // generic one, as every unmapped code (core:localization's messageRes).
                    errors.show(result.error)
                    return
                }
            }
        }
    }

    private fun settle(
        slug: String,
        state: LikeState,
    ) {
        pending.remove(slug)
        _likes.update { it.with(slug, state) }
    }

    companion object {
        /**
         * How long after the last tap the heart's state goes to the server: long enough to fold a double tap into
         * nothing, short enough that leaving the page right after a tap still sends it soon.
         */
        const val LIKE_DEBOUNCE_MILLIS = 600L
    }
}
