package eg.bahr.core.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.catalog.data.TripHearts
import eg.bahr.core.catalog.data.TripLikes
import eg.bahr.core.catalog.model.TripCardDto
import kotlinx.coroutines.launch

/*
 * The hearts in a screen (MP-M1), as the waiting-list tags are (WaitlistTags): a view model keeps the app's one
 * TripLikes snapshot in its UI state for as long as it lives, and taps go to the one TripLikeStore, so a heart tapped
 * on one screen is the same on every other.
 */

/** Keeps a screen's [TripLikes] in step with [store] for as long as the view model lives. */
fun ViewModel.collectTripLikes(
    store: TripHearts,
    onLikes: (TripLikes) -> Unit,
) {
    viewModelScope.launch { store.likes.collect(onLikes) }
}

/** A tap on [trip]'s heart, drawn from [likes]; false when nobody is signed in (see [heartTap]). */
fun TripHearts.toggle(
    trip: TripCardDto,
    likes: TripLikes,
): Boolean = toggle(trip.slug, likes.stateOf(trip))

/**
 * A screen's heart tap: [toggle] (its view model's, true when it went through), or the sign-in sheet ([onSignIn]) for a
 * guest, who has no likes to keep.
 */
fun <T> heartTap(
    toggle: (T) -> Boolean,
    onSignIn: () -> Unit,
): (T) -> Unit = { item -> if (!toggle(item)) onSignIn() }
