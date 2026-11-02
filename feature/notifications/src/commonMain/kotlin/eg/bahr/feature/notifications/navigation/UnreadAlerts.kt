package eg.bahr.feature.notifications.navigation

import kotlinx.coroutines.flow.StateFlow

/**
 * The bell's unread count (M3-M6, handoff §1: `unread-count`, refreshed on app foreground and on push received), for
 * :composeApp to hand Home's bell and to call from the platform's entry points. One per app (Koin), shared with the
 * notifications page, whose every change (read, unread, delete, mark all) the count follows.
 */
interface UnreadAlerts {
    /** Unread items in the inbox on show (the account's, or this install's guest inbox); null while not known. */
    val count: StateFlow<Int?>

    /** Read the count again (the app came to the front). */
    fun refresh()

    /** A push arrived: the count is read again, and an open notifications page reads its list again. */
    fun onPushReceived()
}
