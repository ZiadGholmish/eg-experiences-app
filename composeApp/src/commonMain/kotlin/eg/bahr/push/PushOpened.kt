package eg.bahr.push

import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.feature.booking.di.HoldReminder

/**
 * A tap on one of our pushes (M3-M5), from Android's notification intent or iOS's notification delegate, with the
 * push's string data (openapi `registerDevice`, "Push payload"): its `link` opens like any App Link / Universal Link,
 * in the Home tab; a `waitlist_seat_opened` push also says a place opened on the date, once the page has checked it is
 * still open; a hold reminder ([ref], M3-M7) opens that hold's seats. A push without a `link` (a broadcast that points
 * nowhere) just opens the app.
 */
internal fun openPush(
    link: String?,
    type: String?,
    ref: String? = null,
) {
    // The hold reminder (M3-M7; the phone's own, or a server `hold_expiring` push carrying the same keys): its held
    // seats, by reference, ahead of any link.
    if (type == HoldReminder.TYPE && !ref.isNullOrBlank()) {
        AppDeepLinkInbox.offerHold(ref)
        return
    }
    if (link.isNullOrBlank()) return
    AppDeepLinkInbox.offer(link, placeOpened = type == TYPE_SEAT_OPENED)
}

/** openapi `registerDevice`'s push `type` for "a place opened". */
internal const val TYPE_SEAT_OPENED = "waitlist_seat_opened"

/** The push data keys a tap reads (openapi `registerDevice`, "Push payload"). */
internal const val PUSH_LINK = "link"
internal const val PUSH_TYPE = "type"

/** A hold reminder's booking reference (M3-M7). */
internal const val PUSH_REF = HoldReminder.REF_KEY
