package eg.bahr.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a [DeepLink] can open. The NavHost implements it over its NavController; tests use a fake. */
internal interface DeepLinkDestinations {
    /** The trip page, with the trip list under it so back goes to the list. */
    fun openTrip(slug: String)

    fun openList()
}

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

    fun offer(url: String) {
        _pending.value = url
    }

    /** Opens the pending link, if any, exactly once. */
    fun dispatch(
        parser: DeepLinkParser,
        destinations: DeepLinkDestinations,
    ) {
        val url = _pending.value ?: return
        // compareAndSet, so a link offered while this one is being opened is kept for the next dispatch.
        if (!_pending.compareAndSet(url, null)) return
        when (val link = parser.parse(url)) {
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
