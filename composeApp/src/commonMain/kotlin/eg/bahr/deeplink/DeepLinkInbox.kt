package eg.bahr.deeplink

import eg.bahr.core.common.locale.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a [DeepLink] can open. The Home tab's NavHost implements it over its NavController (links open
 * in the Home tab's stack, M4-M8); tests use a fake.
 */
internal interface DeepLinkDestinations {
    /** The language the app shows now. */
    val language: AppLanguage

    /** A booking is under way in the Home tab (date + party, or held seats): see [DeepLinkInbox.dispatch]. */
    val bookingInProgress: Boolean

    /**
     * Stores [language] as the app's language. Every Compose root then redraws in it, and that rebuilds
     * the NavHost asking: so this is all it does, and the link is opened by the rebuilt one.
     */
    fun switchLanguage(language: AppLanguage)

    /** The trip page, with the trip list under it so back goes to the list. */
    fun openTrip(slug: String)

    fun openList()
}

/** Where a linked trip page goes on the back stack. */
internal enum class TripLinkPlacement {
    /** Everything above the trip list is dropped: back from the linked trip is the list. */
    AboveList,

    /** On top of what is open: back returns to it. */
    OnTop,
}

/**
 * A booking under way (date + party, or held seats with a live countdown) is kept under a linked
 * trip rather than dropped with its hold (decided 2026-10-09, M1-M2 review S2): Back from the trip
 * returns to it, and the held-seats screen re-reads its deadline when it shows again.
 */
internal fun tripLinkPlacement(bookingInProgress: Boolean): TripLinkPlacement =
    if (bookingInProgress) TripLinkPlacement.OnTop else TripLinkPlacement.AboveList

/**
 * Holds the latest link the platform delivered until the NavHost can open it.
 *
 * The platform entry points ([offer]) and the NavHost ([dispatch]) run at different times: on a cold
 * start the link arrives before there is a NavHost, and the splash screen has to finish first. A
 * newer link replaces one not yet opened; the user tapped it last.
 *
 * It holds the raw URL, not a parsed [DeepLink], so the entry points need no configuration and can
 * offer a link before the app's dependency graph exists.
 */
internal class DeepLinkInbox {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** The pending link whose `?lang=` was already asked for (see [dispatch]). */
    private var languageAskedFor: String? = null

    fun offer(url: String) {
        _pending.value = url
    }

    /**
     * Opens the pending link, if any, exactly once.
     *
     * A trip link in another language than the app's (`?lang=`, M4-M8) first switches the language and
     * stays pending: the switch rebuilds the NavHost, so a trip opened now would be thrown away with it.
     * The rebuilt NavHost dispatches again and opens it. That second pass opens the link whatever the
     * language then is, so a write that failed (or never arrived) cannot hold the link forever.
     *
     * With a booking under way the language is not switched at all (M4-M8 review #3): the switch would
     * rebuild the NavHost and throw away the date + party step the linked trip is meant to sit on top
     * of (M1-M2 S2). The trip opens on top, in the reader's language; the link's language is skipped,
     * not saved for later. A switch that does happen is kept, like a pick in Settings.
     */
    fun dispatch(
        parser: DeepLinkParser,
        destinations: DeepLinkDestinations,
    ) {
        val url = _pending.value ?: return
        val link = parser.parse(url)
        val wanted = (link as? DeepLink.Trip)?.language
        if (wanted != null && wanted != destinations.language && languageAskedFor != url && !destinations.bookingInProgress) {
            languageAskedFor = url
            destinations.switchLanguage(wanted)
            return
        }
        // compareAndSet, so a link offered while this one is being opened is kept for the next dispatch.
        if (!_pending.compareAndSet(url, null)) return
        languageAskedFor = null
        when (link) {
            is DeepLink.Trip -> destinations.openTrip(link.slug)
            DeepLink.Unknown -> destinations.openList()
        }
    }
}

/**
 * The one inbox per process. The OS delivers links to the process (Activity intent, SwiftUI
 * `onOpenURL`), and on iOS that can happen before Koin has started, so this is not a Koin binding.
 */
internal val AppDeepLinkInbox = DeepLinkInbox()
