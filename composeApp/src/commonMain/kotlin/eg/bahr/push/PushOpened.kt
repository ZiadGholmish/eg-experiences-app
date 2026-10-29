package eg.bahr.push

import eg.bahr.deeplink.AppDeepLinkInbox

/**
 * A tap on one of our pushes (M3-M5), from Android's notification intent or iOS's notification delegate, with the
 * push's string data (openapi `registerDevice`, "Push payload"): its `link` opens like any App Link / Universal Link,
 * in the Home tab; a `waitlist_seat_opened` push also says a place opened on the date, once the page has checked it is
 * still open. A push without a `link` (a broadcast that points nowhere) just opens the app.
 */
internal fun openPush(
    link: String?,
    type: String?,
) {
    if (link.isNullOrBlank()) return
    AppDeepLinkInbox.offer(link, placeOpened = type == TYPE_SEAT_OPENED)
}

/** openapi `registerDevice`'s push `type` for "a place opened". */
internal const val TYPE_SEAT_OPENED = "waitlist_seat_opened"

/** The push data keys a tap reads (openapi `registerDevice`, "Push payload"). */
internal const val PUSH_LINK = "link"
internal const val PUSH_TYPE = "type"
